@file:Suppress("TooManyFunctions")
package com.xcoder.editor.sora

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rosemoe.sora.widget.CodeEditor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

// ── Editor settings (persisted) ─────────────────────────────────────────────

/**
 * Persisted editor settings, following AndroidIDE's preference model.
 * AndroidIDE reads editor preferences (font size, tab size, color scheme,
 * word wrap, etc.) from SharedPreferences and applies them to each
 * CodeEditorView on creation.
 *
 * @property fontSize Font size in sp.
 * @property tabSize Tab width in spaces.
 * @property isDark Whether the editor uses a dark color scheme.
 * @property wordWrap Enable soft word wrap.
 * @property showLineNumbers Show line number gutter.
 * @property showMinimap Show code minimap.
 * @property showIndentGuides Show indent guide lines.
 * @property stickyScroll Enable sticky scroll for block headers.
 * @property autoIndent Auto-indent new lines.
 * @property autoCompletion Enable completion popup.
 * @property symbolCompletion Auto-close brackets/quotes.
 * @property smartBackspace Smart backspace behavior.
 * @property pinchZoom Pinch to zoom font size.
 */
data class EditorSettings(
    val fontSize: Float = 14f,
    val tabSize: Int = 4,
    val isDark: Boolean = true,
    val wordWrap: Boolean = false,
    val showLineNumbers: Boolean = true,
    val showMinimap: Boolean = true,
    val showIndentGuides: Boolean = true,
    val stickyScroll: Boolean = true,
    val autoIndent: Boolean = true,
    val autoCompletion: Boolean = true,
    val symbolCompletion: Boolean = true,
    val smartBackspace: Boolean = true,
    val pinchZoom: Boolean = true,
) {
    /** Convert to [EditorConfig] for the Compose wrapper. */
    fun toConfig() = EditorConfig(
        fontSize = fontSize,
        tabSize = tabSize,
        isDark = isDark,
        wordWrap = wordWrap,
        showLineNumbers = showLineNumbers,
        showMinimap = showMinimap,
        showIndentGuides = showIndentGuides,
        stickyScroll = stickyScroll,
        autoIndent = autoIndent,
        autoCompletion = autoCompletion,
        symbolCompletion = symbolCompletion,
        smartBackspace = smartBackspace,
        pinchZoom = pinchZoom,
    )
}

// ── Search state ────────────────────────────────────────────────────────────

/**
 * Search state that persists across tabs.
 * AndroidIDE's search wraps sora-editor's built-in Searcher, supporting
 * regex and case-insensitive modes. This state mirrors that model.
 *
 * @property query The search query text.
 * @property replacement The replacement text.
 * @property caseSensitive Whether search is case-sensitive.
 * @property useRegex Whether the query is treated as a regex pattern.
 * @property matchCount Number of matches found (-1 if not yet searched).
 * @property currentMatchIndex Index of the current match (-1 if none).
 */
data class SearchState(
    val query: String = "",
    val replacement: String = "",
    val caseSensitive: Boolean = false,
    val useRegex: Boolean = false,
    val matchCount: Int = -1,
    val currentMatchIndex: Int = -1,
    val isVisible: Boolean = false,
)

// ── Cursor info for status bar ──────────────────────────────────────────────

/**
 * Current cursor/selection info displayed in the status bar.
 * AndroidIDE shows `Ln X, Col Y` in the bottom bar; we also include
 * selection range and total lines.
 */
data class CursorInfo(
    val line: Int = 1,
    val column: Int = 1,
    val selectionStart: Int = -1,
    val selectionEnd: Int = -1,
    val totalLines: Int = 0,
    val selectedText: String? = null,
) {
    /** Whether there is an active text selection. */
    val hasSelection: Boolean get() = selectionStart >= 0 && selectionEnd != selectionStart

    /** Selected character count. */
    val selectionLength: Int
        get() = if (hasSelection) kotlin.math.abs(selectionEnd - selectionStart) else 0
}

// ── Recent file entry ───────────────────────────────────────────────────────

data class RecentFileEntry(
    val filePath: String,
    val lastOpened: Long = System.currentTimeMillis(),
)

// ── Unsaved changes dialog state ────────────────────────────────────────────

data class UnsavedDialogState(
    val showing: Boolean = false,
    val tab: EditorTab? = null,
    val tabIndex: Int = -1,
    val pendingAction: (() -> Unit)? = null,
)

// ── ViewModel ───────────────────────────────────────────────────────────────

/**
 * ViewModel managing the multi-tab editor state.
 *
 * Follows AndroidIDE's [CodeEditorView] architecture:
 * - **Tab management**: [EditorTabManager] handles open/close/switch/reopen
 * - **Modified tracking**: Per-tab dirty flag with `*` indicator on tab
 * - **File I/O**: Coroutine-based read/write on [Dispatchers.IO]
 * - **Encoding detection**: BOM-based and heuristic
 * - **Recent files**: MRU list of opened files
 * - **Search state**: Persistent across tab switches
 * - **Cursor info**: Live cursor position for status bar
 * - **Editor settings**: Font size, tab size, color scheme, toggles
 *
 * The ViewModel is the single source of truth for the editor UI.
 * The Compose `EditorScreen` observes these state flows and renders accordingly.
 */
@HiltViewModel
class SoraEditorViewModel @Inject constructor() : ViewModel() {

    // ── Tab management ───────────────────────────────────────────────────

    private val _tabManager = EditorTabManager()

    /** Observable tab list for Compose. */
    val tabs: StateFlow<List<EditorTab>> = MutableStateFlow(_tabManager.tabs)

    /** Observable active tab index. */
    val activeTabIndex: StateFlow<Int> = MutableStateFlow(_tabManager.activeTabIndex)

    /** Disambiguation map for tab display names (AndroidIDE pattern). */
    val disambigMap: StateFlow<Map<Long, Int?>> = MutableStateFlow(emptyMap())

    /** Whether the active tab has unsaved changes. */
    val isCurrentModified: StateFlow<Boolean> = MutableStateFlow(false)

    /** Can we reopen a recently closed tab? */
    val canReopen: StateFlow<Boolean> = MutableStateFlow(false)

    private fun refreshTabState() {
        (tabs as MutableStateFlow).value = _tabManager.tabs
        (activeTabIndex as MutableStateFlow).value = _tabManager.activeTabIndex
        (disambigMap as MutableStateFlow).value = _tabManager.getDisambiguationMap()
        (isCurrentModified as MutableStateFlow).value = _tabManager.isCurrentModified
        (canReopen as MutableStateFlow).value = _tabManager.canReopen
    }

    /** Get the active tab's file path. */
    val activeFilePath: String get() = _tabManager.activeTab?.filePath ?: ""

    /** Get the active tab's display name. */
    val activeTabDisplayName: String get() = _tabManager.activeTab?.displayName() ?: ""

    /** Get the active tab's language name. */
    val activeTabLanguage: String get() = _tabManager.activeTab?.let { getLanguageName(it.filePath) } ?: ""

    /** Get the active tab's encoding. */
    val activeTabEncoding: String get() = _tabManager.activeTab?.encoding ?: "UTF-8"

    /** Get the active tab's initial content (for editor initialization). */
    val activeTabContent: String get() = _tabManager.activeTab?.content ?: ""

    /** Get the active tab's cursor state (for restoring on tab switch). */
    val activeTabCursorState: Triple<Int, Int, Int>? get() {
        val tab = _tabManager.activeTab ?: return null
        return Triple(tab.cursorLine, tab.cursorColumn, tab.scrollY)
    }

    /** Get a snapshot of the current tab for reference (to detect modifications). */
    val activeTabOriginalContent: String get() = _tabManager.activeTab?.content ?: ""

    // ── Editor reference ─────────────────────────────────────────────────

    /** Direct reference to the current CodeEditor (set from Compose wrapper). */
    var editor: CodeEditor? = null
        private set

    /** Register the editor instance (called from Compose wrapper). */
    fun attachEditor(editor: CodeEditor) {
        this.editor = editor
    }

    /** Detach the editor (on tab switch or cleanup). */
    fun detachEditor() {
        saveCurrentCursorState()
        editor = null
    }

    // ── Cursor info (live, for status bar) ───────────────────────────────

    private val _cursorInfo = MutableStateFlow(CursorInfo())
    val cursorInfo: StateFlow<CursorInfo> = _cursorInfo

    /** Update cursor info from editor events. */
    fun updateCursorInfo(line: Int, column: Int, selStart: Int, selEnd: Int) {
        val totalLines = editor?.let { getLineCount(it) } ?: 0
        _cursorInfo.value = CursorInfo(
            line = line,
            column = column,
            selectionStart = selStart,
            selectionEnd = selEnd,
            totalLines = totalLines,
            selectedText = if (selStart >= 0 && selStart != selEnd) {
                editor?.text?.subSequence(
                    minOf(selStart, selEnd),
                    maxOf(selStart, selEnd)
                )?.toString()
            } else null,
        )
    }

    // ── Search state ─────────────────────────────────────────────────────

    private val _searchState = MutableStateFlow(SearchState())
    val searchState: StateFlow<SearchState> = _searchState

    fun setSearchVisible(visible: Boolean) {
        _searchState.value = _searchState.value.copy(isVisible = visible)
    }

    fun updateSearchQuery(query: String) {
        _searchState.value = _searchState.value.copy(query = query)
    }

    fun updateSearchReplacement(replacement: String) {
        _searchState.value = _searchState.value.copy(replacement = replacement)
    }

    fun toggleSearchCaseSensitive() {
        _searchState.value = _searchState.value.copy(
            caseSensitive = !_searchState.value.caseSensitive
        )
    }

    fun toggleSearchRegex() {
        _searchState.value = _searchState.value.copy(
            useRegex = !_searchState.value.useRegex
        )
    }

    // ── Unsaved changes dialog ───────────────────────────────────────────

    private val _unsavedDialog = MutableStateFlow(UnsavedDialogState())
    val unsavedDialog: StateFlow<UnsavedDialogState> = _unsavedDialog

    fun dismissUnsavedDialog() {
        _unsavedDialog.value = UnsavedDialogState()
    }

    /** Save and proceed with the pending action. */
    fun saveAndProceed() {
        val state = _unsavedDialog.value
        saveActiveFile()
        state.pendingAction?.invoke()
        _unsavedDialog.value = UnsavedDialogState()
    }

    /** Discard changes and proceed with the pending action. */
    fun discardAndProceed() {
        val state = _unsavedDialog.value
        // Mark the tab as unmodified, then proceed
        val idx = state.tabIndex
        if (idx >= 0) _tabManager.markSaved(idx)
        refreshTabState()
        state.pendingAction?.invoke()
        _unsavedDialog.value = UnsavedDialogState()
    }

    // ── Editor settings ──────────────────────────────────────────────────

    private val _settings = MutableStateFlow(EditorSettings())
    val settings: StateFlow<EditorSettings> = _settings

    /** Update settings (partial). */
    fun updateSettings(transform: (EditorSettings) -> EditorSettings) {
        _settings.value = transform(_settings.value)
    }

    // ── Recent files ─────────────────────────────────────────────────────

    private val _recentFiles = MutableStateFlow<List<RecentFileEntry>>(emptyList())
    val recentFiles: StateFlow<List<RecentFileEntry>> = _recentFiles

    private fun addToRecent(filePath: String) {
        if (filePath.isEmpty()) return
        val current = _recentFiles.value.filter { it.filePath != filePath }
        _recentFiles.value = listOf(RecentFileEntry(filePath)) + current
            .take(50) // Keep last 50
    }

    // ── Pending action for the editor ────────────────────────────────────

    private val _pendingAction = MutableStateFlow<EditorAction?>(null)
    val pendingAction: StateFlow<EditorAction?> = _pendingAction

    /** Dispatch an action to the editor (one-shot, consumed after handling). */
    fun dispatchAction(action: EditorAction) {
        _pendingAction.value = action
    }

    /** Clear the pending action (called after the editor handles it). */
    fun clearPendingAction() {
        _pendingAction.value = null
    }

    // ── File operations ──────────────────────────────────────────────────

    /**
     * Open a file: read its content on IO, add a tab, and switch to it.
     *
     * Following AndroidIDE's pattern: if the file is already open in a tab,
     * just switch to that tab. Otherwise, read the file content (with encoding
     * detection) and create a new tab.
     *
     * @param filePath Absolute path to the file.
     * @param onFileNotFound Called when the file doesn't exist.
     */
    fun openFile(filePath: String, onFileNotFound: ((String) -> Unit)? = null) {
        viewModelScope.launch {
            // If already open, just switch
            if (_tabManager.isOpen(filePath)) {
                saveCurrentCursorState()
                _tabManager.switchToPath(filePath)
                refreshTabState()
                return@launch
            }

            // Read file content on IO thread
            val result = withContext(Dispatchers.IO) {
                readFileWithEncoding(filePath)
            }

            if (result == null) {
                onFileNotFound?.invoke(filePath)
                return@launch
            }

            val (content, encoding) = result
            addToRecent(filePath)

            // Save current tab's cursor state before switching
            saveCurrentCursorState()

            _tabManager.openFile(filePath, content, encoding, activate = true)
            refreshTabState()
        }
    }

    /**
     * Open a file with pre-loaded content (no disk I/O).
     * Used when content is already available (e.g. from file picker).
     */
    fun openFileWithContent(filePath: String, content: String, encoding: String = "UTF-8") {
        addToRecent(filePath)
        saveCurrentCursorState()
        _tabManager.openFile(filePath, content, encoding, activate = true)
        refreshTabState()
    }

    /** Open a new untitled tab. */
    fun openUntitled() {
        saveCurrentCursorState()
        _tabManager.openUntitled()
        refreshTabState()
    }

    /**
     * Save the active file to disk.
     * @return The saved text, or null if there's nothing to save.
     */
    fun saveActiveFile(): String? {
        val tab = _tabManager.activeTab ?: return null
        if (tab.isUntitled) return null  // Untitled files can't be saved without a path

        val text = editor?.text?.toString() ?: tab.content
        val encoding = tab.encoding

        viewModelScope.launch(Dispatchers.IO) {
            File(tab.filePath).writeText(text, charset(encoding))
        }

        _tabManager.markActiveSaved(text)
        refreshTabState()
        return text
    }

    /**
     * Save active file as a new path (Save As).
     */
    fun saveActiveFileAs(newPath: String, newEncoding: String? = null) {
        val text = editor?.text?.toString() ?: ""
        val encoding = newEncoding ?: _tabManager.activeTab?.encoding ?: "UTF-8"

        viewModelScope.launch(Dispatchers.IO) {
            File(newPath).writeText(text, charset(encoding))
        }

        val idx = _tabManager.activeTabIndex
        if (idx >= 0) {
            _tabManager.updateTabPath(idx, newPath, encoding)
            _tabManager.markSaved(idx, text)
            refreshTabState()
        }
        addToRecent(newPath)
    }

    /**
     * Close a tab by index, showing unsaved dialog if needed.
     */
    fun closeTab(index: Int) {
        val result = _tabManager.closeTabAt(index)
        when (result) {
            is TabCloseResult.Closed -> refreshTabState()
            is TabCloseResult.UnsavedChanges -> {
                _unsavedDialog.value = UnsavedDialogState(
                    showing = true,
                    tab = result.tab,
                    tabIndex = result.tabIndex,
                    pendingAction = {
                        val forceResult = _tabManager.forceCloseAfterConfirm(result.tabIndex)
                        if (forceResult is TabCloseResult.Closed) refreshTabState()
                    }
                )
            }
            is TabCloseResult.CannotCloseLastTab -> { /* ignore */ }
        }
    }

    /** Close a specific file (by path). */
    fun closeFile(filePath: String) {
        val index = _tabManager.indexOfPath(filePath)
        if (index >= 0) closeTab(index)
    }

    /** Switch to a tab by index. */
    fun switchToTab(index: Int) {
        if (index == _tabManager.activeTabIndex) return
        saveCurrentCursorState()
        _tabManager.switchTo(index)
        refreshTabState()
    }

    /** Reopen the most recently closed tab. */
    fun reopenClosedTab() {
        val idx = _tabManager.reopenLastClosed()
        if (idx >= 0) refreshTabState()
    }

    /** Close all tabs except the active one. */
    fun closeOtherTabs() {
        saveCurrentCursorState()
        val unsaved = _tabManager.closeOtherTabs(force = true)
        refreshTabState()
    }

    /**
     * Save the current cursor/scroll state to the active tab.
     * Called before tab switches (AndroidIDE pattern: preserve state per tab).
     */
    fun saveCurrentCursorState() {
        val ed = editor ?: return
        val (line, col) = getCursorPosition(ed)
        _tabManager.updateActiveCursorState(
            line = line,
            column = col,
            scrollX = 0,  // sora-editor doesn't easily expose scroll X
            scrollY = ed.scrollY,
            selectionStart = ed.cursor?.left ?: -1,
            selectionEnd = ed.cursor?.right ?: -1,
        )
        // Also save current content
        _tabManager.updateActiveContent(ed.text.toString(), activeTabOriginalContent)
    }

    /** Get all modified tabs (for "save all" or app exit check). */
    fun getModifiedTabs(): List<EditorTab> = _tabManager.modifiedTabs

    /** Get all open file paths (for session restoration). */
    fun getOpenFilePaths(): List<String> = _tabManager.getOpenFilePaths()

    // ── Encoding detection ───────────────────────────────────────────────

    /**
     * Detect file encoding using BOM (Byte Order Mark) and heuristic analysis.
     * Falls back to UTF-8 which is the safest default.
     */
    fun detectEncoding(path: String): String {
        val file = File(path)
        if (!file.exists()) return "UTF-8"
        val bytes = file.readBytes().take(4).toByteArray()
        return when {
            bytes.size >= 3 && bytes[0] == 0xEF.toByte() &&
                    bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() -> "UTF-8"
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() &&
                    bytes[1] == 0xFE.toByte() -> "UTF-16LE"
            bytes.size >= 2 && bytes[0] == 0xFE.toByte() &&
                    bytes[1] == 0xFF.toByte() -> "UTF-16BE"
            bytes.size >= 4 && bytes[0] == 0x00.toByte() &&
                    bytes[1] == 0x00.toByte() && bytes[2] == 0xFE.toByte() &&
                    bytes[3] == 0xFF.toByte() -> "UTF-32BE"
            bytes.size >= 4 && bytes[0] == 0xFF.toByte() &&
                    bytes[1] == 0xFE.toByte() && bytes[2] == 0x00.toByte() &&
                    bytes[3] == 0x00.toByte() -> "UTF-32LE"
            else -> "UTF-8"
        }
    }

    // ── Content change handler (from editor callback) ───────────────────

    /**
     * Called when the editor content changes.
     * Updates the active tab's dirty flag following AndroidIDE's pattern:
     * the tab text shows `*` prefix when modified.
     */
    fun onEditorContentChanged(newText: String) {
        val idx = _tabManager.activeTabIndex
        if (idx >= 0) {
            _tabManager.updateContent(idx, newText, activeTabOriginalContent)
            refreshTabState()
        }
    }

    // ── Formatting helpers ───────────────────────────────────────────────

    private val dateFormatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    /** Format a timestamp for display. */
    fun formatTimestamp(timestamp: Long): String = dateFormatter.format(Date(timestamp))

    // ── Cleanup ──────────────────────────────────────────────────────────

    override fun onCleared() {
        super.onCleared()
        saveCurrentCursorState()
        editor = null
        _tabManager.clear()
    }
}

// ── File I/O utilities (coroutine-safe) ─────────────────────────────────────

/**
 * Read a file with automatic encoding detection.
 * @return Pair of (content, encoding) or null if the file doesn't exist.
 */
private suspend fun readFileWithEncoding(path: String): Pair<String, String>? {
    return withContext(Dispatchers.IO) {
        val file = File(path)
        if (!file.exists()) return@withContext null

        val encoding = detectEncodingForFile(path)
        try {
            val content = file.readText(charset(encoding))
            content to encoding
        } catch (e: Exception) {
            // Fallback: try UTF-8
            try {
                file.readText(Charsets.UTF_8) to "UTF-8"
            } catch (e2: Exception) {
                "" to "UTF-8"
            }
        }
    }
}

/**
 * Detect encoding from BOM.
 */
private fun detectEncodingForFile(path: String): String {
    val file = File(path)
    if (!file.exists()) return "UTF-8"
    val bytes = file.readBytes().take(4).toByteArray()
    return when {
        bytes.size >= 3 && bytes[0] == 0xEF.toByte() &&
                bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() -> "UTF-8"
        bytes.size >= 2 && bytes[0] == 0xFF.toByte() &&
                bytes[1] == 0xFE.toByte() -> "UTF-16LE"
        bytes.size >= 2 && bytes[0] == 0xFE.toByte() &&
                bytes[1] == 0xFF.toByte() -> "UTF-16BE"
        else -> "UTF-8"
    }
}
