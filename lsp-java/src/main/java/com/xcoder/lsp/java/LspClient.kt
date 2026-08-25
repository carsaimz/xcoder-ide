package com.xcoder.lsp.java

import android.util.Log
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageServer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** LSP4J client with document synchronization, diagnostics and safe fallbacks. */
@Singleton
class LspClient @Inject constructor() : LanguageClient {
    companion object {
        private const val TAG = "LspClient"
        val FALLBACK_KEYWORDS = listOf(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
            "class", "const", "continue", "default", "do", "double", "else", "enum",
            "extends", "final", "finally", "float", "for", "if", "implements", "import",
            "instanceof", "int", "interface", "long", "native", "new", "package", "private",
            "protected", "public", "return", "short", "static", "strictfp", "super",
            "switch", "synchronized", "this", "throw", "throws", "transient", "try",
            "void", "volatile", "while", "true", "false", "null", "String", "Integer",
            "Long", "Double", "Float", "Boolean", "List", "ArrayList", "Map", "HashMap",
            "Set", "HashSet", "Object", "Class", "System", "Thread", "Runnable"
        )
    }

    var onDiagnostic: ((uri: String, diagnostics: List<Diagnostic>) -> Unit)? = null
    var onServerNotification: ((method: String, params: Any?) -> Unit)? = null
    var onShowMessage: ((MessageParams) -> Unit)? = null
    var onApplyEdit: ((WorkspaceEdit) -> Boolean)? = null

    private var server: LanguageServer? = null
    private val diagnosticsCache = mutableMapOf<String, MutableList<Diagnostic>>()
    private val documentVersions = mutableMapOf<String, Int>()
    private val documentContents = mutableMapOf<String, String>()

    fun connect(server: LanguageServer) {
        this.server = server
        Log.d(TAG, "Connected to LanguageServer")
    }

    fun disconnect() {
        server = null
        diagnosticsCache.clear()
        documentVersions.clear()
        documentContents.clear()
    }

    fun openDocument(uri: String, content: String, languageId: String = "java") {
        documentVersions[uri] = 1
        documentContents[uri] = content
        server?.textDocumentService?.didOpen(
            DidOpenTextDocumentParams(TextDocumentItem(uri, languageId, 1, content))
        )
    }

    fun changeDocument(uri: String, content: String) {
        val version = (documentVersions[uri] ?: 0) + 1
        documentVersions[uri] = version
        documentContents[uri] = content
        server?.textDocumentService?.didChange(
            DidChangeTextDocumentParams(
                VersionedTextDocumentIdentifier(uri, version),
                listOf(TextDocumentContentChangeEvent(content))
            )
        )
    }

    fun changeDocumentIncremental(uri: String, range: Range, rangeLength: Int, newText: String) {
        val version = (documentVersions[uri] ?: 0) + 1
        documentVersions[uri] = version
        documentContents[uri]?.let { documentContents[uri] = applyTextEdit(it, range, newText) }
        server?.textDocumentService?.didChange(
            DidChangeTextDocumentParams(
                VersionedTextDocumentIdentifier(uri, version),
                listOf(TextDocumentContentChangeEvent(range, rangeLength, newText))
            )
        )
    }

    fun saveDocument(uri: String, includeText: Boolean = false) {
        server?.textDocumentService?.didSave(
            DidSaveTextDocumentParams(
                TextDocumentIdentifier(uri),
                if (includeText) documentContents[uri] else null
            )
        )
    }

    fun closeDocument(uri: String) {
        documentVersions.remove(uri)
        documentContents.remove(uri)
        diagnosticsCache.remove(uri)
        server?.textDocumentService?.didClose(DidCloseTextDocumentParams(TextDocumentIdentifier(uri)))
    }

    fun getCompletions(uri: String, line: Int, column: Int): List<CompletionItem> {
        val srv = server ?: return getFallbackCompletions(uri, column)
        return try {
            val params = CompletionParams(TextDocumentIdentifier(uri), Position(line, column)).apply {
                context = CompletionContext(CompletionTriggerKind.Invoked)
            }
            val result = srv.textDocumentService.completion(params).get(10, TimeUnit.SECONDS)
            result.left ?: result.right?.items ?: emptyList()
        } catch (error: Exception) {
            Log.w(TAG, "Completion request failed", error)
            getFallbackCompletions(uri, column)
        }
    }

    fun getHover(uri: String, line: Int, column: Int): Hover? = try {
        server?.textDocumentService?.hover(HoverParams(TextDocumentIdentifier(uri), Position(line, column)))
            ?.get(5, TimeUnit.SECONDS)
    } catch (error: Exception) {
        Log.w(TAG, "Hover request failed", error)
        null
    }

    fun getDefinition(uri: String, line: Int, column: Int): List<Location> = try {
        val result = server?.textDocumentService
            ?.definition(DefinitionParams(TextDocumentIdentifier(uri), Position(line, column)))
            ?.get(5, TimeUnit.SECONDS)
        result?.left?.map { it } ?: result?.right?.map { Location(it.targetUri, it.targetRange) } ?: emptyList()
    } catch (error: Exception) {
        Log.w(TAG, "Definition request failed", error)
        emptyList()
    }

    fun getReferences(uri: String, line: Int, column: Int): List<Location> = try {
        server?.textDocumentService?.references(
            ReferenceParams(TextDocumentIdentifier(uri), Position(line, column), ReferenceContext(true))
        )?.get(10, TimeUnit.SECONDS) ?: emptyList()
    } catch (error: Exception) {
        Log.w(TAG, "References request failed", error)
        emptyList()
    }

    fun getDiagnostics(uri: String): List<Diagnostic> = diagnosticsCache[uri] ?: emptyList()
    fun clearDiagnostics() = diagnosticsCache.clear()

    override fun publishDiagnostics(params: PublishDiagnosticsParams) {
        val diagnostics = params.diagnostics ?: emptyList()
        diagnosticsCache[params.uri] = diagnostics.toMutableList()
        onDiagnostic?.invoke(params.uri, diagnostics)
    }

    override fun applyEdit(params: ApplyWorkspaceEditParams): CompletableFuture<ApplyWorkspaceEditResponse> =
        CompletableFuture.completedFuture(ApplyWorkspaceEditResponse(onApplyEdit?.invoke(params.edit) ?: false))

    override fun registerCapability(params: RegistrationParams): CompletableFuture<Void> =
        CompletableFuture.completedFuture(null)

    override fun unregisterCapability(params: UnregistrationParams): CompletableFuture<Void> =
        CompletableFuture.completedFuture(null)

    override fun showMessage(params: MessageParams) {
        onShowMessage?.invoke(params)
    }

    override fun showMessageRequest(params: ShowMessageRequestParams): CompletableFuture<MessageActionItem> =
        CompletableFuture.completedFuture(params.actions?.firstOrNull() ?: MessageActionItem("OK"))

    override fun logMessage(params: MessageParams) {
        when (params.type) {
            MessageType.Error -> Log.e(TAG, params.message)
            MessageType.Warning -> Log.w(TAG, params.message)
            else -> Log.d(TAG, params.message)
        }
    }

    override fun telemetryEvent(params: Any) = Unit
    override fun refreshCodeLenses(): CompletableFuture<Void> = CompletableFuture.completedFuture(null)

    private fun getFallbackCompletions(uri: String, column: Int): List<CompletionItem> {
        val content = documentContents[uri] ?: return emptyList()
        val line = content.lines().lastOrNull() ?: return emptyList()
        val prefix = line.take(column.coerceIn(0, line.length))
            .split(Regex("\\s|[.;,()]"))
            .lastOrNull()
            .orEmpty()
        return FALLBACK_KEYWORDS.filter { it.startsWith(prefix, ignoreCase = true) && !it.equals(prefix, true) }
            .take(10)
            .map { CompletionItem(it).apply { kind = CompletionItemKind.Keyword; insertText = it } }
    }

    private fun applyTextEdit(content: String, range: Range, newText: String): String {
        val lines = content.split("\n").toMutableList()
        val startLine = lines.getOrNull(range.start.line) ?: return content
        val endLine = lines.getOrNull(range.end.line) ?: return content
        val before = startLine.take(range.start.character.coerceIn(0, startLine.length))
        val after = endLine.drop(range.end.character.coerceIn(0, endLine.length))
        lines[range.start.line] = before + newText + after
        for (index in range.end.line downTo range.start.line + 1) lines.removeAt(index)
        return lines.joinToString("\n")
    }
}
