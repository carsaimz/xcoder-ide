package com.xcoder.core.terminal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.onKeyEvent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

@Stable
class TerminalColorScheme(
    val background: Color = Color(0xFF1E1E2E),
    val foreground: Color = Color(0xFFCDD6F4),
    val cursor: Color = Color(0xFFF5E0DC),
    val cursorForeground: Color = Color(0xFF1E1E2E),
    val selectionBackground: Color = Color(0x40594FD6),
    val black: Color = Color(0xFF45475A),
    val red: Color = Color(0xFFF38BA8),
    val green: Color = Color(0xFFA6E3A1),
    val yellow: Color = Color(0xFFF9E2AF),
    val blue: Color = Color(0xFF89B4FA),
    val magenta: Color = Color(0xFFF5C2E7),
    val cyan: Color = Color(0xFF94E2D5),
    val white: Color = Color(0xFFBAC2DE),
    val brightBlack: Color = Color(0xFF585B70),
    val brightRed: Color = Color(0xFFF38BA8),
    val brightGreen: Color = Color(0xFFA6E3A1),
    val brightYellow: Color = Color(0xFFF9E2AF),
    val brightBlue: Color = Color(0xFF89B4FA),
    val brightMagenta: Color = Color(0xFFF5C2E7),
    val brightCyan: Color = Color(0xFF94E2D5),
    val brightWhite: Color = Color(0xFFA6ADC8)
) {
    fun colorForIndex(index: Int): Color {
        return when (index) {
            0 -> black
            1 -> red
            2 -> green
            3 -> yellow
            4 -> blue
            5 -> magenta
            6 -> cyan
            7 -> white
            8 -> brightBlack
            9 -> brightRed
            10 -> brightGreen
            11 -> brightYellow
            12 -> brightBlue
            13 -> brightMagenta
            14 -> brightCyan
            15 -> brightWhite
            else -> foreground
        }
    }
}

@Stable
class TerminalBuffer(
    val columns: Int = 80,
    val rows: Int = 24,
    val scrollbackMax: Int = 10000
) {
    private val cells = Array(rows + scrollbackMax) { Array(columns) { TerminalCell() } }
    private var _cursorRow = 0
    private var _cursorCol = 0
    private var _scrollTop = 0
    private var _scrollBottom = rows - 1
    private var _scrollOffset = 0
    var currentFg: Color = Color.White
    var currentBg: Color = Color.Transparent
    var currentBold: Boolean = false
    var currentUnderline: Boolean = false
    var currentInverse: Boolean = false

    val cursorRow: Int get() = _cursorRow
    val cursorCol: Int get() = _cursorCol
    val scrollOffset: Int get() = _scrollOffset
    val scrollTop: Int get() = _scrollTop
    val scrollBottom: Int get() = _scrollBottom

    fun getCell(row: Int, col: Int): TerminalCell {
        val actualRow = row + _scrollOffset
        return if (actualRow in cells.indices && col in 0 until columns) {
            cells[actualRow][col]
        } else {
            TerminalCell()
        }
    }

    fun setCell(row: Int, col: Int, char: Char) {
        val actualRow = row + _scrollOffset
        if (actualRow in cells.indices && col in 0 until columns) {
            cells[actualRow][col].char = char
            cells[actualRow][col].fg = currentFg
            cells[actualRow][col].bg = currentBg
            cells[actualRow][col].bold = currentBold
            cells[actualRow][col].underline = currentUnderline
            cells[actualRow][col].inverse = currentInverse
        }
    }

    fun advanceCursor() {
        _cursorCol++
        if (_cursorCol >= columns) {
            _cursorCol = 0
            lineFeed()
        }
    }

    fun lineFeed() {
        if (_cursorRow == _scrollBottom) {
            scrollUp(1)
        } else if (_cursorRow < rows - 1) {
            _cursorRow++
        }
    }

    fun carriageReturn() {
        _cursorCol = 0
    }

    fun scrollUp(count: Int) {
        for (i in _scrollTop until _scrollBottom) {
            System.arraycopy(cells[i + count + _scrollOffset], 0, cells[i + _scrollOffset], 0, columns)
        }
        for (c in 0 until columns) {
            cells[_scrollBottom + _scrollOffset][c] = TerminalCell()
        }
    }

    fun deleteLine() {
        for (c in 0 until columns) {
            cells[_cursorRow + _scrollOffset][c] = TerminalCell()
        }
    }

    fun deleteCharacters(count: Int) {
        for (i in _cursorCol until columns - count) {
            cells[_cursorRow + _scrollOffset][i] = cells[_cursorRow + _scrollOffset][i + count].copy()
        }
        for (i in max(0, columns - count) until columns) {
            cells[_cursorRow + _scrollOffset][i] = TerminalCell()
        }
    }

    fun clearScreen() {
        for (row in cells.indices) {
            for (col in 0 until columns) {
                cells[row][col] = TerminalCell()
            }
        }
        _cursorRow = 0
        _cursorCol = 0
        _scrollOffset = 0
    }

    fun clearToEndOfLine() {
        for (c in _cursorCol until columns) {
            cells[_cursorRow + _scrollOffset][c] = TerminalCell()
        }
    }

    fun clearToStartOfLine() {
        for (c in 0 until _cursorCol) {
            cells[_cursorRow + _scrollOffset][c] = TerminalCell()
        }
    }

    fun clearLine() {
        for (c in 0 until columns) {
            cells[_cursorRow + _scrollOffset][c] = TerminalCell()
        }
    }

    fun insertLines(count: Int) {
        for (row in _scrollBottom downTo _cursorRow + count) {
            System.arraycopy(cells[row - count + _scrollOffset], 0, cells[row + _scrollOffset], 0, columns)
        }
        for (row in _cursorRow until min(_cursorRow + count, _scrollBottom + 1)) {
            for (c in 0 until columns) {
                cells[row + _scrollOffset][c] = TerminalCell()
            }
        }
    }

    fun setCursor(row: Int, col: Int) {
        _cursorRow = row.coerceIn(0, rows - 1)
        _cursorCol = col.coerceIn(0, columns - 1)
    }

    fun cursorUp(count: Int) {
        _cursorRow = (_cursorRow - count).coerceAtLeast(0)
    }

    fun cursorDown(count: Int) {
        _cursorRow = (_cursorRow + count).coerceAtMost(rows - 1)
    }

    fun cursorForward(count: Int) {
        _cursorCol = (_cursorCol + count).coerceAtMost(columns - 1)
    }

    fun cursorBackward(count: Int) {
        _cursorCol = (_cursorCol - count).coerceAtLeast(0)
    }

    fun adjustScrollOffset(delta: Int) {
        _scrollOffset = (_scrollOffset + delta).coerceIn(0, scrollbackMax)
    }

    fun resetScrollOffset() {
        _scrollOffset = 0
    }
}

@Stable
data class TerminalCell(
    var char: Char = ' ',
    var fg: Color = Color.White,
    var bg: Color = Color.Transparent,
    var bold: Boolean = false,
    var underline: Boolean = false,
    var inverse: Boolean = false
)

class Vt100Parser(private val buffer: TerminalBuffer, private val colorScheme: TerminalColorScheme) {

    private val escapeBuffer = StringBuilder()
    private var isInEscape = false
    private var escapeIntermediate = ""

    fun parse(input: String) {
        var i = 0
        while (i < input.length) {
            val ch = input[i]
            when {
                isInEscape -> {
                    if (ch == '[' || ch == '(' || ch == ')') {
                        escapeIntermediate += ch
                        i++
                        continue
                    }
                    if (ch in '0'..'9' || ch == ';' || ch == '?' || ch == ' ' || ch == '"' || ch == '\'') {
                        escapeBuffer.append(ch)
                        i++
                        continue
                    }
                    handleEscapeSequence(ch)
                    isInEscape = false
                    escapeBuffer.clear()
                    escapeIntermediate = ""
                }
                ch == '\u001B' -> {
                    isInEscape = true
                    escapeBuffer.clear()
                    escapeIntermediate = ""
                }
                ch == '\n' -> {
                    buffer.lineFeed()
                }
                ch == '\r' -> {
                    buffer.carriageReturn()
                }
                ch == '\t' -> {
                    val spacesToNextTab = 8 - (buffer.cursorCol % 8)
                    repeat(spacesToNextTab) {
                        if (buffer.cursorCol < buffer.columns) {
                            buffer.setCell(buffer.cursorRow, buffer.cursorCol, ' ')
                            buffer.advanceCursor()
                        }
                    }
                }
                ch == '\b' -> {
                    buffer.cursorBackward(1)
                }
                ch == '\u0007' -> {
                }
                ch in '\u0000'..'\u001F' -> {
                }
                else -> {
                    buffer.setCell(buffer.cursorRow, buffer.cursorCol, ch)
                    buffer.advanceCursor()
                }
            }
            i++
        }
    }

    private fun handleEscapeSequence(finalChar: Char) {
        val params = escapeBuffer.toString()
        val parts = params.split(';').map { it.trim().filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
        when (finalChar) {
            'H', 'f' -> {
                val row = (parts.getOrElse(0) { 1 } - 1).coerceIn(0, buffer.rows - 1)
                val col = (parts.getOrElse(1) { 1 } - 1).coerceIn(0, buffer.columns - 1)
                buffer.setCursor(row, col)
            }
            'A' -> buffer.cursorUp(parts.getOrElse(0) { 1 })
            'B' -> buffer.cursorDown(parts.getOrElse(0) { 1 })
            'C' -> buffer.cursorForward(parts.getOrElse(0) { 1 })
            'D' -> buffer.cursorBackward(parts.getOrElse(0) { 1 })
            'J' -> {
                when (parts.getOrElse(0) { 0 }) {
                    0 -> buffer.clearToEndOfScreen()
                    1 -> buffer.clearToStartOfScreen()
                    2 -> buffer.clearScreen()
                }
            }
            'K' -> {
                when (parts.getOrElse(0) { 0 }) {
                    0 -> buffer.clearToEndOfLine()
                    1 -> buffer.clearToStartOfLine()
                    2 -> buffer.clearLine()
                }
            }
            'L' -> buffer.insertLines(parts.getOrElse(0) { 1 })
            'M' -> buffer.deleteLines(parts.getOrElse(0) { 1 })
            'P' -> buffer.deleteCharacters(parts.getOrElse(0) { 1 })
            'm' -> handleSgr(parts)
            'r' -> {
                val top = (parts.getOrElse(0) { 1 } - 1).coerceIn(0, buffer.rows - 1)
                val bottom = (parts.getOrElse(1) { buffer.rows } - 1).coerceIn(0, buffer.rows - 1)
            }
            'G' -> buffer.cursorCol = (parts.getOrElse(0) { 1 } - 1).coerceIn(0, buffer.columns - 1)
            'd' -> buffer.cursorRow = (parts.getOrElse(0) { 1 } - 1).coerceIn(0, buffer.rows - 1)
            's' -> { }
            'u' -> { }
            'n' -> { }
            'h', 'l' -> { }
            else -> { }
        }
    }

    private fun handleSgr(params: List<Int>) {
        if (params.isEmpty() || params.all { it == 0 }) {
            buffer.currentFg = colorScheme.foreground
            buffer.currentBg = Color.Transparent
            buffer.currentBold = false
            buffer.currentUnderline = false
            buffer.currentInverse = false
            return
        }
        var i = 0
        while (i < params.size) {
            when (params[i]) {
                0 -> {
                    buffer.currentFg = colorScheme.foreground
                    buffer.currentBg = Color.Transparent
                    buffer.currentBold = false
                    buffer.currentUnderline = false
                    buffer.currentInverse = false
                }
                1 -> buffer.currentBold = true
                4 -> buffer.currentUnderline = true
                7 -> buffer.currentInverse = true
                22 -> buffer.currentBold = false
                24 -> buffer.currentUnderline = false
                27 -> buffer.currentInverse = false
                in 30..37 -> buffer.currentFg = colorScheme.colorForIndex(params[i] - 30)
                in 40..47 -> buffer.currentBg = colorScheme.colorForIndex(params[i] - 40)
                38 -> {
                    if (i + 1 < params.size && params[i + 1] == 5 && i + 2 < params.size) {
                        buffer.currentFg = colorScheme.colorForIndex(params[i + 2] % 16)
                        i += 2
                    } else if (i + 1 < params.size && params[i + 1] == 2 && i + 4 < params.size) {
                        val r = params[i + 2].coerceIn(0, 255)
                        val g = params[i + 3].coerceIn(0, 255)
                        val b = params[i + 4].coerceIn(0, 255)
                        buffer.currentFg = Color(r, g, b)
                        i += 4
                    }
                }
                48 -> {
                    if (i + 1 < params.size && params[i + 1] == 5 && i + 2 < params.size) {
                        buffer.currentBg = colorScheme.colorForIndex(params[i + 2] % 16)
                        i += 2
                    } else if (i + 1 < params.size && params[i + 1] == 2 && i + 4 < params.size) {
                        val r = params[i + 2].coerceIn(0, 255)
                        val g = params[i + 3].coerceIn(0, 255)
                        val b = params[i + 4].coerceIn(0, 255)
                        buffer.currentBg = Color(r, g, b)
                        i += 4
                    }
                }
                39 -> buffer.currentFg = colorScheme.foreground
                49 -> buffer.currentBg = Color.Transparent
                90..97 -> buffer.currentFg = colorScheme.colorForIndex(params[i] - 90 + 8)
                100..107 -> buffer.currentBg = colorScheme.colorForIndex(params[i] - 100 + 8)
            }
            i++
        }
    }

    private fun clearToEndOfScreen() {
        buffer.clearToEndOfLine()
        for (row in buffer.cursorRow + 1 until buffer.rows) {
            for (col in 0 until buffer.columns) {
                buffer.setCell(row, col, ' ')
            }
        }
    }

    private fun clearToStartOfScreen() {
        buffer.clearToStartOfLine()
        for (row in 0 until buffer.cursorRow) {
            for (col in 0 until buffer.columns) {
                buffer.setCell(row, col, ' ')
            }
        }
    }

    fun reset() {
        buffer.clearScreen()
        buffer.currentFg = colorScheme.foreground
        buffer.currentBg = Color.Transparent
        buffer.currentBold = false
        buffer.currentUnderline = false
        buffer.currentInverse = false
        isInEscape = false
        escapeBuffer.clear()
        escapeIntermediate = ""
    }
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun EmulatorView(
    session: TerminalSession,
    modifier: Modifier = Modifier,
    colorScheme: TerminalColorScheme = TerminalColorScheme(),
    fontSize: Float = 14f,
    fontFamily: FontFamily = FontFamily.Monospace
) {
    val buffer = remember { TerminalBuffer(80, 24) }
    val parser = remember { Vt100Parser(buffer, colorScheme) }
    val focusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    val charWidth = remember(density, fontSize) {
        with(density) { fontSize.sp.toPx() * 0.6f }
    }
    val charHeight = remember(density, fontSize) {
        with(density) { fontSize.sp.toPx() * 1.2f }
    }

    var columns by remember { mutableIntStateOf(80) }
    var rows by remember { mutableIntStateOf(24) }
    var lastOutputLength by remember { mutableIntStateOf(0) }
    var scrollOffset by remember { mutableFloatStateOf(0f) }
    var clipboardText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(session.output.value) {
        val currentOutput = session.output.value
        if (currentOutput.length > lastOutputLength) {
            val newChunk = currentOutput.substring(lastOutputLength)
            parser.parse(newChunk)
            lastOutputLength = currentOutput.length
        }
    }

    LaunchedEffect(Unit) {
        session.addOutputListener { }
    }

    DisposableEffect(Unit) {
        onDispose {
            coroutineScope.launch {
                session.resize(columns, rows)
            }
        }
    }

    LaunchedEffect(charWidth, charHeight) {
        // Will be recalculated when canvas size changes
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .focusable()
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.Enter -> {
                            session.writeToPty("\r")
                            true
                        }
                        Key.Tab -> {
                            session.writeToPty("\u0009")
                            true
                        }
                        Key.Backspace -> {
                            session.writeToPty("\u007F")
                            true
                        }
                        Key.DirectionUp -> {
                            session.writeToPty("\u001B[A")
                            true
                        }
                        Key.DirectionDown -> {
                            session.writeToPty("\u001B[B")
                            true
                        }
                        Key.DirectionRight -> {
                            session.writeToPty("\u001B[C")
                            true
                        }
                        Key.DirectionLeft -> {
                            session.writeToPty("\u001B[D")
                            true
                        }
                        Key.PageUp -> {
                            buffer.adjustScrollOffset(1)
                            true
                        }
                        Key.PageDown -> {
                            buffer.adjustScrollOffset(-1)
                            true
                        }
                        Key.Home -> {
                            session.writeToPty("\u001B[H")
                            true
                        }
                        Key.End -> {
                            session.writeToPty("\u001B[F")
                            true
                        }
                        Key.Escape -> {
                            session.writeToPty("\u001B")
                            true
                        }
                        Key.A -> {
                            val text = event.utf16CodePoint.toChar().toString()
                            session.writeToPty(text)
                            true
                        }
                        else -> {
                            val codePoint = event.utf16CodePoint
                            if (codePoint > 0) {
                                val text = String(intArrayOf(codePoint), 0, 1)
                                session.writeToPty(text)
                                true
                            } else {
                                false
                            }
                        }
                    }
                } else {
                    false
                }
            }
            .padding(4.dp)
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val computedCols = max(1, floor(canvasWidth / charWidth).toInt())
        val computedRows = max(1, floor(canvasHeight / charHeight).toInt())

        if (computedCols != columns || computedRows != rows) {
            columns = computedCols
            rows = computedRows
            coroutineScope.launch {
                session.resize(columns, rows)
            }
        }

        drawRect(color = colorScheme.background, size = size)

        val textPaint = android.graphics.Paint().apply {
            color = colorScheme.foreground.toArgb()
            textSize = fontSize * density.density
            typeface = android.graphics.Typeface.MONOSPACE
            isAntiAlias = true
        }

        for (row in 0 until rows) {
            for (col in 0 until columns) {
                val cell = buffer.getCell(row, col)
                val x = col * charWidth
                val y = (row + 1) * charHeight

                val bgColor = if (cell.inverse) cell.fg else cell.bg
                if (bgColor != Color.Transparent) {
                    drawRect(bgColor, topLeft = Offset(x, y - charHeight), size = androidx.compose.ui.geometry.Size(charWidth, charHeight))
                }

                val fgColor = if (cell.inverse) cell.bg else cell.fg
                if (cell.char != ' ') {
                    textPaint.color = fgColor.toArgb()
                    textPaint.isFakeBoldText = cell.bold
                    textPaint.isUnderlineText = cell.underline
                    drawContext.canvas.nativeCanvas.drawText(
                        cell.char.toString(),
                        x,
                        y - (charHeight * 0.2f),
                        textPaint
                    )
                }
            }
        }

        // Draw cursor
        if (session.isRunning.value) {
            val cursorX = buffer.cursorCol * charWidth
            val cursorY = buffer.cursorRow * charHeight
            drawRect(
                color = colorScheme.cursor,
                topLeft = Offset(cursorX, cursorY),
                size = androidx.compose.ui.geometry.Size(charWidth, charHeight),
                alpha = 0.7f
            )
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}
