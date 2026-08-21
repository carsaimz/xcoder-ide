package com.xcoder.ide.ui.visual

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.draganddrop.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xcoder.ide.theme.*
import com.xcoder.ide.theme.LocalIdeColors
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
// Data model
// ---------------------------------------------------------------------------

/** A palette item that can be dragged onto the canvas. */
data class PaletteItem(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val category: PaletteCategory
)

enum class PaletteCategory(val label: String) {
    LAYOUT("Layout"),
    UI("UI Components"),
    LOGIC("Logic"),
    MEDIA("Media"),
    DATA("Data")
}

/** A block placed on the visual canvas. */
data class CanvasBlock(
    val id: String,
    val paletteItemId: String,
    val label: String,
    val x: Float,
    val y: Float,
    val width: Float = 120f,
    val height: Float = 56f,
    val color: Color = PurplePrimary // brand purple
)

/** A property row for the selected block. */
data class BlockProperty(
    val key: String,
    val value: String
)

// ---------------------------------------------------------------------------
// Palette items
// ---------------------------------------------------------------------------

private val paletteItems = listOf(
    PaletteItem("row", "Row", Icons.Default.ViewColumn, PaletteCategory.LAYOUT),
    PaletteItem("column", "Column", Icons.Default.ViewStream, PaletteCategory.LAYOUT),
    PaletteItem("box", "Box", Icons.Default.CropSquare, PaletteCategory.LAYOUT),
    PaletteItem("scroll", "ScrollView", Icons.Default.SwipeVertical, PaletteCategory.LAYOUT),
    PaletteItem("grid", "Grid", Icons.Default.GridOn, PaletteCategory.LAYOUT),
    PaletteItem("button", "Button", Icons.Default.SmartButton, PaletteCategory.UI),
    PaletteItem("text", "Text", Icons.Default.Title, PaletteCategory.UI),
    PaletteItem("image", "Image", Icons.Default.Image, PaletteCategory.UI),
    PaletteItem("input", "TextField", Icons.Default.Edit, PaletteCategory.UI),
    PaletteItem("switch", "Switch", Icons.Default.ToggleOn, PaletteCategory.UI),
    PaletteItem("checkbox", "Checkbox", Icons.Default.CheckBox, PaletteCategory.UI),
    PaletteItem("if", "If / Else", Icons.Default.CallSplit, PaletteCategory.LOGIC),
    PaletteItem("loop", "For Loop", Icons.Default.Repeat, PaletteCategory.LOGIC),
    PaletteItem("variable", "Variable", Icons.Default.DataObject, PaletteCategory.DATA),
    PaletteItem("api", "API Call", Icons.Default.Http, PaletteCategory.DATA),
    PaletteItem("storage", "Storage", Icons.Default.Storage, PaletteCategory.DATA),
    PaletteItem("video", "Video", Icons.Default.Videocam, PaletteCategory.MEDIA),
    PaletteItem("audio", "Audio", Icons.Default.Audiotrack, PaletteCategory.MEDIA),
)

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

/**
 * Visual / block-based editor screen.
 *
 * Three-panel layout:
 * - Left: **Palette** – categories of draggable blocks
 * - Center: **Canvas** – drop zone where blocks are arranged
 * - Right: **Property panel** – shows properties of the selected block
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisualEditorScreen(
    modifier: Modifier = Modifier
) {
    var canvasBlocks by remember {
        mutableStateOf(
            listOf(
                CanvasBlock("1", "column", "Column", 60f, 40f, color = Color(0xFF00B894)),
                CanvasBlock("2", "button", "Button", 220f, 120f, color = PurplePrimary),
                CanvasBlock("3", "text", "Text", 400f, 40f, color = LocalIdeColors.current.info),
                CanvasBlock("4", "image", "Image", 400f, 180f, color = LocalIdeColors.current.warning),
            )
        )
    }
    var selectedBlockId by remember { mutableStateOf<String?>("1") }
    var expandedCategory by remember { mutableStateOf(PaletteCategory.LAYOUT) }

    // Drag state
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var draggingId by remember { mutableStateOf<String?>(null) }

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // --- Left: Palette ----------------------------------------------
        Surface(
            modifier = Modifier.width(220.dp).fillMaxHeight(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            shadowElevation = 4.dp
        ) {
            Column {
                // Palette header
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Dashboard, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Palette", style = MaterialTheme.typography.titleSmall)
                }
                HorizontalDivider()

                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(vertical = 4.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    PaletteCategory.entries.forEach { category ->
                        item(key = "cat_${category.name}") {
                            PaletteCategoryHeader(
                                category = category,
                                expanded = expandedCategory == category,
                                onToggle = { expandedCategory = if (expandedCategory == category) null else category }
                            )
                        }
                        if (expandedCategory == category) {
                            items(
                                items = paletteItems.filter { it.category == category },
                                key = { it.id }
                            ) { item ->
                                PaletteItemRow(
                                    item = item,
                                    onClick = {
                                        val newBlock = CanvasBlock(
                                            id = System.currentTimeMillis().toString(),
                                            paletteItemId = item.id,
                                            label = item.label,
                                            x = 100f + (canvasBlocks.size % 5) * 140f,
                                            y = 100f + (canvasBlocks.size / 5) * 80f
                                        )
                                        canvasBlocks = canvasBlocks + newBlock
                                        selectedBlockId = newBlock.id
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- Center: Canvas ---------------------------------------------
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(LocalIdeColors.current.studioSurfaceDim)
        ) {
            // Grid dots
            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                val spacing = 24.dp.toPx()
                val dotRadius = 1.dp.toPx()
                val color = MaterialTheme.colorScheme.outlineVariant
                for (x in 0 until size.width.toInt() step spacing.toInt()) {
                    for (y in 0 until size.height.toInt() step spacing.toInt()) {
                        drawCircle(color = color, radius = dotRadius, center = Offset(x.toFloat(), y.toFloat()))
                    }
                }
            }

            // Blocks
            canvasBlocks.forEach { block ->
                val isSelected = block.id == selectedBlockId
                CanvasBlockView(
                    block = block,
                    isSelected = isSelected,
                    onSelect = { selectedBlockId = block.id },
                    onDrag = { dx, dy ->
                        canvasBlocks = canvasBlocks.map {
                            if (it.id == block.id) it.copy(x = it.x + dx, y = it.y + dy) else it
                        }
                    },
                    onDelete = {
                        canvasBlocks = canvasBlocks.filter { it.id != block.id }
                        if (selectedBlockId == block.id) selectedBlockId = null
                    }
                )
            }

            // Canvas toolbar
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SmallFloatingActionButton(
                    onClick = { canvasBlocks = canvasBlocks.map { it.copy(x = it.x - 10f) } },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Icon(Icons.Default.West, "Pan left", Modifier.size(16.dp))
                }
                Spacer(Modifier.width(4.dp))
                SmallFloatingActionButton(
                    onClick = { canvasBlocks = canvasBlocks.map { it.copy(x = it.x + 10f) } },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Icon(Icons.Default.East, "Pan right", Modifier.size(16.dp))
                }
                Spacer(Modifier.width(4.dp))
                SmallFloatingActionButton(
                    onClick = { canvasBlocks = canvasBlocks.map { it.copy(y = it.y - 10f) } },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Icon(Icons.Default.North, "Pan up", Modifier.size(16.dp))
                }
                Spacer(Modifier.width(4.dp))
                SmallFloatingActionButton(
                    onClick = { canvasBlocks = canvasBlocks.map { it.copy(y = it.y + 10f) } },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Icon(Icons.Default.South, "Pan down", Modifier.size(16.dp))
                }
            }
        }

        // --- Right: Property panel --------------------------------------
        val selectedBlock = canvasBlocks.find { it.id == selectedBlockId }
        AnimatedVisibility(
            visible = selectedBlock != null,
            enter = expandHorizontally() + fadeIn(),
            exit = shrinkHorizontally() + fadeOut()
        ) {
            Surface(
                modifier = Modifier.width(240.dp).fillMaxHeight(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                shadowElevation = 4.dp
            ) {
                if (selectedBlock != null) {
                    Column {
                        // Property panel header
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Tune, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("Properties", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { selectedBlockId = null }, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Default.Close, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        HorizontalDivider()

                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Block info
                            item {
                                Text("Block", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(4.dp))
                                Text(selectedBlock.label, style = MaterialTheme.typography.bodyLarge)
                            }
                            item { HorizontalDivider() }

                            // Position
                            item {
                                PropertyField("X", "${selectedBlock.x.roundToInt()}")
                                PropertyField("Y", "${selectedBlock.y.roundToInt()}")
                            }
                            item { HorizontalDivider() }

                            // Size
                            item {
                                PropertyField("Width", "${selectedBlock.width.roundToInt()}")
                                PropertyField("Height", "${selectedBlock.height.roundToInt()}")
                            }
                            item { HorizontalDivider() }

                            // Color
                            item {
                                Text("Color", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(selectedBlock.color)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("${selectedBlock.color}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            item { HorizontalDivider() }

                            // Actions
                            item {
                                OutlinedButton(
                                    onClick = { selectedBlockId = null },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Delete, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Delete Block")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Sub-composables
// ---------------------------------------------------------------------------

@Composable
private fun PaletteCategoryHeader(
    category: PaletteCategory,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
            null,
            Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(4.dp))
        Text(
            category.label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun PaletteItemRow(
    item: PaletteItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            item.icon, null,
            Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.tertiary
        )
        Spacer(Modifier.width(8.dp))
        Text(item.label, style = MaterialTheme.typography.bodySmall)
    }
    Spacer(Modifier.height(2.dp))
}

@Composable
private fun PropertyField(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(64.dp))
        Spacer(Modifier.width(8.dp))
        OutlinedTextField(
            value = value,
            onValueChange = { /* update property */ },
            modifier = Modifier.height(32.dp),
            textStyle = MaterialTheme.typography.bodySmall,
            singleLine = true
        )
    }
}

@Composable
private fun CanvasBlockView(
    block: CanvasBlock,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDrag: (dx: Float, dy: Float) -> Unit,
    onDelete: () -> Unit
) {
    var offset by remember { mutableStateOf(Offset(block.x, block.y)) }

    Box(
        modifier = Modifier
            .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
            .width(block.width.dp)
            .height(block.height.dp)
            .pointerInput(block.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { onSelect() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        offset += dragAmount
                        onDrag(dragAmount.x, dragAmount.y)
                    }
                )
            }
            .clickable(onClick = onSelect)
            .drawBehind {
                val strokeWidth = if (isSelected) 2.dp.toPx() else 1.dp.toPx()
                val strokeColor = if (isSelected) LocalIdeColors.current.primary else block.color.copy(alpha = 0.5f)
                drawRoundRect(
                    color = block.color.copy(alpha = 0.15f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx())
                )
                drawRoundRect(
                    color = strokeColor,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()),
                    style = Stroke(width = strokeWidth)
                )
            }
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                paletteItems.find { it.id == block.paletteItemId }?.icon ?: Icons.Default.Widget,
                null,
                Modifier.size(16.dp),
                tint = block.color
            )
            Spacer(Modifier.width(6.dp))
            Text(
                block.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }

    // Delete button when selected
    if (isSelected) {
        IconButton(
            onClick = onDelete,
            modifier = Modifier
                .offset { IntOffset((offset.x + block.width - 8).roundToInt(), (offset.y - 12).roundToInt()) }
                .size(20.dp)
                .background(LocalIdeColors.current.error, CircleShape)
        ) {
            Icon(Icons.Default.Close, null, Modifier.size(12.dp), tint = Color.White)
        }
    }
}
