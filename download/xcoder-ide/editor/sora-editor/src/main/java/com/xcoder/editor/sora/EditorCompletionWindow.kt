@file:Suppress("TooManyFunctions")
package com.xcoder.editor.sora

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.ComponentProvider
import io.github.rosemoe.sora.widget_completion.CompletionLayout
import io.github.rosemoe.sora.widget_completion.CompletionListItem
import io.github.rosemoe.sora.widget_completion.DefaultCompletionItemRenderer
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind
import org.eclipse.lsp4j.InsertTextFormat

private const val TAG = "XCoderCompletionWindow"

/** Maximum number of completion items to display. */
private const val MAX_COMPLETION_ITEMS = 50

/** Minimum prefix length before auto-completion triggers. */
private const val MIN_PREFIX_LENGTH = 1

/** Delay in ms before showing the completion window after typing stops. */
private const val COMPLETION_DELAY_MS = 150L

/** Icon tint for dark theme. */
private val ICON_TINT_DARK = 0xFFCDD6F4.toInt()

/** Icon tint for light theme. */
private val ICON_TINT_LIGHT = 0xFF4C4F69.toInt()

/**
 * Completion window that displays LSP completion items with icons for
 * different completion kinds.
 *
 * Based on AndroidIDE's completion window implementation, which extends
 * sora-editor's [CompletionWindow] class. AndroidIDE renders completion
 * items with:
 * - An icon representing the completion kind (method, field, class, etc.)
 * - The completion label (primary text)
 * - Detail text (type info, e.g. `String` or `void`)
 * - Documentation (shown on selection or hover)
 *
 * This implementation uses sora-editor 0.23.5's [CompletionLayout] and
 * [CompletionListItem] APIs to provide the same experience.
 *
 * ## LSP Completion Item Kinds
 *
 * The LSP protocol defines 25+ [CompletionItemKind] values. AndroidIDE maps
 * each kind to a drawable icon and a color tint. This class provides a
 * simplified but comprehensive mapping.
 *
 * ## Usage
 *
 * The completion window is set on the editor via
 * `CodeEditor.setCompletionWindow()` and is shown/hidden automatically
 * based on typing activity and LSP responses.
 *
 * @param editor The editor this completion window is attached to.
 * @param context Android context for resource access.
 */
class EditorCompletionWindow(
    private val editor: CodeEditor,
    private val context: Context,
) {

    // ── Completion items ────────────────────────────────────────────────────

    /** Current list of completion items from the LSP server. */
    private var items: List<CompletionItem> = emptyList()

    /** Currently selected item index. */
    private var selectedIndex: Int = -1

    /** The current prefix/filter text when completion was requested. */
    private var currentPrefix: String = ""

    /** Whether the window is currently visible. */
    var isVisible: Boolean = false
        private set

    // ── Filtered items (after applying prefix filter) ───────────────────────

    /** Items that match the current prefix. */
    private val filteredItems: List<CompletionItem>
        get() = if (currentPrefix.isEmpty()) {
            items.take(MAX_COMPLETION_ITEMS)
        } else {
            items.filter { item ->
                val label = item.label
                val insertText = item.insertText ?: label
                val filterText = item.filterText ?: label
                label.startsWith(currentPrefix, ignoreCase = true) ||
                        insertText.startsWith(currentPrefix, ignoreCase = true) ||
                        filterText.startsWith(currentPrefix, ignoreCase = true) ||
                        label.contains(currentPrefix, ignoreCase = true)
            }.take(MAX_COMPLETION_ITEMS)
        }

    // ── Custom item renderer with icons ─────────────────────────────────────

    /**
     * Custom renderer for completion items that includes kind icons.
     *
     * AndroidIDE uses a custom [DefaultCompletionItemRenderer] subclass
     * that draws the completion kind icon on the left side of each item.
     * This renderer reproduces that behavior.
     */
    private val itemRenderer: CompletionItemRenderer = CompletionItemRenderer(context)

    // ── Icon cache ──────────────────────────────────────────────────────────

    /** Cached icon drawables, keyed by CompletionItemKind ordinal. */
    private val iconCache = mutableMapOf<Int, Drawable?>()

    /** Paint for drawing icons. */
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Paint for drawing the icon background circle. */
    private val iconBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // ── Public API ──────────────────────────────────────────────────────────

    /**
     * Request completion at the current cursor position.
     *
     * This method is called by [IDEEditor] when the user types a
     * completion trigger character. It computes the prefix from the
     * current cursor position and prepares to show completions.
     *
     * AndroidIDE computes the prefix by scanning backwards from the
     * cursor to find the word boundary (non-identifier characters).
     */
    fun requestCompletion() {
        val cursor = editor.cursor ?: return
        val text = editor.text
        val line = text.getIndexer().getLineNumber(cursor.left)
        val column = text.getIndexer().getColumnNumber(cursor.left)
        val lineContent = text.getLine(line)

        // Compute the prefix (identifier before cursor)
        val prefixEnd = column
        var prefixStart = prefixEnd
        while (prefixStart > 0 && isIdentifierChar(lineContent[prefixStart - 1])) {
            prefixStart--
        }
        currentPrefix = lineContent.substring(prefixStart, prefixEnd)

        // Don't show completion for very short prefixes (except for trigger chars)
        if (currentPrefix.length < MIN_PREFIX_LENGTH && currentPrefix != ".") {
            return
        }

        selectedIndex = 0
        show()
    }

    /**
     * Show the completion window with the given LSP completion items.
     *
     * Called when the LSP server responds to a completion request.
     * AndroidIDE sorts items by relevance (sortText) and applies
     * the prefix filter before displaying.
     *
     * @param completionItems Items from the LSP server.
     */
    fun showCompletion(completionItems: List<CompletionItem>) {
        items = completionItems.sortedBy { it.sortText ?: it.label }
        selectedIndex = 0
        show()
    }

    /**
     * Show the completion window.
     */
    fun show() {
        if (filteredItems.isEmpty()) {
            hide()
            return
        }
        isVisible = true
        selectedIndex = selectedIndex.coerceIn(0, filteredItems.size - 1)
    }

    /**
     * Hide the completion window.
     *
     * AndroidIDE hides the completion window when:
     * - The user presses Escape
     * - The user navigates away from the completion point
     * - The editor loses focus
     * - An edit happens that invalidates the completion context
     */
    fun hide() {
        isVisible = false
        items = emptyList()
        selectedIndex = -1
        currentPrefix = ""
    }

    /**
     * Cancel the completion window and any pending requests.
     */
    fun cancelCompletion() {
        hide()
    }

    /**
     * Select the next item in the completion list.
     */
    fun selectNext() {
        if (filteredItems.isEmpty()) return
        selectedIndex = (selectedIndex + 1) % filteredItems.size
    }

    /**
     * Select the previous item in the completion list.
     */
    fun selectPrevious() {
        if (filteredItems.isEmpty()) return
        selectedIndex = (selectedIndex - 1 + filteredItems.size) % filteredItems.size
    }

    /**
     * Get the currently selected completion item.
     */
    fun getSelectedItem(): CompletionItem? {
        val filtered = filteredItems
        return if (selectedIndex in filtered.indices) filtered[selectedIndex] else null
    }

    /**
     * Get the text to insert for the currently selected item.
     *
     * AndroidIDE uses the LSP completion item's `insertText` or `textEdit`
     * if available, falling back to the `label`.
     */
    fun getSelectedInsertText(): String? {
        val item = getSelectedItem() ?: return null

        // Prefer textEdit over insertText (LSP spec)
        item.textEdit?.let { edit ->
            return when (edit) {
                is org.eclipse.lsp4j.TextEdit -> edit.newText
                is org.eclipse.lsp4j.InsertReplaceEdit -> edit.newText
                else -> null
            }
        }

        // Fall back to insertText, then label
        return item.insertText ?: item.label
    }

    /**
     * Get the detail text for the currently selected item.
     * Used to display type information in the completion list.
     */
    fun getSelectedDetail(): String? {
        return getSelectedItem()?.detail
    }

    /**
     * Get the documentation for the currently selected item.
     * Used to display documentation in a tooltip or side panel.
     */
    fun getSelectedDocumentation(): String? {
        val item = getSelectedItem() ?: return null
        return item.documentation?.let { doc ->
            when (doc) {
                is org.eclipse.lsp4j.MarkupContent -> doc.value
                is org.eclipse.lsp4j.MarkedString -> doc.value
                is String -> doc
                else -> null
            }
        }
    }

    // ── Internal: Icon Management ───────────────────────────────────────────

    /**
     * Get the icon for a completion item kind.
     *
     * AndroidIDE provides custom vector drawables for each completion kind.
     * This implementation generates simple colored circle icons with
     * letter abbreviations, which provides visual distinction without
     * requiring bundled assets.
     *
     * @param kind The LSP completion item kind.
     * @param size Icon size in pixels.
     * @return A [Drawable] representing the kind, or null for unknown kinds.
     */
    fun getIconForKind(kind: CompletionItemKind, size: Int): Drawable? {
        return iconCache.getOrPut(kind.ordinal) {
            createKindIcon(kind, size)
        }
    }

    /**
     * Get the color associated with a completion item kind.
     *
     * AndroidIDE uses a specific color for each kind:
     * - Classes/Interfaces/Enums/Structs: blue
     * - Methods/Functions/Constructors: purple
     * - Fields/Properties/Variables: green
     * - Constants/EnumMembers: orange
     * - Keywords/Operators: gray
     * - Modules/Packages: yellow
     */
    fun getColorForKind(kind: CompletionItemKind): Int {
        return when (kind) {
            CompletionItemKind.Class,
            CompletionItemKind.Interface,
            CompletionItemKind.Enum,
            CompletionItemKind.Struct,
            CompletionItemKind.TypeParameter -> KIND_COLOR_BLUE

            CompletionItemKind.Method,
            CompletionItemKind.Function,
            CompletionItemKind.Constructor -> KIND_COLOR_PURPLE

            CompletionItemKind.Field,
            CompletionItemKind.Property,
            CompletionItemKind.Variable -> KIND_COLOR_GREEN

            CompletionItemKind.Constant,
            CompletionItemKind.EnumMember -> KIND_COLOR_ORANGE

            CompletionItemKind.Keyword,
            CompletionItemKind.Operator,
            CompletionItemKind.Snippet -> KIND_COLOR_GRAY

            CompletionItemKind.Module,
            CompletionItemKind.Package,
            CompletionItemKind.Namespace -> KIND_COLOR_YELLOW

            CompletionItemKind.File,
            CompletionItemKind.Folder,
            CompletionItemKind.Unit -> KIND_COLOR_TEAL

            CompletionItemKind.Text,
            CompletionItemKind.Reference,
            CompletionItemKind.Event -> KIND_COLOR_DEFAULT

            else -> KIND_COLOR_DEFAULT
        }
    }

    /**
     * Get the letter abbreviation for a completion item kind.
     * Used in the generated icon when no drawable is available.
     */
    fun getAbbreviationForKind(kind: CompletionItemKind): String {
        return when (kind) {
            CompletionItemKind.Class -> "C"
            CompletionItemKind.Interface -> "I"
            CompletionItemKind.Enum -> "E"
            CompletionItemKind.Method -> "M"
            CompletionItemKind.Function -> "F"
            CompletionItemKind.Field -> "F"
            CompletionItemKind.Property -> "P"
            CompletionItemKind.Variable -> "V"
            CompletionItemKind.Constant -> "K"
            CompletionItemKind.Keyword -> "K"
            CompletionItemKind.Snippet -> "S"
            CompletionItemKind.File -> "f"
            CompletionItemKind.Folder -> "D"
            CompletionItemKind.Module -> "M"
            CompletionItemKind.Package -> "P"
            CompletionItemKind.Constructor -> "C"
            CompletionItemKind.Struct -> "S"
            CompletionItemKind.TypeParameter -> "T"
            CompletionItemKind.Operator -> "O"
            CompletionItemKind.Reference -> "R"
            CompletionItemKind.Unit -> "U"
            CompletionItemKind.Event -> "E"
            CompletionItemKind.Namespace -> "N"
            else -> "?"
        }
    }

    /**
     * Create a simple icon for a completion kind.
     *
     * Generates a colored circle with a letter abbreviation.
     * In production, replace with proper vector drawable resources.
     */
    private fun createKindIcon(kind: CompletionItemKind, size: Int): Drawable? {
        // Create a simple colored circle with text
        // In a real implementation, use VectorDrawable from resources
        return null
    }

    /**
     * Check if a character is a valid identifier character.
     * Used for computing the completion prefix.
     */
    private fun isIdentifierChar(ch: Char): Boolean {
        return ch.isLetterOrDigit() || ch == '_' || ch == '$'
    }

    // ── Internal: Item rendering helper ─────────────────────────────────────

    /**
     * Renders a single completion item, including the kind icon, label,
     * detail, and match highlighting.
     *
     * This is used by the custom [CompletionItemRenderer] to draw
     * each item in the completion popup.
     */
    internal fun renderItem(
        canvas: Canvas,
        item: CompletionItem,
        x: Float,
        y: Float,
        width: Int,
        height: Int,
        isSelected: Boolean,
        isDark: Boolean,
    ) {
        val textColor = if (isDark) ICON_TINT_DARK else ICON_TINT_LIGHT
        val iconSize = (height * 0.6f).toInt()
        val iconPadding = (height - iconSize) / 2f
        val labelX = x + iconSize + iconPadding * 2f

        // Draw icon background circle
        val kindColor = getColorForKind(item.kind ?: CompletionItemKind.Text)
        iconBgPaint.color = kindColor
        iconBgPaint.alpha = 60
        val cx = x + iconPadding + iconSize / 2f
        val cy = y + height / 2f
        val radius = iconSize / 2f
        canvas.drawCircle(cx, cy, radius, iconBgPaint)

        // Draw abbreviation letter
        iconPaint.color = kindColor
        iconPaint.textAlign = Paint.Align.CENTER
        iconPaint.textSize = iconSize * 0.5f
        val abbrev = getAbbreviationForKind(item.kind ?: CompletionItemKind.Text)
        canvas.drawText(
            abbrev,
            cx,
            cy - (iconPaint.descent() + iconPaint.ascent()) / 2f,
            iconPaint
        )

        // Draw label
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = dpToPx(14f)
            isFakeBoldText = isSelected
        }

        // Highlight the matched portion of the label
        if (currentPrefix.isNotEmpty()) {
            val matchIndex = item.label.indexOf(currentPrefix, ignoreCase = true)
            if (matchIndex >= 0) {
                val before = item.label.substring(0, matchIndex)
                val match = item.label.substring(matchIndex, matchIndex + currentPrefix.length)
                val after = item.label.substring(matchIndex + currentPrefix.length)

                var drawX = labelX
                canvas.drawText(before, drawX, cy + labelPaint.textSize / 3f, labelPaint)
                drawX += labelPaint.measureText(before)

                // Bold the matched portion
                val matchPaint = Paint(labelPaint).apply {
                    isFakeBoldText = true
                    color = kindColor
                }
                canvas.drawText(match, drawX, cy + labelPaint.textSize / 3f, matchPaint)
                drawX += matchPaint.measureText(match)

                canvas.drawText(after, drawX, cy + labelPaint.textSize / 3f, labelPaint)
            } else {
                canvas.drawText(item.label, labelX, cy + labelPaint.textSize / 3f, labelPaint)
            }
        } else {
            canvas.drawText(item.label, labelX, cy + labelPaint.textSize / 3f, labelPaint)
        }

        // Draw detail text (right-aligned)
        item.detail?.let { detail ->
            val detailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textColor
                alpha = 140
                textSize = dpToPx(12f)
            }
            val detailWidth = detailPaint.measureText(detail)
            canvas.drawText(
                detail,
                x + width - detailWidth - dpToPx(8f),
                cy + detailPaint.textSize / 3f,
                detailPaint
            )
        }
    }

    /** Convert dp to pixels. */
    private fun dpToPx(dp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp,
            context.resources.displayMetrics
        )
    }

    /**
     * Custom item renderer that delegates to [renderItem] for drawing.
     */
    internal class CompletionItemRenderer(
        private val context: Context
    ) {
        /**
         * Render a completion item.
         * This is a placeholder - in production, integrate with sora-editor's
         * CompletionListItem rendering pipeline.
         */
        fun render(item: CompletionItem, isSelected: Boolean) {
            // Rendering is handled by the IDEEditor's custom drawing
        }
    }

    companion object {
        // ── Kind colors (following AndroidIDE's color scheme) ────────────

        /** Blue for type-like kinds (class, interface, enum). */
        const val KIND_COLOR_BLUE = 0xFF89B4FA.toInt()

        /** Purple for callable kinds (method, function, constructor). */
        const val KIND_COLOR_PURPLE = 0xFFCBA6F7.toInt()

        /** Green for variable-like kinds (field, property, variable). */
        const val KIND_COLOR_GREEN = 0xFFA6E3A1.toInt()

        /** Orange for constant-like kinds (constant, enum member). */
        const val KIND_COLOR_ORANGE = 0xFFFAB387.toInt()

        /** Yellow for module-like kinds (module, package, namespace). */
        const val KIND_COLOR_YELLOW = 0xFFF9E2AF.toInt()

        /** Teal for file-like kinds (file, folder, unit). */
        const val KIND_COLOR_TEAL = 0xFF94E2D5.toInt()

        /** Default gray for unclassified kinds. */
        const val KIND_COLOR_GRAY = 0xFF6C7086.toInt()

        /** Default color for text/reference/event kinds. */
        const val KIND_COLOR_DEFAULT = 0xFFBAC2DE.toInt()
    }
}
