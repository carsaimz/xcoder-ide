package com.xcoder.lsp.java

import android.util.Log
import io.github.rosemoe.sora.lang.completion.CompletionHelper
import io.github.rosemoe.sora.lang.completion.CompletionItem
import io.github.rosemoe.sora.lang.completion.CompletionItemKind
import io.github.rosemoe.sora.widget.ComponentCollector
import org.eclipse.lsp4j.*
import java.util.concurrent.CompletableFuture
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Maps LSP [CompletionItem] from jdt.ls to sora-editor [CompletionItem].
 *
 * Based on AndroidIDE's ICompletionProvider which:
 * - Provides icons for each [CompletionItemKind] (25+ kinds)
 * - Maps label, detail, and documentation fields
 * - Handles insert text with snippet support ($1, ${2:default})
 * - Sorts results by sortText or relevance
 * - Applies prefix filtering and deduplication
 *
 * ## Snippet Support
 *
 * jdt.ls returns completions with insertTextFormat=Snippet that contain
 * placeholders like `${1:parameter}`, `$0` (final cursor position).
 * These are converted to sora-editor's snippet format.
 *
 * ## Icon Mapping
 *
 * Each LSP CompletionItemKind is mapped to a sora-editor CompletionItemKind
 * and a drawable icon. The icons are drawn programmatically using
 * ComponentCollector's icon set.
 *
 * ## Usage
 *
 * ```kotlin
 * val provider = CompletionProvider()
 * val items = provider.mapCompletions(lspItems)
 * completionWindow.setItems(items)
 * ```
 */
@Singleton
class CompletionProvider @Inject constructor() {

    companion object {
        private const val TAG = "CompletionProvider"

        /** Characters that trigger auto-completion. */
        val TRIGGER_CHARS = charArrayOf('.', '(', '<', ' ', '=', ',', ';', '@', '!')

        /** Maximum number of completions to return. */
        const val MAX_COMPLETIONS = 100
    }

    // ── Core mapping ──────────────────────────────────────────────

    /**
     * Map LSP completion items to sora-editor completion items.
     *
     * @param lspItems list of LSP CompletionItem from jdt.ls
     * @param prefix current word prefix for filtering
     * @return sorted, filtered sora-editor CompletionItem list
     */
    fun mapCompletions(
        lspItems: List<org.eclipse.lsp4j.CompletionItem>,
        prefix: String = ""
    ): List<CompletionItem> {
        val results = mutableListOf<CompletionItem>()

        for (item in lspItems) {
            if (results.size >= MAX_COMPLETIONS) break

            // Apply prefix filtering
            val label = item.label
            if (prefix.isNotEmpty() &&
                !label.startsWith(prefix, ignoreCase = true) &&
                !label.contains(prefix, ignoreCase = true)
            ) {
                continue
            }

            val editorItem = mapItem(item)
            if (editorItem != null) {
                results.add(editorItem)
            }
        }

        // Sort by sortText (LSP sort order)
        results.sortWith(compareBy { it.sortText ?: it.label })
        return results
    }

    /**
     * Map a single LSP CompletionItem to sora-editor CompletionItem.
     */
    fun mapItem(item: org.eclipse.lsp4j.CompletionItem): CompletionItem? {
        try {
            val kind = mapKind(item.kind)
            val label = item.label
            val detail = item.detail ?: ""
            val documentation = item.documentation?.let { doc ->
                when (doc) {
                    is String -> doc
                    is MarkupContent -> doc.value
                    else -> null
                }
            }

            // Determine insert text
            val (insertText, isSnippet) = resolveInsertText(item)

            return CompletionItem(
                label,
                kind,
                detail,
                insertText,
                prefix = label
            ).apply {
                // Attach sort text for ordering
                this.sortText = item.sortText ?: item.label

                // Attach documentation as commit completion callback
                // so it can be shown in the tooltip
                if (documentation != null) {
                    this.commitCompletion = null // Could set a custom action
                    // sora-editor stores extra data via tag
                    this.tag = documentation
                }

                // Snippet tab stops
                if (isSnippet) {
                    this.cursorOffset = insertText.indexOf("")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to map completion item: ${item.label}", e)
            return null
        }
    }

    /**
     * Resolve the insert text and whether it's a snippet.
     *
     * Priority:
     * 1. textEdit (if present, use the text from the edit)
     * 2. insertText
     * 3. label (fallback)
     */
    private fun resolveInsertText(item: org.eclipse.lsp4j.CompletionItem): Pair<String, Boolean> {
        // Check for text edit first (jdt.ls often uses this for imports)
        val textEdit = item.textEdit
        if (textEdit != null) {
            val text = when (textEdit) {
                is TextEdit -> textEdit.newText
                is Either<*, *> -> {
                    // InsertReplaceEdit or TextEdit
                    @Suppress("UNCHECKED_CAST")
                    if (textEdit.isLeft) {
                        (textEdit.left as TextEdit).newText
                    } else {
                        (textEdit.right as? InsertReplaceEdit)?.newText ?: item.label
                    }
                }
                else -> null
            }
            if (text != null) {
                val isSnippet = item.insertTextFormat == InsertTextFormat.Snippet
                return text to isSnippet
            }
        }

        val insertText = item.insertText ?: item.label
        val isSnippet = item.insertTextFormat == InsertTextFormat.Snippet
        return insertText to isSnippet
    }

    // ── Kind mapping ───────────────────────────────────────────────

    /**
     * Map LSP CompletionItemKind to sora-editor CompletionItemKind.
     *
     * LSP has 25+ kinds; sora-editor has a subset. This maps each
     * LSP kind to the closest sora-editor equivalent.
     */
    fun mapKind(kind: org.eclipse.lsp4j.CompletionItemKind): CompletionItemKind {
        return when (kind) {
            // Text and files
            CompletionItemKind.Text -> CompletionItemKind.Text
            CompletionItemKind.Snippet -> CompletionItemKind.Snippet
            CompletionItemKind.File -> CompletionItemKind.File
            CompletionItemKind.Folder -> CompletionItemKind.File

            // Code structure
            CompletionItemKind.Class -> CompletionItemKind.Class
            CompletionItemKind.Interface -> CompletionItemKind.Interface
            CompletionItemKind.Enum -> CompletionItemKind.Enum
            CompletionItemKind.EnumMember -> CompletionItemKind.Field
            CompletionItemKind.Struct -> CompletionItemKind.Class
            CompletionItemKind.TypeParameter -> CompletionItemKind.Class

            // Members
            CompletionItemKind.Method -> CompletionItemKind.Method
            CompletionItemKind.Function -> CompletionItemKind.Method
            CompletionItemKind.Constructor -> CompletionItemKind.Constructor
            CompletionItemKind.Property -> CompletionItemKind.Property
            CompletionItemKind.Field -> CompletionItemKind.Field
            CompletionItemKind.Variable -> CompletionItemKind.Field
            CompletionItemKind.Constant -> CompletionItemKind.Constant

            // Keywords and operators
            CompletionItemKind.Keyword -> CompletionItemKind.Keyword
            CompletionItemKind.Operator -> CompletionItemKind.Keyword

            // Modules and packages
            CompletionItemKind.Module -> CompletionItemKind.Module
            CompletionItemKind.Package -> CompletionItemKind.Module

            // References
            CompletionItemKind.Reference -> CompletionItemKind.Field
            CompletionItemKind.Unit -> CompletionItemKind.Field
            CompletionItemKind.Value -> CompletionItemKind.Constant

            // Documentation
            CompletionItemKind.Event -> CompletionItemKind.Method
            CompletionItemKind.Color -> CompletionItemKind.Constant
            CompletionItemKind.Topic -> CompletionItemKind.Text

            // Fallback
            null -> CompletionItemKind.Text
        }
    }

    /**
     * Get a human-readable description for an LSP CompletionItemKind.
     */
    fun getKindDescription(kind: org.eclipse.lsp4j.CompletionItemKind): String {
        return when (kind) {
            CompletionItemKind.Text -> "Text"
            CompletionItemKind.Method -> "Method"
            CompletionItemKind.Function -> "Function"
            CompletionItemKind.Constructor -> "Constructor"
            CompletionItemKind.Field -> "Field"
            CompletionItemKind.Variable -> "Variable"
            CompletionItemKind.Class -> "Class"
            CompletionItemKind.Interface -> "Interface"
            CompletionItemKind.Module -> "Module"
            CompletionItemKind.Property -> "Property"
            CompletionItemKind.Unit -> "Unit"
            CompletionItemKind.Value -> "Value"
            CompletionItemKind.Enum -> "Enum"
            CompletionItemKind.Keyword -> "Keyword"
            CompletionItemKind.Snippet -> "Snippet"
            CompletionItemKind.Color -> "Color"
            CompletionItemKind.File -> "File"
            CompletionItemKind.Reference -> "Reference"
            CompletionItemKind.Folder -> "Folder"
            CompletionItemKind.EnumMember -> "Enum Member"
            CompletionItemKind.Constant -> "Constant"
            CompletionItemKind.Struct -> "Struct"
            CompletionItemKind.Event -> "Event"
            CompletionItemKind.Operator -> "Operator"
            CompletionItemKind.TypeParameter -> "Type Parameter"
            CompletionItemKind.Package -> "Package"
            null -> "Unknown"
        }
    }

    // ── Snippet conversion ─────────────────────────────────────────

    /**
     * Convert LSP snippet syntax to sora-editor snippet syntax.
     *
     * LSP snippets use: ${1:default}, $0, ${1|choice1,choice2|}
     * sora-editor snippets use: ${1:default}, ${0}, ${1|choice1,choice2|}
     *
     * They are mostly compatible, but this method handles edge cases.
     */
    fun convertSnippet(lspSnippet: String): String {
        var result = lspSnippet
        // Convert $0 (final cursor) to ${0}
        result = result.replace("\\$0(?![0-9])".toRegex(), "\${0}")
        return result
    }

    // ── Async completion request ───────────────────────────────────

    /**
     * Request completions from the language server and map them.
     *
     * This is the primary entry point for sora-editor integration.
     * It handles the async LSP request and converts the result.
     *
     * @param languageServer the Java language server
     * @param uri file URI
     * @param content document content (for fallback)
     * @param line 0-based line
     * @param column 0-based column (UTF-16)
     * @param prefix current word prefix
     * @param callback receives mapped completion items
     */
    fun requestCompletions(
        languageServer: JavaLanguageServer?,
        uri: String,
        content: String,
        line: Int,
        column: Int,
        prefix: String,
        callback: (List<CompletionItem>) -> Unit
    ) {
        if (languageServer == null || languageServer.state != JavaLanguageServer.ServerState.RUNNING) {
            // Fallback to keyword completions
            callback(getFallbackCompletions(content, line, column, prefix))
            return
        }

        languageServer.getCompletion(uri, content, line, column)
            .thenAccept { lspItems ->
                val items = mapCompletions(lspItems, prefix)
                callback(items)
            }
            .exceptionally { e ->
                Log.w(TAG, "Completion request failed, falling back", e)
                callback(getFallbackCompletions(content, line, column, prefix))
                null
            }
    }

    /**
     * Fallback keyword completions when the LSP server is unavailable.
     */
    private fun getFallbackCompletions(
        content: String,
        line: Int,
        column: Int,
        prefix: String
    ): List<CompletionItem> {
        val keywords = LspClient.Companion.FALLBACK_KEYWORDS
            .filter { it.lowercase().startsWith(prefix) && it.lowercase() != prefix }
            .take(10)
            .map { keyword ->
                CompletionItem(
                    keyword,
                    CompletionItemKind.Keyword,
                    "Java keyword",
                    keyword,
                    prefix = keyword
                ).apply { sortText = keyword }
            }
        return keywords
    }
}
