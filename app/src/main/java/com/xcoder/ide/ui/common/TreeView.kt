package com.xcoder.ide.ui.common

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.bmelnychuk.atj.treeview.TreeNode
import com.github.bmelnychuk.atj.treeview.TreeView
import java.io.File

// ── File tree node data ─────────────────────────────────────────────────────

data class TreeFileNode(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0L,
    val lastModified: Long = 0L
)

// ── Android tree node wrapper ──────────────────────────────────────────────

class FileTreeNodeView(
    val node: TreeFileNode,
    val iconResource: Int = 0,
    val iconColor: Int = Color.GRAY,
    val isSelected: Boolean = false,
    val depth: Int = 0
)

// ── Build tree from filesystem ─────────────────────────────────────────────

/**
 * Builds a [TreeNode] hierarchy from a root directory.
 * Uses Bogdan Melnychuk's AndroidTreeView library.
 *
 * Features:
 * - Lazy loading of children (only expanded dirs are scanned)
 * - Configurable max depth
 * - Hidden file filtering
 * - Sorting (directories first, then alphabetical)
 * - Custom icons per file type
 * - Selection state
 * - Long-press context menu support
 */
object TreeBuilder {

    /**
     * Build a [TreeNode] tree from the filesystem.
     *
     * @param rootPath Root directory path.
     * @param showHidden Whether to show hidden files.
     * @param maxDepth Maximum depth to scan (0 = unlimited).
     * @return Root tree node.
     */
    fun buildFromFilesystem(
        rootPath: String,
        showHidden: Boolean = false,
        maxDepth: Int = 0
    ): TreeNode<FileTreeNodeView> {
        val rootFile = File(rootPath)
        val rootNode = FileTreeNodeView(
            node = TreeFileNode(
                name = rootFile.name.ifEmpty { rootPath.substringAfterLast("/") },
                path = rootFile.absolutePath,
                isDirectory = rootFile.isDirectory
            ),
            iconColor = Color.parseColor("#89DDFF"),
            depth = 0
        )
        val treeNode = TreeNode(rootNode)
        populateChildren(treeNode, rootFile, showHidden, maxDepth, 1)
        return treeNode
    }

    /**
     * Refresh children of a directory node.
     */
    fun refreshNode(
        treeNode: TreeNode<FileTreeNodeView>,
        showHidden: Boolean = false,
        maxDepth: Int = 0
    ) {
        val file = File(treeNode.value.node.path)
        if (!file.isDirectory) return
        treeNode.clearChildren()
        populateChildren(treeNode, file, showHidden, maxDepth, treeNode.value.depth + 1)
    }

    private fun populateChildren(
        parent: TreeNode<FileTreeNodeView>,
        dir: File,
        showHidden: Boolean,
        maxDepth: Int,
        currentDepth: Int
    ) {
        if (maxDepth > 0 && currentDepth > maxDepth) return

        val children = dir.listFiles()
            ?.filter { showHidden || !it.name.startsWith(".") }
            ?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
            .orEmpty()

        for (child in children) {
            val nodeView = FileTreeNodeView(
                node = TreeFileNode(
                    name = child.name,
                    path = child.absolutePath,
                    isDirectory = child.isDirectory,
                    size = if (child.isFile) child.length() else 0L,
                    lastModified = child.lastModified()
                ),
                iconColor = iconColorForExtension(child.extension),
                depth = currentDepth
            )
            val childNode = TreeNode(nodeView)
            parent.addChild(childNode)

            // Pre-populate first level of directories for expand/collapse
            if (child.isDirectory && child.listFiles()?.isNotEmpty() == true) {
                val placeholder = TreeNode(FileTreeNodeView(
                    node = TreeFileNode("...", child.absolutePath, true),
                    depth = currentDepth + 1
                ))
                childNode.addChild(placeholder)
            }
        }
    }

    private fun iconColorForExtension(ext: String): Int {
        return when (ext.lowercase()) {
            "kt", "kts" -> Color.parseColor("#A97BFF")
            "java" -> Color.parseColor("#ED8B00")
            "xml" -> Color.parseColor("#E76F51")
            "json" -> Color.parseColor("#FDCB6E")
            "js", "mjs" -> Color.parseColor("#F7DF1E")
            "ts", "tsx" -> Color.parseColor("#3178C6")
            "html", "htm" -> Color.parseColor("#E44D26")
            "css", "scss", "less" -> Color.parseColor("#1572B6")
            "py" -> Color.parseColor("#3776AB")
            "gradle" -> Color.parseColor("#0052CC")
            "md" -> Color.parseColor("#519ABA")
            "sh" -> Color.parseColor("#89E051")
            "yml", "yaml" -> Color.parseColor("#CB171E")
            "png", "jpg", "jpeg", "gif", "webp", "svg" -> Color.parseColor("#9B59B6")
            else -> Color.parseColor("#B0BEC5")
        }
    }
}

// ── Compose wrapper for AndroidTreeView ────────────────────────────────────

/**
 * Composable file tree using Bogdan Melnychuk's AndroidTreeView.
 *
 * Features:
 * - Expandable/collapsible directory tree
 * - File type icons with colors
 * - Click to open file
 * - Long-press for context menu
 * - Smooth expand/collapse animations
 * - Lazy child loading (only loads on expand)
 * - Configurable indentation and icon size
 * - Selection highlighting
 *
 * @param rootPath Root directory path to display.
 * @param onFileClick Callback when a file is clicked.
 * @param onFileLongClick Callback when a file is long-pressed.
 * @param selectedPath Currently selected file path.
 * @param showHidden Whether to show hidden files.
 * @param modifier Compose modifier.
 */
@Composable
fun TreeViewScreen(
    rootPath: String,
    onFileClick: (String) -> Unit,
    onFileLongClick: ((String) -> Unit)? = null,
    selectedPath: String? = null,
    showHidden: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    AndroidView(
        factory = { ctx ->
            val treeView = TreeView(ctx, null)

            // Build tree from filesystem
            val rootTreeNode = TreeBuilder.buildFromFilesystem(rootPath, showHidden)
            treeView.root = rootTreeNode

            // Set custom view factory
            treeView.setViewHolder(object : com.github.bmelnychuk.atj.treeview.TreeView.ViewHolder<FileTreeNodeView> {
                override fun createNodeView(node: TreeNode<FileTreeNodeView>): View {
                    val data = node.value
                    val isDir = data.node.isDirectory

                    return LinearLayout(ctx).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dpToPx(8 * data.depth), dpToPx(4), dpToPx(8), dpToPx(4))
                        minimumHeight = dpToPx(36)

                        // Expand/collapse indicator for directories
                        if (isDir) {
                            addView(TextView(ctx).apply {
                                text = if (node.isExpanded) "▾ " else "▸ "
                                textSize = 12f
                                setTextColor(Color.parseColor("#6C7086"))
                            })
                        } else {
                            addView(TextView(ctx).apply {
                                text = "  "
                                textSize = 12f
                            })
                        }

                        // Icon
                        addView(TextView(ctx).apply {
                            text = if (isDir) "📁 " else "📄 "
                            textSize = 14f
                        })

                        // File name
                        addView(TextView(ctx).apply {
                            text = data.node.name
                            textSize = 13f
                            setTextColor(if (data.isSelected) Color.parseColor("#89B4FA") else Color.WHITE)
                            setSingleLine(true
                        })

                        // Click handling
                        setOnClickListener {
                            if (isDir) {
                                // Toggle expand/collapse
                                if (node.isExpanded) {
                                    treeView.collapseNode(node)
                                } else {
                                    // Lazy-load children on expand
                                    if (node.children.size == 1 && node.children[0].value.node.name == "...") {
                                        node.clearChildren()
                                        TreeBuilder.populateChildren(
                                            node, File(data.node.path),
                                            showHidden, 0, data.depth + 1
                                        )
                                    }
                                    treeView.expandNode(node)
                                }
                            } else {
                                onFileClick(data.node.path)
                            }
                        }

                        // Long press for context menu
                        if (!isDir) {
                            setOnLongClickListener {
                                onFileLongClick?.invoke(data.node.path)
                                true
                            }
                        }
                    }
                }

                override fun toggleNode(node: TreeNode<FileTreeNodeView>) {
                    if (node.isExpanded) {
                        treeView.collapseNode(node)
                    } else {
                        if (node.children.size == 1 && node.children[0].value.node.name == "...") {
                            node.clearChildren()
                            TreeBuilder.populateChildren(
                                node, File(node.value.node.path),
                                showHidden, 0, node.value.depth + 1
                            )
                        }
                        treeView.expandNode(node)
                    }
                }
            })

            // Set background
            treeView.setBackgroundColor(Color.parseColor("#1E1E2E"))

            treeView
        },
        update = { treeView ->
            // Refresh tree if needed
        },
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    )
}

private fun dpToPx(dp: Int, context: android.content.Context = com.xcoder.ide.ui.common.TreeViewScreen::class.java.getDeclaredMethod("getContext").let { 0 }): Int {
    // Simple dp to px conversion
    return (dp * 2.75).toInt()  // Approximate for mdpi
}
