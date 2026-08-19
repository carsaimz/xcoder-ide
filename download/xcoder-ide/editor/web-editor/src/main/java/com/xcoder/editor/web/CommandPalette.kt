package com.xcoder.editor.web

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.input.key.onKeyEvent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class CommandCategory(val displayName: String, val color: Color) {
    FILE("File", Color(0xFF4285F4)),
    EDIT("Edit", Color(0xFF34A853)),
    VIEW("View", Color(0xFFFBBC05)),
    NAVIGATION("Navigation", Color(0xFFEA4335)),
    EDITOR("Editor", Color(0xFF8E24AA)),
    GIT("Git", Color(0xFFF97316)),
    RUN("Run", Color(0xFF10B981));

    companion object {
        fun fromDisplayName(name: String): CommandCategory =
            entries.firstOrNull { it.displayName == name } ?: EDITOR
    }
}

data class EditorCommand(
    val id: String,
    val label: String,
    val shortcut: String,
    val category: CommandCategory,
    val icon: ImageVector
)

private val allCommands = listOf(
    EditorCommand("file.new", "New File", "Ctrl+N", CommandCategory.FILE, Icons.Default.NoteAdd),
    EditorCommand("file.open", "Open File", "Ctrl+O", CommandCategory.FILE, Icons.Default.FolderOpen),
    EditorCommand("file.save", "Save", "Ctrl+S", CommandCategory.FILE, Icons.Default.Save),
    EditorCommand("file.save_as", "Save As", "Ctrl+Shift+S", CommandCategory.FILE, Icons.Default.SaveAlt),
    EditorCommand("file.close", "Close File", "Ctrl+W", CommandCategory.FILE, Icons.Default.Close),
    EditorCommand("file.close_all", "Close All", "Ctrl+Shift+W", CommandCategory.FILE, Icons.Default.CloseFullscreen),
    EditorCommand("file.settings", "Settings", "Ctrl+,", CommandCategory.FILE, Icons.Default.Settings),
    EditorCommand("file.export", "Export File", "Ctrl+Shift+E", CommandCategory.FILE, Icons.Default.FileDownload),
    EditorCommand("edit.undo", "Undo", "Ctrl+Z", CommandCategory.EDIT, Icons.Default.Undo),
    EditorCommand("edit.redo", "Redo", "Ctrl+Shift+Z", CommandCategory.EDIT, Icons.Default.Redo),
    EditorCommand("edit.cut", "Cut", "Ctrl+X", CommandCategory.EDIT, Icons.Default.ContentCut),
    EditorCommand("edit.copy", "Copy", "Ctrl+C", CommandCategory.EDIT, Icons.Default.ContentCopy),
    EditorCommand("edit.paste", "Paste", "Ctrl+V", CommandCategory.EDIT, Icons.Default.ContentPaste),
    EditorCommand("edit.select_all", "Select All", "Ctrl+A", CommandCategory.EDIT, Icons.Default.SelectAll),
    EditorCommand("edit.delete_line", "Delete Line", "Ctrl+Shift+K", CommandCategory.EDIT, Icons.Default.DeleteSweep),
    EditorCommand("edit.duplicate_line", "Duplicate Line", "Ctrl+Shift+D", CommandCategory.EDIT, Icons.Default.ContentCopy),
    EditorCommand("edit.move_line_up", "Move Line Up", "Alt+Up", CommandCategory.EDIT, Icons.Default.ArrowUpward),
    EditorCommand("edit.move_line_down", "Move Line Down", "Alt+Down", CommandCategory.EDIT, Icons.Default.ArrowDownward),
    EditorCommand("edit.indent", "Indent Line", "Tab", CommandCategory.EDIT, Icons.Default.FormatIndentIncrease),
    EditorCommand("edit.outdent", "Outdent Line", "Shift+Tab", CommandCategory.EDIT, Icons.Default.FormatIndentDecrease),
    EditorCommand("edit.toggle_comment", "Toggle Comment", "Ctrl+/", CommandCategory.EDIT, Icons.Default.Comment),
    EditorCommand("edit.find", "Find", "Ctrl+F", CommandCategory.EDIT, Icons.Default.Search),
    EditorCommand("edit.find_replace", "Find and Replace", "Ctrl+H", CommandCategory.EDIT, Icons.Default.FindReplace),
    EditorCommand("edit.format", "Format Document", "Ctrl+Shift+F", CommandCategory.EDIT, Icons.Default.AutoFixHigh),
    EditorCommand("view.toggle_sidebar", "Toggle Sidebar", "Ctrl+B", CommandCategory.VIEW, Icons.Default.ViewSidebar),
    EditorCommand("view.toggle_terminal", "Toggle Terminal", "Ctrl+`", CommandCategory.VIEW, Icons.Default.Terminal),
    EditorCommand("view.toggle_minimap", "Toggle Minimap", "", CommandCategory.VIEW, Icons.Default.Map),
    EditorCommand("view.toggle_word_wrap", "Toggle Word Wrap", "Alt+Z", CommandCategory.VIEW, Icons.Default.WrapText),
    EditorCommand("view.zoom_in", "Zoom In", "Ctrl++", CommandCategory.VIEW, Icons.Default.ZoomIn),
    EditorCommand("view.zoom_out", "Zoom Out", "Ctrl+-", CommandCategory.VIEW, Icons.Default.ZoomOut),
    EditorCommand("view.reset_zoom", "Reset Zoom", "Ctrl+0", CommandCategory.VIEW, Icons.Default.ZoomOutMap),
    EditorCommand("view.toggle_fullscreen", "Toggle Full Screen", "F11", CommandCategory.VIEW, Icons.Default.Fullscreen),
    EditorCommand("view.split_horizontal", "Split Editor Horizontal", "Ctrl+\\", CommandCategory.VIEW, Icons.Default.ViewColumn),
    EditorCommand("nav.goto_line", "Go to Line", "Ctrl+G", CommandCategory.NAVIGATION, Icons.Default.VerticalAlignCenter),
    EditorCommand("nav.goto_file", "Go to File", "Ctrl+P", CommandCategory.NAVIGATION, Icons.Default.FileOpen),
    EditorCommand("nav.goto_symbol", "Go to Symbol", "Ctrl+Shift+O", CommandCategory.NAVIGATION, Icons.Default.Symbol),
    EditorCommand("nav.go_back", "Go Back", "Alt+Left", CommandCategory.NAVIGATION, Icons.Default.ArrowBack),
    EditorCommand("nav.go_forward", "Go Forward", "Alt+Right", CommandCategory.NAVIGATION, Icons.Default.ArrowForward),
    EditorCommand("nav.goto_definition", "Go to Definition", "F12", CommandCategory.NAVIGATION, Icons.Default.Input),
    EditorCommand("nav.find_references", "Find References", "Shift+F12", CommandCategory.NAVIGATION, Icons.Default.Receipt),
    EditorCommand("nav.goto_next_problem", "Go to Next Problem", "F8", CommandCategory.NAVIGATION, Icons.Default.Warning),
    EditorCommand("editor.toggle_breadcrumbs", "Toggle Breadcrumbs", "", CommandCategory.EDITOR, Icons.Default.NavigateNext),
    EditorCommand("editor.fold_all", "Fold All", "Ctrl+Shift+[", CommandCategory.EDITOR, Icons.Default.UnfoldLess),
    EditorCommand("editor.unfold_all", "Unfold All", "Ctrl+Shift+]", CommandCategory.EDITOR, Icons.Default.UnfoldMore),
    EditorCommand("editor.toggle_fold", "Toggle Fold", "Ctrl+Shift+\\", CommandCategory.EDITOR, Icons.Default.ViewHeadline),
    EditorCommand("editor.transform_uppercase", "Transform to Uppercase", "Ctrl+Shift+U", CommandCategory.EDITOR, Icons.Default.TextFields),
    EditorCommand("editor.transform_lowercase", "Transform to Lowercase", "Ctrl+Shift+L", CommandCategory.EDITOR, Icons.Default.TextFields),
    EditorCommand("git.init", "Initialize Repository", "", CommandCategory.GIT, Icons.Default.AddCircle),
    EditorCommand("git.commit", "Commit", "", CommandCategory.GIT, Icons.Default.Commit),
    EditorCommand("git.push", "Push", "", CommandCategory.GIT, Icons.Default.CloudUpload),
    EditorCommand("git.pull", "Pull", "", CommandCategory.GIT, Icons.Default.CloudDownload),
    EditorCommand("git.branch", "Create Branch", "", CommandCategory.GIT, Icons.Default.CallSplit),
    EditorCommand("git.log", "View History", "", CommandCategory.GIT, Icons.Default.History),
    EditorCommand("git.diff", "Open Diff", "", CommandCategory.GIT, Icons.Default.Difference),
    EditorCommand("git.stash", "Stash Changes", "", CommandCategory.GIT, Icons.Default.Archive),
    EditorCommand("run.run_project", "Run Project", "Shift+F10", CommandCategory.RUN, Icons.Default.PlayArrow),
    EditorCommand("run.debug_project", "Debug Project", "Shift+F9", CommandCategory.RUN, Icons.Default.BugReport),
    EditorCommand("run.stop", "Stop", "Ctrl+F2", CommandCategory.RUN, Icons.Default.Stop),
    EditorCommand("run.run_file", "Run Current File", "Ctrl+Shift+F10", CommandCategory.RUN, Icons.Default.PlayCircle),
    EditorCommand("run.clear_output", "Clear Output", "", CommandCategory.RUN, Icons.Default.DeleteSweep)
)

private fun fuzzyMatch(query: String, text: String): Boolean {
    if (query.isBlank()) return true
    val lowerQuery = query.lowercase()
    val lowerText = text.lowercase()
    if (lowerText.contains(lowerQuery)) return true
    var qi = 0
    for (ci in lowerText.indices) {
        if (qi < lowerQuery.length && lowerText[ci] == lowerQuery[qi]) {
            qi++
        }
    }
    return qi == lowerQuery.length
}

private fun fuzzyScore(query: String, text: String): Int {
    if (query.isBlank()) return 0
    val lowerQuery = query.lowercase()
    val lowerText = text.lowercase()
    val exactIndex = lowerText.indexOf(lowerQuery)
    if (exactIndex >= 0) return 1000 - exactIndex
    var score = 0
    var qi = 0
    for (ci in lowerText.indices) {
        if (qi < lowerQuery.length && lowerText[ci] == lowerQuery[qi]) {
            score += if (ci == 0 || lowerText[ci - 1] == ' ' || lowerText[ci - 1] == '_') 10 else 1
            qi++
        }
    }
    return if (qi == lowerQuery.length) score else -1
}

@Composable
fun CommandPalette(
    visible: Boolean,
    onCommandSelected: (EditorCommand) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val recentCommands = remember { mutableStateListOf<EditorCommand>() }
    var searchQuery by remember { mutableStateOf("") }
    var selectedIndex by remember { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    val filteredCommands = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            val recent = recentCommands.take(5)
            val rest = allCommands.filter { cmd -> cmd !in recent }
            recent + rest
        } else {
            allCommands
                .filter { cmd -> fuzzyMatch(searchQuery, cmd.label) || fuzzyMatch(searchQuery, cmd.id) }
                .sortedByDescending { cmd ->
                    maxOf(fuzzyScore(searchQuery, cmd.label), fuzzyScore(searchQuery, cmd.id))
                }
        }
    }

    LaunchedEffect(filteredCommands.size) {
        if (selectedIndex >= filteredCommands.size) {
            selectedIndex = (filteredCommands.size - 1).coerceAtLeast(0)
        }
    }

    LaunchedEffect(visible) {
        if (visible) {
            searchQuery = ""
            selectedIndex = 0
            delay(80)
            focusRequester.requestFocus()
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(100)) + scaleIn(
            initialScale = 0.95f,
            animationSpec = tween(100)
        ),
        exit = fadeOut(animationSpec = tween(60)) + scaleOut(
            targetScale = 0.95f,
            animationSpec = tween(60)
        ),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .fillMaxHeight(0.6f)
                .padding(top = 80.dp)
                .align(Alignment.TopCenter)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 16.dp
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                            selectedIndex = 0
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .onKeyEvent { event ->
                                when {
                                    event.type == KeyEventType.KeyDown && event.key == Key.Enter -> {
                                        if (filteredCommands.isNotEmpty() && selectedIndex in filteredCommands.indices) {
                                            val cmd = filteredCommands[selectedIndex]
                                            scope.launch {
                                                recentCommands.remove(cmd)
                                                recentCommands.add(0, cmd)
                                                if (recentCommands.size > 5) recentCommands.removeLast()
                                            }
                                            onCommandSelected(cmd)
                                            onDismiss()
                                        }
                                        true
                                    }
                                    event.type == KeyEventType.KeyDown && event.key == Key.Escape -> {
                                        onDismiss()
                                        true
                                    }
                                    event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp -> {
                                        selectedIndex = (selectedIndex - 1).coerceAtLeast(0)
                                        true
                                    }
                                    event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown -> {
                                        selectedIndex = (selectedIndex + 1).coerceAtMost(
                                            (filteredCommands.size - 1).coerceAtLeast(0)
                                        )
                                        true
                                    }
                                    else -> false
                                }
                            },
                        placeholder = {
                            Text(
                                "> Type a command...",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            cursorColor = MaterialTheme.colorScheme.primary
                        )
                    )

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        if (searchQuery.isBlank() && recentCommands.isNotEmpty()) {
                            item {
                                Text(
                                    "RECENT",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                )
                            }
                            itemsIndexed(
                                items = recentCommands,
                                key = { _, cmd -> "recent-${cmd.id}" }
                            ) { index, cmd ->
                                CommandItem(
                                    command = cmd,
                                    isSelected = index == selectedIndex,
                                    onClick = {
                                        onCommandSelected(cmd)
                                        onDismiss()
                                    }
                                )
                            }
                            item {
                                Text(
                                    "ALL COMMANDS",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                )
                            }
                            val recentIds = recentCommands.map { it.id }.toSet()
                            val rest = filteredCommands.filter { it.id !in recentIds }
                            itemsIndexed(
                                items = rest,
                                key = { _, cmd -> "all-${cmd.id}" }
                            ) { index, cmd ->
                                val adjustedIndex = recentCommands.size + index
                                CommandItem(
                                    command = cmd,
                                    isSelected = adjustedIndex == selectedIndex,
                                    onClick = {
                                        onCommandSelected(cmd)
                                        onDismiss()
                                    }
                                )
                            }
                        } else {
                            itemsIndexed(
                                items = filteredCommands,
                                key = { _, cmd -> cmd.id }
                            ) { index, cmd ->
                                CommandItem(
                                    command = cmd,
                                    isSelected = index == selectedIndex,
                                    onClick = {
                                        onCommandSelected(cmd)
                                        onDismiss()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommandItem(
    command: EditorCommand,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    } else {
        Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = command.icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = if (isSelected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )

        Text(
            text = command.label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        if (command.shortcut.isNotEmpty()) {
            Text(
                text = command.shortcut,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.width(4.dp))

        Surface(
            shape = RoundedCornerShape(4.dp),
            color = command.category.color.copy(alpha = 0.15f)
        ) {
            Text(
                text = command.category.displayName,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = command.category.color,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}
