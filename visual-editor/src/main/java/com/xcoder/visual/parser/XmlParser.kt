/**
 * Parses Android XML layout files into a flat list of [ViewBean].
 *
 * Based on Sketchware-IA's ViewBeanParser (246 lines).
 * Uses [android.content.res.XmlResourceParser] / [org.xmlpull.v1.XmlPullParser]
 * for SAX-style parsing. Nesting is resolved via a [Stack] so that each child
 * knows its parent id, parent type, and sibling index.
 *
 * Unique IDs are auto-generated for views that lack an `android:id`.
 */
package com.xcoder.visual.parser

import com.xcoder.visual.model.*
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.io.Reader
import java.io.StringReader
import java.util.*

/**
 * Result of parsing an XML layout file.
 */
data class ParseResult(
    val views: List<ViewBean>,
    val warnings: List<String> = emptyList()
)

object XmlParser {

    // Counter for generating unique IDs when a view lacks one.
    private var idCounter = 0L

    // Set of known XML tags we care about (Android view/widget classes).
    private val KNOWN_TAGS: Set<String> = setOf(
        "LinearLayout", "RelativeLayout", "FrameLayout",
        "ConstraintLayout", "ScrollView", "HorizontalScrollView",
        "RecyclerView", "CoordinatorLayout", "CardView", "MaterialCardView",
        "Button", "TextView", "EditText", "ImageView", "CheckBox",
        "RadioButton", "Switch", "ProgressBar", "SeekBar", "Spinner",
        "WebView", "VideoView", "RatingBar", "ToggleButton", "ImageButton",
        "TextClock", "Chronometer", "View", "Space",
        // Also accept fully-qualified names
        "androidx.constraintlayout.widget.ConstraintLayout",
        "androidx.recyclerview.widget.RecyclerView",
        "androidx.coordinatorlayout.widget.CoordinatorLayout",
        "androidx.cardview.widget.CardView",
        "com.google.android.material.card.MaterialCardView",
        "androidx.constraintlayout.widget.Guideline"
    )

    // Map of simple tag name → child index counter per parent.
    private val childIndexCounters = mutableMapOf<String, Int>()

    /**
     * Parse an XML string into a list of [ViewBean].
     *
     * @param xml the complete XML layout string
     * @return [ParseResult] containing all parsed views and any warnings
     */
    @JvmStatic
    fun parse(xml: String): ParseResult {
        idCounter = 0L
        childIndexCounters.clear()
        return parseInternal(StringReader(xml))
    }

    /**
     * Parse from an [InputStream].
     */
    @JvmStatic
    fun parse(inputStream: InputStream): ParseResult {
        idCounter = 0L
        childIndexCounters.clear()
        return parseInternal(inputStream.reader())
    }

    /**
     * Parse from a [Reader].
     */
    @JvmStatic
    fun parse(reader: Reader): ParseResult {
        idCounter = 0L
        childIndexCounters.clear()
        return parseInternal(reader)
    }

    // ── Internal implementation ───────────────────────────────────────

    private fun parseInternal(reader: Reader): ParseResult {
        val views = mutableListOf<ViewBean>()
        val warnings = mutableListOf<String>()
        val stack = Stack<ViewBean>() // nesting stack

        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(reader)

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val tagName = parser.name
                        if (!isKnownTag(tagName)) {
                            // Unknown tag — skip children
                            skipTag(parser)
                            eventType = parser.eventType
                            continue
                        }

                        val type = ViewBean.fromTagName(tagName)
                        if (type == null) {
                            warnings.add("Unknown tag: $tagName")
                            skipTag(parser)
                            eventType = parser.eventType
                            continue
                        }

                        val bean = ViewBean(type = type)

                        // Hierarchy
                        if (stack.isNotEmpty()) {
                            val parentBean = stack.peek()
                            bean.parent = parentBean.id
                            bean.parentType = parentBean.type
                            val key = parentBean.id
                            bean.index = (childIndexCounters[key] ?: 0)
                            childIndexCounters[key] = bean.index + 1
                        } else {
                            bean.index = (childIndexCounters[""] ?: 0)
                            childIndexCounters[""] = bean.index + 1
                        }

                        // Collect XML attributes
                        collectAttributes(parser, bean)

                        views.add(bean)
                        if (ViewBean.isLayout(type)) {
                            stack.push(bean)
                        }
                    }

                    XmlPullParser.END_TAG -> {
                        val tagName = parser.name
                        val type = ViewBean.fromTagName(tagName)
                        if (type != null && ViewBean.isLayout(type) && stack.isNotEmpty()) {
                            stack.pop()
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            warnings.add("Parse error: ${e.message}")
        }

        return ParseResult(views, warnings)
    }

    /**
     * Collect all relevant XML attributes into the [ViewBean].
     */
    private fun collectAttributes(parser: XmlPullParser, bean: ViewBean) {
        for (i in 0 until parser.attributeCount) {
            val ns = parser.getAttributeNamespace(i)
            val name = parser.getAttributeName(i)
            val value = parser.getAttributeValue(i)

            // Only process android: namespace attributes (or no-namespace)
            if (ns.isNotEmpty() && ns != "http://schemas.android.com/apk/res/android") continue

            when (name) {
                "id" -> {
                    bean.id = value.removePrefix("@").removePrefix("+id/")
                    bean.name = bean.id
                }
                // ── Enabled / Clickable ──
                "enabled" -> bean.enabled = value.toBooleanStrictOrNull() ?: true
                "clickable" -> bean.clickable = value.toBooleanStrictOrNull() ?: false
                // ── Alpha ──
                "alpha" -> bean.alpha = value.toFloatOrNull() ?: 1f
                // ── Translation ──
                "translationX" -> bean.translationX = parseDimension(value).toFloat()
                "translationY" -> bean.translationY = parseDimension(value).toFloat()
                // ── Scale ──
                "scaleX" -> bean.scaleX = value.toFloatOrNull() ?: 1f
                "scaleY" -> bean.scaleY = value.toFloatOrNull() ?: 1f
                // ── Layout properties ──
                "layout_width" -> bean.layout.width = parseLayoutDimension(value)
                "layout_height" -> bean.layout.height = parseLayoutDimension(value)
                "orientation" -> bean.layout.orientation =
                    if (value == "horizontal") LayoutBean.ORIENTATION_HORIZONTAL
                    else LayoutBean.ORIENTATION_VERTICAL
                "gravity" -> bean.layout.gravity = parseGravity(value)
                "layout_gravity" -> bean.layout.layoutGravity = parseGravity(value)
                "padding" -> {
                    val px = parseDimension(value)
                    bean.layout.paddingLeft = px
                    bean.layout.paddingTop = px
                    bean.layout.paddingRight = px
                    bean.layout.bottomPadding = px
                }
                "paddingLeft" -> bean.layout.paddingLeft = parseDimension(value)
                "paddingTop" -> bean.layout.paddingTop = parseDimension(value)
                "paddingRight" -> bean.layout.paddingRight = parseDimension(value)
                "paddingBottom" -> bean.layout.bottomPadding = parseDimension(value)
                "paddingStart" -> bean.layout.paddingLeft = parseDimension(value)
                "paddingEnd" -> bean.layout.paddingRight = parseDimension(value)
                "layout_margin" -> {
                    val px = parseDimension(value)
                    bean.layout.marginLeft = px
                    bean.layout.marginTop = px
                    bean.layout.marginRight = px
                    bean.layout.marginBottom = px
                }
                "layout_marginLeft" -> bean.layout.marginLeft = parseDimension(value)
                "layout_marginTop" -> bean.layout.marginTop = parseDimension(value)
                "layout_marginRight" -> bean.layout.marginRight = parseDimension(value)
                "layout_marginBottom" -> bean.layout.marginBottom = parseDimension(value)
                "layout_marginStart" -> bean.layout.marginLeft = parseDimension(value)
                "layout_marginEnd" -> bean.layout.marginRight = parseDimension(value)
                "layout_weight" -> bean.layout.weight = value.toFloatOrNull() ?: 0f
                "weightSum" -> bean.layout.weightSum = value.toFloatOrNull() ?: -1f
                "background" -> {
                    val color = parseColor(value)
                    if (color != null) bean.layout.backgroundColor = color
                    else bean.layout.backgroundResource = value
                }
                "backgroundColor" -> parseColor(value)?.let { bean.layout.backgroundColor = it }
                // ── Text properties ──
                "text" -> bean.text.text = value
                "textSize" -> bean.text.textSize = parseTextSize(value)
                "textColor" -> parseColor(value)?.let { bean.text.textColor = it }
                "textStyle" -> {
                    val parts = value.split("|")
                    bean.text.bold = "bold" in parts
                    bean.text.italic = "italic" in parts
                }
                "fontFamily" -> bean.text.fontFamily = value
                "textAlignment" -> bean.text.textAlign = gravityToTextAlign(value)
                "gravity" -> { /* already handled for layout gravity; also sets text gravity */
                    bean.text.textAlign = gravityToTextAlign(value)
                }
                "hint" -> bean.text.hint = value
                "textColorHint" -> parseColor(value)?.let { bean.text.hintColor = it }
                "maxLines" -> bean.text.maxLines = value.toIntOrNull() ?: Integer.MAX_VALUE
                "ellipsize" -> bean.text.ellipsize = value
                "lineSpacingMultiplier" -> bean.text.lineSpacing = value.toFloatOrNull() ?: 1f
                "letterSpacing" -> bean.text.letterSpacing = value.toFloatOrNull() ?: 0f
                // ── Image properties ──
                "src" -> bean.image.src = value
                "scaleType" -> bean.image.scaleType = value
                "tint" -> parseColor(value)?.let { bean.image.tint = it }
                "contentDescription" -> bean.image.contentDescription = value
                "adjustViewBounds" -> bean.image.adjustViewBounds = value.toBooleanStrictOrNull() ?: false
                "cropToPadding" -> bean.image.cropToPadding = value.toBooleanStrictOrNull() ?: false
            }
        }

        // Auto-generate an ID if none was set
        if (bean.id.isBlank()) {
            idCounter++
            bean.id = "${bean.simpleClassName.lowercase()}${idCounter}"
            bean.name = bean.id
        }
    }

    // ── XML helpers ───────────────────────────────────────────────────

    private fun isKnownTag(tag: String): Boolean {
        val simple = tag.substringAfterLast('.')
        return KNOWN_TAGS.any { it.substringAfterLast('.') == simple || it == tag }
    }

    /** Skip to the matching END_TAG for the current START_TAG. */
    private fun skipTag(parser: XmlPullParser) {
        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
            }
        }
    }

    // ── Value parsers ─────────────────────────────────────────────────

    /** Parse a layout dimension (match_parent, wrap_content, or Ndp/Npx/Nsp). */
    private fun parseLayoutDimension(value: String): Int = when (value) {
        "match_parent", "fill_parent" -> LayoutBean.MATCH_PARENT
        "wrap_content" -> LayoutBean.WRAP_CONTENT
        else -> parseDimension(value)
    }

    /** Parse a dimension string (e.g. "16dp", "8px") to pixels (approximate). */
    private fun parseDimension(value: String): Int {
        val num = value.filter { it.isDigit() || it == '.' || it == '-' }.toFloatOrNull() ?: return 0
        return num.toInt()
    }

    /** Parse a text size (e.g. "14sp") to SP float. */
    private fun parseTextSize(value: String): Float {
        return value.filter { it.isDigit() || it == '.' || it == '-' }.toFloatOrNull() ?: 14f
    }

    /** Parse a color string (#RRGGBB, #AARRGGBB, @color/name). */
    private fun parseColor(value: String): Int? {
        if (value.startsWith("@")) return null // resource reference, not a literal color
        return try {
            when {
                value.startsWith("#") && value.length == 7 -> {
                    // #RRGGBB → add full alpha
                    val rgb = value.substring(1).toLong(16)
                    (0xFF000000.toInt() or rgb.toInt())
                }
                value.startsWith("#") && value.length == 9 -> {
                    // #AARRGGBB
                    value.substring(1).toLong(16).toInt()
                }
                else -> android.graphics.Color.parseColor(value)
            }
        } catch (_: Exception) { null }
    }

    /** Parse an android:gravity string into a bitmask. */
    private fun parseGravity(value: String): Int {
        if (value.isBlank() || value == "none") return LayoutBean.GRAVITY_NONE
        var mask = LayoutBean.GRAVITY_NONE
        val parts = value.split("|").map { it.trim() }
        for (part in parts) {
            mask = mask or when (part) {
                "top" -> LayoutBean.GRAVITY_TOP
                "bottom" -> LayoutBean.GRAVITY_BOTTOM
                "left" -> LayoutBean.GRAVITY_LEFT
                "right" -> LayoutBean.GRAVITY_RIGHT
                "center_vertical" -> LayoutBean.GRAVITY_CENTER_VERTICAL
                "center_horizontal" -> LayoutBean.GRAVITY_CENTER_HORIZONTAL
                "center" -> LayoutBean.GRAVITY_CENTER
                "start" -> LayoutBean.GRAVITY_START
                "end" -> LayoutBean.GRAVITY_END
                "clip_vertical" -> 0x80
                "clip_horizontal" -> 0x08
                else -> continue
            }
        }
        return mask
    }

    /** Convert a gravity value to textAlignment string. */
    private fun gravityToTextAlign(gravity: String): String = when {
        "center" in gravity -> "center"
        "right" in gravity || "end" in gravity -> "view_end"
        "left" in gravity || "start" in gravity -> "view_start"
        else -> "view_start"
    }
}
