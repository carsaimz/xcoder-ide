package com.xcoder.editor.web

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class BottomPanel { OUTPUT, PROBLEMS, TERMINAL }

data class BreadcrumbSegment(val label: String, val path: String)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun EnhancedWebEditorScreen(
    filePath: String,
    fileContent: String,
    language: String,
    openFiles: List<EditorFile>,
    currentFileIndex: Int,
    cursorRow: Int,
    cursorCol: Int,
    encoding: String = "UTF-8",
    indentSize: Int = 4,
    useTabs: Boolean = false,
    onContentChanged: (String) -> Unit,
    onSave: () -> Unit,
    onOpenFile: (String) -> Unit,
    onCloseFile: (Int) -> Unit,
    onSwitchTab: (Int) -> Unit,
    onCommand: (EditorCommand) -> Unit,
    onGotoLine: (String) -> Unit,
    onFileDropped: (String) -> Unit,
    modifier: Modifier = Modifier,
    bridge: EditorBridge? = null
) {
    var showSearchPanel by remember { mutableStateOf(false) }
    var showCommandPalette by remember { mutableStateOf(false) }
    var showMinimap by remember { mutableStateOf(true) }
    var wordWrap by remember { mutableStateOf(false) }
    var activeBottomPanel by remember { mutableStateOf<BottomPanel?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var replaceText by remember { mutableStateOf("") }
    var searchOptions by remember { mutableStateOf(SearchOptions()) }
    var searchResult by remember { mutableStateOf(SearchResult(emptyList(), "")) }
    var selectedMatchIndex by remember { mutableIntStateOf(-1) }
    val splitState = remember { SplitEditorState() }

    val breadcrumbs = remember(filePath) {
        val parts = filePath.split("/").filter { it.isNotEmpty() }
        parts.mapIndexed { index, part ->
            BreadcrumbSegment(part, parts.take(index + 1).joinToString("/"))
        }
    }

    val wordCount = remember(fileContent) {
        fileContent.split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .size
    }
    val fileSize = remember(fileContent) {
        fileContent.toByteArray(Charsets.UTF_8).size
    }
    val lineCount = remember(fileContent) {
        fileContent.count { it == '\n' } + 1
    }

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        EditorToolbar(
            showMinimap = showMinimap,
            wordWrap = wordWrap,
            showSearchPanel = showSearchPanel,
            onToggleMinimap = { showMinimap = !showMinimap },
            onToggleWordWrap = { wordWrap = !wordWrap },
            onSave = onSave,
            onUndo = { bridge?.sendCommand("editor.undo()") },
            onRedo = { bridge?.sendCommand("editor.redo()") },
            onSearch = { showSearchPanel = !showSearchPanel },
            onCommandPalette = { showCommandPalette = true },
            onSplit = { splitState.toggleHorizontalSplit() },
            onFormat = { bridge?.sendCommand("editor.commands.exec('format')") },
            onSettings = { onCommand(EditorCommand("file.settings", "Settings", "Ctrl+,", CommandCategory.FILE, Icons.Default.Settings)) }
        )

        if (breadcrumbs.isNotEmpty()) {
            BreadcrumbBar(
                segments = breadcrumbs,
                onSegmentClick = { onGotoLine(it.path) }
            )
        }

        EditorTabsRow(
            files = openFiles,
            currentIndex = currentFileIndex,
            onTabClick = onSwitchTab,
            onCloseTab = onCloseFile
        )

        SearchPanel(
            visible = showSearchPanel,
            searchQuery = searchQuery,
            replaceText = replaceText,
            options = searchOptions,
            searchResult = searchResult,
            selectedMatchIndex = selectedMatchIndex,
            onSearchQueryChange = {
                searchQuery = it
                searchResult = SearchEngine.search(fileContent, it, searchOptions)
                selectedMatchIndex = if (searchResult.hasMatches) 0 else -1
            },
            onReplaceTextChange = { replaceText = it },
            onOptionsChange = { opts ->
                searchOptions = opts
                searchResult = SearchEngine.search(fileContent, searchQuery, opts)
                selectedMatchIndex = if (searchResult.hasMatches) 0 else -1
            },
            onSearch = {
                searchResult = SearchEngine.search(fileContent, searchQuery, searchOptions)
                selectedMatchIndex = if (searchResult.hasMatches) 0 else -1
            },
            onNext = {
                if (searchResult.hasMatches) {
                    selectedMatchIndex = (selectedMatchIndex + 1) % searchResult.matchCount
                }
            },
            onPrevious = {
                if (searchResult.hasMatches) {
                    selectedMatchIndex = (selectedMatchIndex - 1 + searchResult.matchCount) % searchResult.matchCount
                }
            },
            onReplace = {
                val idx = selectedMatchIndex
                if (idx in searchResult.matches.indices) {
                    onContentChanged(SearchEngine.replaceOne(fileContent, searchResult.matches[idx], replaceText))
                    searchResult = SearchEngine.search(fileContent, searchQuery, searchOptions)
                }
            },
            onReplaceAll = {
                onContentChanged(SearchEngine.replaceAll(fileContent, searchQuery, replaceText, searchOptions))
                searchResult = SearchResult(emptyList(), searchQuery)
            },
            onMatchClick = { selectedMatchIndex = it },
            onDismiss = { showSearchPanel = false }
        )

        Box(modifier = Modifier.weight(1f)) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webViewClient = WebViewClient()
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (showCommandPalette) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)).clickable { showCommandPalette = false }) {
                    CommandPalette(
                        visible = true,
                        onCommandSelected = { cmd ->
                            onCommand(cmd)
                            showCommandPalette = false
                        },
                        onDismiss = { showCommandPalette = false }
                    )
                }
            }
        }

        if (activeBottomPanel != null) {
            BottomPanelArea(
                activePanel = activeBottomPanel!!,
                onSwitchPanel = { panel -> activeBottomPanel = if (activeBottomPanel == panel) null else panel },
                onClose = { activeBottomPanel = null }
            )
        } else {
            BottomPanelToolbar(
                activePanel = activeBottomPanel,
                onSwitchPanel = { panel -> activeBottomPanel = panel }
            )
        }

        EditorStatusBar(
            language = language,
            encoding = encoding,
            line = cursorRow,
            col = cursorCol,
            indentSize = indentSize,
            useTabs = useTabs,
            wordCount = wordCount,
            fileSize = fileSize,
            lineCount = lineCount,
            onLanguageClick = {},
            onEncodingClick = {},
            onIndentClick = {}
        )
    }
}

@Composable
private fun EditorToolbar(
    showMinimap: Boolean,
    wordWrap: Boolean,
    showSearchPanel: Boolean,
    onToggleMinimap: () -> Unit,
    onToggleWordWrap: () -> Unit,
    onSave: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSearch: () -> Unit,
    onCommandPalette: () -> Unit,
    onSplit: () -> Unit,
    onFormat: () -> Unit,
    onSettings: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            ToolbarButton(Icons.Default.Save, "Save", onSave)
            ToolbarButton(Icons.Default.Undo, "Undo", onUndo)
            ToolbarButton(Icons.Default.Redo, "Redo", onRedo)
            ToolbarButton(Icons.Default.Search, "Search", onSearch, tint = if (showSearchPanel) MaterialTheme.colorScheme.primary else null)
            ToolbarButton(Icons.Default.FindReplace, "Replace", onSearch)
            ToolbarButton(Icons.Default.Terminal, "Command Palette", onCommandPalette)
            ToolbarButton(Icons.Default.ViewColumn, "Split", onSplit)
            ToolbarButton(Icons.Default.Map, "Minimap", onToggleMinimap, tint = if (showMinimap) MaterialTheme.colorScheme.primary else null)
            ToolbarButton(Icons.Default.WrapText, "Word Wrap", onToggleWordWrap, tint = if (wordWrap) MaterialTheme.colorScheme.primary else null)
            ToolbarButton(Icons.Default.AutoFixHigh, "Format", onFormat)
            ToolbarButton(Icons.Default.Settings, "Settings", onSettings)
        }
    }
}

@Composable
private fun ToolbarButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, tint: Color? = null) {
    IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
        Icon(icon, label, modifier = Modifier.size(20.dp), tint = tint ?: MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BreadcrumbBar(segments: List<BreadcrumbSegment>, onSegmentClick: (BreadcrumbSegment) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            segments.forEachIndexed { index, segment ->
                if (index > 0) {
                    Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                }
                Text(
                    text = segment.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (index == segments.lastIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (index == segments.lastIndex) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.clickable { onSegmentClick(segment) }.padding(horizontal = 2.dp)
                )
            }
        }
    }
}

private val fileTypeColors = mapOf(
    "kt" to Color(0xFFA97BFF),
    "java" to Color(0xFFE76F00),
    "xml" to Color(0xFFE44D26),
    "json" to Color(0xFFF5D142),
    "js" to Color(0xFFF7DF1E),
    "ts" to Color(0xFF3178C6),
    "py" to Color(0xFF3776AB),
    "html" to Color(0xFFE44D26),
    "css" to Color(0xFF264DE4),
    "md" to Color(0xFF519ABA),
    "txt" to Color(0xFF888888),
    "sql" to Color(0xFFE38C00),
    "sh" to Color(0xFF4EAA25),
    "yml" to Color(0xFFCB171E),
    "gradle" to Color(0xFF02303A)
)

@Composable
private fun EditorTabsRow(files: List<EditorFile>, currentIndex: Int, onTabClick: (Int) -> Unit, onCloseTab: (Int) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f), tonalElevation = 1.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            files.forEachIndexed { index, file ->
                val isSelected = index == currentIndex
                val ext = file.name.substringAfterLast('.', "").lowercase()
                val dotColor = fileTypeColors[ext] ?: Color(0xFF90A4AE)
                val bgColor = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent

                Surface(
                    color = bgColor,
                    shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp),
                    modifier = Modifier.clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp).widthIn(max = 180.dp).clickable { onTabClick(index) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(color = dotColor, shape = CircleShape, modifier = Modifier.size(8.dp)) {}
                        Text(
                            file.name, style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
                        )
                        if (file.isModified) {
                            Surface(color = MaterialTheme.colorScheme.primary, shape = CircleShape, modifier = Modifier.size(6.dp)) {}
                        }
                        IconButton(onClick = { onCloseTab(index) }, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Default.Close, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomPanelToolbar(activePanel: BottomPanel?, onSwitchPanel: (BottomPanel) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 1.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomPanelTab("Output", Icons.Default.Output, activePanel == BottomPanel.OUTPUT) { onSwitchPanel(BottomPanel.OUTPUT) }
            BottomPanelTab("Problems", Icons.Default.Warning, activePanel == BottomPanel.PROBLEMS) { onSwitchPanel(BottomPanel.PROBLEMS) }
            BottomPanelTab("Terminal", Icons.Default.Terminal, activePanel == BottomPanel.TERMINAL) { onSwitchPanel(BottomPanel.TERMINAL) }
        }
    }
}

@Composable
private fun BottomPanelTab(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, isActive: Boolean, onClick: () -> Unit) {
    val color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, null, modifier = Modifier.size(14.dp), tint = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun BottomPanelArea(activePanel: BottomPanel, onSwitchPanel: (BottomPanel) -> Unit, onClose: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().height(200.dp)) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row {
                    BottomPanelTab("Output", Icons.Default.Output, activePanel == BottomPanel.OUTPUT) { onSwitchPanel(BottomPanel.OUTPUT) }
                    BottomPanelTab("Problems", Icons.Default.Warning, activePanel == BottomPanel.PROBLEMS) { onSwitchPanel(BottomPanel.PROBLEMS) }
                    BottomPanelTab("Terminal", Icons.Default.Terminal, activePanel == BottomPanel.TERMINAL) { onSwitchPanel(BottomPanel.TERMINAL) }
                }
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Box(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer).padding(12.dp)
        ) {
            when (activePanel) {
                BottomPanel.OUTPUT -> {
                    Text("Build output will appear here.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                }
                BottomPanel.PROBLEMS -> {
                    Text("No problems detected.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                }
                BottomPanel.TERMINAL -> {
                    Text("Terminal ready.", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@Composable
private fun EditorStatusBar(
    language: String, encoding: String, line: Int, col: Int,
    indentSize: Int, useTabs: Boolean, wordCount: Int, fileSize: Int, lineCount: Int,
    onLanguageClick: () -> Unit, onEncodingClick: () -> Unit, onIndentClick: () -> Unit
) {
    val formattedSize = when {
        fileSize < 1024 -> "$fileSize B"
        fileSize < 1024 * 1024 -> "${fileSize / 1024} KB"
        else -> "${"%.1f".format(fileSize / (1024.0 * 1024.0))} MB"
    }
    val indentLabel = if (useTabs) "Tab Size: $indentSize" else "Spaces: $indentSize"
    val statusColor = MaterialTheme.colorScheme.onSurfaceVariant
    val statusStyle = MaterialTheme.typography.labelSmall

    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 1.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatusItem(language, statusStyle, statusColor, onLanguageClick)
            StatusItem(encoding, statusStyle, statusColor, onEncodingClick)
            StatusItem("Ln $line, Col $col", statusStyle, statusColor, onClick = {})
            StatusItem(indentLabel, statusStyle, statusColor, onIndentClick)
            StatusItem("Words: $wordCount", statusStyle, statusColor, onClick = {})
            StatusItem("Lines: $lineCount", statusStyle, statusColor, onClick = {})
            Spacer(Modifier.weight(1f))
            Text(formattedSize, style = statusStyle, color = statusColor)
        }
    }
}

@Composable
private fun StatusItem(label: String, style: androidx.compose.ui.text.TextStyle, color: Color, onClick: () -> Unit) {
    Text(label, style = style, color = color, modifier = Modifier.clickable(onClick = onClick).padding(vertical = 2.dp))
}
