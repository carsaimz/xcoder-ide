@file:Suppress("TooManyFunctions")
package com.xcoder.editor.sora

import java.io.File
import java.util.LinkedList
import java.util.Stack
import kotlin.math.max
import kotlin.math.min

// ── Data models ─────────────────────────────────────────────────────────────

/**
 * Represents a single open editor tab, following AndroidIDE's tab model.
 *
 * AndroidIDE uses unique naming when files share names (e.g. `MainActivity.java (1)`)
 * and tracks a `*` modified indicator on tab text. Each tab stores its own undo/redo
 * snapshot, cursor position, and scroll state so that switching tabs is seamless.
 *
 * @property filePath Absolute path of the file on disk.
 * @property tabId Unique stable ID for this tab instance (survives renames).
 * @property content The full text content (cached in-memory).
 * @property isModified Whether the content differs from what is on disk.
 * @property encoding Character encoding used when reading/writing the file.
 * @property cursorLine 1-indexed cursor line (preserved across tab switches).
 * @property cursorColumn 1-indexed cursor column.
 * @property scrollX Horizontal scroll position.
 * @property scrollY Vertical scroll position (line-based).
 * @property selectionStart Start of selection (character index), or -1 if no selection.
 * @property selectionEnd End of selection (character index), or -1 if no selection.
 */
data class EditorTab(
    val filePath: String,
    val tabId: Long,
    val content: String = "",
    val isModified: Boolean = false,
    val encoding: String = "UTF-8",
    val cursorLine: Int = 1,
    val cursorColumn: Int = 1,
    val scrollX: Int = 0,
    val scrollY: Int = 0,
    val selectionStart: Int = -1,
    val selectionEnd: Int = -1,
) {
    /** Short file name (e.g. `MainActivity.java`). */
    val fileName: String get() = filePath.substringAfterLast(File.separator)

    /** File extension without the dot, or empty string. */
    val fileExtension: String get() = fileName.substringAfterLast('.', "")

    /** Whether this is a newly created file that hasn't been saved yet. */
    val isUntitled: Boolean get() = filePath.isEmpty()

    /** Display name for the tab, including modified indicator. */
    fun displayName(disambigIndex: Int? = null): String {
        val base = if (disambigIndex != null && disambigIndex > 0) {
            "$fileName ($disambigIndex)"
        } else {
            fileName.ifEmpty { "Untitled" }
        }
        return if (isModified) "*$base" else base
    }

    /** Create a copy with updated modification state. */
    fun copyModified(modified: Boolean) = copy(isModified = modified)

    /** Create a copy with updated content and mark as modified if content changed. */
    fun copyContent(newContent: String, originalContent: String? = null): EditorTab =
        copy(
            content = newContent,
            isModified = newContent != (originalContent ?: content)
        )

    /** Create a copy with updated cursor/scroll state. */
    fun copyCursorState(
        line: Int = cursorLine,
        column: Int = cursorColumn,
        scrollX: Int = this.scrollX,
        scrollY: Int = this.scrollY,
        selectionStart: Int = this.selectionStart,
        selectionEnd: Int = this.selectionEnd,
    ) = copy(
        cursorLine = line,
        cursorColumn = column,
        scrollX = scrollX,
        scrollY = scrollY,
        selectionStart = selectionStart,
        selectionEnd = selectionEnd,
    )

    companion object {
        /** Generate a stable tab ID from a file path. */
        fun idForPath(path: String): Long = if (path.isEmpty()) {
            System.nanoTime()
        } else {
            // Simple stable hash; collisions are extremely unlikely for file paths
            var hash = 17L
            for (ch in path) {
                hash = hash * 31 + ch.code
            }
            hash
        }

        /** Create a new untitled tab with an auto-generated name. */
        fun untitled(index: Int): EditorTab = EditorTab(
            filePath = "",
            tabId = System.nanoTime() + index,
            content = "",
            isModified = false,
        )
    }
}

// ── Tab close result ────────────────────────────────────────────────────────

/**
 * Result of attempting to close a tab.
 * Follows AndroidIDE's pattern: if unsaved, the caller must confirm before closing.
 */
sealed class TabCloseResult {
    /** Tab was closed successfully. */
    data class Closed(val tab: EditorTab, val tabIndex: Int) : TabCloseResult()

    /** Tab has unsaved changes – caller must confirm. */
    data class UnsavedChanges(val tab: EditorTab, val tabIndex: Int) : TabCloseResult()

    /** Tab was the only one and cannot be closed (editor requires ≥ 1 tab). */
    object CannotCloseLastTab : TabCloseResult()
}

// ── Tab manager ─────────────────────────────────────────────────────────────

/**
 * Manages editor tabs following AndroidIDE's [CodeEditorView] tab model.
 *
 * AndroidIDE uses a `ViewFlipper` + `TabLayout` where:
 * - Each tab wraps its own [CodeEditor] instance
 * - Switching tabs hides the current editor and shows the target one
 * - Modified files show a `*` prefix on the tab text
 * - Files with identical names get disambiguated suffixes like `(1)`, `(2)`
 * - Recently closed tabs can be re-opened
 *
 * This manager is a pure-Kotlin state holder (no Android dependencies) that
 * can be used from both Compose and the ViewModel layer.
 *
 * @property maxTabs Maximum number of open tabs (default 30, like AndroidIDE).
 * @property maxRecentClosed Maximum recently-closed entries for re-open (default 20).
 */
class EditorTabManager(
    private val maxTabs: Int = 30,
    private val maxRecentClosed: Int = 20,
) {
    // ── Internal state ───────────────────────────────────────────────────

    private val _tabs = mutableListOf<EditorTab>()
    private val _recentlyClosed = Stack<EditorTab>()
    private var _activeTabIndex = -1
    private var _nextUntitledIndex = 1

    // ── Read-only accessors ──────────────────────────────────────────────

    /** Currently open tabs (ordered left-to-right). */
    val tabs: List<EditorTab> get() = _tabs.toList()

    /** Index of the currently active tab, or -1 if no tabs are open. */
    val activeTabIndex: Int get() = _activeTabIndex

    /** The currently active tab, or null. */
    val activeTab: EditorTab? get() = _tabs.getOrNull(_activeTabIndex)

    /** Number of open tabs. */
    val tabCount: Int get() = _tabs.size

    /** Recently closed tabs (most recent first). */
    val recentlyClosed: List<EditorTab> get() = _recentlyClosed.toList()

    /** Whether there are recently closed tabs that can be re-opened. */
    val canReopen: Boolean get() = _recentlyClosed.isNotEmpty()

    // ── Tab queries ──────────────────────────────────────────────────────

    /** Find the index of a tab by file path, or -1. */
    fun indexOfPath(filePath: String): Int =
        _tabs.indexOfFirst { it.filePath == filePath }

    /** Find the index of a tab by tab ID, or -1. */
    fun indexOfId(tabId: Long): Int =
        _tabs.indexOfFirst { it.tabId == tabId }

    /** Check if a file is already open in a tab. */
    fun isOpen(filePath: String): Boolean = indexOfPath(filePath) >= 0

    /** Check if the active tab has unsaved changes. */
    val isCurrentModified: Boolean get() = activeTab?.isModified == true

    /** Get all modified tabs. */
    val modifiedTabs: List<EditorTab> get() = _tabs.filter { it.isModified }

    // ── Disambiguation (AndroidIDE pattern) ──────────────────────────────

    /**
     * Compute disambiguation indices for tabs with the same file name.
     *
     * AndroidIDE shows `MainActivity.java` and `MainActivity.java (1)` when two
     * files share the same name in different directories. This follows the
     * same pattern: tabs with unique names get `null`, duplicates get 1-indexed.
     */
    fun getDisambiguationMap(): Map<Long, Int?> {
        val nameGroups = _tabs.groupBy { it.fileName }
        return _tabs.associate { tab ->
            val group = nameGroups[tab.fileName]
            val index = if (group != null && group.size > 1) {
                // Find position within the group, ordered by original insertion
                group.indexOfFirst { it.tabId == tab.tabId } + 1
            } else {
                null
            }
            tab.tabId to index
        }
    }

    // ── Tab operations ───────────────────────────────────────────────────

    /**
     * Open a file in a new tab or switch to the existing tab if already open.
     *
     * @param filePath Absolute path to the file.
     * @param content File content to load.
     * @param encoding File encoding (detected or specified).
     * @param activate Whether to switch to this tab (default true).
     * @return The index of the tab (new or existing).
     */
    fun openFile(
        filePath: String,
        content: String = "",
        encoding: String = "UTF-8",
        activate: Boolean = true,
    ): Int {
        // If already open, switch to it
        val existingIndex = indexOfPath(filePath)
        if (existingIndex >= 0) {
            if (activate) switchTo(existingIndex)
            return existingIndex
        }

        // Enforce max tabs
        if (_tabs.size >= maxTabs) {
            // Close the least-recently-used tab (first non-active)
            val toClose = (0 until _tabs.size).firstOrNull { it != _activeTabIndex }
            if (toClose != null) closeTabAt(toClose, force = true)
        }

        // Create new tab
        val tab = EditorTab(
            filePath = filePath,
            tabId = EditorTab.idForPath(filePath),
            content = content,
            isModified = false,
            encoding = encoding,
        )
        _tabs.add(tab)
        val index = _tabs.lastIndex
        if (activate) switchTo(index)
        return index
    }

    /**
     * Open a new untitled tab.
     *
     * @return The index of the new tab.
     */
    fun openUntitled(): Int {
        val tab = EditorTab.untitled(_nextUntitledIndex++)
        _tabs.add(tab)
        val index = _tabs.lastIndex
        switchTo(index)
        return index
    }

    /**
     * Switch to a tab by index.
     * This is analogous to AndroidIDE's `ViewFlipper.setDisplayedChild()`.
     */
    fun switchTo(index: Int) {
        if (index in _tabs.indices && index != _activeTabIndex) {
            _activeTabIndex = index
        }
    }

    /**
     * Switch to a tab by file path.
     * @return true if the tab was found and switched to.
     */
    fun switchToPath(filePath: String): Boolean {
        val index = indexOfPath(filePath)
        if (index >= 0) {
            switchTo(index)
            return true
        }
        return false
    }

    /**
     * Attempt to close a tab by index.
     *
     * Follows AndroidIDE's pattern: if the tab has unsaved changes,
     * return [TabCloseResult.UnsavedChanges] so the caller can show a dialog.
     * Pass `force = true` to close without confirmation.
     */
    fun closeTabAt(index: Int, force: Boolean = false): TabCloseResult {
        if (index !in _tabs.indices) {
            return TabCloseResult.CannotCloseLastTab
        }

        // Don't allow closing the last tab
        if (_tabs.size <= 1) {
            return TabCloseResult.CannotCloseLastTab
        }

        val tab = _tabs[index]

        // Check for unsaved changes
        if (!force && tab.isModified) {
            return TabCloseResult.UnsavedChanges(tab, index)
        }

        // Push to recently closed stack
        _recentlyClosed.push(tab)
        if (_recentlyClosed.size > maxRecentClosed) {
            // Remove oldest
            val deque = LinkedList(_recentlyClosed)
            deque.removeLast()
            _recentlyClosed.clear()
            _recentlyClosed.addAll(deque)
        }

        // Remove the tab
        _tabs.removeAt(index)

        // Adjust active index
        when {
            _activeTabIndex == index -> {
                // Active tab was closed – switch to nearest neighbor
                _activeTabIndex = min(index, _tabs.lastIndex)
            }
            _activeTabIndex > index -> {
                // Active tab was after the closed one – shift left
                _activeTabIndex--
            }
            // else: active tab was before – no change needed
        }

        return TabCloseResult.Closed(tab, index)
    }

    /**
     * Attempt to close a tab by file path.
     */
    fun closeFile(filePath: String, force: Boolean = false): TabCloseResult {
        val index = indexOfPath(filePath)
        return if (index >= 0) closeTabAt(index, force) else TabCloseResult.CannotCloseLastTab
    }

    /**
     * Force-close the tab at the given index after the user confirms discarding changes.
     */
    fun forceCloseAfterConfirm(index: Int): TabCloseResult = closeTabAt(index, force = true)

    /**
     * Re-open the most recently closed tab.
     * Follows AndroidIDE's pattern of restoring closed tabs.
     *
     * @return The index of the re-opened tab, or -1 if nothing to reopen.
     */
    fun reopenLastClosed(): Int {
        if (_recentlyClosed.isEmpty()) return -1
        val tab = _recentlyClosed.pop()
        return openFile(tab.filePath, tab.content, tab.encoding, activate = true)
    }

    // ── Tab state updates ────────────────────────────────────────────────

    /**
     * Update the content of a tab and mark it as modified.
     *
     * @param tabIndex Index of the tab to update.
     * @param newContent The new text content.
     * @param originalContent The original on-disk content (for dirty detection).
     *                        If null, compares against the tab's current `content`.
     */
    fun updateContent(tabIndex: Int, newContent: String, originalContent: String? = null) {
        if (tabIndex !in _tabs.indices) return
        val old = _tabs[tabIndex]
        _tabs[tabIndex] = old.copyContent(newContent, originalContent)
    }

    /**
     * Update the content of the currently active tab.
     */
    fun updateActiveContent(newContent: String, originalContent: String? = null) {
        if (_activeTabIndex >= 0) {
            updateContent(_activeTabIndex, newContent, originalContent)
        }
    }

    /**
     * Mark a tab as saved (clear modified flag, update content to match disk).
     */
    fun markSaved(tabIndex: Int, savedContent: String? = null) {
        if (tabIndex !in _tabs.indices) return
        val old = _tabs[tabIndex]
        _tabs[tabIndex] = old.copy(
            content = savedContent ?: old.content,
            isModified = false,
        )
    }

    /**
     * Mark the active tab as saved.
     */
    fun markActiveSaved(savedContent: String? = null) {
        if (_activeTabIndex >= 0) markSaved(_activeTabIndex, savedContent)
    }

    /**
     * Update cursor/scroll state for a tab (called when switching away from it).
     */
    fun updateCursorState(
        tabIndex: Int,
        line: Int,
        column: Int,
        scrollX: Int = 0,
        scrollY: Int = 0,
        selectionStart: Int = -1,
        selectionEnd: Int = -1,
    ) {
        if (tabIndex !in _tabs.indices) return
        val old = _tabs[tabIndex]
        _tabs[tabIndex] = old.copyCursorState(
            line = line,
            column = column,
            scrollX = scrollX,
            scrollY = scrollY,
            selectionStart = selectionStart,
            selectionEnd = selectionEnd,
        )
    }

    /**
     * Update cursor state for the active tab.
     */
    fun updateActiveCursorState(
        line: Int,
        column: Int,
        scrollX: Int = 0,
        scrollY: Int = 0,
        selectionStart: Int = -1,
        selectionEnd: Int = -1,
    ) {
        if (_activeTabIndex >= 0) {
            updateCursorState(
                _activeTabIndex, line, column,
                scrollX, scrollY, selectionStart, selectionEnd
            )
        }
    }

    /**
     * Update the file path of a tab (e.g. after Save As).
     */
    fun updateTabPath(tabIndex: Int, newPath: String, newEncoding: String? = null) {
        if (tabIndex !in _tabs.indices) return
        val old = _tabs[tabIndex]
        _tabs[tabIndex] = old.copy(
            filePath = newPath,
            tabId = EditorTab.idForPath(newPath),
            encoding = newEncoding ?: old.encoding,
        )
    }

    // ── Bulk operations ──────────────────────────────────────────────────

    /**
     * Close all tabs except the active one.
     * Returns a list of tabs that had unsaved changes (need confirmation).
     */
    fun closeOtherTabs(force: Boolean = false): List<EditorTab> {
        if (_tabs.size <= 1) return emptyList()
        val activeId = activeTab?.tabId ?: return emptyList()
        val unsaved = mutableListOf<EditorTab>()
        var i = 0
        while (i < _tabs.size) {
            if (_tabs[i].tabId != activeId) {
                if (force || !_tabs[i].isModified) {
                    closeTabAt(i, force = true)
                    // Don't increment – list shifted
                } else {
                    unsaved.add(_tabs[i])
                    i++
                }
            } else {
                i++
            }
        }
        return unsaved
    }

    /**
     * Close all tabs. Returns tabs that need save confirmation.
     */
    fun closeAllTabs(force: Boolean = false): List<EditorTab> {
        if (force) {
            val all = _tabs.toList()
            _recentlyClosed.addAll(all)
            _tabs.clear()
            _activeTabIndex = -1
            return emptyList()
        }
        return _tabs.filter { it.isModified }
    }

    /**
     * Get a list of file paths for all open tabs (for session restoration).
     */
    fun getOpenFilePaths(): List<String> = _tabs.map { it.filePath }

    /**
     * Clear all state. Used during cleanup.
     */
    fun clear() {
        _tabs.clear()
        _recentlyClosed.clear()
        _activeTabIndex = -1
    }
}
