package com.xcoder.remote.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xcoder.remote.model.RemoteFileEntry

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RemoteFileItem(
    entry: RemoteFileEntry,
    isSelected: Boolean,
    showPermissions: Boolean = false,
    showSize: Boolean = true,
    showDate: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    Surface(
        color = backgroundColor,
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Icon(
                imageVector = when {
                    entry.isDirectory -> Icons.Default.Folder
                    entry.isSymbolicLink -> Icons.Default.Link
                    else -> FileIconProvider.getIcon(entry.extension)
                },
                contentDescription = null,
                tint = when {
                    entry.isDirectory -> MaterialTheme.colorScheme.primary
                    entry.isSymbolicLink -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Name + Metadata
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurface
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    if (showPermissions) {
                        Text(
                            text = entry.permissionString,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.alpha(0.7f)
                        )
                    }
                    if (entry.isDirectory && showSize) {
                        Text(
                            text = "Directory",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (!entry.isDirectory && showSize) {
                        Text(
                            text = entry.formattedSize,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (showDate && entry.lastModified > 0) {
                        Text(
                            text = entry.formattedDate,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Selection indicator
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/**
 * Provides file-type icons based on extension.
 */
object FileIconProvider {
    fun getIcon(extension: String) = when (extension.lowercase()) {
        "kt", "java", "c", "cpp", "h", "hpp", "rs", "go", "py", "rb",
        "js", "ts", "swift", "dart", "scala", "lua", "sh" -> Icons.Default.Code
        "html", "htm", "xml", "xhtml", "svg", "xsl" -> Icons.Default.Code
        "css", "scss", "sass", "less" -> Icons.Default.Settings
        "json", "yaml", "yml", "toml", "ini", "cfg", "properties" -> Icons.Default.Code
        "md", "mdx", "rst", "txt", "log" -> Icons.Default.FileCopy
        "pdf" -> Icons.Default.FileCopy
        "png", "jpg", "jpeg", "gif", "bmp", "webp", "ico" -> Icons.Default.FileCopy
        "mp4", "avi", "mkv", "mov", "webm" -> Icons.Default.FileCopy
        "mp3", "wav", "ogg", "flac", "aac" -> Icons.Default.FileCopy
        "zip", "tar", "gz", "bz2", "xz", "7z", "rar" -> Icons.Default.Folder
        "gradle" -> Icons.Default.Settings
        else -> Icons.Default.FileCopy
    }
}