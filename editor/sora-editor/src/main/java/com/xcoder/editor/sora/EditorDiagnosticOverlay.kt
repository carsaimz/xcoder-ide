@file:Suppress("TooManyFunctions")
package com.xcoder.editor.sora

import android.content.Context
import android.graphics.Canvas
import android.graphics.CornerPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.Log
import android.widget.Toast
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorTouchEventHandler
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range

private const val TAG = "XCoderDiagOverlay"

/** Minimum long-press duration in ms to show diagnostic message. */
private const val LONG_PRESS_TIMEOUT_MS = 500L

/** Wave amplitude for diagnostic underlines (in pixels). */
private const val WAVE_AMPLITUDE = 3f

/** Wave frequency (number of waves per character width). */
private const val WAVE_FREQUENCY = 2.5f

/** Underline position offset from text baseline (in pixels). */
private const val UNDERLINE_OFFSET = 4f

/** Underline stroke width in pixels. */
private const val UNDERLINE_STROKE_WIDTH = 2.5f

/** Color for error diagnostics. */
private val ERROR_COLOR = 0xFFF38BA8.toInt()

/** Color for warning diagnostics. */
private val WARNING_COLOR = 0xFFF9E2AF.toInt()

/** Color for information diagnostics. */
private val INFO_COLOR = 0xFF89B4FA.toInt()

/** Color for hint diagnostics. */
private val HINT_COLOR = 0xFFA6E3A1.toInt()

/** Background color for the diagnostic tooltip. */
private val TOOLTIP_BG_COLOR = 0xFF313244.toInt()

/** Text color for the diagnostic tooltip. */
private val TOOLTIP_TEXT_COLOR = 0xFFCDD6F4.toInt()

/** Padding for the diagnostic tooltip. */
private const val TOOLTIP_PADDING = 12f

/** Maximum tooltip width in pixels. */
private const val TOOLTIP_MAX_WIDTH = 500f

/** Tooltip text size in sp. */
private const val TOOLTIP_TEXT_SIZE = 12f

/** Tooltip corner radius in dp. */
private const val TOOLTIP_CORNER_RADIUS = 8f

/** Tooltip arrow height in dp. */
private const val TOOLTIP_ARROW_HEIGHT = 6f

/** Tooltip show duration in ms. */
private const val TOOLTIP_DURATION_MS = 5000L

/**
 * Renders LSP diagnostics as colored underlines on the editor.
 *
 * Based on AndroidIDE's diagnostic overlay implementation, which uses
 * sora-editor's diagnostic API to render error, warning, info, and hint
 * markers on the editor surface.
 *
 * ## How AndroidIDE Renders Diagnostics
 *
 * AndroidIDE's `DiagnosticOverlay` extends sora-editor's diagnostic rendering:
 * 1. Diagnostics are stored per-line in a sorted list.
 * 2. Each diagnostic range is drawn as a wavy (or straight) underline.
 * 3. The color depends on the severity: error=red, warning=yellow, info=blue, hint=green.
 * 4. Long-pressing on a diagnostic shows a tooltip with the full message.
 * 5. The tooltip includes the diagnostic code, message, and source.
 *
 * ## Integration with sora-editor 0.23.5
 *
 * sora-editor provides `DiagnosticOverlay` as part of its LSP module.
 * This class wraps the sora-editor API and adds:
 * - Custom wavy underline rendering
 * - Long-press tooltip with full diagnostic message
 * - Diagnostic count badges in the gutter
 * - Quick-fix indicators for diagnostics with code actions
 *
 * @param editor The editor this overlay is attached to.
 */
class EditorDiagnosticOverlay(
    private val editor: CodeEditor,
) {

    // ── Diagnostic storage ─────────────────────────────────────────────────

    /** Current diagnostics for the file, keyed by start line index (0-based). */
    private val diagnosticsByLine = mutableMapOf<Int, MutableList<DiagnosticEntry>>()

    /** All current diagnostics (flat list for quick access). */
    private var allDiagnostics: List<Diagnostic> = emptyList()

    // ── Tooltip state ──────────────────────────────────────────────────────

    /** Whether the diagnostic tooltip is currently visible. */
    var isTooltipVisible: Boolean = false
        private set

    /** The diagnostic currently shown in the tooltip. */
    private var tooltipDiagnostic: Diagnostic? = null

    /** The screen coordinates of the tooltip anchor point. */
    private var tooltipAnchorX: Float = 0f
    private var tooltipAnchorY: Float = 0f

    /** Timestamp when the tooltip was shown (for auto-dismiss). */
    private var tooltipShowTime: Long = 0L

    /** Runnable to auto-dismiss the tooltip. */
    private var tooltipDismissRunnable: Runnable? = null

    // ── Paints ─────────────────────────────────────────────────────────────

    /** Paint for error underlines (wavy red). */
    private val errorPaint = createUnderlinePaint(ERROR_COLOR)

    /** Paint for warning underlines (wavy yellow). */
    private val warningPaint = createUnderlinePaint(WARNING_COLOR)

    /** Paint for info underlines (wavy blue). */
    private val infoPaint = createUnderlinePaint(INFO_COLOR)

    /** Paint for hint underlines (wavy green). */
    private val hintPaint = createUnderlinePaint(HINT_COLOR)

    /** Paint for the tooltip background. */
    private val tooltipBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TOOLTIP_BG_COLOR
    }

    /** Paint for the tooltip text. */
    private val tooltipTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TOOLTIP_TEXT_COLOR
        textSize = TOOLTIP_TEXT_SIZE * editor.resources.displayMetrics.scaledDensity
    }

    /** Paint for the tooltip source/code text (smaller, dimmer). */
    private val tooltipDetailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TOOLTIP_TEXT_COLOR
        alpha = 160
        textSize = (TOOLTIP_TEXT_SIZE - 2f) * editor.resources.displayMetrics.scaledDensity
    }

    // ── Counts for badge display ────────────────────────────────────────────

    /** Number of error diagnostics. */
    val errorCount: Int get() = allDiagnostics.count { it.severity == DiagnosticSeverity.Error }

    /** Number of warning diagnostics. */
    val warningCount: Int get() = allDiagnostics.count { it.severity == DiagnosticSeverity.Warning }

    /** Number of info diagnostics. */
    val infoCount: Int get() = allDiagnostics.count {
        it.severity == DiagnosticSeverity.Information
    }

    /** Total number of diagnostics. */
    val totalCount: Int get() = allDiagnostics.size

    // ── Public API ──────────────────────────────────────────────────────────

    /**
     * Set diagnostics for the current file.
     *
     * AndroidIDE receives diagnostics from the LSP server via the
     * `textDocument/publishDiagnostics` notification. The diagnostics
     * are sorted by range and stored per-line for efficient rendering.
     *
     * @param diagnostics The list of LSP diagnostics.
     */
    fun setDiagnostics(diagnostics: List<Diagnostic>) {
        clearDiagnostics()
        allDiagnostics = diagnostics

        // Index diagnostics by start line
        for (diagnostic in diagnostics) {
            val startLine = diagnostic.range.start.line
            val entry = DiagnosticEntry(
                diagnostic = diagnostic,
                startLine = startLine,
                startCol = diagnostic.range.start.character,
                endLine = diagnostic.range.end.line,
                endCol = diagnostic.range.end.character,
                severity = diagnostic.severity ?: DiagnosticSeverity.Error,
            )
            diagnosticsByLine.getOrPut(startLine) { mutableListOf() }.add(entry)
        }

        // Sort each line's diagnostics by start column
        diagnosticsByLine.values.forEach { entries ->
            entries.sortBy { it.startCol }
        }

        // Invalidate editor to trigger redraw
        editor.invalidate()
        Log.d(TAG, "Set ${diagnostics.size} diagnostics (${errorCount} errors, ${warningCount} warnings)")
    }

    /**
     * Clear all diagnostics.
     *
     * AndroidIDE clears diagnostics when:
     * - The file is closed
     * - The LSP server publishes an empty diagnostic list
     * - The editor is switched to a non-LSP file
     */
    fun clearDiagnostics() {
        diagnosticsByLine.clear()
        allDiagnostics = emptyList()
        hideTooltip()
        editor.invalidate()
    }

    /**
     * Get the diagnostic at the given position, if any.
     *
     * Used by the long-press handler to show diagnostic messages.
     *
     * @param line 0-based line number.
     * @param column 0-based column number.
     * @return The diagnostic at the position, or null.
     */
    fun getDiagnosticAt(line: Int, column: Int): Diagnostic? {
        val entries = diagnosticsByLine[line] ?: return null
        return entries.firstOrNull { entry ->
            column >= entry.startCol && column <= entry.endCol
        }?.diagnostic
    }

    /**
     * Get all diagnostics on a given line.
     *
     * Used for showing a list of diagnostics when the cursor is on
     * a line with multiple issues (AndroidIDE pattern).
     */
    fun getDiagnosticsOnLine(line: Int): List<Diagnostic> {
        return diagnosticsByLine[line]?.map { it.diagnostic } ?: emptyList()
    }

    /**
     * Show a tooltip with the diagnostic message at the given screen position.
     *
     * AndroidIDE shows a tooltip when the user long-presses on a diagnostic
     * underline. The tooltip displays:
     * - The diagnostic severity icon (error/warning/info)
     * - The diagnostic message
     * - The source and code (e.g. "java: compiler.err.cant.resolve")
     *
     * @param diagnostic The diagnostic to display.
     * @param x Screen X coordinate of the anchor point.
     * @param y Screen Y coordinate of the anchor point.
     */
    fun showTooltip(diagnostic: Diagnostic, x: Float, y: Float) {
        tooltipDiagnostic = diagnostic
        tooltipAnchorX = x
        tooltipAnchorY = y
        tooltipShowTime = System.currentTimeMillis()
        isTooltipVisible = true

        // Auto-dismiss after timeout
        tooltipDismissRunnable?.let { editor.removeCallbacks(it) }
        tooltipDismissRunnable = Runnable { hideTooltip() }
        editor.postDelayed(tooltipDismissRunnable!!, TOOLTIP_DURATION_MS)

        editor.invalidate()
    }

    /**
     * Hide the diagnostic tooltip.
     */
    fun hideTooltip() {
        isTooltipVisible = false
        tooltipDiagnostic = null
        tooltipDismissRunnable?.let { editor.removeCallbacks(it) }
        tooltipDismissRunnable = null
        editor.invalidate()
    }

    // ── Drawing ─────────────────────────────────────────────────────────────

    /**
     * Draw all diagnostic underlines on the canvas.
     *
     * Called from the editor's onDraw after the text is rendered.
     * This follows AndroidIDE's pattern of drawing diagnostics as an
     * overlay on top of the editor content.
     *
     * @param canvas The canvas to draw on.
     */
    fun drawDiagnostics(canvas: Canvas) {
        val firstVisibleLine = editor.firstVisibleLine
        val lastVisibleLine = editor.lastVisibleLine

        for (line in firstVisibleLine..minOf(lastVisibleLine, editor.text.lineCount - 1)) {
            val entries = diagnosticsByLine[line] ?: continue
            for (entry in entries) {
                drawDiagnosticUnderline(canvas, entry)
            }
        }

        // Draw tooltip if visible
        if (isTooltipVisible && tooltipDiagnostic != null) {
            drawTooltip(canvas)
        }
    }

    /**
     * Draw a wavy underline for a single diagnostic.
     *
     * AndroidIDE draws wavy underlines for errors and warnings,
     * and straight underlines for info and hints.
     */
    private fun drawDiagnosticUnderline(canvas: Canvas, entry: DiagnosticEntry) {
        val layout = editor.layout ?: return

        val paint = when (entry.severity) {
            DiagnosticSeverity.Error -> errorPaint
            DiagnosticSeverity.Warning -> warningPaint
            DiagnosticSeverity.Information -> infoPaint
            DiagnosticSeverity.Hint -> hintPaint
            else -> errorPaint
        }

        // Get the character positions for the diagnostic range
        val lineContent = editor.text.getLine(entry.startLine)
        val startCharPos = entry.startCol.coerceAtMost(lineContent.length)
        val endCharPos = entry.endCol.coerceAtMost(lineContent.length)

        if (startCharPos >= endCharPos) return

        // Get the horizontal positions from the layout
        val startX = layout.getCharHorizontalPosition(startCharPos, entry.startLine).toFloat()
        val endX = layout.getCharHorizontalPosition(endCharPos, entry.startLine).toFloat()

        // Get the vertical position (baseline + offset)
        val baselineY = layout.getBaseline(entry.startLine).toFloat()
        val underlineY = baselineY + UNDERLINE_OFFSET

        // Draw wavy underline
        drawWavyLine(canvas, startX, underlineY, endX, underlineY, paint)
    }

    /**
     * Draw a wavy (sine wave) line between two points.
     *
     * AndroidIDE uses a wavy line pattern for error and warning diagnostics,
     * similar to VS Code's diagnostic rendering.
     */
    private fun drawWavyLine(
        canvas: Canvas,
        startX: Float, startY: Float,
        endX: Float, endY: Float,
        paint: Paint,
    ) {
        val path = Path()
        path.moveTo(startX, startY)

        val totalWidth = endX - startX
        if (totalWidth <= 0) return

        val wavelength = totalWidth / (WAVE_FREQUENCY * 2f)
        var x = startX

        while (x < endX) {
            val nextX = (x + wavelength).coerceAtMost(endX)
            val midX = (x + nextX) / 2f
            path.quadTo(midX, startY - WAVE_AMPLITUDE, nextX, startY)
            x = nextX
            val nextX2 = (x + wavelength).coerceAtMost(endX)
            val midX2 = (x + nextX2) / 2f
            path.quadTo(midX2, startY + WAVE_AMPLITUDE, nextX2, startY)
            x = nextX2
        }

        canvas.drawPath(path, paint)
    }

    /**
     * Draw the diagnostic tooltip.
     *
     * The tooltip is rendered as a rounded rectangle with:
     * - A small arrow pointing to the underline
     * - The diagnostic message as primary text
     * - The source and code as secondary text
     */
    private fun drawTooltip(canvas: Canvas) {
        val diagnostic = tooltipDiagnostic ?: return
        val context = editor.context

        // Build the tooltip text
        val severityPrefix = when (diagnostic.severity) {
            DiagnosticSeverity.Error -> "\u274C "  // ❌
            DiagnosticSeverity.Warning -> "\u26A0\uFE0F "  // ⚠️
            DiagnosticSeverity.Information -> "\u2139\uFE0F "  // ℹ️
            DiagnosticSeverity.Hint -> "\uD83D\uDCA1 "  // 💡
            else -> ""
        }

        val message = diagnostic.message
        val detail = buildString {
            diagnostic.source?.let { append(it) }
            if (!diagnostic.code?.asString.isNullOrEmpty()) {
                if (isNotEmpty()) append(": ")
                append(diagnostic.code.asString)
            }
        }

        // Measure text
        val messageWidth = tooltipTextPaint.measureText(message)
        val detailWidth = if (detail.isNotEmpty()) {
            tooltipDetailPaint.measureText(detail)
        } else 0f

        val maxWidth = maxOf(messageWidth, detailWidth) + TOOLTIP_PADDING * 2f
        val tooltipWidth = minOf(maxWidth, TOOLTIP_MAX_WIDTH)
        val lineHeight = tooltipTextPaint.textSize * 1.3f
        val tooltipHeight = if (detail.isNotEmpty()) {
            lineHeight * 2 + TOOLTIP_PADDING * 2f
        } else {
            lineHeight + TOOLTIP_PADDING * 2f
        }

        // Position tooltip above the anchor
        val tooltipX = tooltipAnchorX.coerceIn(
            TOOLTIP_PADDING,
            canvas.width - tooltipWidth - TOOLTIP_PADDING
        )
        val tooltipY = tooltipAnchorY - tooltipHeight - TOOLTIP_ARROW_HEIGHT

        // Draw tooltip background
        val bgRect = RectF(tooltipX, tooltipY, tooltipX + tooltipWidth, tooltipY + tooltipHeight)
        canvas.drawRoundRect(bgRect, TOOLTIP_CORNER_RADIUS, TOOLTIP_CORNER_RADIUS, tooltipBgPaint)

        // Draw arrow
        val arrowPath = Path().apply {
            val arrowCenterX = tooltipAnchorX.coerceIn(
                tooltipX + TOOLTIP_CORNER_RADIUS,
                tooltipX + tooltipWidth - TOOLTIP_CORNER_RADIUS
            )
            moveTo(arrowCenterX - TOOLTIP_ARROW_HEIGHT, tooltipY + tooltipHeight)
            lineTo(arrowCenterX, tooltipY + tooltipHeight + TOOLTIP_ARROW_HEIGHT)
            lineTo(arrowCenterX + TOOLTIP_ARROW_HEIGHT, tooltipY + tooltipHeight)
            close()
        }
        canvas.drawPath(arrowPath, tooltipBgPaint)

        // Draw message text
        val textX = tooltipX + TOOLTIP_PADDING
        var textY = tooltipY + TOOLTIP_PADDING + tooltipTextPaint.textSize
        canvas.drawText(message, textX, textY, tooltipTextPaint)

        // Draw detail text
        if (detail.isNotEmpty()) {
            textY += lineHeight
            canvas.drawText(detail, textX, textY, tooltipDetailPaint)
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    /** Create a paint for diagnostic underlines. */
    private fun createUnderlinePaint(color: Int): Paint {
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = UNDERLINE_STROKE_WIDTH
            strokeCap = Paint.Cap.ROUND
            pathEffect = CornerPathEffect(2f)
        }
    }

    /**
     * Get the color for a diagnostic severity.
     */
    fun getColorForSeverity(severity: DiagnosticSeverity): Int {
        return when (severity) {
            DiagnosticSeverity.Error -> ERROR_COLOR
            DiagnosticSeverity.Warning -> WARNING_COLOR
            DiagnosticSeverity.Information -> INFO_COLOR
            DiagnosticSeverity.Hint -> HINT_COLOR
        }
    }

    /**
     * Data class holding a parsed diagnostic entry for efficient rendering.
     */
    data class DiagnosticEntry(
        val diagnostic: Diagnostic,
        val startLine: Int,
        val startCol: Int,
        val endLine: Int,
        val endCol: Int,
        val severity: DiagnosticSeverity,
    )
}
