package com.xcoder.lsp.java

import android.util.Log
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.Launcher
import org.eclipse.lsp4j.services.LanguageServer
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import javax.inject.Inject
import javax.inject.Singleton

/** Java language-server lifecycle and LSP request facade. */
@Singleton
open class JavaLanguageServer @Inject constructor() {
    companion object {
        private const val TAG = "JavaLanguageServer"
        private const val DEFAULT_JAVA = "java"
        private val JDTLS_SEARCH_PATHS = listOf(
            "/data/data/com.xcoder.ide/files/jdtls/bin/jdtls",
            "/data/data/com.xcoder.ide/files/tools/jdtls/bin/jdtls",
            "/data/data/com.xcoder.ide/jdtls/bin/jdtls",
            "/sdcard/xcoder/jdtls/bin/jdtls"
        )
    }

    enum class ServerState { STOPPED, STARTING, RUNNING, ERROR }
    enum class ConnectionMode { STDIO, TCP }

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

    var config = Config()
    var state: ServerState = ServerState.STOPPED
        private set
    var serverCapabilities: ServerCapabilities? = null
        private set

    private var serverProcess: Process? = null
    private var languageServer: LanguageServer? = null
    private var lspClient: LspClient? = null
    private val executor: ExecutorService = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "jdt-ls-worker").apply { isDaemon = true }
    }
    private val stateListeners = mutableListOf<(ServerState) -> Unit>()
    private val diagnosticListeners = mutableListOf<(String, List<Diagnostic>) -> Unit>()

    fun startServer(): CompletableFuture<Boolean> {
        val future = CompletableFuture<Boolean>()
        if (state == ServerState.RUNNING || state == ServerState.STARTING) {
            future.complete(false)
            return future
        }
        updateState(ServerState.STARTING)
        executor.submit {
            try {
                val path = resolveJdtlsPath()
                if (path == null) {
                    Log.w(TAG, "jdt.ls not found")
                    updateState(ServerState.ERROR)
                    future.complete(false)
                } else {
                    startStdio(path, future)
                }
            } catch (error: Exception) {
                Log.e(TAG, "Failed to start language server", error)
                updateState(ServerState.ERROR)
                future.complete(false)
            }
        }
        return future
    }

    fun stopServer() {
        try {
            languageServer?.shutdown()?.get(5, TimeUnit.SECONDS)
            languageServer?.exit()
        } catch (_: Exception) {
        }
        serverProcess?.destroyForcibly()
        serverProcess = null
        languageServer = null
        lspClient = null
        updateState(ServerState.STOPPED)
    }

    fun restartServer(): CompletableFuture<Boolean> {
        stopServer()
        return startServer()
    }

    private fun startStdio(path: String, future: CompletableFuture<Boolean>) {
        val command = buildList {
            add(config.javaPath)
            addAll(config.jvmArgs)
            add("-jar")
            add(path)
            add("-data")
            add(config.workspaceRoot)
        }
        val workspace = File(config.workspaceRoot).also { it.mkdirs() }
        serverProcess = ProcessBuilder(command)
            .directory(workspace)
            .redirectErrorStream(true)
            .start()
        val client = LspClient().also { created ->
            created.onDiagnostic = { uri, diagnostics ->
                diagnosticListeners.forEach { listener -> listener(uri, diagnostics) }
            }
        }
        lspClient = client
        val launcher = Launcher.createLauncher(
            client,
            LanguageServer::class.java,
            serverProcess!!.inputStream,
            serverProcess!!.outputStream,
            executor,
            Consumer { message -> Log.v(TAG, "LSP: $message") }
        )
        languageServer = launcher.remoteProxy
        client.connect(languageServer!!)
        launcher.startListening()
        performInitialize(future)
    }

    private fun performInitialize(future: CompletableFuture<Boolean>) {
        try {
            val params = InitializeParams().apply {
                processId = android.os.Process.myPid()
                rootUri = "file://${config.workspaceRoot}"
                rootPath = config.workspaceRoot
                capabilities = ClientCapabilities()
                if (config.initializationOptions.isNotEmpty()) {
                    initializationOptions = config.initializationOptions
                }
            }
            val result = languageServer!!.initialize(params).get(30, TimeUnit.SECONDS)
            serverCapabilities = result.capabilities
            languageServer!!.initialized(InitializedParams())
            updateState(ServerState.RUNNING)
            future.complete(true)
        } catch (error: Exception) {
            Log.e(TAG, "Language-server initialization failed", error)
            updateState(ServerState.ERROR)
            future.complete(false)
        }
    }

    fun getCompletion(uri: String, content: String, line: Int, column: Int): CompletableFuture<List<CompletionItem>> =
        ensureServerRunning {
            val params = CompletionParams(TextDocumentIdentifier(uri), Position(line, column)).apply {
                context = CompletionContext(CompletionTriggerKind.Invoked)
            }
            val result = languageServer!!.textDocumentService.completion(params).get(10, TimeUnit.SECONDS)
            result.left ?: result.right?.items ?: emptyList()
        }

    fun getHover(uri: String, line: Int, column: Int): CompletableFuture<Hover?> =
        ensureServerRunning {
            languageServer!!.textDocumentService
                .hover(HoverParams(TextDocumentIdentifier(uri), Position(line, column)))
                .get(5, TimeUnit.SECONDS)
        }

    fun getDefinition(uri: String, line: Int, column: Int): CompletableFuture<List<Location>> =
        ensureServerRunning {
            val result = languageServer!!.textDocumentService
                .definition(DefinitionParams(TextDocumentIdentifier(uri), Position(line, column)))
                .get(5, TimeUnit.SECONDS)
            result.left?.map { it } ?: result.right?.map { Location(it.targetUri, it.targetRange) } ?: emptyList()
        }

    fun getReferences(uri: String, line: Int, column: Int, includeDeclaration: Boolean = false): CompletableFuture<List<Location>> =
        ensureServerRunning {
            languageServer!!.textDocumentService.references(
                ReferenceParams(TextDocumentIdentifier(uri), Position(line, column), ReferenceContext(includeDeclaration))
            ).get(10, TimeUnit.SECONDS)
        }

    fun getSignatureHelp(uri: String, line: Int, column: Int): CompletableFuture<SignatureHelp?> =
        ensureServerRunning {
            languageServer!!.textDocumentService.signatureHelp(
                SignatureHelpParams(TextDocumentIdentifier(uri), Position(line, column), SignatureHelpContext(SignatureHelpTriggerKind.Invoked, false))
            ).get(5, TimeUnit.SECONDS)
        }

    fun getDocumentSymbols(uri: String): CompletableFuture<List<DocumentSymbol>> =
        ensureServerRunning {
            languageServer!!.textDocumentService.documentSymbol(DocumentSymbolParams(TextDocumentIdentifier(uri)))
                .get(10, TimeUnit.SECONDS)
                .mapNotNull { either ->
                    either.left?.let { symbol ->
                        DocumentSymbol(symbol.name, symbol.kind, symbol.location.range, symbol.location.range, symbol.containerName)
                    } ?: either.right
                }
        }

    fun formatCode(uri: String): CompletableFuture<List<TextEdit>> =
        ensureServerRunning {
            languageServer!!.textDocumentService.formatting(
                DocumentFormattingParams(TextDocumentIdentifier(uri), FormattingOptions(4, true))
            ).get(5, TimeUnit.SECONDS).map { it }
        }

    fun formatRange(uri: String, startLine: Int, startCol: Int, endLine: Int, endCol: Int): CompletableFuture<List<TextEdit>> =
        ensureServerRunning {
            languageServer!!.textDocumentService.rangeFormatting(
                DocumentRangeFormattingParams(
                    TextDocumentIdentifier(uri),
                    FormattingOptions(4, true),
                    Range(Position(startLine, startCol), Position(endLine, endCol))
                )
            ).get(5, TimeUnit.SECONDS).map { it }
        }

    fun renameSymbol(uri: String, line: Int, column: Int, newName: String): CompletableFuture<WorkspaceEdit?> =
        ensureServerRunning {
            languageServer!!.textDocumentService.rename(
                RenameParams(TextDocumentIdentifier(uri), Position(line, column), newName)
            ).get(5, TimeUnit.SECONDS)
        }

    fun didOpen(uri: String, content: String, languageId: String = "java") {
        if (state == ServerState.RUNNING) {
            languageServer?.textDocumentService?.didOpen(
                DidOpenTextDocumentParams(TextDocumentItem(uri, languageId, 0, content))
            )
        }
    }

    fun didChange(uri: String, version: Int, fullContent: String) {
        if (state == ServerState.RUNNING) {
            languageServer?.textDocumentService?.didChange(
                DidChangeTextDocumentParams(
                    VersionedTextDocumentIdentifier(uri, version),
                    listOf(TextDocumentContentChangeEvent(fullContent))
                )
            )
        }
    }

    fun didChangeIncremental(uri: String, version: Int, range: Range, newText: String) {
        if (state == ServerState.RUNNING) {
            languageServer?.textDocumentService?.didChange(
                DidChangeTextDocumentParams(
                    VersionedTextDocumentIdentifier(uri, version),
                    listOf(TextDocumentContentChangeEvent(range, newText))
                )
            )
        }
    }

    fun didSave(uri: String, content: String? = null) {
        if (state == ServerState.RUNNING) {
            languageServer?.textDocumentService?.didSave(DidSaveTextDocumentParams(TextDocumentIdentifier(uri), content))
        }
    }

    fun didClose(uri: String) {
        if (state == ServerState.RUNNING) {
            languageServer?.textDocumentService?.didClose(DidCloseTextDocumentParams(TextDocumentIdentifier(uri)))
        }
    }

    fun getDiagnostics(uri: String): List<Diagnostic> = lspClient?.getDiagnostics(uri) ?: emptyList()

    fun addDiagnosticListener(listener: (String, List<Diagnostic>) -> Unit) {
        diagnosticListeners.add(listener)
    }

    fun addStateListener(listener: (ServerState) -> Unit) {
        stateListeners.add(listener)
    }

    fun removeStateListener(listener: (ServerState) -> Unit) {
        stateListeners.remove(listener)
    }

    private fun updateState(newState: ServerState) {
        state = newState
        stateListeners.toList().forEach { it(newState) }
    }

    private fun <T> ensureServerRunning(block: () -> T): CompletableFuture<T> {
        val future = CompletableFuture<T>()
        if (state != ServerState.RUNNING || languageServer == null) {
            future.completeExceptionally(IllegalStateException("Server not running"))
            return future
        }
        executor.submit {
            try {
                future.complete(block())
            } catch (error: Exception) {
                future.completeExceptionally(error)
            }
        }
        return future
    }

    private fun resolveJdtlsPath(): String? = when {
        config.jdtlsPath.isNotEmpty() && File(config.jdtlsPath).exists() -> config.jdtlsPath
        else -> JDTLS_SEARCH_PATHS.firstOrNull { File(it).exists() }
    }
}
