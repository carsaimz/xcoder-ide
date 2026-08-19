package com.xcoder.ide.ui.common

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.io.File

// ---------------------------------------------------------------------------
// Data model
// ---------------------------------------------------------------------------

/** Represents a single node in the file tree. */
data class FileTreeNode(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val children: List<FileTreeNode> = emptyList(),
    val extension: String = File(path).extension
)

/** Build a [FileTreeNode] tree from a root [File]. */
fun File.toFileTreeNode(
    maxDepth: Int = 5,
    currentDepth: Int = 0,
    showHidden: Boolean = false
): FileTreeNode {
    if (isFile || !isDirectory || currentDepth >= maxDepth) {
        return FileTreeNode(
            name = name,
            path = absolutePath,
            isDirectory = isDirectory
        )
    }
    val sortedChildren = listFiles()
        ?.filter { showHidden || !it.name.startsWith(".") }
        ?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
        .orEmpty()
        .map { it.toFileTreeNode(maxDepth, currentDepth + 1, showHidden) }

    return FileTreeNode(
        name = name,
        path = absolutePath,
        isDirectory = true,
        children = sortedChildren
    )
}

// ---------------------------------------------------------------------------
// Composable
// ---------------------------------------------------------------------------

/**
 * A recursive, collapsible file tree composable.
 *
 * @param rootNodes Top-level nodes to display.
 * @param onFileClick Callback when a file (leaf) is clicked.
 * @param onFileLongClick Callback on long-press a file.
 * @param onDirectoryClick Callback when a directory is clicked (expanded/collapsed).
 * @param selectedPath Currently selected file path (highlighted).
 * @param modifier Modifier for the overall tree container.
 * @param indentPerLevel Horizontal indent in dp per nesting level.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileTree(
    rootNodes: List<FileTreeNode>,
    onFileClick: (FileTreeNode) -> Unit,
    onFileLongClick: ((FileTreeNode) -> Unit)? = null,
    onDirectoryClick: ((FileTreeNode) -> Unit)? = null,
    selectedPath: String? = null,
    modifier: Modifier = Modifier,
    indentPerLevel: Dp = 16.dp
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 4.dp)
    ) {
        items(
            items = rootNodes,
            key = { it.path }
        ) { node ->
            FileTreeNodeRow(
                node = node,
                depth = 0,
                onFileClick = onFileClick,
                onFileLongClick = onFileLongClick,
                onDirectoryClick = onDirectoryClick,
                selectedPath = selectedPath,
                indentPerLevel = indentPerLevel
            )
        }
    }
}

@Composable
private fun FileTreeNodeRow(
    node: FileTreeNode,
    depth: Int,
    onFileClick: (FileTreeNode) -> Unit,
    onFileLongClick: ((FileTreeNode) -> Unit)?,
    onDirectoryClick: ((FileTreeNode) -> Unit)?,
    selectedPath: String?,
    indentPerLevel: Dp
) {
    var expanded by remember(node.path) { mutableStateOf(true) }
    val isSelected = selectedPath == node.path

    val backgroundColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        else -> Color.Transparent
    }
    val contentColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(backgroundColor)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    if (node.isDirectory) {
                        expanded = !expanded
                        onDirectoryClick?.invoke(node)
                    } else {
                        onFileClick(node)
                    }
                }
                .padding(start = indentPerLevel * (depth + 1), end = 8.dp, top = 2.dp, bottom = 2.dp)
                .height(28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Expand / collapse chevron for directories
            if (node.isDirectory) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = if (expanded) Icons.Default.FolderOpen else Icons.Default.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.8f)
                )
            } else {
                Spacer(Modifier.width(22.dp))
                Icon(
                    imageVector = fileIconForExtension(node.extension),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = fileIconColorForExtension(node.extension)
                )
            }

            Spacer(Modifier.width(6.dp))

            Text(
                text = node.name,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        // Children (animated visibility)
        if (node.isDirectory && node.children.isNotEmpty()) {
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(animationSpec = tween(100)),
                exit = shrinkVertically() + fadeOut(animationSpec = tween(100))
            ) {
                Column {
                    node.children.forEach { child ->
                        FileTreeNodeRow(
                            node = child,
                            depth = depth + 1,
                            onFileClick = onFileClick,
                            onFileLongClick = onFileLongClick,
                            onDirectoryClick = onDirectoryClick,
                            selectedPath = selectedPath,
                            indentPerLevel = indentPerLevel
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Icon mapping helpers
// ---------------------------------------------------------------------------

@Composable
private fun fileIconForExtension(ext: String) = when (ext.lowercase()) {
    "kt", "kts" -> Icons.Default.Code
    "java" -> Icons.Default.Code
    "xml" -> Icons.Default.Code
    "json" -> Icons.Default.DataObject
    "js", "mjs" -> Icons.Default.Javascript
    "ts", "tsx" -> Icons.Default.Javascript
    "html", "htm" -> Icons.Default.Html
    "css", "scss", "less" -> Icons.Default.Palette
    "py" -> Icons.Default.Code
    "md" -> Icons.Default.Article
    "gradle" -> Icons.Default.Build
    "png", "jpg", "jpeg", "gif", "webp", "svg" -> Icons.Default.Image
    "txt", "log" -> Icons.Default.Description
    "sh" -> Icons.Default.Terminal
    "yml", "yaml" -> Icons.Default.Settings
    "properties" -> Icons.Default.Settings
    else -> Icons.Default.InsertDriveFile
}

@Composable
private fun fileIconColorForExtension(ext: String) = when (ext.lowercase()) {
    "kt", "kts" -> Color(0xFFA97BFF)
    "java" -> Color(0xFFED8B00)
    "xml" -> Color(0xFFE76F51)
    "json" -> Color(0xFFFDCB6E)
    "js", "mjs" -> Color(0xFFF7DF1E)
    "ts", "tsx" -> Color(0xFF3178C6)
    "html" -> Color(0xFFE44D26)
    "css", "scss", "less" -> Color(0xFF1572B6)
    "py" -> Color(0xFF3776AB)
    "md" -> Color(0xFF519ABA)
    "gradle" -> Color(0xFF0052CC)
    "sh" -> Color(0xFF89E051)
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
