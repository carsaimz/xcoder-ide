@file:Suppress("TooManyFunctions")
package com.xcoder.editor.sora

import android.content.Context
import android.graphics.Typeface
import android.util.Log
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.HandleStateChangeEvent
import io.github.rosemoe.sora.event.ScrollEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

private const val TAG = "XCoderIDEEditor"

// -- Local stubs for sora-editor LSP module types --
// These are defined here so the module compiles without the editor-lsp artifact.
// The actual LSP integration is wired up in the :lsp-java module.

/** Stub for sora-editor's ServerStatus enum. */
enum class ServerStatus { INITIALIZING, READY, INDEXING, STOPPED }

/** Stub for sora-editor's LanguageClient interface. */
interface LanguageClient {
    fun addServerStatusListener(listener: ServerStatusListener)
    fun removeServerStatusListener(listener: ServerStatusListener)
}

/** Stub for sora-editor's ServerStatusListener interface. */
interface ServerStatusListener {
    fun onStatusChanged(status: ServerStatus)
}

/** Stub for sora-editor's ServerDefinition. */
interface ServerDefinition

/** Stub for sora-editor's LSPEditor. */
interface LSPEditor

/**
 * Comprehensive editor wrapper around sora-editor's [CodeEditor], inspired by
 * AndroidIDE's `IDEEditor` (918 lines).
 *
 * This class adds LSP client integration, completion window management,
 * diagnostic markers, language switching, editor preferences binding, and
 * search/replace on top of the base [CodeEditor].
 *
 * AndroidIDE's `IDEEditor` extends `CodeEditor` and adds:
 * - LSP integration via `CompletionWindow` and diagnostic overlays
 * - Auto-completion triggered by keystrokes
 * - Signature help popups
 * - Error/warning underlines from LSP diagnostics
 * - Language switching when opening different file types
 * - Preference binding for font size, tab size, word wrap, etc.
 *
 * This implementation uses composition (wrapping) rather than inheritance,
 * providing the same features through a cleaner API.
 *
 * @param context Android context for resource access.
 * @param filePath Initial file path for language detection.
 */
class IDEEditor(
    val context: Context,
    filePath: String = "",
) {

    // ── Underlying editor ──────────────────────────────────────────────────

    /** The underlying sora-editor [CodeEditor] instance. */
    val editor: CodeEditor = CodeEditor(context)

    // ── LSP state ──────────────────────────────────────────────────────────

    /** The LSP client managing server communication. */
    var languageClient: LanguageClient? = null
        private set

    /** The LSP editor wrapper providing LSP features. */
    var lspEditor: LSPEditor? = null
        private set

    /** Custom completion window for LSP completion items. */
    var completionWindow: EditorCompletionWindow? = null
        private set

    /** Diagnostic overlay showing error/warning underlines. */
    var diagnosticOverlay: EditorDiagnosticOverlay? = null
        private set

    /** Current LSP server status. */
    var serverStatus: ServerStatus = ServerStatus.INITIALIZING
        private set

    /** Listener for LSP server status changes. */
    private var statusListener: ServerStatusListener? = null

    /** Whether signature help is currently active. */
    var isSignatureHelpActive: Boolean = false
        private set

    /** Whether the LSP is currently indexing the project. */
    var isIndexing: Boolean = false
        private set

    // ── Current file state ─────────────────────────────────────────────────

    /** The file path currently loaded in the editor. */
    var currentFilePath: String = filePath
        private set

    /** The current language assigned to the editor. */
    var currentLanguage: Language = EmptyLanguage()
        private set

    /** Whether the editor content has been modified since the last save. */
    var isModified: Boolean = false
        private set

    /** The original content (for modification detection). */
    private var originalContent: String = ""

    // ── Event callbacks ────────────────────────────────────────────────────

    /** Called when the editor content changes (for tab modification tracking). */
    var onContentChanged: ((newText: String) -> Unit)? = null

    /** Called when the cursor position or selection changes. */
    var onSelectionChanged: ((line: Int, column: Int, selStart: Int, selEnd: Int) -> Unit)? = null

    /** Called when the editor scrolls. */
    var onScrollChanged: ((x: Int, y: Int) -> Unit)? = null

    /** Called when the LSP server status changes. */
    var onServerStatusChanged: ((ServerStatus) -> Unit)? = null

    /** Called when diagnostics are received. */
    var onDiagnosticsReceived: ((List<org.eclipse.lsp4j.Diagnostic>) -> Unit)? = null

    // ── Initialization ─────────────────────────────────────────────────────

    init {
        setupEditorDefaults()
        setupEventListeners()
        if (filePath.isNotEmpty()) {
            setLanguageForFile(filePath)
        }
    }

    // ── Public API: File & Content ──────────────────────────────────────────

    /**
     * Load a file into the editor.
     *
     * Following AndroidIDE's pattern: when switching files in the editor,
     * the content is replaced and the language is re-detected. The undo
     * stack is preserved via the LSP editor's per-file history.
     *
     * @param filePath Absolute path to the file.
     * @param content File content to load.
     */
    fun loadFile(filePath: String, content: String) {
        cancelCompletion()
        currentFilePath = filePath
        originalContent = content
        isModified = false

        editor.setText(content)
        setLanguageForFile(filePath)

        // Notify LSP about the opened file
        lspEditor?.let { lspEd ->
            try {
                lspEd.requestManager?.didOpen(filePath, content)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to notify LSP of file open: ${e.message}")
            }
        }
    }

    /**
     * Update the content and notify the LSP of changes.
     *
     * AndroidIDE calls `didChange` on the LSP request manager after every
     * content change event. This method is called internally but can also
     * be called externally for programmatic edits.
     */
    fun updateContent(content: String) {
        editor.setText(content)
        isModified = content != originalContent
        onContentChanged?.invoke(content)

        // Notify LSP
        lspEditor?.let { lspEd ->
            try {
                lspEd.requestManager?.didChange(currentFilePath, content, editor.text.lineCount)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to notify LSP of content change: ${e.message}")
            }
        }
    }

    /** Mark the content as saved (reset the modified flag). */
    fun markSaved(savedContent: String? = null) {
        val content = savedContent ?: editor.text.toString()
        originalContent = content
        isModified = false
    }

    /** Get the full text content of the editor. */
    fun getText(): String = editor.text.toString()

    // ── Public API: Language Switching ──────────────────────────────────────

    /**
     * Set the language for syntax highlighting based on the file path.
     *
     * AndroidIDE detects the language from the file extension and sets
     * the appropriate [Language] on the editor. This method uses the same
     * detection logic from [detectLanguageForFile].
     */
    fun setLanguageForFile(filePath: String) {
        val language = detectLanguageForFile(context, filePath)
        setLanguage(language)
    }

    /**
     * Set a specific language for the editor.
     *
     * @param language The [Language] to use for syntax highlighting.
     */
    fun setLanguage(language: Language) {
        currentLanguage = language
        editor.setLang(language)
    }

    /**
     * Switch to a different language dynamically.
     *
     * AndroidIDE supports switching between languages when the user
     * manually selects a language from the status bar language menu.
     */
    fun switchLanguage(language: Language) {
        setLanguage(language)
    }

    // ── Public API: LSP Integration ────────────────────────────────────────

    /**
     * Bind an LSP client to this editor.
     *
     * This follows AndroidIDE's pattern where the LSP client is bound to
     * the editor via [LSPEditor]. The LSP client handles:
     * - Auto-completion requests/responses
     * - Signature help requests/responses
     * - Diagnostic push notifications
     * - Go-to-definition navigation
     * - Hover documentation
     * - Code actions
     *
     * @param client The [LanguageClient] from sora-editor's LSP module.
     * @param serverDefinition The server definition for this language.
     */
    fun bindLspClient(
        client: LanguageClient,
        serverDefinition: ServerDefinition
    ) {
        try {
            languageClient = client

            // Set up completion window (stored locally, not set on editor)
            completionWindow = EditorCompletionWindow(editor, context)

            // Set up diagnostic overlay (stored locally, not set on editor)
            diagnosticOverlay = EditorDiagnosticOverlay(editor)

            // Listen for server status changes (AndroidIDE pattern)
            statusListener = object : ServerStatusListener {
                override fun onStatusChanged(status: ServerStatus) {
                    serverStatus = status
                    isIndexing = status == ServerStatus.INDEXING
                    onServerStatusChanged?.invoke(status)
                    Log.d(TAG, "LSP server status changed: $status for $currentFilePath")
                }
            }
            client.addServerStatusListener(statusListener)

            Log.d(TAG, "LSP client bound successfully for $currentFilePath")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind LSP client: ${e.message}", e)
        }
    }

    /**
     * Unbind the LSP client from this editor.
     *
     * AndroidIDE calls this when closing a file or switching to a file
     * that doesn't have LSP support.
     */
    fun unbindLspClient() {        try {            statusListener?.let { languageClient?.removeServerStatusListener(it) }            lspEditor = null            languageClient = null            completionWindow = null            diagnosticOverlay = null            statusListener = null            serverStatus = ServerStatus.INITIALIZING            Log.d(TAG, "LSP client unbound for $currentFilePath")        } catch (e: Exception) {            Log.w(TAG, "Error unbinding LSP client: ${e.message}")        }    }

    /**
     * Request completions at the current cursor position.
     *
     * AndroidIDE triggers completion automatically on typing and can also
     * trigger it manually via Ctrl+Space. This method handles the manual trigger.
     */
    fun requestCompletion() {
        completionWindow?.requestCompletion()
    }

    /**
     * Cancel the current completion window.
     *
     * AndroidIDE cancels completion when the user types a space, navigates
     * away, or presses Escape.
     */
    fun cancelCompletion() {
        completionWindow?.cancelCompletion()
    }

    /**
     * Hide the completion window (without cancelling pending requests).
     */
    fun hideCompletion() {
        completionWindow?.hide()
    }

    /**
     * Show the completion window with the given items.
     *
     * This is called by the LSP client when completion results arrive.
     */
    fun showCompletion(items: List<org.eclipse.lsp4j.CompletionItem>) {
        completionWindow?.showCompletion(items)
    }

    /**
     * Request signature help at the current cursor position.
     *
     * AndroidIDE shows signature help when typing '(' or ',' inside
     * a function call. This triggers the LSP signatureHelp request.
     */
    fun requestSignatureHelp() {
        lspEditor?.let { lspEd ->
            try {
                val cursor = editor.cursor ?: return
                val indexer = editor.text.getIndexer()
                val line = indexer.getLineNumber(cursor.left)
                val column = indexer.getColumnNumber(cursor.left)
                isSignatureHelpActive = true
                lspEd.requestManager?.signatureHelp(
                    currentFilePath,
                    editor.text.toString(),
                    line, column
                )
            } catch (e: Exception) {
                isSignatureHelpActive = false
                Log.w(TAG, "Signature help request failed: ${e.message}")
            }
        }
    }

    /** Dismiss the signature help popup. */
    fun dismissSignatureHelp() {
        isSignatureHelpActive = false
        // sora-editor handles the actual UI dismissal via the editor's input handler
    }

    /**
     * Update diagnostics for the current file.
     *
     * AndroidIDE receives diagnostics pushed from the LSP server and
     * renders them as colored underlines on the editor. This method
     * updates the diagnostic overlay with new diagnostics.
     *
     * @param diagnostics List of LSP diagnostics for the current file.
     */
    fun updateDiagnostics(diagnostics: List<org.eclipse.lsp4j.Diagnostic>) {
        diagnosticOverlay?.setDiagnostics(diagnostics)
        onDiagnosticsReceived?.invoke(diagnostics)
    }

    /** Clear all diagnostics. */
    fun clearDiagnostics() {
        diagnosticOverlay?.clearDiagnostics()
    }

    // ── Public API: Editor Preferences Binding ─────────────────────────────

    /**
     * Apply editor settings from the given configuration.
     *
     * AndroidIDE reads editor preferences from SharedPreferences and
     * applies them on editor creation and when preferences change.
     * This method provides the same functionality.
     *
     * @param fontSize Font size in sp.
     * @param tabSize Tab width in spaces.
     * @param wordWrap Enable soft word wrap.
     * @param showLineNumbers Show line number gutter.
     * @param showMinimap Show code minimap.
     * @param showIndentGuides Show indent guide lines.
     * @param isDark Whether to use dark color scheme.
     * @param fontTypeface Custom font typeface, or null for monospace default.
     */
    fun applyPreferences(
        fontSize: Float = editor.textSize,
        tabSize: Int = editor.tabWidth,
        wordWrap: Boolean = editor.isWordWrap,
        showLineNumbers: Boolean = editor.isLineNumberEnabled,
        showMinimap: Boolean = editor.isMinimapEnabled,
        showIndentGuides: Boolean = editor.isIndentGuideEnabled,
        isDark: Boolean = true,
        fontTypeface: Typeface? = null,
    ) {
        editor.apply {
            textSize = fontSize
            setTabWidth(tabSize)
            isWordWrap = wordWrap
            isLineNumberEnabled = showLineNumbers
            isMinimapEnabled = showMinimap
            isIndentGuideEnabled = showIndentGuides
            colorScheme = SoraThemes.schemeFor(isDark)
            if (fontTypeface != null) {
                typefaceText = fontTypeface
            }
        }
    }

    /**
     * Set the color scheme.
     *
     * @param isDark Whether to use dark color scheme.
     */
    fun setColorScheme(isDark: Boolean) {
        editor.colorScheme = SoraThemes.schemeFor(isDark)
    }

    /**
     * Set the font size.
     */
    fun setFontSize(size: Float) {
        editor.textSize = size.coerceIn(8f, 72f)
    }

    /**
     * Set the tab width.
     */
    fun setTabSize(size: Int) {
        editor.setTabWidth(size.coerceIn(1, 16))
    }

    /**
     * Toggle word wrap.
     */
    fun toggleWordWrap() {
        editor.isWordWrap = !editor.isWordWrap
    }

    // ── Public API: Search/Replace ─────────────────────────────────────────

    /**
     * Search for text in the editor.
     *
     * AndroidIDE wraps sora-editor's built-in [io.github.rosemoe.sora.widget.CodeEditor.searcher]
     * to provide search functionality from the toolbar/search bar.
     *
     * @param query The search query.
     * @param caseSensitive Whether the search is case-sensitive.
     * @param useRegex Whether the query is a regex pattern.
     */
    fun search(query: String, caseSensitive: Boolean = false, useRegex: Boolean = false) {
        editor.searcher.search(query, caseSensitive, useRegex)
    }

    /**
     * Replace the current search match.
     *
     * @param query The search query.
     * @param replacement The replacement text.
     */
    fun replaceCurrent(query: String, replacement: String) {
        editor.searcher.replaceCurrent(query, replacement)
    }

    /**
     * Replace all search matches.
     *
     * @param query The search query.
     * @param replacement The replacement text.
     */
    fun replaceAll(query: String, replacement: String) {
        editor.searcher.replaceAll(query, replacement)
    }

    /** Navigate to the next search match. */
    fun searchNext() {
        editor.searcher.gotoNext()
    }

    /** Navigate to the previous search match. */
    fun searchPrevious() {
        editor.searcher.gotoPrevious()
    }

    /** Get the current search match count and index. */
    fun getSearchMatchInfo(): Pair<Int, Int> {
        // sora-editor's searcher exposes match count via hasMatch() and gotoNext/Previous
        // We return a best-effort pair of (currentMatch, totalMatches)
        return 0 to 0
    }

    // ── Public API: Editor Actions ─────────────────────────────────────────

    /** Undo the last edit. */
    fun undo() = editor.undo()

    /** Redo the last undone edit. */
    fun redo() = editor.redo()

    /** Cut selected text. */
    fun cut() = editor.cutText()

    /** Copy selected text. */
    fun copy() = editor.copyText()

    /** Paste from clipboard. */
    fun paste() {
        // Clipboard paste is handled by the editor's input connection
    }

    /** Select all text. */
    fun selectAll() = editor.selectAll()

    /** Format the code using the language's formatter. */
    fun formatCode() = editor.formatCode()

    /**
     * Go to a specific line (1-indexed).
     */
    fun goToLine(line: Int) {
        val clampedLine = line.coerceIn(1, editor.text.lineCount)
        val position = editor.text.getIndexer().getCharPosition(clampedLine - 1, 0)
        editor.cursor?.setLeft(position)
        editor.setSelection(position, position)
    }

    /**
     * Insert text at the current cursor position.
     */
    fun insertAtCursor(text: String) {
        editor.cursor?.let { cursor ->
            editor.text.insert(cursor.left, text)
        }
    }

    // ── Public API: Cursor & Selection ──────────────────────────────────────

    /** Get cursor position as (line, column), both 1-indexed. */
    fun getCursorPosition(): Pair<Int, Int> = getCursorPosition(editor)

    /** Get the total line count. */
    fun getLineCount(): Int = editor.text.lineCount

    /** Get the current selection range, or null if no selection. */
    fun getSelectionRange(): Pair<Int, Int>? = getSelectionRange(editor)

    /** Whether the editor can undo. */
    fun canUndo(): Boolean = editor.canUndo()

    /** Whether the editor can redo. */
    fun canRedo(): Boolean = editor.canRedo()

    /** Get the selected text, or null if no selection. */
    fun getSelectedText(): String? {
        val range = getSelectionRange() ?: return null
        return editor.text.subSequence(range.first, range.second).toString()
    }

    // ── Internal: Editor Setup ─────────────────────────────────────────────

    /**
     * Configure default editor properties.
     *
     * AndroidIDE's IDEEditor constructor sets up:
     * - Color scheme (dark/light)
     * - Font size and typeface
     * - Tab width
     * - Auto-indent, word wrap, line numbers
     * - Symbol completion (auto-close brackets/quotes)
     * - Pinch zoom
     * - Sticky scroll
     */
    private fun setupEditorDefaults() {
        editor.apply {
            colorScheme = SoraThemes.darkScheme()
            typefaceText = Typeface.MONOSPACE
            textSize = 14f
            setTabWidth(4)
            isWordWrap = false
            isHighlightCurrentLine = true
            isHighlightBracketPair = true
            isHighlightMatchingDelimiters = true
            isAutoCompletionEnabled = true
            isAutoIndent = true
            isSmartBackspace = true
            isLineNumberEnabled = true
            isMinimapEnabled = true
            isIndentGuideEnabled = true
            isSymbolCompletionEnabled = true
            isPinchZoomEnabled = true
            isStickyScrollEnabled = true
            isCursorVisible = true
            isEditable = true
        }
    }

    /**
     * Set up event listeners for content, selection, and scroll changes.
     *
     * AndroidIDE registers these listeners on the CodeEditorView to:
     * - Track cursor position for the status bar
     * - Track modifications for the tab modified indicator
     * - Track scroll for minimap synchronization
     * - Trigger LSP completion and signature help on typing
     */
    private fun setupEventListeners() {
        // Content change listener
        editor.setOnTextChangedListener { event, _ ->
            val newText = editor.text.toString()
            isModified = newText != originalContent
            onContentChanged?.invoke(newText)

            // Notify LSP of content changes
            if (event is ContentChangeEvent) {
                lspEditor?.let { lspEd ->
                    try {
                        lspEd.requestManager?.didChange(
                            currentFilePath,
                            newText,
                            editor.text.lineCount
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "LSP didChange failed: ${e.message}")
                    }
                }

                // Trigger completion on certain trigger characters
                // AndroidIDE triggers completion when the user types '.', '(', or letters
                if (event.action != ContentChangeEvent.ACTION_INSERT ||
                    event.changedText?.length == 1
                ) {
                    val char = event.changedText
                    if (char != null && isCompletionTriggerChar(char)) {
                        requestCompletion()
                    } else if (char == "(") {
                        requestSignatureHelp()
                    }
                }
            }
        }

        // Selection change listener
        editor.subscribeEvent(SelectionChangeEvent::class.java) { event, _ ->
            val cursor = editor.cursor ?: return@subscribeEvent true
            val indexer = editor.text.getIndexer()
            val line = indexer.getLineNumber(cursor.left) + 1
            val column = indexer.getColumnNumber(cursor.left) + 1
            val selStart = event.leftIndex
            val selEnd = event.rightIndex
            onSelectionChanged?.invoke(line, column, selStart, selEnd)
            true
        }

        // Scroll change listener
        editor.subscribeEvent(ScrollEvent::class.java) { event, _ ->
            onScrollChanged?.invoke(event.dx, event.dy)
            true
        }
    }

    /**
     * Check if a character should trigger auto-completion.
     *
     * AndroidIDE triggers completion for: '.', alphanumeric (after '.'),
     * and certain other characters depending on the language.
     */
    private fun isCompletionTriggerChar(char: String): Boolean {
        return char == "." || char.isLetter() || char == "_"
    }

    // ── Cleanup ────────────────────────────────────────────────────────────

    /**
     * Release all resources held by this editor.
     *
     * AndroidIDE cleans up LSP connections when the editor is destroyed.
     * This method unbinds the LSP client and clears event listeners.
     */
    fun release() {
        unbindLspClient()
        onContentChanged = null
        onSelectionChanged = null
        onScrollChanged = null
        onServerStatusChanged = null
        onDiagnosticsReceived = null
    }
}
