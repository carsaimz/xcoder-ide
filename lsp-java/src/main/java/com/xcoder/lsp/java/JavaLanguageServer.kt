package com.xcoder.lsp.java

import android.util.Log
import kotlinx.coroutines.*
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.Launcher
import org.eclipse.lsp4j.services.LanguageServer
import java.io.*
import java.util.concurrent.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-process Java Language Server using Eclipse JDT Language Server (jdt.ls).
 *
 * Based on AndroidIDE's JavaLanguageServer (319 lines) which manages:
 * - Connecting to jdtls via LSP protocol over stdio/TCP
 * - Handling the LSP initialize/initialized handshake
 * - Providing completion, definition, references, hover, diagnostics
 * - Managing server lifecycle (start, stop, restart)
 *
 * ## Architecture
 *
 * ```
 * sora-editor  ←→  LspClient  ←→  JavaLanguageServer  ←→  jdt.ls (subprocess)
 * ```
 *
 * The [JavaLanguageServer] manages the subprocess and LSP connection.
 * The [LspClient] implements the client-side LSP callbacks.
 * sora-editor's built-in LSP support handles editor integration.
 *
 * ## jdt.ls Requirements
 *
 * - Java 17+ runtime
 * - jdt.ls JAR or installation directory
 * - Workspace root for project source files
 *
 * ## Features
 *
 * - [initialize] handshake with server capabilities negotiation
 * - [getCompletion] at cursor position
 * - [getDefinition] (go to definition)
 * - [getReferences] (find all usages)
 * - [getHover] (documentation at cursor)
 * - [getSignatureHelp] (method parameter info)
 * - [getDiagnostics] (error/warning markers)
 * - [formatCode] (code formatting)
 * - [getDocumentSymbols] (outline/structure)
 * - [renameSymbol] (rename refactoring)
 */
@Singleton
open class JavaLanguageServer @Inject constructor() {

    companion object {
        private const val TAG = "JavaLanguageServer"
        private const val JDTLS_MAIN_CLASS = "org.eclipse.jdt.ls.core.internal.LanguageServerStarter"
        private const val DEFAULT_JAVA = "java"

        /** Well-known jdt.ls installation paths on Android. */
        private val JDTLS_SEARCH_PATHS = listOf(
            "/data/data/com.xcoder.ide/files/jdtls/bin/jdtls",
            "/data/data/com.xcoder.ide/files/tools/jdtls/bin/jdtls",
            "/data/data/com.xcoder.ide/jdtls/bin/jdtls",
            "/sdcard/xcoder/jdtls/bin/jdtls"
        )
    }

    // ── Server state ──────────────────────────────────────────────

    /** Current state of the language server connection. */
    enum class ServerState {
        /** Server not started. */
        STOPPED,
        /** Starting the server process. */
        STARTING,
        /** Server is running and ready. */
        RUNNING,
        /** Server crashed or disconnected. */
        ERROR
    }

    /** Connection mode. */
    enum class ConnectionMode {
        /** JSON-RPC over stdin/stdout (direct process pipes). */
        STDIO,
        /** JSON-RPC over TCP socket. */
        TCP
    }

    // ── Configuration ─────────────────────────────────────────────

    data class Config(
        val connectionMode: ConnectionMode = ConnectionMode.STDIO,
        val tcpHost: String = "localhost",
        val tcpPort: Int = 5037,
        val workspaceRoot: String = "/data/data/com.xcoder.ide/files/workspace",
        val jdtlsPath: String = "",
        val javaPath: String = DEFAULT_JAVA,
        val jvmArgs: List<String> = listOf("-Xmx512m", "-XX:+UseG1GC"),
        val initializationOptions: Map<String, Any> = emptyMap()
    )

    // ── Fields ────────────────────────────────────────────────────

    var config = Config()

    var state: ServerState = ServerState.STOPPED
        private set

    /** The JDT LS process. */
    private var serverProcess: Process? = null

    /** The LSP4J LanguageServer proxy. */
    private var languageServer: LanguageServer? = null

    /** Client-side LSP callbacks. */
    private var lspClient: LspClient? = null

    /** Thread pool for LSP operations. */
    private val executor = Executors.newCachedThreadPool { r ->
        Thread(r, "jdt-ls-worker").apply { isDaemon = true }
    }

    /** Coroutine scope for async operations. */
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** Server capabilities received during initialization. */
    var serverCapabilities: ServerCapabilities? = null
        private set

    /** Listeners for state changes. */
    private val stateListeners = mutableListOf<(ServerState) -> Unit>()

    /** Listeners for diagnostic updates. */
    private val diagnosticListeners = mutableListOf<(String, List<Diagnostic>) -> Unit>()

    // ── Lifecycle ─────────────────────────────────────────────────

    /**
     * Start the Java Language Server.
     *
     * Resolves the jdtls path, starts the process, and performs
     * the LSP initialize handshake.
     *
     * @return true if the server started successfully
     */
    fun startServer(): CompletableFuture<Boolean> {
        val future = CompletableFuture<Boolean>()

        if (state == ServerState.RUNNING || state == ServerState.STARTING) {
            future.complete(false)
            return future
        }

        updateState(ServerState.STARTING)

        executor.submit {
            try {
                val jdtlsPath = resolveJdtlsPath()
                if (jdtlsPath == null) {
                    Log.w(TAG, "jdtls not found — server will run in stub mode")
                    updateState(ServerState.ERROR)
                    future.complete(false)
                    return@submit
                }

                Log.d(TAG, "Starting jdtls at: $jdtlsPath")

                when (config.connectionMode) {
                    ConnectionMode.STDIO -> startStdio(jdtlsPath, future)
                    ConnectionMode.TCP -> startTcp(jdtlsPath, future)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start server", e)
                updateState(ServerState.ERROR)
                future.complete(false)
            }
        }

        return future
    }

    /**
     * Stop the language server and release resources.
     */
    fun stopServer() {
        try {
            scope.launch {
                languageServer?.shutdown()?.get(5, TimeUnit.SECONDS)
                languageServer?.exit()
            }
            serverProcess?.destroyForcibly()
        } catch (_: Exception) {}

        serverProcess = null
        languageServer = null
        lspClient = null
        updateState(ServerState.STOPPED)
    }

    /**
     * Restart the language server.
     */
    fun restartServer(): CompletableFuture<Boolean> {
        stopServer()
        return startServer()
    }

    // ── Connection methods ────────────────────────────────────────

    private fun startStdio(jdtlsPath: String, future: CompletableFuture<Boolean>) {
        val cmd = buildList {
            add(config.javaPath)
            addAll(config.jvmArgs)
            add("-jar")
            add(jdtlsPath)
            add("-data")
            add(config.workspaceRoot)
        }

        val processBuilder = ProcessBuilder(cmd)
            .directory(File(config.workspaceRoot))
            .redirectErrorStream(true)

        serverProcess = processBuilder.start()

        // Create LSP client with diagnostic callback
        lspClient = LspClient().apply {
            onDiagnostic = { uri, diagnostics ->
                diagnosticListeners.forEach { it(uri, diagnostics) }
            }
            onServerNotification = { method, params ->
                Log.d(TAG, "Server notification: $method")
            }
        }

        // Connect LSP4J launcher to process stdio
        val launcher = Launcher.createLauncher(
            lspClient,
            LanguageServer::class.java,
            serverProcess!!.inputStream,
            serverProcess!!.outputStream,
            executor,
            Consumer { message -> Log.v(TAG, "LSP: $message") }
        )

        languageServer = launcher.remoteProxy
        lspClient?.connect(languageServer!!)

        // Perform initialize handshake
        performInitialize(future)

        // Monitor process exit
        Thread {
            try { serverProcess!!.waitFor() }
            catch (_: InterruptedException) {}
            if (state == ServerState.RUNNING) {
                Log.w(TAG, "jdt.ls process exited unexpectedly")
                updateState(ServerState.ERROR)
            }
        }.start()
    }

    private fun startTcp(jdtlsPath: String, future: CompletableFuture<Boolean>) {
        // For TCP mode, we'd connect to an already-running jdtls
        // This is useful when jdtls is managed externally
        // Implementation: Socket → InputStream/OutputStream → Launcher
        // For now, fall back to STDIO
        Log.w(TAG, "TCP mode not fully implemented, falling back to STDIO")
        startStdio(jdtlsPath, future)
    }

    // ── LSP Initialize handshake ───────────────────────────────────

    private fun performInitialize(future: CompletableFuture<Boolean>) {
        try {
            val initParams = InitializeParams().apply {
                processId = ProcessHandle.current().pid()
                rootUri = "file://${config.workspaceRoot}"
                rootPath = config.workspaceRoot
                capabilities = ClientCapabilities().apply {
                    textDocument = TextDocumentClientCapabilities().apply {
                        completion = TextDocumentClientCapabilities.CompletionCapabilities().apply {
                            completionItem = CompletionItemCapabilities(
                                snippetSupport = true,
                                documentationFormat = listOf(MarkupKind.MARKDOWN, MarkupKind.PLAINTEXT)
                            )
                            contextSupport = true
                        }
                        hover = TextDocumentClientCapabilities.HoverCapabilities().apply {
                            contentFormat = listOf(MarkupKind.MARKDOWN, MarkupKind.PLAINTEXT)
                        }
                        signatureHelp = TextDocumentClientCapabilities.SignatureHelpCapabilities().apply {
                            signatureInformation = SignatureHelpCapabilitiesSignatureInformation().apply {
                                documentationFormat = listOf(MarkupKind.MARKDOWN, MarkupKind.PLAINTEXT)
                                parameterInformation = SignatureHelpCapabilitiesSignatureInformationParameterInformation(
                                    labelOffsetSupport = true
                                )
                            }
                        }
                        definition = TextDocumentClientCapabilities.DefinitionCapabilities()
                        references = TextDocumentClientCapabilities.ReferencesCapabilities()
                        rename = TextDocumentClientCapabilities.RenameCapabilities().apply {
                            prepareSupport = true
                        }
                        documentSymbol = TextDocumentClientCapabilities.DocumentSymbolCapabilities().apply {
                            hierarchicalDocumentSymbolSupport = true
                            symbolKind = SymbolKindCapabilities(
                                valueSet = SymbolKind.values().map { it.value }
                            )
                        }
                        codeAction = TextDocumentClientCapabilities.CodeActionCapabilities().apply {
                            codeActionLiteralSupport = CodeActionLiteralSupportCapabilities(
                                CodeActionKindCapabilities(
                                    valueSet = listOf(
                                        CodeActionKind.QuickFix,
                                        CodeActionKind.Refactor,
                                        CodeActionKind.RefactorRewrite,
                                        CodeActionKind.SourceOrganizeImports
                                    )
                                )
                            )
                        }
                        formatting = TextDocumentClientCapabilities.FormattingCapabilities()
                        rangeFormatting = TextDocumentClientCapabilities.RangeFormattingCapabilities()
                    }
                    workspace = WorkspaceClientCapabilities().apply {
                        applyEdit = true
                        didChangeConfiguration = DidChangeConfigurationCapabilities(
                            dynamicRegistration = false
                        )
                        executeCommand = ExecuteCommandCapabilities(
                            dynamicRegistration = false
                        )
                        symbol = WorkspaceSymbolCapabilities()
                        workspaceEdit = WorkspaceEditCapabilities().apply {
                            documentChanges = true
                            resourceOperations = listOf(
                                ResourceOperationKind.Create,
                                ResourceOperationKind.Delete,
                                ResourceOperationKind.Rename
                            )
                        }
                    }
                    window = WindowClientCapabilities()
                    experimental = null
                }

                if (config.initializationOptions.isNotEmpty()) {
                    initializationOptions = config.initializationOptions
                }
            }

            val initResult = languageServer!!.initialize(initParams).get(30, TimeUnit.SECONDS)
            serverCapabilities = initResult.capabilities

            // Send initialized notification
            languageServer!!.initialized(InitializedParams())

            updateState(ServerState.RUNNING)
            Log.d(TAG, "jdt.ls initialized: ${initResult.serverInfo?.name} ${initResult.serverInfo?.version}")
            future.complete(true)
        } catch (e: Exception) {
            Log.e(TAG, "Initialize handshake failed", e)
            updateState(ServerState.ERROR)
            future.complete(false)
        }
    }

    // ── LSP Features ──────────────────────────────────────────────

    /**
     * Request code completion at a position.
     */
    fun getCompletion(uri: String, content: String, line: Int, column: Int): CompletableFuture<List<CompletionItem>> {
        return ensureServerRunning {
            val params = CompletionParams(
                TextDocumentIdentifier(uri),
                Position(line, column)
            ).apply {
                context = CompletionContext(CompletionTriggerKind.Invoked)
            }
            val result = languageServer!!.textDocumentService.completion(params).get(10, TimeUnit.SECONDS)
            result.right?.items ?: result.left?.map { it.toCompletionItem() } ?: emptyList()
        }
    }

    /**
     * Request hover documentation at a position.
     */
    fun getHover(uri: String, line: Int, column: Int): CompletableFuture<Hover?> {
        return ensureServerRunning {
            val params = HoverParams(TextDocumentIdentifier(uri), Position(line, column))
            languageServer!!.textDocumentService.hover(params).get(5, TimeUnit.SECONDS)
        }
    }

    /**
     * Request go-to-definition.
     */
    fun getDefinition(uri: String, line: Int, column: Int): CompletableFuture<List<Location>> {
        return ensureServerRunning {
            val params = DefinitionParams(TextDocumentIdentifier(uri), Position(line, column))
            val result = languageServer!!.textDocumentService.definition(params).get(5, TimeUnit.SECONDS)
            result.right?.map { it.toLocation() } ?: result.left?.toLocation()?.let { listOf(it) } ?: emptyList()
        }
    }

    /**
     * Request find-references.
     */
    fun getReferences(uri: String, line: Int, column: Int, includeDeclaration: Boolean = false): CompletableFuture<List<Location>> {
        return ensureServerRunning {
            val params = ReferenceParams(
                TextDocumentIdentifier(uri),
                Position(line, column),
                ReferenceContext(includeDeclaration)
            )
            languageServer!!.textDocumentService.references(params).get(10, TimeUnit.SECONDS)
        }
    }

    /**
     * Request signature help (method parameter info).
     */
    fun getSignatureHelp(uri: String, line: Int, column: Int): CompletableFuture<SignatureHelp?> {
        return ensureServerRunning {
            val params = SignatureHelpParams(
                TextDocumentIdentifier(uri),
                Position(line, column),
                SignatureHelpContext(SignatureHelpTriggerKind.Invoked, 0)
            )
            languageServer!!.textDocumentService.signatureHelp(params).get(5, TimeUnit.SECONDS)
        }
    }

    /**
     * Request document symbols (outline/structure).
     */
    fun getDocumentSymbols(uri: String): CompletableFuture<List<DocumentSymbol>> {
        return ensureServerRunning {
            val params = DocumentSymbolParams(TextDocumentIdentifier(uri))
            val result = languageServer!!.textDocumentService.documentSymbol(params).get(10, TimeUnit.SECONDS)
            result.right ?: result.left?.map { it.toDocumentSymbol() } ?: emptyList()
        }
    }

    /**
     * Request code formatting.
     */
    fun formatCode(uri: String): CompletableFuture<List<TextEdit>> {
        return ensureServerRunning {
            val params = DocumentFormattingParams(
                TextDocumentIdentifier(uri),
                FormattingOptions(4, true)
            )
            languageServer!!.textDocumentService.formatting(params).get(5, TimeUnit.SECONDS)
        }
    }

    /**
     * Request range formatting.
     */
    fun formatRange(uri: String, startLine: Int, startCol: Int, endLine: Int, endCol: Int): CompletableFuture<List<TextEdit>> {
        return ensureServerRunning {
            val params = DocumentRangeFormattingParams(
                TextDocumentIdentifier(uri),
                Range(Position(startLine, startCol), Position(endLine, endCol)),
                FormattingOptions(4, true)
            )
            languageServer!!.textDocumentService.rangeFormatting(params).get(5, TimeUnit.SECONDS)
        }
    }

    /**
     * Request symbol rename.
     */
    fun renameSymbol(uri: String, line: Int, column: Int, newName: String): CompletableFuture<WorkspaceEdit?> {
        return ensureServerRunning {
            val params = RenameParams(
                TextDocumentIdentifier(uri),
                Position(line, column),
                newName
            )
            languageServer!!.textDocumentService.rename(params).get(5, TimeUnit.SECONDS)
        }
    }

    // ── Document synchronization ───────────────────────────────────

    /**
     * Notify the server that a document was opened.
     */
    fun didOpen(uri: String, content: String, languageId: String = "java") {
        if (state != ServerState.RUNNING) return
        val params = DidOpenTextDocumentParams(
            TextDocumentItem(uri, languageId, 0, content)
        )
        languageServer?.textDocumentService?.didOpen(params)
    }

    /**
     * Notify the server of a document change (full sync).
     */
    fun didChange(uri: String, content: Int, fullContent: String) {
        if (state != ServerState.RUNNING) return
        val params = DidChangeTextDocumentParams(
            VersionedTextDocumentIdentifier(uri, content),
            listOf(TextDocumentContentChangeEvent().apply {
                range = Range(Position(0, 0), Position(Int.MAX_VALUE, Int.MAX_VALUE))
                text = fullContent
            })
        )
        languageServer?.textDocumentService?.didChange(params)
    }

    /**
     * Notify the server of incremental document change.
     */
    fun didChangeIncremental(uri: String, version: Int, range: Range, newText: String) {
        if (state != ServerState.RUNNING) return
        val params = DidChangeTextDocumentParams(
            VersionedTextDocumentIdentifier(uri, version),
            listOf(TextDocumentContentChangeEvent(range, newText.length, newText))
        )
        languageServer?.textDocumentService?.didChange(params)
    }

    /**
     * Notify the server that a document was saved.
     */
    fun didSave(uri: String, content: String? = null) {
        if (state != ServerState.RUNNING) return
        val params = DidSaveTextDocumentParams(
            TextDocumentIdentifier(uri),
            content?.let { TextDocument(it) }
        )
        languageServer?.textDocumentService?.didSave(params)
    }

    /**
     * Notify the server that a document was closed.
     */
    fun didClose(uri: String) {
        if (state != ServerState.RUNNING) return
        val params = DidCloseTextDocumentParams(TextDocumentIdentifier(uri))
        languageServer?.textDocumentService?.didClose(params)
    }

    // ── Diagnostics ───────────────────────────────────────────────

    /**
     * Get cached diagnostics for a file.
     *
     * Diagnostics are pushed from the server via the client's
     * publishDiagnostics callback. This returns the most recent set.
     */
    fun getDiagnostics(uri: String): List<Diagnostic> {
        return lspClient?.getDiagnostics(uri) ?: emptyList()
    }

    /**
     * Add a listener for diagnostic updates.
     */
    fun addDiagnosticListener(listener: (String, List<Diagnostic>) -> Unit) {
        diagnosticListeners.add(listener)
    }

    // ── Listeners ─────────────────────────────────────────────────

    fun addStateListener(listener: (ServerState) -> Unit) {
        stateListeners.add(listener)
    }

    fun removeStateListener(listener: (ServerState) -> Unit) {
        stateListeners.remove(listener)
    }

    // ── Helpers ────────────────────────────────────────────────────

    private fun updateState(newState: ServerState) {
        state = newState
        stateListeners.forEach { it(newState) }
    }

    private fun <T> ensureServerRunning(block: () -> T): CompletableFuture<T> {
        val future = CompletableFuture<T>()
        if (state != ServerState.RUNNING) {
            future.completeExceptionally(IllegalStateException("Server not running"))
            return future
        }
        executor.submit {
            try {
                future.complete(block())
            } catch (e: Exception) {
                future.completeExceptionally(e)
            }
        }
        return future
    }

    private fun resolveJdtlsPath(): String? {
        if (config.jdtlsPath.isNotEmpty() && File(config.jdtlsPath).exists()) {
            return config.jdtlsPath
        }
        return JDTLS_SEARCH_PATHS.firstOrNull { File(it).exists() }
    }

    // ── LSP type conversions ───────────────────────────────────────

    private fun LocationLink.toLocation(): Location =
        Location(targetUri, targetRange)

    private fun org.eclipse.lsp4j.SymbolInformation.toDocumentSymbol(): DocumentSymbol =
        DocumentSymbol(name, kind, range, selectionRange, detail, children)

    private fun Either<Range, LocationLink>.toLocation(): Location? =
        if (isLeft) left else right?.targetUri?.let { Location(it, right.targetRange) }
}
