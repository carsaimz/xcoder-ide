package com.xcoder.visual

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

// Data classes representing the visual block graph

enum class PortType { STRING, NUMBER, BOOLEAN, OBJECT, ANY }

data class PortDef(val name: String, val type: PortType)

@Suppress("unused")
data class BlockDefinition(
    val name: String,
    val color: Color,
    val inputs: List<PortDef> = emptyList(),
    val outputs: List<PortDef> = emptyList(),
    val defaultValues: Map<String, String> = emptyMap()
)

@Suppress("unused")
data class PlacedBlock(
    val id: String = "",
    val definition: BlockDefinition,
    val x: Float = 0f,
    val y: Float = 0f,
    val values: Map<String, String> = emptyMap(),
    val connections: List<BlockConnection> = emptyList()
)

@Suppress("unused")
data class BlockConnection(
    val fromBlockId: String,
    val fromPortIndex: Int,
    val toBlockId: String,
    val toPortIndex: Int
)

@Suppress("unused")
data class VisualProject(
    val name: String = "Untitled",
    val blocks: List<PlacedBlock> = emptyList(),
    val connections: List<BlockConnection> = emptyList(),
    val layoutXml: String = "",
    val generatedCode: String = ""
)

// Undo/Redo stack
@Suppress("unused")
class UndoRedoStack<T>(private val maxSize: Int = 50) {
    private val undoStack = mutableListOf<T>()
    private val redoStack = mutableListOf<T>()

    fun push(state: T) {
        undoStack.add(state)
        if (undoStack.size > maxSize) undoStack.removeAt(0)
        redoStack.clear()
    }

    fun undo(current: T): T? {
        if (undoStack.isEmpty()) return null
        redoStack.add(current)
        return undoStack.removeAt(undoStack.lastIndex)
    }

    fun redo(current: T): T? {
        if (redoStack.isEmpty()) return null
        undoStack.add(current)
        return redoStack.removeAt(redoStack.lastIndex)
    }

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
}

// ViewModel-like state holder
@Suppress("unused")
class BlockEditorState {
    private val _blocks = mutableStateOf<List<PlacedBlock>>(emptyList())
    val blocks: State<List<PlacedBlock>> = _blocks

    private val _connections = mutableStateOf<List<BlockConnection>>(emptyList())
    val connections: State<List<BlockConnection>> = _connections

    private val _selectedBlockId = mutableStateOf<String?>(null)
    val selectedBlockId: State<String?> = _selectedBlockId

    private val _cameraOffset = mutableStateOf(Offset.Zero)
    val cameraOffset: State<Offset> = _cameraOffset

    private val _zoom = mutableStateOf(1f)
    val zoom: State<Float> = _zoom

    val undoRedo = UndoRedoStack<List<PlacedBlock>>()

    private var blockCounter = 0L

    fun addBlock(definition: BlockDefinition, x: Float, y: Float) {
        blockCounter++
        val block = PlacedBlock(
            id = "block_${blockCounter}",
            definition = definition,
            x = x,
            y = y,
            values = definition.defaultValues
        )
        undoRedo.push(_blocks.value)
        _blocks.value = _blocks.value + block
        _selectedBlockId.value = block.id
    }

    fun removeBlock(blockId: String) {
        undoRedo.push(_blocks.value)
        _blocks.value = _blocks.value.filter { it.id != blockId }
        _connections.value = _connections.value.filter {
            it.fromBlockId != blockId && it.toBlockId != blockId
        }
        if (_selectedBlockId.value == blockId) {
            _selectedBlockId.value = null
        }
    }

    fun selectBlock(blockId: String?) {
        _selectedBlockId.value = blockId
    }

    fun moveBlock(blockId: String, dx: Float, dy: Float) {
        _blocks.value = _blocks.value.map {
            if (it.id == blockId) it.copy(x = it.x + dx, y = it.y + dy) else it
        }
    }

    fun updateBlockValue(blockId: String, key: String, value: String) {
        _blocks.value = _blocks.value.map {
            if (it.id == blockId) it.copy(values = it.values + (key to value)) else it
        }
    }

    fun addConnection(connection: BlockConnection) {
        _connections.value = _connections.value + connection
    }

    fun removeConnection(fromBlockId: String, fromPortIndex: Int) {
        _connections.value = _connections.value.filterNot {
            it.fromBlockId == fromBlockId && it.fromPortIndex == fromPortIndex
        }
    }

    fun undo() {
        val state = undoRedo.undo(_blocks.value) ?: return
        _blocks.value = state
    }

    fun redo() {
        val state = undoRedo.redo(_blocks.value) ?: return
        _blocks.value = state
    }

    fun panCamera(dx: Float, dy: Float) {
        _cameraOffset.value = _cameraOffset.value + Offset(dx, dy)
    }

    fun setZoom(newZoom: Float) {
        _zoom.value = newZoom.coerceIn(0.3f, 3f)
    }

    fun clear() {
        undoRedo.push(_blocks.value)
        _blocks.value = emptyList()
        _connections.value = emptyList()
        _selectedBlockId.value = null
    }

    fun getSelectedBlock(): PlacedBlock? =
        _blocks.value.find { it.id == _selectedBlockId.value }
}

@Composable
fun BlockEditorScreen(
    state: BlockEditorState = remember { BlockEditorState() },
    onGenerateCode: (VisualProject) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showPalette by remember { mutableStateOf(true) }
    var showProperties by remember { mutableStateOf(false) }

    Row(modifier = modifier.fillMaxSize()) {
        // Left palette panel
        if (showPalette) {
            Surface(
                modifier = Modifier.width(260.dp).fillMaxHeight(),
                tonalElevation = 2.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                PaletteScreen(
                    onWidgetSelected = { viewType ->
                        val def = BlockDefinition(
                            name = "Block_$viewType",
                            color = Color(0xFF4CAF50)
                        )
                        state.addBlock(def, 100f, 100f)
                    }
                )
            }
        }

        // Center canvas
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            BlockCanvas(
                state = state,
                modifier = Modifier.fillMaxSize()
            )

            // Toolbar
            Row(
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = { showPalette = !showPalette }) {
                    Icon(Icons.Default.ViewSidebar, "Toggle palette")
                }
                IconButton(onClick = { state.undo() }, enabled = state.undoRedo.canUndo) {
                    Icon(Icons.Default.Undo, "Undo")
                }
                IconButton(onClick = { state.redo() }, enabled = state.undoRedo.canRedo) {
                    Icon(Icons.Default.Redo, "Redo")
                }
                IconButton(onClick = { state.clear() }) {
                    Icon(Icons.Default.DeleteSweep, "Clear all")
                }
                IconButton(onClick = { state.setZoom(state.zoom.value + 0.1f) }) {
                    Icon(Icons.Default.ZoomIn, "Zoom in")
                }
                IconButton(onClick = { state.setZoom(state.zoom.value - 0.1f) }) {
                    Icon(Icons.Default.ZoomOut, "Zoom out")
                }
                IconButton(onClick = { state.setZoom(1f) }) {
                    Icon(Icons.Default.CenterFocusStrong, "Reset zoom")
                }
            }

            // Generate button
            FloatingActionButton(
                onClick = {
                    val project = VisualProject(
                        blocks = state.blocks.value,
                        connections = state.connections.value
                    )
                    onGenerateCode(project)
                },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.PlayArrow, "Generate code")
            }
        }

        // Right properties panel
        if (showProperties) {
            Surface(
                modifier = Modifier.width(280.dp).fillMaxHeight(),
                tonalElevation = 2.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                PropertyPanel(
                    selectedView = null,
                    onPropertyChange = OnPropertyChangeListener { _, _, _ -> },
                    onDeleteView = { blockId -> state.removeBlock(blockId) }
                )
            }
        }
    }
}

@Composable
fun BlockCanvas(
    state: BlockEditorState,
    modifier: Modifier = Modifier
) {
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragBlockId by remember { mutableStateOf<String?>(null) }
    var dragBlockStart by remember { mutableStateOf<Offset?>(null) }

    Canvas(
        modifier = modifier
            .background(Color(0xFF1E1E2E))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        // Check if clicking on a block
                        val scaledOffset = (offset - state.cameraOffset.value) / state.zoom.value
                        val clickedBlock = state.blocks.value.findLast { block ->
                            abs(block.x - scaledOffset.x) < 100f && abs(block.y - scaledOffset.y) < 40f
                        }
                        if (clickedBlock != null) {
                            dragBlockId = clickedBlock.id
                            dragBlockStart = Offset(clickedBlock.x, clickedBlock.y)
                            state.selectBlock(clickedBlock.id)
                        } else {
                            dragStart = offset
                            state.selectBlock(null)
                        }
                    },
                    onDrag = { change, _ ->
                        if (dragBlockId != null && dragBlockStart != null) {
                            val scaled = (change.position - state.cameraOffset.value) / state.zoom.value
                            val dx = scaled.x - dragBlockStart!!.x
                            val dy = scaled.y - dragBlockStart!!.y
                            state.moveBlock(dragBlockId!!, dx, dy)
                            dragBlockStart = scaled
                        } else if (dragStart != null) {
                            val dx = change.position.x - dragStart!!.x
                            val dy = change.position.y - dragStart!!.y
                            state.panCamera(dx, dy)
                            dragStart = change.position
                        }
                    },
                    onDragEnd = {
                        dragStart = null
                        dragBlockId = null
                        dragBlockStart = null
                    }
                )
            }
    ) {
        // Draw grid
        drawGrid()

        // Draw connections
        state.connections.value.forEach { conn ->
            drawConnection(conn, state.blocks.value)
        }

        // Draw blocks
        state.blocks.value.forEach { block ->
            drawBlock(block, isSelected = block.id == state.selectedBlockId.value)
        }
    }
}

private fun DrawScope.drawGrid() {
    val gridSize = 20f
    val dotColor = Color(0xFF2A2A3E)
    for (x in 0 until size.width.toInt() step gridSize.toInt()) {
        for (y in 0 until size.height.toInt() step gridSize.toInt()) {
            drawCircle(dotColor, radius = 1f, center = Offset(x.toFloat(), y.toFloat()))
        }
    }
}

private fun DrawScope.drawBlock(block: PlacedBlock, isSelected: Boolean) {
    val width = 180f
    val height = 50f
    val cornerRadius = 8f

    // Shadow
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.3f),
        topLeft = Offset(block.x + 2, block.y + 2),
        size = androidx.compose.ui.geometry.Size(width, height),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius)
    )

    // Block body
    drawRoundRect(
        color = if (isSelected) block.definition.color.copy(alpha = 0.4f) else Color(0xFF2D2D44),
        topLeft = Offset(block.x, block.y),
        size = androidx.compose.ui.geometry.Size(width, height),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius)
    )

    // Left color strip
    drawRoundRect(
        color = block.definition.color,
        topLeft = Offset(block.x, block.y),
        size = androidx.compose.ui.geometry.Size(6f, height),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 0f, 0f, 3f)
    )

    // Selection border
    if (isSelected) {
        drawRoundRect(
            color = block.definition.color,
            topLeft = Offset(block.x - 1, block.y - 1),
            size = androidx.compose.ui.geometry.Size(width + 2, height + 2),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius + 1),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
        )
    }

    // Block name
    drawContext.canvas.nativeCanvas.drawText(
        block.definition.name,
        block.x + 16f,
        block.y + height / 2 + 5f,
        android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 13f
            isAntiAlias = true
        }
    )

    // Draw input ports
    block.definition.inputs.forEachIndexed { index, port ->
        val py = block.y + 12f + index * 14f
        drawCircle(portColor(port.type), radius = 5f, center = Offset(block.x - 5f, py))
    }

    // Draw output ports
    block.definition.outputs.forEachIndexed { index, port ->
        val py = block.y + 12f + index * 14f
        drawCircle(portColor(port.type), radius = 5f, center = Offset(block.x + width + 5f, py))
    }
}

private fun DrawScope.drawConnection(conn: BlockConnection, blocks: List<PlacedBlock>) {
    val from = blocks.find { it.id == conn.fromBlockId } ?: return
    val to = blocks.find { it.id == conn.toBlockId } ?: return
    val startX = from.x + 180f + 5f
    val startY = from.y + 12f + conn.fromPortIndex * 14f
    val endX = to.x - 5f
    val endY = to.y + 12f + conn.toPortIndex * 14f
    val cpOffset = abs(endX - startX).coerceAtLeast(50f)

    drawPath(
        path = androidx.compose.ui.graphics.Path().apply {
            moveTo(startX, startY)
            cubicTo(startX + cpOffset, startY, endX - cpOffset, endY, endX, endY)
        },
        color = from.definition.color.copy(alpha = 0.7f),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
    )
}

private fun portColor(type: PortType): Color = when (type) {
    PortType.STRING -> Color(0xFFE74C3C)
    PortType.NUMBER -> Color(0xFF3498DB)
    PortType.BOOLEAN -> Color(0xFF27AE60)
    PortType.OBJECT -> Color(0xFF9B59B6)
    PortType.ANY -> Color(0xFF95A5A6)
}
