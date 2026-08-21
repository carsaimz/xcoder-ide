package com.xcoder.editor.sora

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.java.JavaLanguage
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.text.LineSeparator
import java.io.File

// ── Language detection ──────────────────────────────────────────────────────

/**
 * Detects the sora-editor [TextMateLanguage] or built-in language for a file path.
 * Falls back to [EmptyLanguage] when no match is found.
 *
 * TextMate grammars are loaded from `assets/textmate/` bundled in the module.
 * Supported: Kotlin, Java, JavaScript/TypeScript, Python, C/C++, Go, Rust,
 * HTML, CSS/SCSS, JSON, XML, YAML, Markdown, Shell, SQL, PHP, Ruby, Dart, Lua,
 * Groovy, Gradle, Dockerfile, Makefile, TOML, INI, Diff, C#, Swift.
 */
fun detectLanguageForFile(context: Context, filePath: String): io.github.rosemoe.sora.lang.Language {
    val ext = filePath.substringAfterLast('.', "").lowercase()
    val tmLang = TextMateLanguage.create(
        when (ext) {
            "kt", "kts" -> "source.kotlin"
            "java" -> "source.java"
            "js", "mjs", "cjs" -> "source.js"
            "ts", "tsx" -> "source.ts"
            "jsx" -> "source.js.jsx"
            "py", "pyw" -> "source.python"
            "c", "h" -> "source.c"
            "cpp", "cc", "cxx", "hpp", "hxx" -> "source.cpp"
            "go" -> "source.go"
            "rs" -> "source.rust"
            "html", "htm" -> "text.html.basic"
            "css" -> "source.css"
            "scss" -> "source.scss"
            "less" -> "source.less"
            "json" -> "source.json"
            "xml", "axml", "xib", "plist", "svg" -> "text.xml"
            "yaml", "yml" -> "source.yaml"
            "md", "markdown" -> "text.html.markdown"
            "sh", "bash", "zsh" -> "source.shell"
            "sql" -> "source.sql"
            "php" -> "source.php"
            "rb" -> "source.ruby"
            "dart" -> "source.dart"
            "lua" -> "source.lua"
            "groovy", "gradle" -> "source.groovy"
            "dockerfile" -> "source.dockerfile"
            "toml" -> "source.toml"
            "ini", "cfg", "conf" -> "source.ini"
            "diff", "patch" -> "source.diff"
            "cs" -> "source.cs"
            "swift" -> "source.swift"
            "proto" -> "source.proto"
            "graphql", "gql" -> "source.graphql"
            "r" -> "source.r"
            "tex", "latex" -> "text.tex.latex"
            "vue" -> "text.html.vue"
            "svelte" -> "source.svelte"
            else -> return EmptyLanguage()
        },
        null  // theme from TextMate registry
    )
    return tmLang ?: EmptyLanguage()
}

// ── Theme helpers ──────────────────────────────────────────────────────────

object SoraThemes {
    /** Dark theme matching Material 3 dark surface colors. */
    fun darkScheme(): EditorColorScheme = EditorColorScheme().apply {
        setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, 0xFF1E1E2E.toInt())
        setColor(EditorColorScheme.LINE_NUMBER_FOREGROUND, 0xFF6C7086.toInt())
        setColor(EditorColorScheme.LINE_NUMBER_CURRENT, 0xFFCDD6F4.toInt())
        setColor(EditorColorScheme.TEXT_NORMAL, 0xFFCDD6F4.toInt())
        setColor(EditorColorScheme.WHOLE_BACKGROUND, 0xFF1E1E2E.toInt())
        setColor(EditorColorScheme.TEXT_SELECTED, 0xFF45475A.toInt())
        setColor(EditorColorScheme.SELECTION_INSERT, 0xFF89B4FA.toInt())
        setColor(EditorColorScheme.SELECTION_HANDLE, 0xFF89B4FA.toInt())
        setColor(EditorColorScheme.HIGHLIGHTED_SEARCH_BACKGROUND, 0xFFF9E2AF.toInt())
        setColor(EditorColorScheme.HIGHLIGHTED_DELIMITED_BACKGROUND, 0xFF585B70.toInt())
        setColor(EditorColorScheme.AUTO_COMP_PANEL_BG, 0xFF313244.toInt())
        setColor(EditorColorScheme.AUTO_COMP_PANEL_CORNER, 0xFF45475A.toInt())
        setColor(EditorColorScheme.DIAGNOSTIC_TOOLTIP_BG, 0xFF313244.toInt())
        setColor(EditorColorScheme.DIAGNOSTIC_ERROR, 0xFFF38BA8.toInt())
        setColor(EditorColorScheme.DIAGNOSTIC_WARNING, 0xFFF9E2AF.toInt())
        setColor(EditorColorScheme.DIAGNOSTIC_INFO, 0xFF89B4FA.toInt())
        setColor(EditorColorScheme.SIDEBAR_BACKGROUND, 0xFF181825.toInt())
        setColor(EditorColorScheme.CURRENT_LINE, 0xFF313244.toInt())
        setColor(EditorColorScheme.BLOCK_LINE, 0xFF45475A.toInt())
        setColor(EditorColorScheme.COMMENT_FOREGROUND, 0xFF6C7086.toInt())
        setColor(EditorColorScheme.NON_PRINTABLE_CHAR, 0xFF585B70.toInt())
    }

    /** Light theme. */
    fun lightScheme(): EditorColorScheme = EditorColorScheme().apply {
        setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, 0xFFEFF1F5.toInt())
        setColor(EditorColorScheme.LINE_NUMBER_FOREGROUND, 0xFF7C7F93.toInt())
        setColor(EditorColorScheme.LINE_NUMBER_CURRENT, 0xFF4C4F69.toInt())
        setColor(EditorColorScheme.TEXT_NORMAL, 0xFF4C4F69.toInt())
        setColor(EditorColorScheme.WHOLE_BACKGROUND, 0xFFEFF1F5.toInt())
        setColor(EditorColorScheme.TEXT_SELECTED, 0xFFCCD0DA.toInt())
        setColor(EditorColorScheme.SELECTION_INSERT, 0xFF1E66F5.toInt())
        setColor(EditorColorScheme.SELECTION_HANDLE, 0xFF1E66F5.toInt())
        setColor(EditorColorScheme.HIGHLIGHTED_SEARCH_BACKGROUND, 0xFFDF8E1D.toInt())
        setColor(EditorColorScheme.CURRENT_LINE, 0xFFE6E9EF.toInt())
        setColor(EditorColorScheme.COMMENT_FOREGROUND, 0xFF7C7F93.toInt())
        setColor(EditorColorScheme.NON_PRINTABLE_CHAR, 0xFFCCD0DA.toInt())
    }
}

// ── Compose wrapper ────────────────────────────────────────────────────────

/**
 * Composable wrapper around [CodeEditor] (Rosemoe/sora-editor).
 *
 * Features provided out-of-the-box by sora-editor:
 * - Syntax highlighting for 30+ languages (via TextMate)
 * - Code folding
 * - Auto-completion
 * - Search & replace (with regex support)
 * - Code formatting
 * - Undo/redo
 * - Line numbers with current-line highlight
 * - Minimap
 * - Bracket matching & auto-closing
 * - Word wrap
 * - Pinch-to-zoom
 * - Sticky scroll
 * - Indent guides
 * - Breadcrumbs
 * - TextActionWindow (context menu)
 * - Keyboard shortcuts
 *
 * @param filePath Path of the file being edited (used for language detection).
 * @param contentText Initial text content.
 * @param isDark Whether to use the dark color scheme.
 * @param readOnly Whether the editor is read-only.
 * @param fontSize Editor font size in sp.
 * @param tabSize Tab width in spaces.
 * @param onContentChanged Callback with the current text when content changes.
 * @param editorRef Ref to expose the underlying [CodeEditor] for advanced use.
 * @param modifier Compose modifier.
 */
@Composable
fun SoraEditor(
    filePath: String = "",
    contentText: String = "",
    isDark: Boolean = true,
    readOnly: Boolean = false,
    fontSize: Float = 14f,
    tabSize: Int = 4,
    onContentChanged: ((String) -> Unit)? = null,
    editorRef: androidx.compose.runtime.Ref<CodeEditor?>? = null,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    AndroidView(
        factory = { ctx ->
            CodeEditor(ctx).apply {
                // ── Appearance ────────────────────────────────────────
                colorScheme = if (isDark) SoraThemes.darkScheme() else SoraThemes.lightScheme()
                typefaceText = Typeface.MONOSPACE
                textSize = fontSize
                setTabWidth(tabSize)
                isWordWrap = false
                isCursorVisible = !readOnly
                isEditable = !readOnly
                isHighlightCurrentLine = true
                isHighlightBracketPair = true
                isHighlightMatchingDelimiters = true
                isAutoCompletionEnabled = true
                isAutoIndent = true
                isSmartBackspace = true
                isShowLineNumber = true
                isLineNumberEnabled = true
                isPinchZoomEnabled = true
                isStickyScrollEnabled = true
                isIndentGuideEnabled = true
                isSymbolCompletionEnabled = true

                // ── Content ──────────────────────────────────────────
                if (contentText.isNotEmpty()) {
                    setText(contentText)
                } else {
                    setText("")
                }

                // ── Language ─────────────────────────────────────────
                if (filePath.isNotEmpty()) {
                    setLang(detectLanguageForFile(ctx, filePath))
                }

                // ── Propagate ref ────────────────────────────────────
                editorRef?.value = this
            }
        },
        update = { editor ->
            editor.colorScheme = if (isDark) SoraThemes.darkScheme() else SoraThemes.lightScheme()
            editor.textSize = fontSize
            editor.setTabWidth(tabSize)
            editor.isEditable = !readOnly
            editor.isCursorVisible = !readOnly
            editor.isWordWrap = false
            editor.isStickyScrollEnabled = true
            editor.isIndentGuideEnabled = true
            editorRef?.value = editor
        },
        modifier = modifier.fillMaxSize()
    )
}

// ── Editor operations helper ───────────────────────────────────────────────

/** High-level editor operations backed by sora-editor's built-in features. */
object EditorOperations {

    /** Find text in the editor. sora-editor has built-in Searcher. */
    fun search(editor: CodeEditor, query: String, caseSensitive: Boolean = false) {
        editor.searcher.search(query, caseSensitive)
    }

    /** Find and replace text. */
    fun replace(editor: CodeEditor, query: String, replacement: String, all: Boolean = false) {
        if (all) {
            editor.searcher.replaceAll(query, replacement)
        } else {
            editor.searcher.replaceCurrent(query, replacement)
        }
    }

    /** Format code using the editor's built-in formatter. */
    fun format(editor: CodeEditor) {
        editor.formatCode()
    }

    /** Get current text content. */
    fun getText(editor: CodeEditor): String = editor.text.toString()

    /** Set text content and reset undo stack. */
    fun setText(editor: CodeEditor, text: String) {
        editor.setText(text)
    }

    /** Go to a specific line (1-indexed). */
    fun goToLine(editor: CodeEditor, line: Int) {
        editor.cursor?.let { cursor ->
            val position = editor.text.getIndexer().getCharPosition(line - 1, 0)
            cursor.setLeft(position)
            editor.setSelection(position, position)
        }
    }

    /** Toggle word wrap. */
    fun toggleWordWrap(editor: CodeEditor) {
        editor.isWordWrap = !editor.isWordWrap
    }

    /** Toggle minimap. */
    fun toggleMinimap(editor: CodeEditor) {
        editor.isMinimapEnabled = !editor.isMinimapEnabled
    }

    /** Undo last edit. */
    fun undo(editor: CodeEditor) = editor.undo()

    /** Redo last undone edit. */
    fun redo(editor: CodeEditor) = editor.redo()

    /** Insert text at cursor position. */
    fun insertAtCursor(editor: CodeEditor, text: String) {
        editor.cursor?.let { cursor ->
            editor.text.insert(cursor.left, text)
        }
    }

    /** Duplicate current line. */
    fun duplicateLine(editor: CodeEditor) {
        val text = editor.text
        val line = text.getLine(cursorLineNumber(editor))
        text.insert(cursorLineEnd(editor), "\n$line")
    }

    /** Delete current line. */
    fun deleteLine(editor: CodeEditor) {
        val text = editor.text
        val lineIndex = cursorLineNumber(editor)
        val start = text.getLineStart(lineIndex)
        val end = text.getLineEnd(lineIndex)
        // Also delete the newline
        val actualEnd = if (end < text.length && text[end] == '\n') end + 1 else end
        text.delete(start, actualEnd)
    }

    /** Move line up. */
    fun moveLineUp(editor: CodeEditor) {
        val text = editor.text
        val lineIndex = cursorLineNumber(editor)
        if (lineIndex > 0) {
            val currentLine = text.getLine(lineIndex)
            val prevLine = text.getLine(lineIndex - 1)
            val prevStart = text.getLineStart(lineIndex - 1)
            val currentEnd = text.getLineEnd(lineIndex) + 1
            text.replace(prevStart, currentEnd.coerceAtMost(text.length), "$currentLine\n$prevLine")
        }
    }

    /** Move line down. */
    fun moveLineDown(editor: CodeEditor) {
        val text = editor.text
        val lineIndex = cursorLineNumber(editor)
        if (lineIndex < text.lineCount - 1) {
            val currentLine = text.getLine(lineIndex)
            val nextLine = text.getLine(lineIndex + 1)
            val currentStart = text.getLineStart(lineIndex)
            val nextEnd = text.getLineEnd(lineIndex + 1) + 1
            text.replace(currentStart, nextEnd.coerceAtMost(text.length), "$nextLine\n$currentLine")
        }
    }

    /** Toggle comment for the current line(s). */
    fun toggleComment(editor: CodeEditor) {
        val text = editor.text
        val lang = editor.currentLanguage
        // Detect line comment prefix from language
        val prefix = when {
            lang is JavaLanguage -> "// "
            lang is TextMateLanguage -> "// "
            filePath != null && (filePath!!.endsWith(".py") || filePath!!.endsWith(".sh")) -> "# "
            filePath != null && (filePath!!.endsWith(".xml") || filePath!!.endsWith(".html")) -> "<!-- "
            else -> "// "
        }
        val lineIndex = cursorLineNumber(editor)
        val line = text.getLine(lineIndex)
        if (line.trimStart().startsWith(prefix.trim())) {
            // Uncomment
            val start = text.getLineStart(lineIndex)
            val idx = line.indexOf(prefix)
            text.delete(start + idx, start + idx + prefix.length)
        } else {
            // Comment
            text.insert(text.getLineStart(lineIndex), prefix)
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private var filePath: String? = null

    private fun cursorLineNumber(editor: CodeEditor): Int {
        val cursor = editor.cursor ?: return 0
        return editor.text.getIndexer().getLineNumber(cursor.left)
    }

    private fun cursorLineEnd(editor: CodeEditor): Int {
        val text = editor.text
        val lineIndex = cursorLineNumber(editor)
        return text.getLineEnd(lineIndex)
    }
}
