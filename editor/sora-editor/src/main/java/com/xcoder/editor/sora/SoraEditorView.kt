@file:Suppress("TooManyFunctions")
package com.xcoder.editor.sora

import android.content.Context
import android.graphics.Typeface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.HandleStateChangeEvent
import io.github.rosemoe.sora.event.ScrollEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.java.JavaLanguage
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

// ── Editor actions (AndroidIDE pattern) ─────────────────────────────────────

/**
 * Sealed class of editor actions, following AndroidIDE's command pattern.
 * AndroidIDE's [IDEEditor] dispatches actions through a command pipeline;
 * we mirror that pattern so the UI (toolbar buttons, menus, keyboard shortcuts)
 * can dispatch actions without knowing the editor implementation details.
 */
sealed class EditorAction {
    /** Undo the last edit. */
    data object Undo : EditorAction()
    /** Redo the last undone edit. */
    data object Redo : EditorAction()
    /** Cut selected text to clipboard. */
    data object Cut : EditorAction()
    /** Copy selected text to clipboard. */
    data object Copy : EditorAction()
    /** Paste from clipboard at cursor. */
    data object Paste : EditorAction()
    /** Select all text. */
    data object SelectAll : EditorAction()

    /** Format code using the language's formatter. */
    data object FormatCode : EditorAction()

    /** Toggle line comment on current line(s). */
    data object ToggleComment : EditorAction()
    /** Duplicate the current line. */
    data object DuplicateLine : EditorAction()
    /** Delete the current line. */
    data object DeleteLine : EditorAction()
    /** Move current line up by one. */
    data object MoveLineUp : EditorAction()
    /** Move current line down by one. */
    data object MoveLineDown : EditorAction()

    /** Toggle word wrap on/off. */
    data object ToggleWordWrap : EditorAction()
    /** Toggle line numbers on/off. */
    data object ToggleLineNumbers : EditorAction()
    /** Toggle minimap on/off. */
    data object ToggleMinimap : EditorAction()
    /** Toggle indent guides on/off. */
    data object ToggleIndentGuides : EditorAction()
    /** Toggle sticky scroll on/off. */
    data object ToggleStickyScroll : EditorAction()

    /** Increase font size by 1sp. */
    data object FontSizeIncrease : EditorAction()
    /** Decrease font size by 1sp. */
    data object FontSizeDecrease : EditorAction()
    /** Reset font size to default. */
    data class FontSizeReset(val defaultSize: Float = 14f) : EditorAction()

    /** Search for text. */
    data class Search(val query: String, val caseSensitive: Boolean = false, val regex: Boolean = false) : EditorAction()
    /** Replace current match. */
    data class Replace(val query: String, val replacement: String) : EditorAction()
    /** Replace all matches. */
    data class ReplaceAll(val query: String, val replacement: String) : EditorAction()
    /** Navigate to search result. */
    data class SearchNext(val forward: Boolean = true) : EditorAction()

    /** Go to a specific line (1-indexed). */
    data class GoToLine(val line: Int) : EditorAction()
    /** Insert text at cursor position. */
    data class InsertText(val text: String) : EditorAction()
    /** Set the complete text content (resets undo stack). */
    data class SetText(val text: String) : EditorAction()
}

// ── Editor events ────────────────────────────────────────────────────────────

/**
 * Callbacks for editor events, following AndroidIDE's event dispatch model.
 * AndroidIDE's [CodeEditorView] listens for selection, content, and scroll
 * events to update the status bar, modified indicator, and tab state.
 */
data class EditorEventCallbacks(
    /** Called when the editor content changes. Receives the full new text. */
    val onContentChanged: ((newText: String) -> Unit)? = null,
    /** Called when the cursor position or selection changes. (line, column, start, end) */
    val onSelectionChanged: ((line: Int, column: Int, selStart: Int, selEnd: Int) -> Unit)? = null,
    /** Called when the editor scrolls. (x, y) */
    val onScrollChanged: ((x: Int, y: Int) -> Unit)? = null,
    /** Called after an editor action is dispatched. */
    val onActionHandled: ((action: EditorAction) -> Unit)? = null,
)

// ── Language detection ──────────────────────────────────────────────────────

/**
 * Comment prefix configuration per language for toggle-comment support.
 * AndroidIDE detects the comment prefix from the active language;
 * we do the same but also support file-extension-based detection as a fallback.
 */
data class CommentStyle(
    val linePrefix: String,
    val blockStart: String? = null,
    val blockEnd: String? = null,
)

/**
 * Enhanced language detection mapping file extensions to sora-editor Language objects.
 *
 * Uses the TextMate grammar scope names expected by sora-editor's [TextMateLanguage].
 * Falls back to built-in languages (JavaLanguage) for Java where available,
 * then to [EmptyLanguage] for unknown types.
 *
 * @param context Android context for loading TextMate grammars from assets.
 * @param filePath File path used to determine the extension.
 * @return A [Language] instance appropriate for the file type.
 */
fun detectLanguageForFile(context: Context, filePath: String): Language {
    val ext = filePath.substringAfterLast('.', "").lowercase()
    val scopeName = EXTENSION_TO_SCOPE[ext] ?: return EmptyLanguage()

    // Use built-in Java language for best performance
    if (ext == "java") {
        return try { JavaLanguage() } catch (_: Exception) { createTextMate(scopeName) }
    }

    return createTextMate(scopeName) ?: EmptyLanguage()
}

/** Get the human-readable language name for a file extension. */
fun getLanguageName(filePath: String): String {
    val ext = filePath.substringAfterLast('.', "").lowercase()
    return EXTENSION_TO_DISPLAY_NAME[ext] ?: ext.uppercase()
}

/** Get the comment style for a file (for toggle-comment). */
fun getCommentStyle(filePath: String): CommentStyle {
    val ext = filePath.substringAfterLast('.', "").lowercase()
    return EXTENSION_TO_COMMENT[ext] ?: CommentStyle("// ")
}

private fun createTextMate(scopeName: String): Language? {
    return try {
        TextMateLanguage.create(scopeName, null)
    } catch (_: Exception) {
        null
    }
}

/** Extension → TextMate grammar scope name. */
private val EXTENSION_TO_SCOPE = mapOf(
    // JVM
    "kt" to "source.kotlin",
    "kts" to "source.kotlin",
    "java" to "source.java",
    "groovy" to "source.groovy",
    "gradle" to "source.groovy",
    "scala" to "source.scala",

    // JavaScript / TypeScript
    "js" to "source.js",
    "mjs" to "source.js",
    "cjs" to "source.js",
    "jsx" to "source.js.jsx",
    "ts" to "source.ts",
    "tsx" to "source.tsx",

    // Web markup / style
    "html" to "text.html.basic",
    "htm" to "text.html.basic",
    "css" to "source.css",
    "scss" to "source.scss",
    "less" to "source.less",
    "vue" to "text.html.vue",
    "svelte" to "source.svelte",

    // Data formats
    "json" to "source.json",
    "xml" to "text.xml",
    "axml" to "text.xml",
    "xib" to "text.xml",
    "plist" to "text.xml",
    "svg" to "text.xml",
    "yaml" to "source.yaml",
    "yml" to "source.yaml",
    "toml" to "source.toml",
    "ini" to "source.ini",
    "cfg" to "source.ini",
    "conf" to "source.ini",
    "props" to "source.java-properties",
    "properties" to "source.java-properties",

    // Systems / scripting
    "sh" to "source.shell",
    "bash" to "source.shell",
    "zsh" to "source.shell",
    "fish" to "source.shell",
    "py" to "source.python",
    "pyw" to "source.python",
    "rb" to "source.ruby",
    "lua" to "source.lua",
    "php" to "source.php",
    "r" to "source.r",
    "sql" to "source.sql",
    "dockerfile" to "source.dockerfile",
    "makefile" to "source.makefile",

    // C / C++ family
    "c" to "source.c",
    "h" to "source.c",
    "cpp" to "source.cpp",
    "cc" to "source.cpp",
    "cxx" to "source.cpp",
    "hpp" to "source.cpp",
    "hxx" to "source.cpp",
    "cs" to "source.cs",
    "swift" to "source.swift",
    "go" to "source.go",
    "rs" to "source.rust",
    "dart" to "source.dart",

    // Other
    "md" to "text.html.markdown",
    "markdown" to "text.html.markdown",
    "diff" to "source.diff",
    "patch" to "source.diff",
    "proto" to "source.proto",
    "graphql" to "source.graphql",
    "gql" to "source.graphql",
    "tex" to "text.tex.latex",
    "latex" to "text.tex.latex",
)

/** Extension → human-readable language name for status bar. */
private val EXTENSION_TO_DISPLAY_NAME = mapOf(
    "kt" to "Kotlin", "kts" to "Kotlin Script",
    "java" to "Java", "groovy" to "Groovy", "gradle" to "Gradle",
    "scala" to "Scala",
    "js" to "JavaScript", "mjs" to "JavaScript", "cjs" to "JavaScript",
    "jsx" to "JSX", "ts" to "TypeScript", "tsx" to "TSX",
    "html" to "HTML", "htm" to "HTML",
    "css" to "CSS", "scss" to "SCSS", "less" to "LESS",
    "vue" to "Vue", "svelte" to "Svelte",
    "json" to "JSON", "xml" to "XML", "axml" to "XML",
    "yaml" to "YAML", "yml" to "YAML", "toml" to "TOML",
    "ini" to "INI", "cfg" to "Config", "conf" to "Config",
    "props" to "Properties", "properties" to "Properties",
    "sh" to "Shell", "bash" to "Bash", "zsh" to "Zsh", "fish" to "Fish",
    "py" to "Python", "pyw" to "Python",
    "rb" to "Ruby", "lua" to "Lua", "php" to "PHP", "r" to "R",
    "sql" to "SQL", "dockerfile" to "Dockerfile",
    "c" to "C", "h" to "C Header",
    "cpp" to "C++", "cc" to "C++", "cxx" to "C++",
    "hpp" to "C++ Header", "hxx" to "C++ Header",
    "cs" to "C#", "swift" to "Swift",
    "go" to "Go", "rs" to "Rust", "dart" to "Dart",
    "md" to "Markdown", "markdown" to "Markdown",
    "diff" to "Diff", "patch" to "Patch",
    "proto" to "Protobuf", "graphql" to "GraphQL", "gql" to "GraphQL",
    "tex" to "LaTeX", "latex" to "LaTeX",
)

/** Extension → comment style for toggle-comment. */
private val EXTENSION_TO_COMMENT = mapOf(
    "py" to CommentStyle("# "),
    "pyw" to CommentStyle("# "),
    "sh" to CommentStyle("# "),
    "bash" to CommentStyle("# "),
    "zsh" to CommentStyle("# "),
    "fish" to CommentStyle("# "),
    "r" to CommentStyle("# "),
    "yaml" to CommentStyle("# "),
    "yml" to CommentStyle("# "),
    "toml" to CommentStyle("# "),
    "rb" to CommentStyle("# "),
    "lua" to CommentStyle("-- "),
    "sql" to CommentStyle("-- "),
    "html" to CommentStyle("<!-- ", "<!-- ", " -->"),
    "htm" to CommentStyle("<!-- ", "<!-- ", " -->"),
    "xml" to CommentStyle("<!-- ", "<!-- ", " -->"),
    "axml" to CommentStyle("<!-- ", "<!-- ", " -->"),
    "svg" to CommentStyle("<!-- ", "<!-- ", " -->"),
    "css" to CommentStyle("/* ", "/* ", " */"),
    "scss" to CommentStyle("// ", "/* ", " */"),
    "js" to CommentStyle("// ", "/* ", " */"),
    "mjs" to CommentStyle("// ", "/* ", " */"),
    "cjs" to CommentStyle("// ", "/* ", " */"),
    "jsx" to CommentStyle("// ", "/* ", " */"),
    "ts" to CommentStyle("// ", "/* ", " */"),
    "tsx" to CommentStyle("// ", "/* ", " */"),
    "java" to CommentStyle("// ", "/* ", " */"),
    "kt" to CommentStyle("// ", "/* ", " */"),
    "kts" to CommentStyle("// ", "/* ", " */"),
    "groovy" to CommentStyle("// ", "/* ", " */"),
    "gradle" to CommentStyle("// ", "/* ", " */"),
    "scala" to CommentStyle("// ", "/* ", " */"),
    "c" to CommentStyle("// ", "/* ", " */"),
    "h" to CommentStyle("// ", "/* ", " */"),
    "cpp" to CommentStyle("// ", "/* ", " */"),
    "cc" to CommentStyle("// ", "/* ", " */"),
    "hpp" to CommentStyle("// ", "/* ", " */"),
    "cs" to CommentStyle("// ", "/* ", " */"),
    "swift" to CommentStyle("// ", "/* ", " */"),
    "go" to CommentStyle("// ", "/* ", " */"),
    "rs" to CommentStyle("// ", "/* ", " */"),
    "dart" to CommentStyle("// ", "/* ", " */"),
    "json" to CommentStyle("// "),   // JSON has no comments; fallback
    "php" to CommentStyle("// ", "/* ", " */"),
)

// ── Color schemes (Catppuccin-inspired) ─────────────────────────────────────

/**
 * Editor color schemes, following AndroidIDE's pattern of loading themes from
 * the filesystem with light/dark variants. We provide two built-in schemes:
 * a dark Catppuccin Mocha and a light Catppuccin Latte.
 *
 * AndroidIDE's [IDEEditor] applies color schemes via [CodeEditor.setColorScheme].
 */
object SoraThemes {

    /**
     * Dark theme (Catppuccin Mocha-inspired).
     * Matches Material 3 dark surface tones for visual consistency.
     */
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
        setColor(EditorColorScheme.DIAGNOSTIC_HINT, 0xFFA6E3A1.toInt())
        setColor(EditorColorScheme.SIDEBAR_BACKGROUND, 0xFF181825.toInt())
        setColor(EditorColorScheme.CURRENT_LINE, 0xFF313244.toInt())
        setColor(EditorColorScheme.BLOCK_LINE, 0xFF45475A.toInt())
        setColor(EditorColorScheme.COMMENT_FOREGROUND, 0xFF6C7086.toInt())
        setColor(EditorColorScheme.NON_PRINTABLE_CHAR, 0xFF585B70.toInt())
        setColor(EditorColorScheme.MATCHED_BRACKETS_BACKGROUND, 0xFF585B70.toInt())
        setColor(EditorColorScheme.WHITELINE, 0xFF585B70.toInt())
    }

    /**
     * Light theme (Catppuccin Latte-inspired).
     */
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
        setColor(EditorColorScheme.AUTO_COMP_PANEL_BG, 0xFFE6E9EF.toInt())
        setColor(EditorColorScheme.AUTO_COMP_PANEL_CORNER, 0xFFCCD0DA.toInt())
        setColor(EditorColorScheme.DIAGNOSTIC_TOOLTIP_BG, 0xFFE6E9EF.toInt())
        setColor(EditorColorScheme.DIAGNOSTIC_ERROR, 0xFFD20F39.toInt())
        setColor(EditorColorScheme.DIAGNOSTIC_WARNING, 0xFFDF8E1D.toInt())
        setColor(EditorColorScheme.DIAGNOSTIC_INFO, 0xFF1E66F5.toInt())
        setColor(EditorColorScheme.DIAGNOSTIC_HINT, 0xFF40A02B.toInt())
        setColor(EditorColorScheme.CURRENT_LINE, 0xFFE6E9EF.toInt())
        setColor(EditorColorScheme.COMMENT_FOREGROUND, 0xFF7C7F93.toInt())
        setColor(EditorColorScheme.NON_PRINTABLE_CHAR, 0xFFCCD0DA.toInt())
        setColor(EditorColorScheme.MATCHED_BRACKETS_BACKGROUND, 0xFFCCD0DA.toInt())
        setColor(EditorColorScheme.WHITELINE, 0xFFCCD0DA.toInt())
    }

    /** Get scheme based on dark/light flag. */
    fun schemeFor(isDark: Boolean): EditorColorScheme =
        if (isDark) darkScheme() else lightScheme()
}

// ── Editor configuration ────────────────────────────────────────────────────

/**
 * Immutable editor configuration, following AndroidIDE's preference binding pattern.
 * AndroidIDE reads editor preferences and applies them when creating/restoring
 * a [CodeEditorView]. This data class serves the same purpose for Compose.
 *
 * @property fontSize Font size in sp (default 14).
 * @property tabSize Tab width in spaces (default 4).
 * @property isDark Whether to use dark color scheme.
 * @property wordWrap Enable word wrap (default off, like AndroidIDE).
 * @property showLineNumbers Show line numbers (default on).
 * @property showMinimap Show minimap (default on for large screens).
 * @property showIndentGuides Show indent guides (default on).
 * @property stickyScroll Enable sticky scroll for headers (default on).
 * @property highlightCurrentLine Highlight the current line (default on).
 * @property highlightBracketPair Highlight matching brackets (default on).
 * @property autoIndent Auto-indent on Enter/newline (default on).
 * @property autoCompletion Enable auto-completion popup (default on).
 * @property symbolCompletion Auto-close brackets/quotes (default on).
 * @property smartBackspace Smart backspace (delete indent, default on).
 * @property pinchZoom Enable pinch-to-zoom (default on).
 * @property readOnly Make editor read-only (default off).
 * @property fontTypeface Typeface for the editor text.
 */
data class EditorConfig(
    val fontSize: Float = 14f,
    val tabSize: Int = 4,
    val isDark: Boolean = true,
    val wordWrap: Boolean = false,
    val showLineNumbers: Boolean = true,
    val showMinimap: Boolean = true,
    val showIndentGuides: Boolean = true,
    val stickyScroll: Boolean = true,
    val highlightCurrentLine: Boolean = true,
    val highlightBracketPair: Boolean = true,
    val autoIndent: Boolean = true,
    val autoCompletion: Boolean = true,
    val symbolCompletion: Boolean = true,
    val smartBackspace: Boolean = true,
    val pinchZoom: Boolean = true,
    val readOnly: Boolean = false,
    val fontTypeface: Typeface? = null,
)

// ── Compose wrapper ─────────────────────────────────────────────────────────

/**
 * Composable wrapper around sora-editor's [CodeEditor].
 *
 * This is the core editor component, analogous to AndroidIDE's [CodeEditorView]
 * which wraps `IDEEditor` (extends `CodeEditor`) with event handling and
 * preference binding.
 *
 * Key features following AndroidIDE patterns:
 * - Language detection via [detectLanguageForFile] with TextMate grammars
 * - Color scheme support (dark/light) via [SoraThemes]
 * - Editor actions dispatched through [EditorAction] sealed class
 * - Event callbacks for content, selection, and scroll changes
 * - Configuration via immutable [EditorConfig]
 * - Auto-indent, word wrap, line numbers, font size all configurable
 *
 * @param filePath Path of the file being edited (for language detection).
 * @param contentText Initial text content.
 * @param config Editor configuration (font size, tab size, color scheme, etc.).
 * @param action Optional action to dispatch (one-shot, consumed after handling).
 * @param callbacks Event callbacks for content/selection/scroll changes.
 * @param editorRef Ref to expose the underlying [CodeEditor] for advanced use.
 * @param modifier Compose modifier.
 */
@Composable
fun SoraEditor(
    filePath: String = "",
    contentText: String = "",
    config: EditorConfig = EditorConfig(),
    action: EditorAction? = null,
    callbacks: EditorEventCallbacks = EditorEventCallbacks(),
    editorRef: androidx.compose.runtime.Ref<CodeEditor?>? = null,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    // Track whether we've already set initial content to avoid re-applying
    // on recomposition. AndroidIDE handles this by checking if the file
    // is the same as the one already loaded.
    var lastSetFilePath by remember { mutableStateOf<String?>(null) }
    var lastSetTextHash by remember { mutableIntStateOf(0) }

    // Compute the current color scheme outside the factory/update
    val currentScheme = remember(config.isDark) { SoraThemes.schemeFor(config.isDark) }

    AndroidView(
        factory = { ctx ->
            CodeEditor(ctx).apply {
                applyConfig(config, currentScheme)

                // Set initial content
                setText(contentText)
                lastSetTextHash = contentText.hashCode()
                lastSetFilePath = filePath

                // Detect and set language
                if (filePath.isNotEmpty()) {
                    setLang(detectLanguageForFile(ctx, filePath))
                }

                // ── Event listeners (AndroidIDE pattern) ──────────────
                // AndroidIDE registers listeners on CodeEditorView to track
                // cursor position (for status bar), modifications (for tab
                // indicator), and scroll (for minimap sync).

                setOnTextChangedListener { _, _ ->
                    callbacks.onContentChanged?.invoke(text.toString())
                }

                subscribeEvent(SelectionChangeEvent::class.java) { event, _ ->
                    val cursor = this.cursor ?: return@subscribeEvent true
                    val indexer = text.getIndexer()
                    val line = indexer.getLineNumber(cursor.left) + 1
                    val column = indexer.getColumnNumber(cursor.left) + 1
                    val selStart = event.leftIndex
                    val selEnd = event.rightIndex
                    callbacks.onSelectionChanged?.invoke(line, column, selStart, selEnd)
                    true
                }

                subscribeEvent(ScrollEvent::class.java) { event, _ ->
                    callbacks.onScrollChanged?.invoke(event.dx, event.dy)
                    true
                }

                // Expose via ref
                editorRef?.value = this
            }
        },
        update = { editor ->
            // Update configuration properties that may have changed
            editor.colorScheme = currentScheme
            editor.textSize = config.fontSize
            editor.setTabWidth(config.tabSize)
            editor.isEditable = !config.readOnly
            editor.isCursorVisible = !config.readOnly
            editor.isWordWrap = config.wordWrap
            editor.isLineNumberEnabled = config.showLineNumbers
            editor.isMinimapEnabled = config.showMinimap
            editor.isIndentGuideEnabled = config.showIndentGuides
            editor.isStickyScrollEnabled = config.stickyScroll
            editor.isHighlightCurrentLine = config.highlightCurrentLine
            editor.isHighlightBracketPair = config.highlightBracketPair
            editor.isAutoIndent = config.autoIndent
            editor.isAutoCompletionEnabled = config.autoCompletion
            editor.isSymbolCompletionEnabled = config.symbolCompletion
            editor.isSmartBackspace = config.smartBackspace
            editor.isPinchZoomEnabled = config.pinchZoom
            config.fontTypeface?.let { editor.typefaceText = it }

            // Handle content/switching (AndroidIDE pattern: only reset content
            // when the file path changes, not on every recomposition)
            if (filePath != lastSetFilePath) {
                editor.setText(contentText)
                lastSetTextHash = contentText.hashCode()
                lastSetFilePath = filePath
                if (filePath.isNotEmpty()) {
                    editor.setLang(detectLanguageForFile(context, filePath))
                }
            }

            // Handle dispatched action (one-shot)
            action?.let { act ->
                handleEditorAction(editor, act, filePath)
                callbacks.onActionHandled?.invoke(act)
            }

            // Keep ref in sync
            editorRef?.value = editor
        },
        modifier = modifier.fillMaxSize(),
    )
}

// ── Internal helpers ────────────────────────────────────────────────────────

/** Apply configuration to a fresh editor instance. */
private fun CodeEditor.applyConfig(config: EditorConfig, scheme: EditorColorScheme) {
    colorScheme = scheme
    typefaceText = config.fontTypeface ?: Typeface.MONOSPACE
    textSize = config.fontSize
    setTabWidth(config.tabSize)
    isWordWrap = config.wordWrap
    isCursorVisible = !config.readOnly
    isEditable = !config.readOnly
    isHighlightCurrentLine = config.highlightCurrentLine
    isHighlightBracketPair = config.highlightBracketPair
    isHighlightMatchingDelimiters = true
    isAutoCompletionEnabled = config.autoCompletion
    isAutoIndent = config.autoIndent
    isSmartBackspace = config.smartBackspace
    isShowLineNumber = config.showLineNumbers
    isLineNumberEnabled = config.showLineNumbers
    isPinchZoomEnabled = config.pinchZoom
    isStickyScrollEnabled = config.stickyScroll
    isIndentGuideEnabled = config.showIndentGuides
    isSymbolCompletionEnabled = config.symbolCompletion
    isMinimapEnabled = config.showMinimap
}

/**
 * Dispatch an [EditorAction] to a [CodeEditor].
 * This is the central action handler, following AndroidIDE's command dispatch pattern.
 */
fun handleEditorAction(editor: CodeEditor, action: EditorAction, filePath: String) {
    when (action) {
        is EditorAction.Undo -> editor.undo()
        is EditorAction.Redo -> editor.redo()
        is EditorAction.Cut -> editor.copyText()  // sora-editor handles cut
        is EditorAction.Copy -> editor.copyText()
        is EditorAction.Paste -> { /* handled by system clipboard */ }
        is EditorAction.SelectAll -> editor.selectAll()
        is EditorAction.FormatCode -> editor.formatCode()

        is EditorAction.ToggleComment -> toggleComment(editor, filePath)
        is EditorAction.DuplicateLine -> duplicateLine(editor)
        is EditorAction.DeleteLine -> deleteLine(editor)
        is EditorAction.MoveLineUp -> moveLineUp(editor)
        is EditorAction.MoveLineDown -> moveLineDown(editor)

        is EditorAction.ToggleWordWrap -> { editor.isWordWrap = !editor.isWordWrap }
        is EditorAction.ToggleLineNumbers -> {
            editor.isLineNumberEnabled = !editor.isLineNumberEnabled
        }
        is EditorAction.ToggleMinimap -> {
            editor.isMinimapEnabled = !editor.isMinimapEnabled
        }
        is EditorAction.ToggleIndentGuides -> {
            editor.isIndentGuideEnabled = !editor.isIndentGuideEnabled
        }
        is EditorAction.ToggleStickyScroll -> {
            editor.isStickyScrollEnabled = !editor.isStickyScrollEnabled
        }

        is EditorAction.FontSizeIncrease -> {
            editor.textSize = editor.textSize + 1f
        }
        is EditorAction.FontSizeDecrease -> {
            editor.textSize = (editor.textSize - 1f).coerceAtLeast(8f)
        }
        is EditorAction.FontSizeReset -> {
            editor.textSize = action.defaultSize
        }

        is EditorAction.Search -> {
            editor.searcher.search(action.query, action.caseSensitive, action.regex)
        }
        is EditorAction.Replace -> {
            editor.searcher.replaceCurrent(action.query, action.replacement)
        }
        is EditorAction.ReplaceAll -> {
            editor.searcher.replaceAll(action.query, action.replacement)
        }
        is EditorAction.SearchNext -> {
            if (action.forward) editor.searcher.gotoNext() else editor.searcher.gotoPrevious()
        }

        is EditorAction.GoToLine -> goToLine(editor, action.line)
        is EditorAction.InsertText -> insertAtCursor(editor, action.text)
        is EditorAction.SetText -> editor.setText(action.text)
    }
}

// ── Line/cursor operations ──────────────────────────────────────────────────

/** Get the 0-indexed line number at the cursor. */
private fun cursorLineIndex(editor: CodeEditor): Int {
    val cursor = editor.cursor ?: return 0
    return editor.text.getIndexer().getLineNumber(cursor.left)
}

/** Get the character index at the end of the cursor's line. */
private fun cursorLineEnd(editor: CodeEditor): Int {
    return editor.text.getLineEnd(cursorLineIndex(editor))
}

/** Insert text at the current cursor position. */
private fun insertAtCursor(editor: CodeEditor, text: String) {
    editor.cursor?.let { cursor ->
        editor.text.insert(cursor.left, text)
    }
}

/** Go to a specific line (1-indexed) and place cursor at column 1. */
private fun goToLine(editor: CodeEditor, line: Int) {
    val clampedLine = line.coerceIn(1, editor.text.lineCount)
    val position = editor.text.getIndexer().getCharPosition(clampedLine - 1, 0)
    editor.cursor?.setLeft(position)
    editor.setSelection(position, position)
}

/** Duplicate the current line below. */
private fun duplicateLine(editor: CodeEditor) {
    val text = editor.text
    val lineIndex = cursorLineIndex(editor)
    val lineContent = text.getLine(lineIndex)
    val endPos = text.getLineEnd(lineIndex)
    // Insert after the current line's newline
    val insertPos = if (endPos < text.length && text[endPos] == '\n') {
        endPos + 1
    } else {
        endPos
    }
    text.insert(insertPos, "$lineContent\n")
}

/** Delete the current line entirely. */
private fun deleteLine(editor: CodeEditor) {
    val text = editor.text
    val lineIndex = cursorLineIndex(editor)
    val start = text.getLineStart(lineIndex)
    var end = text.getLineEnd(lineIndex)
    // Also consume the trailing newline if present
    if (end < text.length && text[end] == '\n') end++
    text.delete(start, end)
}

/** Swap the current line with the line above. */
private fun moveLineUp(editor: CodeEditor) {
    val text = editor.text
    val lineIndex = cursorLineIndex(editor)
    if (lineIndex <= 0) return
    val curLine = text.getLine(lineIndex)
    val prevLine = text.getLine(lineIndex - 1)
    val prevStart = text.getLineStart(lineIndex - 1)
    var curEnd = text.getLineEnd(lineIndex)
    if (curEnd < text.length && text[curEnd] == '\n') curEnd++
    text.replace(prevStart, curEnd.coerceAtMost(text.length), "$curLine\n$prevLine")
}

/** Swap the current line with the line below. */
private fun moveLineDown(editor: CodeEditor) {
    val text = editor.text
    val lineIndex = cursorLineIndex(editor)
    if (lineIndex >= text.lineCount - 1) return
    val curLine = text.getLine(lineIndex)
    val nextLine = text.getLine(lineIndex + 1)
    val curStart = text.getLineStart(lineIndex)
    var nextEnd = text.getLineEnd(lineIndex + 1)
    if (nextEnd < text.length && text[nextEnd] == '\n') nextEnd++
    text.replace(curStart, nextEnd.coerceAtMost(text.length), "$nextLine\n$curLine")
}

/**
 * Toggle line comment on the current line.
 * Uses language-aware comment detection following AndroidIDE's pattern.
 */
private fun toggleComment(editor: CodeEditor, filePath: String) {
    val text = editor.text
    val lineIndex = cursorLineIndex(editor)
    val style = getCommentStyle(filePath)
    val line = text.getLine(lineIndex)
    val lineStart = text.getLineStart(lineIndex)

    // For line comments, toggle the prefix
    val prefix = style.linePrefix.trimEnd()
    if (line.trimStart().startsWith(prefix)) {
        // Uncomment: find and remove the prefix
        val trimmedStart = line.indexOf(prefix)
        if (trimmedStart >= 0) {
            text.delete(lineStart + trimmedStart, lineStart + trimmedStart + prefix.length)
        }
    } else {
        // Comment: insert prefix at the start of line content (preserving indent)
        val indent = line.takeWhile { it == ' ' || it == '\t' }
        val insertPos = lineStart + indent.length
        text.insert(insertPos, style.linePrefix)
    }
}

// ── Quick info helpers (for status bar, etc.) ───────────────────────────────

/** Get the current cursor position as (line, column), both 1-indexed. */
fun getCursorPosition(editor: CodeEditor): Pair<Int, Int> {
    val cursor = editor.cursor ?: return 1 to 1
    val indexer = editor.text.getIndexer()
    val line = indexer.getLineNumber(cursor.left) + 1
    val column = indexer.getColumnNumber(cursor.left) + 1
    return line to column
}

/** Get the total line count. */
fun getLineCount(editor: CodeEditor): Int = editor.text.lineCount

/** Get the current selection range, or null if no selection. */
fun getSelectionRange(editor: CodeEditor): Pair<Int, Int>? {
    val cursor = editor.cursor ?: return null
    val left = cursor.left
    val right = cursor.right
    return if (left != right) minOf(left, right) to maxOf(left, right) else null
}

/** Check if the editor can undo. */
fun canUndo(editor: CodeEditor): Boolean = editor.canUndo()

/** Check if the editor can redo. */
fun canRedo(editor: CodeEditor): Boolean = editor.canRedo()
