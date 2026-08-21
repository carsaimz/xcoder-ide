package com.xcoder.ide.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import com.xcoder.ide.theme.CodeTypography
import com.xcoder.ide.theme.TealAccent
import com.xcoder.ide.theme.TextSecondaryDark

/**
 * Data model representing a single open editor tab.
 *
 * @param id Stable identifier for the tab (e.g. file path hash).
 * @param title Display title (file name).
 * @param subtitle Optional subtitle (directory, language).
 * @param isModified Whether the file has unsaved changes.
 * @param isActive Whether this tab is currently in the foreground.
 * @param icon Optional leading icon.
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
 * @param tabs Current list of open tabs.
 * @param onTabSelected Callback when a tab is clicked.
 * @param onTabClosed Callback when the close icon is clicked. Pass `null` to hide close buttons.
 * @param modifier Modifier applied to the tab bar container.
 * @param onTabReorder Optional drag-and-drop reorder callback.
 */
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

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp),
        color = surfaceColor,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(scrollState)
                .clipToBounds(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { index, tab ->
                val selectedBackgroundColor by animateColorAsState(
                    targetValue = if (tab.isActive) activeColor else Color.Transparent,
                    animationSpec = MaterialTheme.typography.animationSpec(),
                    label = "tabBg"
                )
                val textColor by animateColorAsState(
                    targetValue = if (tab.isActive) activeTextColor else inactiveTextColor,
                    animationSpec = MaterialTheme.typography.animationSpec(),
                    label = "tabText"
                )

                Row(
                    modifier = Modifier
                        .fillMaxHeight()
                        .widthIn(min = 100.dp, max = 200.dp)
                        .background(color = selectedBackgroundColor)
                        .clickable { onTabSelected(tab) }
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Leading icon
                    tab.icon?.invoke()
                    if (tab.icon == null) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = textColor
                        )
                        Spacer(Modifier.width(6.dp))
                    }

                    // Title
                    Text(
                        text = tab.title,
                        style = MaterialTheme.typography.labelMedium,
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    // Modified indicator
                    if (tab.isModified) {
                        Text(
                            text = "●",
                            color = TealAccent,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }

                    // Close button
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

                // Separator between tabs
                if (index < tabs.lastIndex) {
                    VerticalDivider(
                        modifier = Modifier.fillMaxHeight(0.5f),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }
        }
    }
}

/** Extension for consistent animation specs. */
private fun androidx.compose.material3.Typography.animationSpec() = 
    androidx.compose.animation.core.tween(durationMillis = 150)
