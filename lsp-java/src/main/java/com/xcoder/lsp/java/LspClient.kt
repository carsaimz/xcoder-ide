package com.xcoder.lsp.java

import android.util.Log
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageServer
import java.util.concurrent.CompletableFuture
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LSP client implementation for the Java Language Server.
 *
 * Based on AndroidIDE's LspClient which:
 * - Handles the initialize handshake from the server side
 * - Receives and caches diagnostic notifications
 * - Forwards document sync notifications to the server
 * - Provides fallback keyword completions when the server is unavailable
 *
 * This class implements [LanguageClient] from LSP4J and acts as the
 * client-side endpoint of the LSP protocol. The server calls back
 * into this class for notifications and requests.
 *
 * ## Callbacks
 *
 * - [publishDiagnostics]: Server pushes error/warning markers
 * - [applyEdit]: Server requests a workspace edit (refactoring)
 * - [showMessage]: Server shows a message to the user
 * - [logMessage]: Server sends a log message
 * - [registerCapability]: Server requests dynamic capability registration
 *
 * ## Usage
 *
 * ```kotlin
 * // Connect to the language server
 * client.connect(languageServer)
 * // Send document open notification
 * client.openDocument(uri, content, languageId)
 * // Request completions
 * val items = client.getCompletions(uri, line, column)
 * ```
 */
@Singleton
class LspClient @Inject constructor() : LanguageClient {

    companion object {
        private const val TAG = "LspClient"
    }

    // ── Callbacks ─────────────────────────────────────────────────

    /** Called when diagnostics are received from the server. */
    var onDiagnostic: ((uri: String, diagnostics: List<Diagnostic>) -> Unit)? = null

    /** Called for server notifications (non-standard). */
    var onServerNotification: ((method: String, params: Any?) -> Unit)? = null

    /** Called when the server requests to show a message. */
    var onShowMessage: ((MessageParams) -> Unit)? = null

    /** Called when the server requests to apply a workspace edit. */
    var onApplyEdit: ((WorkspaceEdit) -> Boolean)? = null

    // ── Server connection ─────────────────────────────────────────

    /** Connected language server proxy. */
    private var server: LanguageServer? = null

    /** Cached diagnostics per file URI. */
    private val diagnosticsCache = mutableMapOf<String, MutableList<Diagnostic>>()

    /** Open document versions (for didChange version tracking). */
    private val documentVersions = mutableMapOf<String, Int>()

    /** Content cache for open documents. */
    private val documentContents = mutableMapOf<String, String>()

    /**
     * Connect this client to a language server.
     *
     * After connecting, the client can send notifications
     * and the server can call back into this client.
     */
    fun connect(server: LanguageServer) {
        this.server = server
        Log.d(TAG, "Connected to language server")
    }

    /**
     * Disconnect from the language server.
     */
    fun disconnect() {
        server = null
        diagnosticsCache.clear()
        documentVersions.clear()
        documentContents.clear()
        Log.d(TAG, "Disconnected from language server")
    }

    // ── Document synchronization ──────────────────────────────────

    /**
     * Notify the server that a document was opened.
     *
     * @param uri file URI (e.g., "file:///path/to/Foo.java")
     * @param content full document content
     * @param languageId LSP language identifier (e.g., "java")
     */
    fun openDocument(uri: String, content: String, languageId: String = "java") {
        documentVersions[uri] = 1
        documentContents[uri] = content
        server?.textDocumentService?.didOpen(
            DidOpenTextDocumentParams(
                TextDocumentItem(uri, languageId, 1, content)
            )
        )
    }

    /**
     * Notify the server of a full document change.
     *
     * @param uri file URI
     * @param content new full document content
     */
    fun changeDocument(uri: String, content: String) {
        val version = (documentVersions[uri] ?: 0) + 1
        documentVersions[uri] = version
        documentContents[uri] = content
        server?.textDocumentService?.didChange(
            DidChangeTextDocumentParams(
                VersionedTextDocumentIdentifier(uri, version),
                listOf(
                    TextDocumentContentChangeEvent(
                        Range(Position(0, 0), Position(Int.MAX_VALUE, Int.MAX_VALUE)),
                        content.length,
                        content
                    )
                )
            )
        )
    }

    /**
     * Notify the server of an incremental document change.
     *
     * @param uri file URI
     * @param range the range that changed
     * @param rangeLength the length of the text that was replaced
     * @param newText the new text
     */
    fun changeDocumentIncremental(
        uri: String,
        range: Range,
        rangeLength: Int,
        newText: String
    ) {
        val version = (documentVersions[uri] ?: 0) + 1
        documentVersions[uri] = version
        // Update cached content
        documentContents[uri]?.let { current ->
            documentContents[uri] = applyTextEdit(current, range, newText)
        }
        server?.textDocumentService?.didChange(
            DidChangeTextDocumentParams(
                VersionedTextDocumentIdentifier(uri, version),
                listOf(TextDocumentContentChangeEvent(range, rangeLength, newText))
            )
        )
    }

    /**
     * Notify the server that a document was saved.
     *
     * @param uri file URI
     * @param includeText whether to include the document text
     */
    fun saveDocument(uri: String, includeText: Boolean = false) {
        server?.textDocumentService?.didSave(
            DidSaveTextDocumentParams(
                TextDocumentIdentifier(uri),
                if (includeText) documentContents[uri]?.let { TextDocument(it) } else null
            )
        )
    }

    /**
     * Notify the server that a document was closed.
     */
    fun closeDocument(uri: String) {
        documentVersions.remove(uri)
        documentContents.remove(uri)
        diagnosticsCache.remove(uri)
        server?.textDocumentService?.didClose(
            DidCloseTextDocumentParams(TextDocumentIdentifier(uri))
        )
    }

    // ── Feature requests ──────────────────────────────────────────

    /**
     * Request code completion at a position.
     *
     * Falls back to keyword-based completions if the server is not connected.
     *
     * @param uri file URI
     * @param line 0-based line number
     * @param column 0-based column (UTF-16)
     * @return list of completion items
     */
    fun getCompletions(uri: String, line: Int, column: Int): List<CompletionItem> {
        val srv = server ?: return getFallbackCompletions(uri, line, column)
        return try {
            val params = CompletionParams(
                TextDocumentIdentifier(uri),
                Position(line, column)
            ).apply {
                context = CompletionContext(CompletionTriggerKind.Invoked)
            }
            val result = srv.textDocumentService.completion(params).get(10, java.util.concurrent.TimeUnit.SECONDS)
            result.right?.items
                ?: result.left?.map { it.toCompletionItem() }
                ?: emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "Completion request failed", e)
            getFallbackCompletions(uri, line, column)
        }
    }

    /**
     * Request hover documentation.
     */
    fun getHover(uri: String, line: Int, column: Int): Hover? {
        val srv = server ?: return null
        return try {
            val params = HoverParams(TextDocumentIdentifier(uri), Position(line, column))
            srv.textDocumentService.hover(params).get(5, java.util.concurrent.TimeUnit.SECONDS)
        } catch (e: Exception) {
            Log.w(TAG, "Hover request failed", e)
            null
        }
    }

    /**
     * Request go-to-definition.
     */
    fun getDefinition(uri: String, line: Int, column: Int): List<Location> {
        val srv = server ?: return emptyList()
        return try {
            val params = DefinitionParams(TextDocumentIdentifier(uri), Position(line, column))
            val result = srv.textDocumentService.definition(params).get(5, java.util.concurrent.TimeUnit.SECONDS)
            when {
                result.isLeft -> result.left?.toLocation()?.let { listOf(it) } ?: emptyList()
                result.isRight -> result.right?.map { Location(it.targetUri, it.targetRange) } ?: emptyList()
                else -> emptyList()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Definition request failed", e)
            emptyList()
        }
    }

    /**
     * Request find-references.
     */
    fun getReferences(uri: String, line: Int, column: Int): List<Location> {
        val srv = server ?: return emptyList()
        return try {
            val params = ReferenceParams(
                TextDocumentIdentifier(uri),
                Position(line, column),
                ReferenceContext(true)
            )
            srv.textDocumentService.references(params).get(10, java.util.concurrent.TimeUnit.SECONDS)
        } catch (e: Exception) {
            Log.w(TAG, "References request failed", e)
            emptyList()
        }
    }

    // ── Diagnostics ───────────────────────────────────────────────

    /**
     * Get cached diagnostics for a file.
     */
    fun getDiagnostics(uri: String): List<Diagnostic> {
        return diagnosticsCache[uri] ?: emptyList()
    }

    /**
     * Clear all cached diagnostics.
     */
    fun clearDiagnostics() {
        diagnosticsCache.clear()
    }

    // ── LanguageClient implementation ─────────────────────────────

    override fun publishDiagnostics(params: PublishDiagnosticsParams) {
        val uri = params.uri
        val diagnostics = params.diagnostics
        diagnosticsCache[uri] = diagnostics.toMutableList()
        onDiagnostic?.invoke(uri, diagnostics)
        Log.d(TAG, "Diagnostics for ${uri.substringAfterLast("/")}: ${diagnostics.size} issues")
    }

    override fun applyEdit(params: ApplyWorkspaceEditParams): CompletableFuture<ApplyWorkspaceEditResponse> {
        val accepted = onApplyEdit?.invoke(params.edit) ?: false
        return CompletableFuture.completedFuture(
            ApplyWorkspaceEditResponse(accepted)
        )
    }

    override fun registerCapability(params: RegistrationParams) {
        Log.d(TAG, "Server registered capability: ${params.registrations.joinToString { it.method }}")
    }

    override fun unregisterCapability(params: UnregistrationParams) {
        Log.d(TAG, "Server unregistered capability: ${params.unregistrations.joinToString { it.method }}")
    }

    override fun showMessage(params: MessageParams) {
        onShowMessage?.invoke(params)
        Log.d(TAG, "Server message [${params.type}]: ${params.message}")
    }

    override fun showMessageRequest(params: ShowMessageRequestParams): CompletableFuture<MessageActionItem> {
        Log.d(TAG, "Server message request: ${params.message}")
        return CompletableFuture.completedFuture(
            params.actions.firstOrNull() ?: MessageActionItem("", "OK")
        )
    }

    override fun logMessage(params: MessageParams) {
        when (params.type) {
            MessageType.Error -> Log.e(TAG, "Server: ${params.message}")
            MessageType.Warning -> Log.w(TAG, "Server: ${params.message}")
            MessageType.Info -> Log.i(TAG, "Server: ${params.message}")
            MessageType.Log -> Log.d(TAG, "Server: ${params.message}")
        }
    }

    override fun telemetryEvent(params: Any) {
        // No-op: telemetry not needed in IDE client
    }

    override fun refreshCodeLenses() {
        Log.d(TAG, "Server requested code lens refresh")
    }

    // ── Fallback completions ─────────────────────────────────────

    private fun getFallbackCompletions(uri: String, line: Int, column: Int): List<CompletionItem> {
        val content = documentContents[uri] ?: return emptyList()
        val lines = content.lines()
        if (line >= lines.size) return emptyList()

        val currentLine = lines[line]
        val wordStart = currentLine.lastIndexOf(' ', column - 1).let {
            if (it < 0) 0 else it + 1
        }
        val prefix = currentLine.substring(wordStart, column.coerceAtMost(currentLine.length)).lowercase()

        return FALLBACK_KEYWORDS
            .filter { it.lowercase().startsWith(prefix) && it.lowercase() != prefix }
            .take(10)
            .map { keyword ->
                CompletionItem(keyword).apply {
                    kind = CompletionItemKind.Keyword
                    detail = "Java keyword"
                    insertText = keyword
                }
            }
    }

    // ── Text edit helper ──────────────────────────────────────────

    private fun applyTextEdit(content: String, range: Range, newText: String): String {
        val lines = content.lines().toMutableList()
        if (range.start.line == range.end.line) {
            val line = lines.getOrNull(range.start.line) ?: return content
            val before = line.substring(0, range.start.character.coerceAtMost(line.length))
            val after = line.substring(range.end.character.coerceAtMost(line.length))
            lines[range.start.line] = before + newText + after
        } else {
            val startLine = lines.getOrNull(range.start.line) ?: return content
            val endLine = lines.getOrNull(range.end.line) ?: return content
            val before = startLine.substring(0, range.start.character.coerceAtMost(startLine.length))
            val after = endLine.substring(range.end.character.coerceAtMost(endLine.length))
            lines[range.start.line] = before + newText + after
            for (i in range.end.line downTo range.start.line + 1) {
                lines.removeAt(i)
            }
        }
        return lines.joinToString("\n")
    }

    companion object Keywords {
        val FALLBACK_KEYWORDS = listOf(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch",
            "char", "class", "const", "continue", "default", "do", "double",
            "else", "enum", "extends", "final", "finally", "float", "for",
            "goto", "if", "implements", "import", "instanceof", "int",
            "interface", "long", "native", "new", "package", "private",
            "protected", "public", "return", "short", "static", "strictfp",
            "super", "switch", "synchronized", "this", "throw", "throws",
            "transient", "try", "void", "volatile", "while", "true", "false", "null",
            "String", "Integer", "Long", "Double", "Float", "Boolean",
            "List", "ArrayList", "Map", "HashMap", "Set", "HashSet",
            "Object", "Class", "System", "Thread", "Runnable",
            "Override", "Deprecated", "SuppressWarnings", "FunctionalInterface",
            "Activity", "Fragment", "View", "Bundle", "Intent",
            "Context", "SharedPreferences", "RecyclerView", "ViewModel",
            "LiveData", "Observer", "OnClickListener"
        )
    }
}

// Extension: convert CompletionItemEither to CompletionItem
private fun org.eclipse.lsp4j.CompletionItemEither.toCompletionItem(): CompletionItem {
    return if (isLeft) {
        left.toCompletionItem()
    } else {
        right
    }
}

// Extension: convert InsertTextFormatEither to InsertTextFormat
private fun org.eclipse.lsp4j.InsertTextFormatEither.toInsertTextFormat(): InsertTextFormat {
    return if (isLeft) left else right
}

// Extension: convert LocationLink to Location
private fun org.eclipse.lsp4j.LocationLink.toLocation(): Location {
    return Location(targetUri, targetRange)
}

// Extension: convert Either<Range, LocationLink> to Location?
private fun org.eclipse.lsp4j.Either<org.eclipse.lsp4j.Range, org.eclipse.lsp4j.LocationLink>.toLocation(): Location? {
    return if (isLeft) {
        Location("", left)
    } else {
        right?.let { Location(it.targetUri, it.targetRange) }
    }
}

// Extension: convert SymbolInformation to DocumentSymbol
private fun org.eclipse.lsp4j.SymbolInformation.toDocumentSymbol(): DocumentSymbol {
    return DocumentSymbol(
        name,
        kind,
        location.range,
        location.range,
        containerName ?: ""
    )
}
