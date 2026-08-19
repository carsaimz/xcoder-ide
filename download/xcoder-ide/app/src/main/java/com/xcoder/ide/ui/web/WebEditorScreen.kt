package com.xcoder.ide.ui.web

import android.annotation.SuppressLint
import android.net.Uri
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.xcoder.ide.ui.common.EditorTab
import com.xcoder.ide.ui.common.EditorTabs
import com.xcoder.ide.ui.common.FileTreeNode
import com.xcoder.ide.ui.common.FileTree
import java.io.File

/**
 * Web-mode editor screen.
 *
 * Layout: File tree sidebar (left) + Tab bar (top) + WebView editor (center).
 * The [WebView] hosts the Monaco / CodeMirror-based editor from the
 * `:editor:web-editor` module.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebEditorScreen(
    fileUri: Uri? = null,
    modifier: Modifier = Modifier
) {
    // --- State -----------------------------------------------------------
    var sidebarVisible by remember { mutableStateOf(true) }
    var selectedFilePath by remember { mutableStateOf<String?>(null) }
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp

    // Sample file tree – in production this comes from FileManager.
    val sampleNodes = remember {
        listOf(
            FileTreeNode("index.html", "/project/index.html", isDirectory = false, extension = "html"),
            FileTreeNode("style.css", "/project/style.css", isDirectory = false, extension = "css"),
            FileTreeNode("app.js", "/project/app.js", isDirectory = false, extension = "js"),
            FileTreeNode("src", "/project/src", isDirectory = true, children = listOf(
                FileTreeNode("main.js", "/project/src/main.js", isDirectory = false, extension = "js"),
                FileTreeNode("utils.js", "/project/src/utils.js", isDirectory = false, extension = "js")
            )),
            FileTreeNode("assets", "/project/assets", isDirectory = true, children = listOf(
                FileTreeNode("logo.svg", "/project/assets/logo.svg", isDirectory = false, extension = "svg")
            ))
        )
    }

    // Open tabs
    var openTabs by remember {
        mutableStateOf(
            listOf(
                EditorTab(id = "/project/index.html", title = "index.html", isActive = true)
            )
        )
    }

    val sidebarWidth = if (sidebarVisible) 260.dp else 0.dp

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // --- Sidebar: File tree ------------------------------------------
        AnimatedVisibility(
            visible = sidebarVisible,
            enter = horizontalSlideIn(initialOffsetX = { -it }) + fadeIn(),
            exit = horizontalSlideOut(targetOffsetX = { -it }) + fadeOut()
        ) {
            Surface(
                modifier = Modifier
                    .width(sidebarWidth)
                    .fillMaxHeight(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                shadowElevation = 4.dp
            ) {
                Column {
                    // Sidebar header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Explorer",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { sidebarVisible = false }) {
                            Icon(
                                Icons.Default.ChevronLeft,
                                contentDescription = "Close sidebar",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    HorizontalDivider()

                    // File tree
                    FileTree(
                        rootNodes = sampleNodes,
                        onFileClick = { node ->
                            selectedFilePath = node.path
                            openTabs = openTabs.map { it.copy(isActive = it.id == node.path) }
                                .let { current ->
                                    if (current.none { it.id == node.path }) {
                                        current + EditorTab(
                                            id = node.path,
                                            title = node.name,
                                            isActive = true,
                                            extension = node.extension
                                        )
                                    } else {
                                        current
                                    }
                                }
                        },
                        selectedPath = selectedFilePath
                    )
                }
            }
        }

        // --- Main content area -------------------------------------------
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            // Toolbar row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!sidebarVisible) {
                    IconButton(onClick = { sidebarVisible = true }, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.Menu,
                            contentDescription = "Open sidebar",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Default.Search,
                    contentDescription = "Search",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "Search files…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { /* undo */ }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Undo, contentDescription = "Undo", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { /* redo */ }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Redo, contentDescription = "Redo", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Tabs
            EditorTabs(
                tabs = openTabs,
                onTabSelected = { tab ->
                    selectedFilePath = tab.id
                    openTabs = openTabs.map { it.copy(isActive = it.id == tab.id) }
                },
                onTabClosed = { tab ->
                    openTabs = openTabs
                        .filter { it.id != tab.id }
                        .let { updated ->
                            if (updated.isEmpty()) {
                                listOf(EditorTab(id = "untitled", title = "Untitled", isActive = true))
                            } else if (tab.isActive && updated.isNotEmpty()) {
                                updated.mapIndexed { i, t -> t.copy(isActive = i == updated.lastIndex) }
                            } else {
                                updated
                            }
                        }
                }
            )

            HorizontalDivider()

            // WebView editor
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                WebEditorView(
                    filePath = selectedFilePath ?: "/project/index.html",
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Status bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Web Editor",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "UTF-8  |  JavaScript",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// WebView wrapper
// ---------------------------------------------------------------------------

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebEditorView(
    filePath: String,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true
                settings.allowContentAccess = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                webViewClient = WebViewClient()
                // Load the web editor HTML from the :editor:web-editor module assets.
                // In production the module ships an editor.html + bundled JS.
                loadUrl("file:///android_asset/editor/web-editor.html?file=$filePath")
            }
        },
        update = { webView ->
            webView.evaluateJavascript(
                """if(window.xcoderEditor) window.xcoderEditor.openFile('$filePath');""",
                null
            )
        },
        modifier = modifier
    )
}
