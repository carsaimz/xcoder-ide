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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xcoder.ide.theme.LocalIdeColors
import kotlin.math.roundToInt

// ==========================================================================
//  Data Model
// ==========================================================================

/** A palette item that can be dragged onto the canvas. */
data class PaletteItem(
    val id: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val category: PaletteCategory
)

enum class PaletteCategory(val label: String) {
    LAYOUT("Layouts"),
    WIDGETS("Widgets"),
    TEXT("Text"),
    MEDIA("Media"),
    LOGIC("Logic")
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
    val color: Color = Color(0xFF6B5CE7),
    val properties: Map<String, String> = emptyMap()
)

// ==========================================================================
//  Palette Items (30+ widgets — Sketchware-IA pattern)
// ==========================================================================

private val paletteItems = listOf(
    // ── Layouts ──────────────────────────────────────────────
    PaletteItem("linear_v", "LinearLayout (V)", Icons.Default.ViewStream, PaletteCategory.LAYOUT),
    PaletteItem("linear_h", "LinearLayout (H)", Icons.Default.ViewColumn, PaletteCategory.LAYOUT),
    PaletteItem("constraint", "ConstraintLayout", Icons.Default.Dashboard, PaletteCategory.LAYOUT),
    PaletteItem("frame", "FrameLayout", Icons.Default.CropSquare, PaletteCategory.LAYOUT),
    PaletteItem("scroll_v", "ScrollView", Icons.Default.SwipeVertical, PaletteCategory.LAYOUT),
    PaletteItem("scroll_h", "HorizontalScroll", Icons.Default.SwipeHorizontal, PaletteCategory.LAYOUT),
    PaletteItem("grid", "GridLayout", Icons.Default.GridOn, PaletteCategory.LAYOUT),
    PaletteItem("card", "CardView", Icons.Default.Style, PaletteCategory.LAYOUT),
    // ── Widgets ─────────────────────────────────────────────
    PaletteItem("button", "Button", Icons.Default.SmartButton, PaletteCategory.WIDGETS),
    PaletteItem("image_button", "ImageButton", Icons.Default.SmartButton, PaletteCategory.WIDGETS),
    PaletteItem("floating_btn", "FloatingBtn", Icons.Default.AddCircle, PaletteCategory.WIDGETS),
    PaletteItem("switch", "Switch", Icons.Default.ToggleOn, PaletteCategory.WIDGETS),
    PaletteItem("checkbox", "CheckBox", Icons.Default.CheckBox, PaletteCategory.WIDGETS),
    PaletteItem("radio", "RadioButton", Icons.Default.RadioButton, PaletteCategory.WIDGETS),
    PaletteItem("slider", "Slider", Icons.Default.LinearScale, PaletteCategory.WIDGETS),
    PaletteItem("progress", "ProgressBar", Icons.Default.LinearScale, PaletteCategory.WIDGETS),
    PaletteItem("seekbar", "SeekBar", Icons.Default.LinearScale, PaletteCategory.WIDGETS),
    PaletteItem("spinner", "Spinner", Icons.Default.ArrowDropDown, PaletteCategory.WIDGETS),
    PaletteItem("recycler", "RecyclerView", Icons.Default.ViewList, PaletteCategory.WIDGETS),
    PaletteItem("viewpager", "ViewPager", Icons.Default.Swipe, PaletteCategory.WIDGETS),
    // ── Text ─────────────────────────────────────────────────
    PaletteItem("textview", "TextView", Icons.Default.Title, PaletteCategory.TEXT),
    PaletteItem("edittext", "EditText", Icons.Default.Edit, PaletteCategory.TEXT),
    PaletteItem("autocomplete", "AutoComplete", Icons.Default.Edit, PaletteCategory.TEXT),
    PaletteItem("textinput", "TextInputLayout", Icons.Default.EditNote, PaletteCategory.TEXT),
    // ── Media ────────────────────────────────────────────────
    PaletteItem("imageview", "ImageView", Icons.Default.Image, PaletteCategory.MEDIA),
    PaletteItem("videoview", "VideoView", Icons.Default.Videocam, PaletteCategory.MEDIA),
    PaletteItem("webview", "WebView", Icons.Default.Language, PaletteCategory.MEDIA),
    // ── Logic ────────────────────────────────────────────────
    PaletteItem("if_block", "If / Else", Icons.Default.CallSplit, PaletteCategory.LOGIC),
    PaletteItem("loop", "For Loop", Icons.Default.Repeat, PaletteCategory.LOGIC),
    PaletteItem("variable", "Variable", Icons.Default.DataObject, PaletteCategory.LOGIC),
    PaletteItem("event", "Event", Icons.Default.Bolt, PaletteCategory.LOGIC),
    PaletteItem("timer", "Timer", Icons.Default.Timer, PaletteCategory.LOGIC),
    PaletteItem("dialog", "Dialog", Icons.Default.DoorSliding, PaletteCategory.LOGIC),
)

// ==========================================================================
//  Screen
// ==========================================================================

/**
 * Visual / block-based editor screen.
 *
 * Based on Sketchware-IA's `ViewEditor` and `design.xml` layout:
 * - **Left panel** (30dp equivalent): Widget palette with Layouts + Widgets
 * - **Center**: Canvas area showing the layout preview (AndroidView with real widgets)
 * - **Bottom sheet**: Property panel that slides up when a widget is selected
 * - **Top bar**: XML/Design toggle, file name, zoom controls
 *
 * In the real implementation the canvas would host an AndroidView inflating
 * real Android views, matching Sketchware-IA's approach of inflating
 * widgets onto a design surface.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisualEditorScreen(
    modifier: Modifier = Modifier
) {
    val ideColors = LocalIdeColors.current

    // ── State ──────────────────────────────────────────────────
    var isDesignMode by remember { mutableStateOf(true) }
    var fileName by remember { mutableStateOf("activity_main.xml") }
    var zoomLevel by remember { mutableFloatStateOf(1.0f) }
    var showPropertySheet by remember { mutableStateOf(false) }
    var selectedBlockId by remember { mutableStateOf<String?>(null) }
    var expandedCategory by remember { mutableStateOf(PaletteCategory.LAYOUT) }

    var canvasBlocks by remember {
        mutableStateOf(
            listOf(
                CanvasBlock(
                    id = "1", paletteItemId = "linear_v", label = "LinearLayout (V)",
                    x = 40f, y = 40f, width = 340f, height = 480f,
                    color = Color(0xFF00B894)
                ),
                CanvasBlock(
                    id = "2", paletteItemId = "textview", label = "TextView",
                    x = 60f, y = 80f, width = 300f, height = 48f,
                    color = Color(0xFF6B5CE7)
                ),
                CanvasBlock(
                    id = "3", paletteItemId = "edittext", label = "EditText",
                    x = 60f, y = 160f, width = 300f, height = 48f,
                    color = Color(0xFF6B5CE7)
                ),
                CanvasBlock(
                    id = "4", paletteItemId = "button", label = "Button",
                    x = 60f, y = 240f, width = 300f, height = 48f,
                    color = Color(0xFF6B5CE7)
                ),
            )
        )
    }

    val selectedBlock = canvasBlocks.find { it.id == selectedBlockId }
    LaunchedEffect(selectedBlock) { showPropertySheet = selectedBlock != null }

    // ── Bottom sheet state ─────────────────────────────────────
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = false,
        confirmValueChange = { newValue ->
            if (newValue == SheetValue.Hidden) {
                selectedBlockId = null
                showPropertySheet = false
            }
            true
        }
    )

    LaunchedEffect(showPropertySheet) {
        if (showPropertySheet) {
            sheetState.show()
        } else {
            sheetState.hide()
        }
    }

    // ── Layout ─────────────────────────────────────────────────
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ── Top bar (XML/Design toggle, filename, zoom) ─────────
        VisualEditorTopBar(
            fileName = fileName,
            isDesignMode = isDesignMode,
            onToggleMode = { isDesignMode = !isDesignMode },
            zoomLevel = zoomLevel,
            onZoomIn = { zoomLevel = (zoomLevel + 0.1f).coerceAtMost(3.0f) },
            onZoomOut = { zoomLevel = (zoomLevel - 0.1f).coerceAtLeast(0.3f) }
        )

        // ── Main content (XML or Design) ────────────────────────
        if (isDesignMode) {
            Row(modifier = Modifier.weight(1f)) {
                // Left: Widget palette (narrow, scrollable)
                Surface(
                    modifier = Modifier
                        .width(180.dp)
                        .fillMaxHeight(),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    shadowElevation = 2.dp
                ) {
                    WidgetPalette(
                        expandedCategory = expandedCategory,
                        onToggleCategory = { expandedCategory = it },
                        onItemClicked = { item ->
                            val newBlock = CanvasBlock(
                                id = System.currentTimeMillis().toString(),
                                paletteItemId = item.id,
                                label = item.label,
                                x = 80f + (canvasBlocks.size % 4) * 160f,
                                y = 80f + (canvasBlocks.size / 4) * 80f,
                                color = MaterialTheme.colorScheme.primary
                            )
                            canvasBlocks = canvasBlocks + newBlock
                            selectedBlockId = newBlock.id
                        }
                    )
                }

                // Center: Canvas
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(ideColors.studioSurfaceDim)
                ) {
                    // Grid dots (Sketchware-IA canvas grid)
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val spacing = 24.dp.toPx() / zoomLevel
                        val dotRadius = 1.dp.toPx()
                        val color = MaterialTheme.colorScheme.outlineVariant
                        for (x in 0 until size.width.toInt() step spacing.toInt()) {
                            for (y in 0 until size.height.toInt() step spacing.toInt()) {
                                drawCircle(color = color, radius = dotRadius, center = Offset(x.toFloat(), y.toFloat()))
                            }
                        }
                    }

                    // Canvas blocks (widgets placed on design surface)
                    canvasBlocks.forEach { block ->
                        val isSelected = block.id == selectedBlockId
                        CanvasBlockView(
                            block = block,
                            isSelected = isSelected,
                            onSelect = { selectedBlockId = block.id },
                            onDrag = { dx, dy ->
                                canvasBlocks = canvasBlocks.map {
                                    if (it.id == block.id) it.copy(x = it.x + dx, y = it.y + dy)
                                    else it
                                }
                            },
                            onDelete = {
                                canvasBlocks = canvasBlocks.filter { it.id != block.id }
                                if (selectedBlockId == block.id) selectedBlockId = null
                            }
                        )
                    }

                    // Canvas toolbar (bottom-right)
                    CanvasToolbar(
                        onPanLeft = { canvasBlocks = canvasBlocks.map { it.copy(x = it.x - 10f) } },
                        onPanRight = { canvasBlocks = canvasBlocks.map { it.copy(x = it.x + 10f) } },
                        onPanUp = { canvasBlocks = canvasBlocks.map { it.copy(y = it.y - 10f) } },
                        onPanDown = { canvasBlocks = canvasBlocks.map { it.copy(y = it.y + 10f) } }
                    )
                }
            }
        } else {
            // ── XML source view ────────────────────────────────
            XmlSourceView(
                xmlContent = generateXml(canvasBlocks)
            )
        }
    }

    // ── Property bottom sheet ───────────────────────────────────
    if (selectedBlock != null && showPropertySheet) {
        ModalBottomSheet(
            onDismissRequest = {
                selectedBlockId = null
                showPropertySheet = false
            },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            PropertySheetContent(
                block = selectedBlock,
                onPropertyChange = { key, value ->
                    canvasBlocks = canvasBlocks.map {
                        if (it.id == selectedBlock.id) it.copy(
                            properties = it.properties + (key to value)
                        ) else it
                    }
                },
                onDelete = {
                    canvasBlocks = canvasBlocks.filter { it.id != selectedBlock.id }
                    selectedBlockId = null
                    showPropertySheet = false
                }
            )
        }
    }
}

// ==========================================================================
//  Top Bar
// ==========================================================================

@Composable
private fun VisualEditorTopBar(
    fileName: String,
    isDesignMode: Boolean,
    onToggleMode: () -> Unit,
    zoomLevel: Float,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // File name.
            Text(
                text = fileName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )

            Spacer(Modifier.weight(1f))

            // Zoom controls.
            IconButton(onClick = onZoomOut, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.ZoomOut, "Zoom out", modifier = Modifier.size(18.dp))
            }
            Text(
                text = "${(zoomLevel * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.width(40.dp)
            )
            IconButton(onClick = onZoomIn, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.ZoomIn, "Zoom in", modifier = Modifier.size(18.dp))
            }

            // XML / Design toggle.
            SegmentedButton(
                options = listOf("Design", "XML"),
                selectedIndex = if (isDesignMode) 0 else 1,
                onSelect = onToggleMode
            )
        }
    }
}

@Composable
private fun SegmentedButton(
    options: List<String>,
    selectedIndex: Int,
    onSelect: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
        ) {
            options.forEachIndexed { index, option ->
                val selected = index == selectedIndex
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else Color.Transparent,
                    modifier = Modifier
                        .clickable { onSelect() }
                        .padding(2.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = option,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// ==========================================================================
//  Widget Palette (left panel — Sketchware-IA pattern)
// ==========================================================================

@Composable
private fun WidgetPalette(
    expandedCategory: PaletteCategory?,
    onToggleCategory: (PaletteCategory?) -> Unit,
    onItemClicked: (PaletteItem) -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Dashboard, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(6.dp))
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
                        onToggle = { onToggleCategory(if (expandedCategory == category) null else category) }
                    )
                }
                if (expandedCategory == category) {
                    items(
                        items = paletteItems.filter { it.category == category },
                        key = { it.id }
                    ) { item ->
                        PaletteItemRow(item = item, onClick = { onItemClicked(item) })
                    }
                }
            }
        }
    }
}

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
            null, Modifier.size(16.dp),
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
private fun PaletteItemRow(item: PaletteItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(item.icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.tertiary)
        Spacer(Modifier.width(8.dp))
        Text(item.label, style = MaterialTheme.typography.bodySmall)
    }
    Spacer(Modifier.height(2.dp))
}

// ==========================================================================
//  Canvas Block View
// ==========================================================================

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
            .offset { androidx.compose.ui.unit.IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
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
                    cornerRadius = CornerRadius(8.dp.toPx())
                )
                drawRoundRect(
                    color = strokeColor,
                    cornerRadius = CornerRadius(8.dp.toPx()),
                    style = Stroke(width = strokeWidth)
                )
            }
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                paletteItems.find { it.id == block.paletteItemId }?.icon ?: Icons.Default.Widget,
                null, Modifier.size(16.dp), tint = block.color
            )
            Spacer(Modifier.width(6.dp))
            Text(
                block.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }

    // Delete button when selected.
    if (isSelected) {
        IconButton(
            onClick = onDelete,
            modifier = Modifier
                .offset {
                    androidx.compose.ui.unit.IntOffset(
                        (offset.x + block.width - 8).roundToInt(),
                        (offset.y - 12).roundToInt()
                    )
                }
                .size(20.dp)
                .background(LocalIdeColors.current.error, CircleShape)
        ) {
            Icon(Icons.Default.Close, null, Modifier.size(12.dp), tint = Color.White)
        }
    }
}

// ==========================================================================
//  Canvas Toolbar
// ==========================================================================

@Composable
private fun CanvasToolbar(
    onPanLeft: () -> Unit, onPanRight: () -> Unit,
    onPanUp: () -> Unit, onPanDown: () -> Unit
) {
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
            onClick = onPanLeft,
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ) { Icon(Icons.Default.West, "Pan left", Modifier.size(16.dp)) }
        Spacer(Modifier.width(4.dp))
        SmallFloatingActionButton(
            onClick = onPanRight,
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ) { Icon(Icons.Default.East, "Pan right", Modifier.size(16.dp)) }
        Spacer(Modifier.width(4.dp))
        SmallFloatingActionButton(
            onClick = onPanUp,
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ) { Icon(Icons.Default.North, "Pan up", Modifier.size(16.dp)) }
        Spacer(Modifier.width(4.dp))
        SmallFloatingActionButton(
            onClick = onPanDown,
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ) { Icon(Icons.Default.South, "Pan down", Modifier.size(16.dp)) }
    }
}

// ==========================================================================
//  Property Sheet Content
// ==========================================================================

@Composable
private fun PropertySheetContent(
    block: CanvasBlock,
    onPropertyChange: (key: String, value: String) -> Unit,
    onDelete: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        // Header.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Tune, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text("Properties — ${block.label}", style = MaterialTheme.typography.titleSmall)
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Property rows.
        Column(modifier = Modifier.verticalScroll(scrollState)) {
            PropertyField("ID", block.properties["id"] ?: "", "id", onPropertyChange)
            PropertyField("Width", "${block.width.roundToInt()}dp", "width", onPropertyChange)
            PropertyField("Height", "${block.height.roundToInt()}dp", "height", onPropertyChange)
            PropertyField("Text", block.properties["text"] ?: block.label, "text", onPropertyChange)
            PropertyField("Hint", block.properties["hint"] ?: "", "hint", onPropertyChange)
            PropertyField("Padding", "16dp", "padding", onPropertyChange)
            PropertyField("Margin", "0dp", "margin", onPropertyChange)
            PropertyField("Background", "@android:color/white", "background", onPropertyChange)
            PropertyField("Text Size", "14sp", "textSize", onPropertyChange)
            PropertyField("Text Color", "@android:color/black", "textColor", onPropertyChange)
            PropertyField("Visibility", "visible", "visibility", onPropertyChange)
            PropertyField("Clickable", "true", "clickable", onPropertyChange)
        }

        Spacer(Modifier.height(12.dp))

        // Delete button.
        OutlinedButton(
            onClick = onDelete,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Delete, null, Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Delete Widget")
        }

        // Bottom spacer for bottom sheet drag handle.
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun PropertyField(
    label: String,
    value: String,
    key: String,
    onPropertyChange: (String, String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp)
        )
        Spacer(Modifier.width(8.dp))
        OutlinedTextField(
            value = value,
            onValueChange = { onPropertyChange(key, it) },
            modifier = Modifier.height(36.dp),
            textStyle = MaterialTheme.typography.bodySmall,
            singleLine = true
        )
    }
}

// ==========================================================================
//  XML Source View
// ==========================================================================

@Composable
private fun XmlSourceView(xmlContent: String) {
    val ideColors = LocalIdeColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ideColors.editorBackground)
            .padding(16.dp)
    ) {
        Text(
            xmlContent,
            style = com.xcoder.ide.theme.CodeTypography.editorBody,
            color = ideColors.editorForeground
        )
    }
}

/**
 * Generates XML from canvas blocks.
 * In production, [com.xcoder.visual.codegen.XmlGenerator] handles this.
 */
private fun generateXml(blocks: List<CanvasBlock>): String {
    val sb = StringBuilder()
    sb.appendLine("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
    sb.appendLine("<LinearLayout")
    sb.appendLine("    xmlns:android=\"http://schemas.android.com/apk/res/android\"")
    sb.appendLine("    android:layout_width=\"match_parent\"")
    sb.appendLine("    android:layout_height=\"match_parent\"")
    sb.appendLine("    android:orientation=\"vertical\"")
    sb.appendLine(">")
    blocks.forEach { block ->
        sb.appendLine("    <${xmlTag(block.paletteItemId)}")
        sb.appendLine("        android:id=\"@+id/${block.properties["id"] ?: block.id}\"")
        sb.appendLine("        android:layout_width=\"${block.width.roundToInt()}dp\"")
        sb.appendLine("        android:layout_height=\"${block.height.roundToInt()}dp\"")
        if (block.properties.containsKey("text")) {
            sb.appendLine("        android:text=\"${block.properties["text"]}\"")
        }
        sb.appendLine("        />")
    }
    sb.appendLine("</LinearLayout>")
    return sb.toString()
}

private fun xmlTag(paletteId: String): String = when (paletteId) {
    "textview" -> "TextView"
    "edittext" -> "EditText"
    "button", "image_button" -> "Button"
    "imageview" -> "ImageView"
    "linear_v", "linear_h" -> "LinearLayout"
    "constraint" -> "ConstraintLayout"
    "frame" -> "FrameLayout"
    "scroll_v" -> "ScrollView"
    "webview" -> "WebView"
    else -> "View"
}
