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

// ── LSP state ───────────────────────────────────────────────────────────────

/**
 * LSP connection state exposed to the UI.
 *
 * AndroidIDE shows the LSP status in the status bar (e.g. "Java: Connected",
 * "Java: Indexing...", "Java: Error"). This data class provides that information.
 *
 * @property status Current server status from sora-editor's LSP client.
 * @property language The language ID the server is connected for (e.g. "java").
 * @property errorCount Number of error diagnostics in the current file.
 * @property warningCount Number of warning diagnostics in the current file.
 * @property isConnected Whether the server is in a usable state.
 * @property isIndexing Whether the server is currently indexing.
 */
data class LspState(
    val status: ServerStatus = ServerStatus.INITIALIZING,
    val language: String = "",
    val errorCount: Int = 0,
    val warningCount: Int = 0,
) {
    val isConnected: Boolean
        get() = status == ServerStatus.READY

    val isIndexing: Boolean
        get() = status == ServerStatus.INDEXING

    val statusDisplay: String
        get() = when (status) {
            ServerStatus.INITIALIZING -> "Initializing..."
            ServerStatus.STARTING -> "Starting..."
            ServerStatus.READY -> "Connected"
            ServerStatus.INDEXING -> "Indexing..."
            ServerStatus.STOPPED -> "Stopped"
            ServerStatus.ERROR -> "Error"
        }
}

// ── Editor settings (persisted) ─────────────────────────────────────────────

/**
 * Persisted editor settings, following AndroidIDE's preference model.
 *
 * AndroidIDE reads editor preferences (font size, tab size, color scheme,
 * word wrap, etc.) from SharedPreferences and applies them to each
 * CodeEditorView on creation.
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
 *
 * AndroidIDE's search wraps sora-editor's built-in Searcher, supporting
 * regex and case-insensitive modes.
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
 *
 * AndroidIDE shows `Ln X, Col Y` in the bottom bar.
 */
data class CursorInfo(
    val line: Int = 1,
    val column: Int = 1,
    val selectionStart: Int = -1,
    val selectionEnd: Int = -1,
    val totalLines: Int = 0,
    val selectedText: String? = null,
) {
    val hasSelection: Boolean get() = selectionStart >= 0 && selectionEnd != selectionStart
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

// ── Undo/redo history state per file ────────────────────────────────────────

/**
 * Per-file undo/redo state. AndroidIDE preserves undo/redo history
 * when switching between tabs so that each file has its own history.
 *
 * @property filePath The file path this history belongs to.
 * @property canUndo Whether the editor can perform undo.
 * @property canRedo Whether the editor can perform redo.
 */
data class UndoRedoState(
    val filePath: String,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
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
 * - **LSP state**: Connection status, indexing, diagnostics
 * - **Undo/redo**: Per-file history tracking
 *
 * The ViewModel is the single source of truth for the editor UI.
 */
@HiltViewModel
class SoraEditorViewModel @Inject constructor() : ViewModel() {

    // ── Tab management ───────────────────────────────────────────────────

    private val _tabManager = EditorTabManager()

    val tabs: StateFlow<List<EditorTab>> = MutableStateFlow(_tabManager.tabs)
    val activeTabIndex: StateFlow<Int> = MutableStateFlow(_tabManager.activeTabIndex)
    val disambigMap: StateFlow<Map<Long, Int?>> = MutableStateFlow(emptyMap())
    val isCurrentModified: StateFlow<Boolean> = MutableStateFlow(false)
    val canReopen: StateFlow<Boolean> = MutableStateFlow(false)

    private fun refreshTabState() {
        (tabs as MutableStateFlow).value = _tabManager.tabs
        (activeTabIndex as MutableStateFlow).value = _tabManager.activeTabIndex
        (disambigMap as MutableStateFlow).value = _tabManager.getDisambiguationMap()
        (isCurrentModified as MutableStateFlow).value = _tabManager.isCurrentModified
        (canReopen as MutableStateFlow).value = _tabManager.canReopen
    }

    val activeFilePath: String get() = _tabManager.activeTab?.filePath ?: ""
    val activeTabDisplayName: String get() = _tabManager.activeTab?.displayName() ?: ""
    val activeTabLanguage: String get() = _tabManager.activeTab?.let { getLanguageName(it.filePath) } ?: ""
    val activeTabEncoding: String get() = _tabManager.activeTab?.encoding ?: "UTF-8"
    val activeTabContent: String get() = _tabManager.activeTab?.content ?: ""
    val activeTabCursorState: Triple<Int, Int, Int>? get() {
        val tab = _tabManager.activeTab ?: return null
        return Triple(tab.cursorLine, tab.cursorColumn, tab.scrollY)
    }
    val activeTabOriginalContent: String get() = _tabManager.activeTab?.content ?: ""

    // ── Editor reference ─────────────────────────────────────────────────

    var editor: CodeEditor? = null
        private set

    fun attachEditor(editor: CodeEditor) {
        this.editor = editor
    }

    fun detachEditor() {
        saveCurrentCursorState()
        saveUndoRedoState()
        editor = null
    }

    // ── Cursor info (live, for status bar) ───────────────────────────────

    private val _cursorInfo = MutableStateFlow(CursorInfo())
    val cursorInfo: StateFlow<CursorInfo> = _cursorInfo

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

    // ── LSP state ─────────────────────────────────────────────────────────

    private val _lspState = MutableStateFlow(LspState())
    val lspState: StateFlow<LspState> = _lspState

    /** Update the LSP server status. Called from [IDEEditor.onServerStatusChanged]. */
    fun updateLspStatus(status: ServerStatus, language: String = "") {
        _lspState.value = _lspState.value.copy(
            status = status,
            language = language.ifEmpty { _lspState.value.language }
        )
    }

    /** Update diagnostic counts from the overlay. */
    fun updateDiagnosticCounts(errorCount: Int, warningCount: Int) {
        _lspState.value = _lspState.value.copy(
            errorCount = errorCount,
            warningCount = warningCount,
        )
    }

    // ── Undo/redo state ──────────────────────────────────────────────────

    /** Per-file undo/redo state tracking. */
    private val _undoRedoStates = mutableMapOf<String, UndoRedoState>()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo

    /** Update undo/redo state from the editor. */
    fun updateUndoRedoState() {
        val ed = editor ?: return
        val path = activeFilePath
        if (path.isEmpty()) return
        val state = UndoRedoState(
            filePath = path,
            canUndo = ed.canUndo(),
            canRedo = ed.canRedo(),
        )
        _undoRedoStates[path] = state
        _canUndo.value = state.canUndo
        _canRedo.value = state.canRedo
    }

    /** Save the current editor's undo/redo state before switching tabs. */
    private fun saveUndoRedoState() {
        val ed = editor ?: return
        val path = activeFilePath
        if (path.isEmpty()) return
        _undoRedoStates[path] = UndoRedoState(
            filePath = path,
            canUndo = ed.canUndo(),
            canRedo = ed.canRedo(),
        )
    }

    /** Restore undo/redo state for the active tab. */
    private fun restoreUndoRedoState() {
        val path = activeFilePath
        val state = _undoRedoStates[path]
        _canUndo.value = state?.canUndo ?: false
        _canRedo.value = state?.canRedo ?: false
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

    private val TAG = "XCoderEditorVM"

    private val _unsavedDialog = MutableStateFlow(UnsavedDialogState())
    val unsavedDialog: StateFlow<UnsavedDialogState> = _unsavedDialog

    fun dismissUnsavedDialog() {
        _unsavedDialog.value = UnsavedDialogState()
    }

    fun saveAndProceed() {
        val state = _unsavedDialog.value
        saveActiveFile()
        state.pendingAction?.invoke()
        _unsavedDialog.value = UnsavedDialogState()
    }

    fun discardAndProceed() {
        val state = _unsavedDialog.value
        val idx = state.tabIndex
        if (idx >= 0) _tabManager.markSaved(idx)
        refreshTabState()
        state.pendingAction?.invoke()
        _unsavedDialog.value = UnsavedDialogState()
    }

    // ── Editor settings ──────────────────────────────────────────────────

    private val _settings = MutableStateFlow(EditorSettings())
    val settings: StateFlow<EditorSettings> = _settings

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
            .take(50)
    }

    // ── Pending action for the editor ────────────────────────────────────

    private val _pendingAction = MutableStateFlow<EditorAction?>(null)
    val pendingAction: StateFlow<EditorAction?> = _pendingAction

    fun dispatchAction(action: EditorAction) {
        _pendingAction.value = action
    }

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
     */
    fun openFile(filePath: String, onFileNotFound: ((String) -> Unit)? = null) {
        viewModelScope.launch {
            if (_tabManager.isOpen(filePath)) {
                saveCurrentCursorState()
                saveUndoRedoState()
                _tabManager.switchToPath(filePath)
                refreshTabState()
                restoreUndoRedoState()
                return@launch
            }

            val result = withContext(Dispatchers.IO) {
                readFileWithEncoding(filePath)
            }

            if (result == null) {
                onFileNotFound?.invoke(filePath)
                return@launch
            }

            val (content, encoding) = result
            addToRecent(filePath)
            saveCurrentCursorState()
            saveUndoRedoState()
            _tabManager.openFile(filePath, content, encoding, activate = true)
            refreshTabState()
            restoreUndoRedoState()
        }
    }

    fun openFileWithContent(filePath: String, content: String, encoding: String = "UTF-8") {
        addToRecent(filePath)
        saveCurrentCursorState()
        saveUndoRedoState()
        _tabManager.openFile(filePath, content, encoding, activate = true)
        refreshTabState()
        restoreUndoRedoState()
    }

    fun openUntitled() {
        saveCurrentCursorState()
        saveUndoRedoState()
        _tabManager.openUntitled()
        refreshTabState()
        restoreUndoRedoState()
    }

    fun saveActiveFile(): String? {
        val tab = _tabManager.activeTab ?: return null
        if (tab.isUntitled) return null

        val text = editor?.text?.toString() ?: tab.content
        val encoding = tab.encoding

        viewModelScope.launch(Dispatchers.IO) {
            File(tab.filePath).writeText(text, charset(encoding))
        }

        _tabManager.markActiveSaved(text)
        refreshTabState()
        return text
    }

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

    fun closeFile(filePath: String) {
        val index = _tabManager.indexOfPath(filePath)
        if (index >= 0) closeTab(index)
    }

    fun switchToTab(index: Int) {
        if (index == _tabManager.activeTabIndex) return
        saveCurrentCursorState()
        saveUndoRedoState()
        _tabManager.switchTo(index)
        refreshTabState()
        restoreUndoRedoState()
    }

    fun reopenClosedTab() {
        val idx = _tabManager.reopenLastClosed()
        if (idx >= 0) refreshTabState()
    }

    fun closeOtherTabs() {
        saveCurrentCursorState()
        _tabManager.closeOtherTabs(force = true)
        refreshTabState()
    }

    fun saveCurrentCursorState() {
        val ed = editor ?: return
        val (line, col) = getCursorPosition(ed)
        _tabManager.updateActiveCursorState(
            line = line,
            column = col,
            scrollX = 0,
            scrollY = ed.scrollY,
            selectionStart = ed.cursor?.left ?: -1,
            selectionEnd = ed.cursor?.right ?: -1,
        )
        _tabManager.updateActiveContent(ed.text.toString(), activeTabOriginalContent)
    }

    fun getModifiedTabs(): List<EditorTab> = _tabManager.modifiedTabs
    fun getOpenFilePaths(): List<String> = _tabManager.getOpenFilePaths()

    // ── Encoding detection ───────────────────────────────────────────────

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

    // ── Content change handler ───────────────────────────────────────────

    fun onEditorContentChanged(newText: String) {
        val idx = _tabManager.activeTabIndex
        if (idx >= 0) {
            _tabManager.updateContent(idx, newText, activeTabOriginalContent)
            refreshTabState()
        }
        // Update undo/redo state after content changes
        updateUndoRedoState()
    }

    // ── Formatting helpers ───────────────────────────────────────────────

    private val dateFormatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun formatTimestamp(timestamp: Long): String = dateFormatter.format(Date(timestamp))

    // ── Cleanup ──────────────────────────────────────────────────────────

    override fun onCleared() {
        super.onCleared()
        saveCurrentCursorState()
        saveUndoRedoState()
        editor = null
        _tabManager.clear()
        _undoRedoStates.clear()
    }
}

// ── File I/O utilities ──────────────────────────────────────────────────────

private suspend fun readFileWithEncoding(path: String): Pair<String, String>? {
    return withContext(Dispatchers.IO) {
        val file = File(path)
        if (!file.exists()) return@withContext null

        val encoding = detectEncodingForFile(path)
        try {
            val content = file.readText(charset(encoding))
            content to encoding
        } catch (e: Exception) {
            try {
                file.readText(Charsets.UTF_8) to "UTF-8"
            } catch (e2: Exception) {
                "" to "UTF-8"
            }
        }
    }
}

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
