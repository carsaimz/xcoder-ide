package com.xcoder.visual

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class BlockCategory(val displayName: String, val color: Color) {
    EVENTS("Events", Color(0xFFE74C3C)),
    VARIABLES("Variables", Color(0xFFE67E22)),
    CONTROL("Control", Color(0xFFF39C12)),
    LOGIC("Logic", Color(0xFF27AE60)),
    LOOPS("Loops", Color(0xFF2980B9)),
    MATH("Math", Color(0xFF8E44AD)),
    TEXT("Text", Color(0xFF1ABC9C)),
    LISTS("Lists", Color(0xFF3498DB)),
    UI("UI Components", Color(0xFF9B59B6)),
    INTENT("Intent", Color(0xFFE91E63)),
    SENSOR("Sensor", Color(0xFF00BCD4)),
    MEDIA("Media", Color(0xFFFF5722)),
    FILE("File", Color(0xFF795548)),
    NETWORK("Network", Color(0xFF607D8B))
}

enum class PortType { STRING, NUMBER, BOOLEAN, OBJECT, ANY }
enum class PortDirection { INPUT, OUTPUT }

@Suppress("unused")
data class BlockPort(
    val name: String,
    val type: PortType = PortType.ANY,
    val direction: PortDirection,
    val defaultValue: String = ""
)

@Suppress("unused")
data class BlockDefinition(
    val id: String,
    val category: BlockCategory,
    val name: String,
    val color: Color,
    val inputs: List<BlockPort> = emptyList(),
    val outputs: List<BlockPort> = emptyList(),
    val defaultValues: Map<String, String> = emptyMap()
)

object BlockDefinitions {
    val allBlocks: List<BlockDefinition> = listOf(
        // Events
        BlockDefinition("evt_on_create", BlockCategory.EVENTS, "On Create", Color(0xFFE74C3C),
            outputs = listOf(BlockPort("callback", PortType.ANY, PortDirection.OUTPUT))),
        BlockDefinition("evt_on_click", BlockCategory.EVENTS, "On Click", Color(0xFFE74C3C),
            inputs = listOf(BlockPort("view", PortType.OBJECT, PortDirection.INPUT, "")),
            outputs = listOf(BlockPort("callback", PortType.ANY, PortDirection.OUTPUT))),
        BlockDefinition("evt_on_long_click", BlockCategory.EVENTS, "On Long Click", Color(0xFFE74C3C),
            inputs = listOf(BlockPort("view", PortType.OBJECT, PortDirection.INPUT, "")),
            outputs = listOf(BlockPort("callback", PortType.ANY, PortDirection.OUTPUT))),
        BlockDefinition("evt_on_change", BlockCategory.EVENTS, "On Text Change", Color(0xFFE74C3C),
            inputs = listOf(BlockPort("view", PortType.OBJECT, PortDirection.INPUT, "")),
            outputs = listOf(BlockPort("text", PortType.STRING, PortDirection.OUTPUT))),
        BlockDefinition("evt_on_key", BlockCategory.EVENTS, "On Key Press", Color(0xFFE74C3C),
            inputs = listOf(BlockPort("keyCode", PortType.NUMBER, PortDirection.INPUT, "0")),
            outputs = listOf(BlockPort("callback", PortType.ANY, PortDirection.OUTPUT))),

        // Variables
        BlockDefinition("var_set", BlockCategory.VARIABLES, "Set Variable", Color(0xFFE67E22),
            inputs = listOf(BlockPort("name", PortType.STRING, PortDirection.INPUT, "myVar"),
                BlockPort("value", PortType.ANY, PortDirection.INPUT, "0"))),
        BlockDefinition("var_get", BlockCategory.VARIABLES, "Get Variable", Color(0xFFE67E22),
            inputs = listOf(BlockPort("name", PortType.STRING, PortDirection.INPUT, "myVar")),
            outputs = listOf(BlockPort("value", PortType.ANY, PortDirection.OUTPUT))),
        BlockDefinition("var_create", BlockCategory.VARIABLES, "Create Variable", Color(0xFFE67E22),
            inputs = listOf(BlockPort("name", PortType.STRING, PortDirection.INPUT, "myVar"),
                BlockPort("type", PortType.STRING, PortDirection.INPUT, "String"),
                BlockPort("value", PortType.ANY, PortDirection.INPUT, ""))),
        BlockDefinition("var_increment", BlockCategory.VARIABLES, "Increment", Color(0xFFE67E22),
            inputs = listOf(BlockPort("name", PortType.STRING, PortDirection.INPUT, "counter"),
                BlockPort("amount", PortType.NUMBER, PortDirection.INPUT, "1"))),

        // Control
        BlockDefinition("ctrl_if", BlockCategory.CONTROL, "If", Color(0xFFF39C12),
            inputs = listOf(BlockPort("condition", PortType.BOOLEAN, PortDirection.INPUT, "true")),
            outputs = listOf(BlockPort("then", PortType.ANY, PortDirection.OUTPUT),
                BlockPort("else", PortType.ANY, PortDirection.OUTPUT))),
        BlockDefinition("ctrl_if_else", BlockCategory.CONTROL, "If-Else", Color(0xFFF39C12),
            inputs = listOf(BlockPort("condition", PortType.BOOLEAN, PortDirection.INPUT, "true")),
            outputs = listOf(BlockPort("then", PortType.ANY, PortDirection.OUTPUT),
                BlockPort("else", PortType.ANY, PortDirection.OUTPUT))),
        BlockDefinition("ctrl_when", BlockCategory.CONTROL, "When", Color(0xFFF39C12),
            inputs = listOf(BlockPort("value", PortType.ANY, PortDirection.INPUT, "")),
            outputs = listOf(BlockPort("branch", PortType.ANY, PortDirection.OUTPUT))),
        BlockDefinition("ctrl_return", BlockCategory.CONTROL, "Return", Color(0xFFF39C12),
            inputs = listOf(BlockPort("value", PortType.ANY, PortDirection.INPUT, ""))),
        BlockDefinition("ctrl_exit", BlockCategory.CONTROL, "Exit App", Color(0xFFF39C12)),
        BlockDefinition("ctrl_wait", BlockCategory.CONTROL, "Wait", Color(0xFFF39C12),
            inputs = listOf(BlockPort("milliseconds", PortType.NUMBER, PortDirection.INPUT, "1000"))),

        // Logic
        BlockDefinition("logic_and", BlockCategory.LOGIC, "AND", Color(0xFF27AE60),
            inputs = listOf(BlockPort("a", PortType.BOOLEAN, PortDirection.INPUT, "true"),
                BlockPort("b", PortType.BOOLEAN, PortDirection.INPUT, "true")),
            outputs = listOf(BlockPort("result", PortType.BOOLEAN, PortDirection.OUTPUT))),
        BlockDefinition("logic_or", BlockCategory.LOGIC, "OR", Color(0xFF27AE60),
            inputs = listOf(BlockPort("a", PortType.BOOLEAN, PortDirection.INPUT, "false"),
                BlockPort("b", PortType.BOOLEAN, PortDirection.INPUT, "false")),
            outputs = listOf(BlockPort("result", PortType.BOOLEAN, PortDirection.OUTPUT))),
        BlockDefinition("logic_not", BlockCategory.LOGIC, "NOT", Color(0xFF27AE60),
            inputs = listOf(BlockPort("value", PortType.BOOLEAN, PortDirection.INPUT, "true")),
            outputs = listOf(BlockPort("result", PortType.BOOLEAN, PortDirection.OUTPUT))),
        BlockDefinition("logic_compare", BlockCategory.LOGIC, "Compare", Color(0xFF27AE60),
            inputs = listOf(BlockPort("a", PortType.ANY, PortDirection.INPUT, "0"),
                BlockPort("operator", PortType.STRING, PortDirection.INPUT, "=="),
                BlockPort("b", PortType.ANY, PortDirection.INPUT, "0")),
            outputs = listOf(BlockPort("result", PortType.BOOLEAN, PortDirection.OUTPUT))),
        BlockDefinition("logic_null", BlockCategory.LOGIC, "Is Null", Color(0xFF27AE60),
            inputs = listOf(BlockPort("value", PortType.ANY, PortDirection.INPUT, "")),
            outputs = listOf(BlockPort("result", PortType.BOOLEAN, PortDirection.OUTPUT))),

        // Loops
        BlockDefinition("loop_repeat", BlockCategory.LOOPS, "Repeat", Color(0xFF2980B9),
            inputs = listOf(BlockPort("times", PortType.NUMBER, PortDirection.INPUT, "10")),
            outputs = listOf(BlockPort("body", PortType.ANY, PortDirection.OUTPUT))),
        BlockDefinition("loop_for_each", BlockCategory.LOOPS, "For Each", Color(0xFF2980B9),
            inputs = listOf(BlockPort("list", PortType.OBJECT, PortDirection.INPUT, "")),
            outputs = listOf(BlockPort("item", PortType.ANY, PortDirection.OUTPUT),
                BlockPort("body", PortType.ANY, PortDirection.OUTPUT))),
        BlockDefinition("loop_while", BlockCategory.LOOPS, "While", Color(0xFF2980B9),
            inputs = listOf(BlockPort("condition", PortType.BOOLEAN, PortDirection.INPUT, "true")),
            outputs = listOf(BlockPort("body", PortType.ANY, PortDirection.OUTPUT))),
        BlockDefinition("loop_break", BlockCategory.LOOPS, "Break", Color(0xFF2980B9)),
        BlockDefinition("loop_continue", BlockCategory.LOOPS, "Continue", Color(0xFF2980B9)),

        // Math
        BlockDefinition("math_number", BlockCategory.MATH, "Number", Color(0xFF8E44AD),
            inputs = listOf(BlockPort("value", PortType.NUMBER, PortDirection.INPUT, "0")),
            outputs = listOf(BlockPort("result", PortType.NUMBER, PortDirection.OUTPUT))),
        BlockDefinition("math_arithmetic", BlockCategory.MATH, "Arithmetic", Color(0xFF8E44AD),
            inputs = listOf(BlockPort("a", PortType.NUMBER, PortDirection.INPUT, "0"),
                BlockPort("operator", PortType.STRING, PortDirection.INPUT, "+"),
                BlockPort("b", PortType.NUMBER, PortDirection.INPUT, "0")),
            outputs = listOf(BlockPort("result", PortType.NUMBER, PortDirection.OUTPUT))),
        BlockDefinition("math_random", BlockCategory.MATH, "Random", Color(0xFF8E44AD),
            inputs = listOf(BlockPort("min", PortType.NUMBER, PortDirection.INPUT, "0"),
                BlockPort("max", PortType.NUMBER, PortDirection.INPUT, "100")),
            outputs = listOf(BlockPort("result", PortType.NUMBER, PortDirection.OUTPUT))),
        BlockDefinition("math_abs", BlockCategory.MATH, "Absolute", Color(0xFF8E44AD),
            inputs = listOf(BlockPort("value", PortType.NUMBER, PortDirection.INPUT, "0")),
            outputs = listOf(BlockPort("result", PortType.NUMBER, PortDirection.OUTPUT))),
        BlockDefinition("math_round", BlockCategory.MATH, "Round", Color(0xFF8E44AD),
            inputs = listOf(BlockPort("value", PortType.NUMBER, PortDirection.INPUT, "0")),
            outputs = listOf(BlockPort("result", PortType.NUMBER, PortDirection.OUTPUT))),

        // Text
        BlockDefinition("text_create", BlockCategory.TEXT, "Create Text", Color(0xFF1ABC9C),
            inputs = listOf(BlockPort("text", PortType.STRING, PortDirection.INPUT, "Hello")),
            outputs = listOf(BlockPort("result", PortType.STRING, PortDirection.OUTPUT))),
        BlockDefinition("text_join", BlockCategory.TEXT, "Join", Color(0xFF1ABC9C),
            inputs = listOf(BlockPort("a", PortType.STRING, PortDirection.INPUT, ""),
                BlockPort("b", PortType.STRING, PortDirection.INPUT, "")),
            outputs = listOf(BlockPort("result", PortType.STRING, PortDirection.OUTPUT))),
        BlockDefinition("text_length", BlockCategory.TEXT, "Length", Color(0xFF1ABC9C),
            inputs = listOf(BlockPort("text", PortType.STRING, PortDirection.INPUT, "")),
            outputs = listOf(BlockPort("result", PortType.NUMBER, PortDirection.OUTPUT))),
        BlockDefinition("text_upper", BlockCategory.TEXT, "To Upper Case", Color(0xFF1ABC9C),
            inputs = listOf(BlockPort("text", PortType.STRING, PortDirection.INPUT, "")),
            outputs = listOf(BlockPort("result", PortType.STRING, PortDirection.OUTPUT))),
        BlockDefinition("text_contains", BlockCategory.TEXT, "Contains", Color(0xFF1ABC9C),
            inputs = listOf(BlockPort("text", PortType.STRING, PortDirection.INPUT, ""),
                BlockPort("search", PortType.STRING, PortDirection.INPUT, "")),
            outputs = listOf(BlockPort("result", PortType.BOOLEAN, PortDirection.OUTPUT))),

        // UI
        BlockDefinition("ui_textview", BlockCategory.UI, "TextView", Color(0xFF9B59B6),
            inputs = listOf(BlockPort("text", PortType.STRING, PortDirection.INPUT, "Hello World"),
                BlockPort("textSize", PortType.NUMBER, PortDirection.INPUT, "16"),
                BlockPort("id", PortType.STRING, PortDirection.INPUT, "textView1")),
            outputs = listOf(BlockPort("view", PortType.OBJECT, PortDirection.OUTPUT))),
        BlockDefinition("ui_edittext", BlockCategory.UI, "EditText", Color(0xFF9B59B6),
            inputs = listOf(BlockPort("hint", PortType.STRING, PortDirection.INPUT, "Enter text..."),
                BlockPort("inputType", PortType.STRING, PortDirection.INPUT, "text"),
                BlockPort("id", PortType.STRING, PortDirection.INPUT, "editText1")),
            outputs = listOf(BlockPort("view", PortType.OBJECT, PortDirection.OUTPUT),
                BlockPort("text", PortType.STRING, PortDirection.OUTPUT))),
        BlockDefinition("ui_button", BlockCategory.UI, "Button", Color(0xFF9B59B6),
            inputs = listOf(BlockPort("text", PortType.STRING, PortDirection.INPUT, "Click Me"),
                BlockPort("id", PortType.STRING, PortDirection.INPUT, "button1")),
            outputs = listOf(BlockPort("view", PortType.OBJECT, PortDirection.OUTPUT))),
        BlockDefinition("ui_imageview", BlockCategory.UI, "ImageView", Color(0xFF9B59B6),
            inputs = listOf(BlockPort("src", PortType.STRING, PortDirection.INPUT, "@mipmap/ic_launcher"),
                BlockPort("id", PortType.STRING, PortDirection.INPUT, "imageView1")),
            outputs = listOf(BlockPort("view", PortType.OBJECT, PortDirection.OUTPUT))),
        BlockDefinition("ui_set_text", BlockCategory.UI, "Set Text", Color(0xFF9B59B6),
            inputs = listOf(BlockPort("view", PortType.OBJECT, PortDirection.INPUT, ""),
                BlockPort("text", PortType.STRING, PortDirection.INPUT, ""))),
        BlockDefinition("ui_set_visibility", BlockCategory.UI, "Set Visibility", Color(0xFF9B59B6),
            inputs = listOf(BlockPort("view", PortType.OBJECT, PortDirection.INPUT, ""),
                BlockPort("visible", PortType.BOOLEAN, PortDirection.INPUT, "true"))),

        // Intent
        BlockDefinition("intent_open", BlockCategory.INTENT, "Open Activity", Color(0xFFE91E63),
            inputs = listOf(BlockPort("activity", PortType.STRING, PortDirection.INPUT, ""))),
        BlockDefinition("intent_finish", BlockCategory.INTENT, "Finish Activity", Color(0xFFE91E63)),
        BlockDefinition("intent_toast", BlockCategory.INTENT, "Show Toast", Color(0xFFE91E63),
            inputs = listOf(BlockPort("message", PortType.STRING, PortDirection.INPUT, "Hello!"),
                BlockPort("length", PortType.STRING, PortDirection.INPUT, "SHORT"))),

        // File
        BlockDefinition("file_read", BlockCategory.FILE, "Read File", Color(0xFF795548),
            inputs = listOf(BlockPort("path", PortType.STRING, PortDirection.INPUT, "/sdcard/file.txt")),
            outputs = listOf(BlockPort("content", PortType.STRING, PortDirection.OUTPUT))),
        BlockDefinition("file_write", BlockCategory.FILE, "Write File", Color(0xFF795548),
            inputs = listOf(BlockPort("path", PortType.STRING, PortDirection.INPUT, "/sdcard/file.txt"),
                BlockPort("content", PortType.STRING, PortDirection.INPUT, ""))),
        BlockDefinition("file_exists", BlockCategory.FILE, "File Exists", Color(0xFF795548),
            inputs = listOf(BlockPort("path", PortType.STRING, PortDirection.INPUT, "")),
            outputs = listOf(BlockPort("result", PortType.BOOLEAN, PortDirection.OUTPUT))),
        BlockDefinition("file_delete", BlockCategory.FILE, "Delete File", Color(0xFF795548),
            inputs = listOf(BlockPort("path", PortType.STRING, PortDirection.INPUT, ""))),

        // Network
        BlockDefinition("net_http_get", BlockCategory.NETWORK, "HTTP GET", Color(0xFF607D8B),
            inputs = listOf(BlockPort("url", PortType.STRING, PortDirection.INPUT, "")),
            outputs = listOf(BlockPort("response", PortType.STRING, PortDirection.OUTPUT))),
        BlockDefinition("net_http_post", BlockCategory.NETWORK, "HTTP POST", Color(0xFF607D8B),
            inputs = listOf(BlockPort("url", PortType.STRING, PortDirection.INPUT, ""),
                BlockPort("body", PortType.STRING, PortDirection.INPUT, "")),
            outputs = listOf(BlockPort("response", PortType.STRING, PortDirection.OUTPUT)))
    )

    fun byCategory(category: BlockCategory): List<BlockDefinition> =
        allBlocks.filter { it.category == category }

    fun findById(id: String): BlockDefinition? = allBlocks.find { it.id == id }
}

@Composable
fun PaletteScreen(
    onBlockSelected: (BlockDefinition) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategory by remember { mutableStateOf(BlockCategory.EVENTS) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredBlocks = remember(selectedCategory, searchQuery) {
        val blocks = BlockDefinitions.byCategory(selectedCategory)
        if (searchQuery.isBlank()) blocks
        else blocks.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search blocks...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
        )

        // Category tabs
        ScrollableTabRow(
            selectedTabIndex = BlockCategory.entries.indexOf(selectedCategory).coerceAtLeast(0),
            edgePadding = 4.dp,
            divider = {}
        ) {
            BlockCategory.entries.forEach { category ->
                Tab(
                    selected = selectedCategory == category,
                    onClick = { selectedCategory = category },
                    text = { Text(category.displayName, fontSize = 11.sp) },
                    selectedContentColor = category.color,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Block list
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(filteredBlocks, key = { it.id }) { block ->
                DraggableBlockItem(
                    block = block,
                    onClick = { onBlockSelected(block) }
                )
            }
        }
    }
}

@Composable
private fun DraggableBlockItem(
    block: BlockDefinition,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(block.color.copy(alpha = 0.15f))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(block.color)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = block.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (block.inputs.isNotEmpty() || block.outputs.isNotEmpty()) {
                Text(
                    text = "${block.inputs.size} in, ${block.outputs.size} out",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
