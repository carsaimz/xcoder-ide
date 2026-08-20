package com.xcoder.editor.sora

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.rosemoe.sora.widget.CodeEditor

/**
 * Full editor screen using Rosemoe sora-editor.
 * Includes toolbar, editor area, and status bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    filePath: String = "",
    initialContent: String = "",
    onBack: (() -> Unit)? = null,
    onSave: ((String, String) -> Unit)? = null,
    viewModel: SoraEditorViewModel = hiltViewModel()
) {
    val editorRef = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<CodeEditor?>(null) }
    var showSearchBar by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchCaseSensitive by remember { mutableStateOf(false) }

    // Load file content on first composition
    LaunchedEffect(filePath) {
        if (filePath.isNotEmpty() && initialContent.isNotEmpty()) {
            viewModel.openFile(filePath, initialContent)
        }
    }

    // Attach editor when available
    LaunchedEffect(editorRef.value) {
        editorRef.value?.let { viewModel.attachEditor(it) }
    }

    Scaffold(
        topBar = {
            Column {
                // ── Title bar ──────────────────────────────────────
                TopAppBar(
                    title = {
                        Text(
                            text = filePath.substringAfterLast("/"),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1
                        )
                    },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                            }
                        }
                    },
                    actions = {
                        // Save
                        IconButton(onClick = {
                            val text = viewModel.getText()
                            onSave?.invoke(filePath, text)
                            viewModel.saveFile()
                        }) {
                            Icon(Icons.Default.Save, contentDescription = "Save")
                        }
                        // Search
                        IconButton(onClick = { showSearchBar = !showSearchBar }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                        // Undo
                        IconButton(onClick = { viewModel.undo() }) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                        }
                        // Redo
                        IconButton(onClick = { viewModel.redo() }) {
                            Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                        }
                        // More options
                        var showMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More")
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Format Code") },
                                    onClick = { viewModel.format(); showMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Toggle Word Wrap") },
                                    onClick = { viewModel.toggleWordWrap(); showMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Toggle Minimap") },
                                    onClick = { viewModel.toggleMinimap(); showMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Toggle Comment") },
                                    onClick = { viewModel.toggleComment(); showMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Go to Line…") },
                                    onClick = { showMenu = false /* TODO: dialog */ }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

                // ── Search bar ─────────────────────────────────────
                AnimatedVisibility(visible = showSearchBar) {
                    Surface(
                        tonalElevation = 2.dp,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search…") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = {
                                            viewModel.search(searchQuery, searchCaseSensitive)
                                        }) {
                                            Icon(Icons.Default.Search, contentDescription = "Find")
                                        }
                                    }
                                }
                            )
                            IconButton(onClick = { searchCaseSensitive = !searchCaseSensitive }) {
                                Text(
                                    "Aa",
                                    color = if (searchCaseSensitive) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { showSearchBar = false }) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            // ── Status bar ────────────────────────────────────────
            Surface(
                tonalElevation = 3.dp,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        filePath.substringAfterLast("."),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        "UTF-8",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(16.dp))
                    if (viewModel.isModified) {
                        Text(
                            "● Modified",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            SoraEditor(
                filePath = filePath,
                contentText = initialContent,
                editorRef = object : androidx.compose.runtime.Ref<CodeEditor?> {
                    override var value: CodeEditor?
                        get() = editorRef.value
                        set(v) { editorRef.value = v }
                }
            )
        }
    }
}
