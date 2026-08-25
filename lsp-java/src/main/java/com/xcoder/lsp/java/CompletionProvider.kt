package com.xcoder.lsp.java

import android.util.Log
import io.github.rosemoe.sora.lang.completion.CompletionItem
import io.github.rosemoe.sora.lang.completion.CompletionItemKind as SoraKind
import io.github.rosemoe.sora.lang.completion.SimpleCompletionItem
import org.eclipse.lsp4j.CompletionItem as LspCompletionItem
import org.eclipse.lsp4j.CompletionItemKind as LspKind
import javax.inject.Inject
import javax.inject.Singleton

/** Maps LSP4J completion items to sora-editor completion items. */
@Singleton
class CompletionProvider @Inject constructor() {
    companion object {
        private const val TAG = "CompletionProvider"
        val TRIGGER_CHARS = charArrayOf('.', '(', '<', ' ', '=', ',', ';', '@', '!')
        const val MAX_COMPLETIONS = 100
    }

    fun mapCompletions(items: List<LspCompletionItem>, prefix: String = ""): List<CompletionItem> =
        items.asSequence()
            .filter { item ->
                val label = item.label ?: return@filter false
                prefix.isEmpty() || label.startsWith(prefix, ignoreCase = true) ||
                    label.contains(prefix, ignoreCase = true)
            }
            .mapNotNull(::mapItem)
            .sortedBy { it.sortText ?: it.label.toString() }
            .take(MAX_COMPLETIONS)
            .toList()

    fun mapItem(item: LspCompletionItem): CompletionItem? = try {
        val label = item.label ?: return null
        val insertText = item.textEditText ?: item.insertText ?: label
        val prefixLength = item.filterText?.let { 0 } ?: 0
        SimpleCompletionItem(label, item.detail ?: "", prefixLength, insertText).apply {
            kind = mapKind(item.kind)
            sortText = item.sortText ?: label
        }
    } catch (error: Exception) {
        Log.w(TAG, "Failed to map completion item", error)
        null
    }

    fun mapKind(kind: LspKind?): SoraKind = when (kind?.value) {
        2 -> SoraKind.Method
        3 -> SoraKind.Function
        4 -> SoraKind.Constructor
        5, 6 -> SoraKind.Field
        7 -> SoraKind.Class
        8 -> SoraKind.Interface
        9 -> SoraKind.Module
        10 -> SoraKind.Property
        13 -> SoraKind.Enum
        14 -> SoraKind.Keyword
        15 -> SoraKind.Snippet
        16 -> SoraKind.Color
        17 -> SoraKind.File
        18 -> SoraKind.Reference
        19 -> SoraKind.Folder
        20 -> SoraKind.EnumMember
        21 -> SoraKind.Constant
        22 -> SoraKind.Struct
        23 -> SoraKind.Event
        24 -> SoraKind.Operator
        25 -> SoraKind.TypeParameter
        else -> SoraKind.Text
    }

    fun getKindDescription(kind: LspKind?): String = kind?.name ?: "Unknown"

    fun convertSnippet(lspSnippet: String): String =
        lspSnippet.replace("\\$0(?![0-9])".toRegex(), "\${0}")

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
            callback(getFallbackCompletions(prefix))
            return
        }
        languageServer.getCompletion(uri, content, line, column)
            .thenAccept { callback(mapCompletions(it, prefix)) }
            .exceptionally { error ->
                Log.w(TAG, "Completion request failed, falling back", error)
                callback(getFallbackCompletions(prefix))
                null
            }
    }

    private fun getFallbackCompletions(prefix: String): List<CompletionItem> =
        LspClient.FALLBACK_KEYWORDS.asSequence()
            .filter { it.startsWith(prefix, ignoreCase = true) && !it.equals(prefix, ignoreCase = true) }
            .take(10)
            .map { keyword ->
                SimpleCompletionItem(keyword, "Java keyword", prefix.length, keyword).apply {
                    kind = SoraKind.Keyword
                    sortText = keyword
                }
            }
            .toList()
}
