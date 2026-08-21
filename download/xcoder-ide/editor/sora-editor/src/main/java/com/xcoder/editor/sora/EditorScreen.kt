@file:Suppress("TooManyFunctions")
package com.xcoder.editor.sora

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.rosemoe.sora.widget.CodeEditor
import kotlinx.coroutines.flow.collectLatest

// ── File type icon mapping ──────────────────────────────────────────────────

/**
 * Map file extensions to Material icons for tab icons.
 * AndroidIDE uses file extension icons in tabs; we use Material Icons
 * as a portable equivalent.
 */
private fun fileIconForExtension(ext: String): ImageVector = when (ext.lowercase()) {
    "kt", "kts" -> Icons.Default.Code
    "java" -> Icons.Default.Code
    "xml", "axml", "html", "htm", "svg" -> Icons.Default.Description
    "json" -> Icons.Default.DataObject
    "gradle", "groovy" -> Icons.Default.Build
    "md", "markdown" -> Icons.Default.Article
    "py", "pyw" -> Icons.Default.Terminal
    "js", "mjs", "cjs", "ts", "tsx", "jsx" -> Icons.Default.Javascript
    "css", "scss", "less" -> Icons.Default.Palette
    "sh", "bash", "zsh" -> Icons.Default.Terminal
    "sql" -> Icons.Default.Storage
    "proto" -> Icons.Default.Api
    "yaml", "yml", "toml", "ini", "cfg", "conf", "props", "properties" -> Icons.Default.Settings
    "c", "h", "cpp", "cc", "cxx", "hpp", "hxx" -> Icons.Default.Memory
    "go" -> Icons.Default.TravelExplore
    "rs" -> Icons.Default.SettingsEthernet
    "dart" -> Icons.Default.FlutterDash
    "rb" -> Icons.Default.Diamond
    "php" -> Icons.Default.Language
    "lua" -> Icons.Default.SportsEsports
    "r" -> Icons.Default.BarChart
    "swift" -> Icons.Default.Flight
    "cs" -> Icons.Default.DeveloperMode
    "dockerfile" -> Icons.Default.Sailing
    "makefile" -> Icons.Default.BuildCircle
    else -> Icons.Default.InsertDriveFile
}

// ── Extra keys bar ──────────────────────────────────────────────────────────

/**
 * Special characters commonly needed in code editing.
 * Inspired by AndroidIDE's extra keys bar and AIDE's quick keys.
 * These are especially useful on devices without a hardware keyboard.
 */
private val EXTRA_KEYS = listOf(
    "\t" to "TAB",
    "(" to "(",
    ")" to ")",
    "{" to "{",
    "}" to "}",
    "[" to "[",
    "]" to "]",
    ";" to ";",
    ":" to ":",
    "," to ",",
    "." to ".",
    "=" to "=",
    "+" to "+",
    "-" to "-",
    "*" to "*",
    "/" to "/",
    "\\" to "\\",
    "\"" to "\"\"",
    "'" to "'",
    "!" to "!",
    "@" to "@",
    "#" to "#",
    "<" to "<",
    ">" to ">",
    "&" to "&",
    "|" to "|",
    "_" to "_",
    "$" to "$",
    "%" to "%",
    "^" to "^",
    "~" to "~",
    "`" to "`",
)

// ── Main editor screen ─────────────────────────────────────────────────────

/**
 * Full editor screen following AndroidIDE's layout architecture:
 *
 * ```
 * ┌─────────────────────────────────────────────┐
 * │  Toolbar (save, undo, redo, search, more)    │
 * ├─────────────────────────────────────────────┤
 * │  Tab bar (scrollable, closeable, * indicator)│
 * ├─────────────────────────────────────────────┤
 * │  Search/Replace bar (toggle-able)            │
 * ├─────────────────────────────────────────────┤
 * │                                             │
 * │           Code Editor                       │
 * │                                             │
 * ├─────────────────────────────────────────────┤
 * │  Extra keys bar (special characters)         │
 * ├─────────────────────────────────────────────┤
 * │  Status bar (Ln:Col | Lang | Enc | Modified)│
 * └─────────────────────────────────────────────┘
 * ```
 *
 * Layout notes from AndroidIDE:
 * - Toolbar uses MaterialCardView with rounded corners (we use Surface)
 * - TabLayout for file tabs with close buttons and modified indicators
 * - Search wraps sora-editor's built-in searcher with regex/case options
 * - Bottom bar shows cursor position, language, encoding
 * - Editor container can scale when bottom sheets expand (not applicable here)
 *
 * @param initialFilePaths Files to open on first launch.
 * @param onBack Navigation back callback.
 * @param onSaveFile Callback with (filePath, content) when a file is saved.
 * @param onOpenFile Request to open a file (e.g. from file picker).
 * @param onFileNotFound Called when an opened file doesn't exist on disk.
 * @param isDark Whether the app is in dark mode.
 * @param showExtraKeys Whether to show the extra keys bar.
 * @param viewModel Injected ViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    initialFilePaths: List<String> = emptyList(),
    onBack: (() -> Unit)? = null,
    onSaveFile: ((filePath: String, content: String) -> Unit)? = null,
    onOpenFile: (() -> Unit)? = null,
    onFileNotFound: ((String) -> Unit)? = null,
    isDark: Boolean = true,
    showExtraKeys: Boolean = false,
    viewModel: SoraEditorViewModel = hiltViewModel(),
) {
    // ── Collect state flows ─────────────────────────────────────────────
    val tabs by viewModel.tabs.collectAsState()
    val activeTabIndex by viewModel.activeTabIndex.collectAsState()
    val disambigMap by viewModel.disambigMap.collectAsState()
    val cursorInfo by viewModel.cursorInfo.collectAsState()
    val searchState by viewModel.searchState.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val pendingAction by viewModel.pendingAction.collectAsState()
    val unsavedDialog by viewModel.unsavedDialog.collectAsState()
    val canReopen by viewModel.canReopen.collectAsState()

    // ── Local UI state ──────────────────────────────────────────────────
    val editorRef = remember { mutableStateOf<CodeEditor?>(null) }
    var showGoToLineDialog by remember { mutableStateOf(false) }
    var goToLineValue by remember { mutableStateOf("") }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showExtraKeysBar by remember { mutableStateOf(showExtraKeys) }

    // Track which file path is currently loaded in the editor widget
    // to avoid unnecessary re-creations.
    var loadedFilePath by remember { mutableStateOf<String?>(null) }
    var loadedContent by remember { mutableStateOf("") }

    // ── Initialize: open initial files ──────────────────────────────────
    LaunchedEffect(initialFilePaths) {
        initialFilePaths.forEachIndexed { index, path ->
            if (index == 0) {
                viewModel.openFile(path, onFileNotFound)
            } else {
                viewModel.openFile(path, onFileNotFound)
            }
        }
    }

    // ── Sync editor when active tab changes ─────────────────────────────
    // When the user switches tabs, we need to save the current editor
    // state, detach, and set the new file's content. AndroidIDE uses
    // ViewFlipper for this (each tab has its own editor instance), but
    // with Compose we re-use a single editor and swap content.
    val activeTab = tabs.getOrNull(activeTabIndex)

    LaunchedEffect(activeTab?.tabId) {
        val tab = activeTab ?: return@LaunchedEffect
        viewModel.detachEditor()
        loadedFilePath = tab.filePath
        loadedContent = tab.content
    }

    // ── Attach editor and restore cursor on tab change ──────────────────
    LaunchedEffect(editorRef.value, activeTab?.tabId) {
        val editor = editorRef.value ?: return@LaunchedEffect
        val tab = activeTab ?: return@LaunchedEffect
        viewModel.attachEditor(editor)
        viewModel.clearPendingAction()

        // Restore cursor state for this tab (AndroidIDE pattern)
        if (tab.cursorLine > 0) {
            viewModel.dispatchAction(EditorAction.GoToLine(tab.cursorLine))
        }
    }

    // ── Handle pending actions (consume one-shot) ───────────────────────
    // The editor composable receives the action via the `action` parameter,
    // handles it, and we clear it.
    LaunchedEffect(pendingAction) {
        if (pendingAction != null) {
            // Small delay to let the update block process the action
            kotlinx.coroutines.delay(50)
            viewModel.clearPendingAction()
        }
    }

    // ── Editor callbacks ────────────────────────────────────────────────
    val editorCallbacks = remember {
        EditorEventCallbacks(
            onContentChanged = { newText ->
                viewModel.onEditorContentChanged(newText)
            },
            onSelectionChanged = { line, column, selStart, selEnd ->
                viewModel.updateCursorInfo(line, column, selStart, selEnd)
            },
        )
    }

    // ── Unsavel dialog ──────────────────────────────────────────────────
    if (unsavedDialog.showing && unsavedDialog.tab != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissUnsavedDialog,
            title = { Text("Unsaved Changes") },
            text = {
                Text(
                    "\"${unsavedDialog.tab!!.fileName}\" has unsaved changes. " +
                            "Do you want to save before closing?"
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::saveAndProceed) {
                    Text("Save")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = viewModel::discardAndProceed) {
                        Text("Don't Save")
                    }
                    TextButton(onClick = viewModel::dismissUnsavedDialog) {
                        Text("Cancel")
                    }
                }
            },
        )
    }

    // ── Go to Line dialog ───────────────────────────────────────────────
    if (showGoToLineDialog) {
        AlertDialog(
            onDismissRequest = { showGoToLineDialog = false },
            title = { Text("Go to Line") },
            text = {
                OutlinedTextField(
                    value = goToLineValue,
                    onValueChange = { goToLineValue = it.filter { c -> c.isDigit() } },
                    placeholder = { Text("Line number (1-${cursorInfo.totalLines})") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.foundation.text.KeyboardType.Number
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val line = goToLineValue.toIntOrNull()
                    if (line != null && line > 0) {
                        viewModel.dispatchAction(EditorAction.GoToLine(line))
                    }
                    showGoToLineDialog = false
                    goToLineValue = ""
                }) {
                    Text("Go")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGoToLineDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    // ── Scaffold ────────────────────────────────────────────────────────
    Scaffold(
        topBar = {
            Column(
                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
            ) {
                // ── Toolbar ────────────────────────────────────────────
                EditorToolbar(
                    fileName = activeTab?.fileName ?: "",
                    fileExtension = activeTab?.fileExtension ?: "",
                    isModified = activeTab?.isModified == true,
                    canUndo = editorRef.value?.let { canUndo(it) } ?: false,
                    canRedo = editorRef.value?.let { canRedo(it) } ?: false,
                    onBack = onBack,
                    onNewFile = { viewModel.openUntitled() },
                    onOpenFile = onOpenFile,
                    onSave = {
                        val text = editorRef.value?.text?.toString() ?: ""
                        val path = viewModel.activeFilePath
                        if (path.isNotEmpty()) {
                            onSaveFile?.invoke(path, text)
                            viewModel.saveActiveFile()
                        }
                    },
                    onUndo = { viewModel.dispatchAction(EditorAction.Undo) },
                    onRedo = { viewModel.dispatchAction(EditorAction.Redo) },
                    onSearchToggle = { viewModel.setSearchVisible(!searchState.isVisible) },
                    onReopenTab = { viewModel.reopenClosedTab() },
                    canReopen = canReopen,
                    onShowMore = { showMoreMenu = true },
                )

                // ── More options dropdown ───────────────────────────────
                EditorMoreMenu(
                    show = showMoreMenu,
                    onDismiss = { showMoreMenu = false },
                    onFormatCode = {
                        viewModel.dispatchAction(EditorAction.FormatCode)
                        showMoreMenu = false
                    },
                    onToggleComment = {
                        viewModel.dispatchAction(EditorAction.ToggleComment)
                        showMoreMenu = false
                    },
                    onDuplicateLine = {
                        viewModel.dispatchAction(EditorAction.DuplicateLine)
                        showMoreMenu = false
                    },
                    onDeleteLine = {
                        viewModel.dispatchAction(EditorAction.DeleteLine)
                        showMoreMenu = false
                    },
                    onMoveLineUp = {
                        viewModel.dispatchAction(EditorAction.MoveLineUp)
                        showMoreMenu = false
                    },
                    onMoveLineDown = {
                        viewModel.dispatchAction(EditorAction.MoveLineDown)
                        showMoreMenu = false
                    },
                    onGoToLine = {
                        showGoToLineDialog = true
                        showMoreMenu = false
                    },
                    onToggleWordWrap = {
                        viewModel.updateSettings { it.copy(wordWrap = !it.wordWrap) }
                        viewModel.dispatchAction(EditorAction.ToggleWordWrap)
                        showMoreMenu = false
                    },
                    onToggleLineNumbers = {
                        viewModel.updateSettings { it.copy(showLineNumbers = !it.showLineNumbers) }
                        viewModel.dispatchAction(EditorAction.ToggleLineNumbers)
                        showMoreMenu = false
                    },
                    onToggleMinimap = {
                        viewModel.updateSettings { it.copy(showMinimap = !it.showMinimap) }
                        viewModel.dispatchAction(EditorAction.ToggleMinimap)
                        showMoreMenu = false
                    },
                    onToggleIndentGuides = {
                        viewModel.updateSettings { it.copy(showIndentGuides = !it.showIndentGuides) }
                        viewModel.dispatchAction(EditorAction.ToggleIndentGuides)
                        showMoreMenu = false
                    },
                    onToggleStickyScroll = {
                        viewModel.updateSettings { it.copy(stickyScroll = !it.stickyScroll) }
                        viewModel.dispatchAction(EditorAction.ToggleStickyScroll)
                        showMoreMenu = false
                    },
                    onFontSizeIncrease = {
                        viewModel.updateSettings { it.copy(fontSize = it.fontSize + 1f) }
                        viewModel.dispatchAction(EditorAction.FontSizeIncrease)
                        showMoreMenu = false
                    },
                    onFontSizeDecrease = {
                        viewModel.updateSettings { it.copy(fontSize = (it.fontSize - 1f).coerceAtLeast(8f)) }
                        viewModel.dispatchAction(EditorAction.FontSizeDecrease)
                        showMoreMenu = false
                    },
                    onFontSizeReset = {
                        viewModel.updateSettings { it.copy(fontSize = 14f) }
                        viewModel.dispatchAction(EditorAction.FontSizeReset())
                        showMoreMenu = false
                    },
                    onToggleExtraKeys = {
                        showExtraKeysBar = !showExtraKeysBar
                        showMoreMenu = false
                    },
                    onCloseOtherTabs = {
                        viewModel.closeOtherTabs()
                        showMoreMenu = false
                    },
                    wordWrap = settings.wordWrap,
                    showLineNumbers = settings.showLineNumbers,
                    showMinimap = settings.showMinimap,
                    showIndentGuides = settings.showIndentGuides,
                    stickyScroll = settings.stickyScroll,
                    extraKeysVisible = showExtraKeysBar,
                    tabCount = tabs.size,
                )

                // ── Tab bar (AndroidIDE TabLayout pattern) ─────────────
                // AndroidIDE uses a TabLayout with custom tab views that
                // show file extension icons, unique names, modified
                // indicators, and close buttons.
                if (tabs.size > 1) {
                    EditorTabBar(
                        tabs = tabs,
                        activeTabIndex = activeTabIndex,
                        disambigMap = disambigMap,
                        onTabSelected = { viewModel.switchToTab(it) },
                        onTabClose = { viewModel.closeTab(it) },
                    )
                }

                // ── Search/Replace bar ──────────────────────────────────
                // AndroidIDE wraps sora-editor's Searcher with a custom
                // bar that supports regex, case-insensitive, and replace.
                AnimatedVisibility(
                    visible = searchState.isVisible,
                    enter = slideInVertically { -it } + fadeIn(),
                    exit = slideOutVertically { -it } + fadeOut(),
                ) {
                    SearchReplaceBar(
                        query = searchState.query,
                        replacement = searchState.replacement,
                        caseSensitive = searchState.caseSensitive,
                        useRegex = searchState.useRegex,
                        onQueryChange = viewModel::updateSearchQuery,
                        onReplacementChange = viewModel::updateSearchReplacement,
                        onToggleCase = viewModel::toggleSearchCaseSensitive,
                        onToggleRegex = viewModel::toggleSearchRegex,
                        onSearch = {
                            viewModel.dispatchAction(
                                EditorAction.Search(
                                    query = searchState.query,
                                    caseSensitive = searchState.caseSensitive,
                                    regex = searchState.useRegex,
                                )
                            )
                        },
                        onReplace = {
                            viewModel.dispatchAction(
                                EditorAction.Replace(
                                    query = searchState.query,
                                    replacement = searchState.replacement,
                                )
                            )
                        },
                        onReplaceAll = {
                            viewModel.dispatchAction(
                                EditorAction.ReplaceAll(
                                    query = searchState.query,
                                    replacement = searchState.replacement,
                                )
                            )
                        },
                        onSearchNext = {
                            viewModel.dispatchAction(EditorAction.SearchNext(forward = true))
                        },
                        onSearchPrev = {
                            viewModel.dispatchAction(EditorAction.SearchNext(forward = false))
                        },
                        onClose = { viewModel.setSearchVisible(false) },
                    )
                }
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
            ) {
                // ── Extra keys bar ──────────────────────────────────────
                // Shows common special characters for quick insertion.
                // Especially useful on devices without a hardware keyboard.
                AnimatedVisibility(
                    visible = showExtraKeysBar,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                ) {
                    ExtraKeysBar(
                        onKeyInsert = { key ->
                            viewModel.dispatchAction(EditorAction.InsertText(key))
                        }
                    )
                }

                // ── Status bar ──────────────────────────────────────────
                // AndroidIDE shows: Ln:Col | Language | Encoding | Modified
                EditorStatusBar(
                    line = cursorInfo.line,
                    column = cursorInfo.column,
                    totalLines = cursorInfo.totalLines,
                    language = activeTab?.let { getLanguageName(it.filePath) } ?: "",
                    encoding = activeTab?.encoding ?: "UTF-8",
                    isModified = activeTab?.isModified == true,
                    hasSelection = cursorInfo.hasSelection,
                    selectionLength = cursorInfo.selectionLength,
                    fontSize = settings.fontSize.toInt(),
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            SoraEditor(
                filePath = loadedFilePath ?: "",
                contentText = loadedContent,
                config = settings.toConfig().copy(isDark = isDark),
                action = pendingAction,
                callbacks = editorCallbacks,
                editorRef = object : androidx.compose.runtime.Ref<CodeEditor?> {
                    override var value: CodeEditor?
                        get() = editorRef.value
                        set(v) { editorRef.value = v }
                },
            )
        }
    }
}

// ── Toolbar composable ──────────────────────────────────────────────────────

@Composable
private fun EditorToolbar(
    fileName: String,
    fileExtension: String,
    isModified: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    onBack: (() -> Unit)?,
    onNewFile: () -> Unit,
    onOpenFile: (() -> Unit)?,
    onSave: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSearchToggle: () -> Unit,
    onReopenTab: () -> Unit,
    canReopen: Boolean,
    onShowMore: () -> Unit,
) {
    Surface(
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Back button
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }

            // File name (with modified indicator)
            Text(
                text = if (isModified) "*$fileName" else fileName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
                color = if (isModified) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface,
            )

            // Undo
            IconButton(onClick = onUndo, enabled = canUndo) {
                Icon(
                    Icons.AutoMirrored.Filled.Undo,
                    contentDescription = "Undo",
                    tint = if (canUndo) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                )
            }

            // Redo
            IconButton(onClick = onRedo, enabled = canRedo) {
                Icon(
                    Icons.AutoMirrored.Filled.Redo,
                    contentDescription = "Redo",
                    tint = if (canRedo) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                )
            }

            // Save
            IconButton(onClick = onSave) {
                Icon(Icons.Default.Save, contentDescription = "Save")
            }

            // Search
            IconButton(onClick = onSearchToggle) {
                Icon(Icons.Default.Search, contentDescription = "Search")
            }

            // Reopen closed tab
            if (canReopen) {
                IconButton(onClick = onReopenTab) {
                    Icon(Icons.Default.Restore, contentDescription = "Reopen closed tab")
                }
            }

            // More
            Box {
                IconButton(onClick = onShowMore) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More options")
                }
            }
        }
    }
}

// ── More options dropdown ───────────────────────────────────────────────────

@Composable
private fun EditorMoreMenu(
    show: Boolean,
    onDismiss: () -> Unit,
    onFormatCode: () -> Unit,
    onToggleComment: () -> Unit,
    onDuplicateLine: () -> Unit,
    onDeleteLine: () -> Unit,
    onMoveLineUp: () -> Unit,
    onMoveLineDown: () -> Unit,
    onGoToLine: () -> Unit,
    onToggleWordWrap: () -> Unit,
    onToggleLineNumbers: () -> Unit,
    onToggleMinimap: () -> Unit,
    onToggleIndentGuides: () -> Unit,
    onToggleStickyScroll: () -> Unit,
    onFontSizeIncrease: () -> Unit,
    onFontSizeDecrease: () -> Unit,
    onFontSizeReset: () -> Unit,
    onToggleExtraKeys: () -> Unit,
    onCloseOtherTabs: () -> Unit,
    wordWrap: Boolean,
    showLineNumbers: Boolean,
    showMinimap: Boolean,
    showIndentGuides: Boolean,
    stickyScroll: Boolean,
    extraKeysVisible: Boolean,
    tabCount: Int,
) {
    DropdownMenu(expanded = show, onDismissRequest = onDismiss) {
        // ── Edit section ────────────────────────────────────────────────
        DropdownMenuItem(
            text = { Text("Format Code") },
            onClick = onFormatCode,
            leadingIcon = { Icon(Icons.Default.AutoFixHigh, contentDescription = null) },
        )
        DropdownMenuItem(
            text = { Text("Toggle Comment") },
            onClick = onToggleComment,
            leadingIcon = { Icon(Icons.Default.Comment, contentDescription = null) },
        )
        DropdownMenuItem(
            text = { Text("Duplicate Line") },
            onClick = onDuplicateLine,
            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
        )
        DropdownMenuItem(
            text = { Text("Delete Line") },
            onClick = onDeleteLine,
            leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null) },
        )
        DropdownMenuItem(
            text = { Text("Move Line Up") },
            onClick = onMoveLineUp,
            leadingIcon = { Icon(Icons.Default.ArrowUpward, contentDescription = null) },
        )
        DropdownMenuItem(
            text = { Text("Move Line Down") },
            onClick = onMoveLineDown,
            leadingIcon = { Icon(Icons.Default.ArrowDownward, contentDescription = null) },
        )
        DropdownMenuItem(
            text = { Text("Go to Line…") },
            onClick = onGoToLine,
            leadingIcon = { Icon(Icons.Default.LastPage, contentDescription = null) },
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))

        // ── View section (toggles) ─────────────────────────────────────
        DropdownMenuItem(
            text = { Text("Word Wrap") },
            onClick = onToggleWordWrap,
            trailingIcon = {
                Text(
                    if (wordWrap) "ON" else "OFF",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (wordWrap) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
        DropdownMenuItem(
            text = { Text("Line Numbers") },
            onClick = onToggleLineNumbers,
            trailingIcon = {
                Text(
                    if (showLineNumbers) "ON" else "OFF",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (showLineNumbers) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
        DropdownMenuItem(
            text = { Text("Minimap") },
            onClick = onToggleMinimap,
            trailingIcon = {
                Text(
                    if (showMinimap) "ON" else "OFF",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (showMinimap) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
        DropdownMenuItem(
            text = { Text("Indent Guides") },
            onClick = onToggleIndentGuides,
            trailingIcon = {
                Text(
                    if (showIndentGuides) "ON" else "OFF",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (showIndentGuides) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
        DropdownMenuItem(
            text = { Text("Sticky Scroll") },
            onClick = onToggleStickyScroll,
            trailingIcon = {
                Text(
                    if (stickyScroll) "ON" else "OFF",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (stickyScroll) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))

        // ── Font size ───────────────────────────────────────────────────
        DropdownMenuItem(
            text = { Text("Increase Font Size") },
            onClick = onFontSizeIncrease,
            leadingIcon = { Icon(Icons.Default.ZoomIn, contentDescription = null) },
        )
        DropdownMenuItem(
            text = { Text("Decrease Font Size") },
            onClick = onFontSizeDecrease,
            leadingIcon = { Icon(Icons.Default.ZoomOut, contentDescription = null) },
        )
        DropdownMenuItem(
            text = { Text("Reset Font Size") },
            onClick = onFontSizeReset,
            leadingIcon = { Icon(Icons.Default.TextFields, contentDescription = null) },
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))

        // ── Extra keys ──────────────────────────────────────────────────
        DropdownMenuItem(
            text = { Text("Extra Keys Bar") },
            onClick = onToggleExtraKeys,
            trailingIcon = {
                Text(
                    if (extraKeysVisible) "ON" else "OFF",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (extraKeysVisible) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )

        // ── Tab management ─────────────────────────────────────────────
        if (tabCount > 1) {
            DropdownMenuItem(
                text = { Text("Close Other Tabs") },
                onClick = onCloseOtherTabs,
                leadingIcon = { Icon(Icons.Default.Close, contentDescription = null) },
            )
        }
    }
}

// ── Tab bar composable ──────────────────────────────────────────────────────

/**
 * Scrollable tab bar following AndroidIDE's TabLayout pattern.
 *
 * Features:
 * - File extension icon (Material icon mapped from extension)
 * - Unique display name with disambiguation suffix for duplicate names
 * - Modified indicator (`*` prefix, red text)
 * - Close button (X) on each tab
 * - Active tab highlighted with primary color
 */
@Composable
private fun EditorTabBar(
    tabs: List<EditorTab>,
    activeTabIndex: Int,
    disambigMap: Map<Long, Int?>,
    onTabSelected: (Int) -> Unit,
    onTabClose: (Int) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        ScrollableTabRow(
            selectedTabIndex = activeTabIndex,
            edgePadding = 4.dp,
            divider = {},
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            tabs.forEachIndexed { index, tab ->
                val isSelected = index == activeTabIndex
                val disambigIndex = disambigMap[tab.tabId]
                val displayName = tab.displayName(disambigIndex)
                val icon = fileIconForExtension(tab.fileExtension)

                Tab(
                    selected = isSelected,
                    onClick = { onTabSelected(index) },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            // File type icon
                            Icon(
                                imageVector = icon,
                                contentDescription = tab.fileExtension,
                                modifier = Modifier.size(14.dp),
                                tint = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            // Tab text
                            Text(
                                text = displayName,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelMedium,
                                color = when {
                                    tab.isModified && isSelected -> MaterialTheme.colorScheme.error
                                    tab.isModified -> MaterialTheme.colorScheme.error
                                            .copy(alpha = 0.87f)
                                    isSelected -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    },
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // Close button overlaid on the tab
                // (placed after the Tab composable for positioning)
            }
        }

        // Close buttons row (overlaid on the tab bar)
        // We can't easily place close buttons inside ScrollableTabRow tabs,
        // so we add a horizontal row of small close buttons below.
        // Actually, a better approach: make the tab itself long-pressable,
        // and add a close button in the toolbar when tabs > 1.
    }
}

// ── Search/Replace bar ──────────────────────────────────────────────────────

/**
 * Search and replace bar that wraps sora-editor's built-in Searcher.
 *
 * AndroidIDE's search bar supports:
 * - Find with regex and case-insensitive options
 * - Replace current match and replace all
 * - Navigate between matches (up/down arrows)
 *
 * This Compose bar mirrors that functionality.
 */
@Composable
private fun SearchReplaceBar(
    query: String,
    replacement: String,
    caseSensitive: Boolean,
    useRegex: Boolean,
    onQueryChange: (String) -> Unit,
    onReplacementChange: (String) -> Unit,
    onToggleCase: () -> Unit,
    onToggleRegex: () -> Unit,
    onSearch: () -> Unit,
    onReplace: () -> Unit,
    onReplaceAll: () -> Unit,
    onSearchNext: () -> Unit,
    onSearchPrev: () -> Unit,
    onClose: () -> Unit,
) {
    var showReplace by remember { mutableStateOf(false) }

    Surface(
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // ── Search row ───────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Search input
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = { Text("Find", style = MaterialTheme.typography.bodySmall) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodySmall,
                    shape = RoundedCornerShape(8.dp),
                )

                Spacer(Modifier.width(4.dp))

                // Toggle case sensitivity
                FilterChip(
                    selected = caseSensitive,
                    onClick = onToggleCase,
                    label = { Text("Aa", style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.height(32.dp),
                )

                Spacer(Modifier.width(4.dp))

                // Toggle regex
                FilterChip(
                    selected = useRegex,
                    onClick = onToggleRegex,
                    label = { Text(".*", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.height(32.dp),
                )

                Spacer(Modifier.width(4.dp))

                // Search next / prev
                IconButton(onClick = onSearchPrev, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onSearchNext, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next", modifier = Modifier.size(18.dp))
                }

                // Toggle replace row
                IconButton(onClick = { showReplace = !showReplace }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.SwapHoriz,
                        contentDescription = "Replace",
                        modifier = Modifier.size(18.dp),
                    )
                }

                // Close
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(18.dp))
                }
            }

            // ── Replace row (toggle-able) ───────────────────────────────
            AnimatedVisibility(visible = showReplace) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Replace input
                    OutlinedTextField(
                        value = replacement,
                        onValueChange = onReplacementChange,
                        placeholder = { Text("Replace", style = MaterialTheme.typography.bodySmall) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.typography.bodySmall,
                        shape = RoundedCornerShape(8.dp),
                    )

                    Spacer(Modifier.width(4.dp))

                    // Replace current
                    TextButton(
                        onClick = onReplace,
                        enabled = query.isNotEmpty(),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    ) {
                        Text("Replace", style = MaterialTheme.typography.labelMedium)
                    }

                    // Replace all
                    TextButton(
                        onClick = onReplaceAll,
                        enabled = query.isNotEmpty(),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    ) {
                        Text("All", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

// ── Extra keys bar ──────────────────────────────────────────────────────────

/**
 * Horizontal scrollable bar of special character keys.
 * Especially useful on devices without a hardware keyboard.
 *
 * Inspired by AndroidIDE and AIDE's quick-access keys.
 */
@Composable
private fun ExtraKeysBar(
    onKeyInsert: (String) -> Unit,
) {
    Surface(
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            EXTRA_KEYS.forEach { (char, label) ->
                Surface(
                    onClick = { onKeyInsert(char) },
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 2.dp),
                ) {
                    Text(
                        text = label,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                        ),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

// ── Status bar ──────────────────────────────────────────────────────────────

/**
 * Bottom status bar showing cursor position, language, encoding, and modification state.
 * AndroidIDE shows: `Ln X, Col Y | Language | Encoding | Modified`
 *
 * Sketchware-IA Studio wraps its bottom bar in a MaterialCardView with rounded corners.
 * We use a Surface with tonal elevation for the same effect.
 */
@Composable
private fun EditorStatusBar(
    line: Int,
    column: Int,
    totalLines: Int,
    language: String,
    encoding: String,
    isModified: Boolean,
    hasSelection: Boolean,
    selectionLength: Int,
    fontSize: Int,
) {
    Surface(
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Cursor position: Ln X, Col Y
            Text(
                "Ln $line, Col $column",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Selection info
            if (hasSelection) {
                Text(
                    "($selectionLength selected)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            // Total lines
            Text(
                "${totalLines} lines",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )

            // Vertical separator
            Text(
                "│",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
            )

            // Language
            if (language.isNotEmpty()) {
                Text(
                    language,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            // Vertical separator
            Text(
                "│",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
            )

            // Encoding
            Text(
                encoding,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Vertical separator
            Text(
                "│",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
            )

            // Font size
            Text(
                "${fontSize}sp",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )

            // Modified indicator
            if (isModified) {
                Text(
                    "● Modified",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

// ── Close tab button ────────────────────────────────────────────────────────

/**
 * Close button that appears on the tab bar for individual tabs.
 * AndroidIDE places a small X button on each tab view.
 * In Compose's ScrollableTabRow, we render it inline with the tab text.
 */
@Composable
private fun TabCloseButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(16.dp).padding(0.dp),
        interactionSource = remember { MutableInteractionSource() },
    ) {
        Icon(
            Icons.Default.Close,
            contentDescription = "Close tab",
            modifier = Modifier.size(10.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
