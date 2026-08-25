/**
 * Compose widget palette for the visual editor.
 *
 * Based on Sketchware-IA's PaletteWidget (286 lines).
 * Provides two main sections:
 * - Layouts: LinearLayout, RelativeLayout, ConstraintLayout, FrameLayout, ScrollView
 * - Widgets: Button, TextView, EditText, ImageView, CheckBox, RadioButton, Switch, ProgressBar, SeekBar, Spinner, WebView
 *
 * Each widget is shown as icon + label. Clicking adds the widget to the canvas.
 * Drag support is provided via the [dragAndDropSource] modifier.
 */
package com.xcoder.visual

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xcoder.visual.model.ViewBean

// ── Palette item data ────────────────────────────────────────────────

/** A single item shown in the widget palette. */
data class PaletteItem(
    val label: String,
    val icon: ImageVector,
    val viewType: Int,
    val isLayout: Boolean = false
)

/** A section header + list of palette items. */
data class PaletteSection(
    val title: String,
    val items: List<PaletteItem>
)

// ── Palette data ────────────────────────────────────────────────────

/**
 * All available palette items, organized into two sections.
 * Based on Sketchware-IA's widget registry.
 */
object PaletteData {
    val LAYOUTS = PaletteSection(
        title = "Layouts",
        items = listOf(
            PaletteItem("LinearLayout", Icons.Default.ViewList, ViewBean.TYPE_LINEAR_LAYOUT, isLayout = true),
            PaletteItem("RelativeLayout", Icons.Default.Widgets, ViewBean.TYPE_RELATIVE_LAYOUT, isLayout = true),
            PaletteItem("ConstraintLayout", Icons.Default.Dashboard, ViewBean.TYPE_CONSTRAINT_LAYOUT, isLayout = true),
            // TODO: fix - unresolved reference: Icons.Default.Stack
            PaletteItem("FrameLayout", Icons.Default.Layers, ViewBean.TYPE_FRAME_LAYOUT, isLayout = true),
            PaletteItem("ScrollView", Icons.Default.ArrowDownward, ViewBean.TYPE_SCROLL_VIEW, isLayout = true),
            PaletteItem("RecyclerView", Icons.Default.GridView, ViewBean.TYPE_RECYCLER_VIEW, isLayout = true),
            PaletteItem("CoordinatorLayout", Icons.Default.Layers, ViewBean.TYPE_COORDINATOR_LAYOUT, isLayout = true),
            PaletteItem("CardView", Icons.Default.CreditCard, ViewBean.TYPE_CARD_VIEW, isLayout = true)
        )
    )

    val WIDGETS = PaletteSection(
        title = "Widgets",
        items = listOf(
            PaletteItem("Button", Icons.Default.RadioButtonUnchecked, ViewBean.TYPE_BUTTON),
            PaletteItem("TextView", Icons.Default.Title, ViewBean.TYPE_TEXTVIEW),
            PaletteItem("EditText", Icons.Default.Edit, ViewBean.TYPE_EDITTEXT),
            PaletteItem("ImageView", Icons.Default.Image, ViewBean.TYPE_IMAGEVIEW),
            PaletteItem("CheckBox", Icons.Default.CheckBox, ViewBean.TYPE_CHECKBOX),
            PaletteItem("RadioButton", Icons.Default.RadioButtonChecked, ViewBean.TYPE_RADIOBUTTON),
            PaletteItem("Switch", Icons.Default.ToggleOn, ViewBean.TYPE_SWITCH),
            PaletteItem("ProgressBar", Icons.Default.LinearScale, ViewBean.TYPE_PROGRESS_BAR),
            PaletteItem("SeekBar", Icons.Default.Tune, ViewBean.TYPE_SEEK_BAR),
            PaletteItem("Spinner", Icons.Default.ArrowDropDownCircle, ViewBean.TYPE_SPINNER),
            PaletteItem("WebView", Icons.Default.Language, ViewBean.TYPE_WEBVIEW),
            PaletteItem("VideoView", Icons.Default.Videocam, ViewBean.TYPE_VIDEO_VIEW),
            PaletteItem("RatingBar", Icons.Default.Star, ViewBean.TYPE_RATING_BAR),
            PaletteItem("ToggleButton", Icons.Default.ToggleOff, ViewBean.TYPE_TOGGLE_BUTTON),
            PaletteItem("ImageButton", Icons.Default.TouchApp, ViewBean.TYPE_IMAGE_BUTTON)
        )
    )

    val ALL_SECTIONS = listOf(LAYOUTS, WIDGETS)
}

// ── Composable Palette Screen ───────────────────────────────────────

@Composable
fun PaletteScreen(
    onWidgetSelected: (viewType: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        for (section in PaletteData.ALL_SECTIONS) {
            item(key = "header_${section.title}") {
                Text(
                    section.title,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }
            items(
                items = section.items,
                key = { "${section.title}_${it.label}" }
            ) { item ->
                PaletteWidgetItem(
                    item = item,
                    onClick = { onWidgetSelected(item.viewType) }
                )
            }
        }
    }
}

// ── Individual palette widget item ──────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PaletteWidgetItem(
    item: PaletteItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (item.isLayout) {
        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
    } else {
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
    }

    val iconTint = if (item.isLayout) {
        MaterialTheme.colorScheme.onTertiaryContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .clickable { onClick() }
            .dragAndDropSource {
                detectTapGestures(
                    onPress = { offset ->
                        startTransfer(
                            DragAndDropTransferData(
                                clipData = android.content.ClipData.newPlainText(
                                    "widget_type", item.viewType.toString()
                                ),
                                localState = item
                            )
                        )
                    }
                )
            }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.label,
            tint = iconTint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = item.label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Start
        )
    }
}
