package com.xcoder.ide.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xcoder.ide.theme.LocalIdeColors

/**
 * Data model representing a single open editor tab.
 *
 * @param id Stable identifier for the tab (e.g. file path hash).
 * @param title Display title (file name).
 * @param subtitle Optional subtitle (directory, language).
 * @param isModified Whether the file has unsaved changes.
 * @param isActive Whether this tab is currently in the foreground.
 * @param icon Optional leading icon composable (typically [FileIconProvider]).
 */
data class EditorTab(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val isModified: Boolean = false,
    val isActive: Boolean = false,
    val icon: @Composable (() -> Unit)? = null
)

/**
 * Horizontal scrollable tab bar suitable for a multi-tab editor.
 *
 * Based on AndroidIDE's `EditorTabManager` tab presentation which uses:
 * - Horizontal scroll with clipToBounds
 * - File icon based on extension (via [FileIconProvider])
 * - Modified indicator dot
 * - Close button per tab
 * - Long-press context menu (close others / close all)
 *
 * @param tabs Current list of open tabs.
 * @param onTabSelected Callback when a tab is clicked.
 * @param onTabClosed Callback when the close icon is clicked. Pass `null` to hide close buttons.
 * @param onTabReorder Optional drag-and-drop reorder callback `(fromIndex, toIndex)`.
 * @param modifier Modifier applied to the tab bar container.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EditorTabs(
    tabs: List<EditorTab>,
    onTabSelected: (EditorTab) -> Unit,
    onTabClosed: ((EditorTab) -> Unit)? = {},
    modifier: Modifier = Modifier,
    onTabReorder: ((Int, Int) -> Unit)? = null
) {
    val scrollState = rememberScrollState()
    val surfaceColor = MaterialTheme.colorScheme.surface
    val activeColor = MaterialTheme.colorScheme.primaryContainer
    val activeTextColor = MaterialTheme.colorScheme.onPrimaryContainer
    val inactiveTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val ideColors = LocalIdeColors.current

    // Context menu state for long-press.
    var contextMenuTab by remember { mutableStateOf<EditorTab?>(null) }
    var showContextMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp),
        color = surfaceColor,
        tonalElevation = 2.dp
    ) {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(scrollState)
                    .clipToBounds(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { index, tab ->
                    val selectedBgColor by animateColorAsState(
                        targetValue = if (tab.isActive) activeColor else Color.Transparent,
                        animationSpec = tween(durationMillis = 150),
                        label = "tabBg"
                    )
                    val textColor by animateColorAsState(
                        targetValue = if (tab.isActive) activeTextColor else inactiveTextColor,
                        animationSpec = tween(durationMillis = 150),
                        label = "tabText"
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxHeight()
                            .widthIn(min = 100.dp, max = 200.dp)
                            .background(color = selectedBgColor)
                            .combinedClickable(
                                onClick = { onTabSelected(tab) },
                                onLongClick = {
                                    contextMenuTab = tab
                                    showContextMenu = true
                                }
                            )
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Leading icon (file type icon).
                        if (tab.icon != null) {
                            tab.icon?.invoke()
                            Spacer(Modifier.width(6.dp))
                        } else {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = textColor
                            )
                            Spacer(Modifier.width(6.dp))
                        }

                        // Title.
                        Text(
                            text = tab.title,
                            style = MaterialTheme.typography.labelMedium,
                            color = textColor,
                            fontWeight = if (tab.isModified) androidx.compose.ui.text.font.FontWeight.Bold
                            else androidx.compose.ui.text.font.FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        // Modified indicator (dot — AndroidIDE style).
                        if (tab.isModified) {
                            Text(
                                text = "●",
                                color = ideColors.tabModifiedIndicator,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }

                        // Close button.
                        if (onTabClosed != null) {
                            Spacer(Modifier.width(4.dp))
                            IconButton(
                                onClick = { onTabClosed(tab) },
                                modifier = Modifier.size(18.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close ${tab.title}",
                                    modifier = Modifier.size(12.dp),
                                    tint = textColor.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }

                    // Separator between tabs.
                    if (index < tabs.lastIndex) {
                        VerticalDivider(
                            modifier = Modifier.fillMaxHeight(0.5f),
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }

            // ── Context Menu (dropdown) ─────────────────────────
            DropdownMenu(
                expanded = showContextMenu,
                onDismissRequest = { showContextMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Close Tab") },
                    onClick = {
                        contextMenuTab?.let { onTabClosed?.invoke(it) }
                        showContextMenu = false
                    },
                    leadingIcon = { Icon(Icons.Default.Close, null, Modifier.size(18.dp)) }
                )
                DropdownMenuItem(
                    text = { Text("Close Others") },
                    onClick = {
                        contextMenuTab?.let { target ->
                            tabs.filter { it.id != target.id }.forEach { onTabClosed?.invoke(it) }
                        }
                        showContextMenu = false
                    },
                    leadingIcon = { Icon(Icons.Default.CloseFullscreen, null, Modifier.size(18.dp)) }
                )
                DropdownMenuItem(
                    text = { Text("Close All") },
                    onClick = {
                        tabs.forEach { onTabClosed?.invoke(it) }
                        showContextMenu = false
                    },
                    leadingIcon = { Icon(Icons.Default.DeleteSweep, null, Modifier.size(18.dp)) }
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Copy Path") },
                    onClick = {
                        // In production: clipboard copy tab.id
                        showContextMenu = false
                    },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, null, Modifier.size(18.dp)) }
                )
            }
        }
    }
}
