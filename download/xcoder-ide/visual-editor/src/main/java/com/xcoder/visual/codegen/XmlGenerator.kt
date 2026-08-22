/**
 * Generates Android XML layout files from a list of [ViewBean].
 *
 * Based on Sketchware-IA's XmlBuilder (141 lines).
 *
 * Handles proper indentation, self-closing tags, namespace declarations,
 * and round-trips faithfully with [XmlParser]. The tree structure is
 * inferred from parent/id relationships.
 */
package com.xcoder.visual.codegen

import com.xcoder.visual.model.*

object XmlGenerator {

    private const val INDENT_UNIT = "    "
    private const val NS_ANDROID = "http://schemas.android.com/apk/res/android"
    private const val NS_APP = "http://schemas.android.com/apk/res-auto"

    /**
     * Generate a complete Android XML layout string from a flat list of [ViewBean].
     *
     * @param views flat list of parsed/edited ViewBeans (parent relationships define tree)
     * @param extraNamespaces additional namespace declarations beyond android:
     * @return well-formed XML string
     */
    @JvmStatic
    fun generate(
        views: List<ViewBean>,
        extraNamespaces: Map<String, String> = emptyMap()
    ): String {
        if (views.isEmpty()) return emptyLayoutXml()

        // Build parent → children map
        val childrenMap = views.filter { it.parent.isNotBlank() }.groupBy { it.parent }
        val rootViews = views.filter { it.parent.isBlank() }
        if (rootViews.isEmpty()) return emptyLayoutXml()

        val root = rootViews.first()
        val sb = StringBuilder()
        sb.appendLine("<?xml version=\"1.0\" encoding=\"utf-8\"?>")

        // Opening tag of root with namespaces
        sb.append("<${root.simpleClassName}")
        sb.appendLine("\n    xmlns:android=\"$NS_ANDROID\"")
        if (extraNamespaces.isNotEmpty()) {
            for ((prefix, uri) in extraNamespaces) {
                sb.appendLine("    xmlns:$prefix=\"$uri\"")
            }
        }
        // Include app: namespace if using ConstraintLayout or Material components
        if (root.type == ViewBean.TYPE_CONSTRAINT_LAYOUT ||
            root.type == ViewBean.TYPE_MATERIAL_CARD_VIEW ||
            root.type == ViewBean.TYPE_COORDINATOR_LAYOUT
        ) {
            sb.appendLine("    xmlns:app=\"$NS_APP\"")
        }

        appendAttributes(sb, root, 1, true)

        val children = (childrenMap[root.id] ?: emptyList()).sortedBy { it.index }
        if (children.isEmpty() && !hasTextContent(root) && !needsClosingTag(root)) {
            sb.append("    />\n")
        } else {
            sb.appendLine(">")
            for (child in children) {
                serializeViewBean(sb, child, childrenMap, 2)
            }
            sb.append("</${root.simpleClassName}>\n")
        }

        return sb.toString()
    }

    /**
     * Generate XML for a single [ViewBean] subtree (useful for copy/paste or partial export).
     */
    @JvmStatic
    fun generateSingle(bean: ViewBean, children: List<ViewBean> = emptyList()): String {
        val childrenMap = children.filter { it.parent == bean.id }.groupBy { it.parent }
        val sb = StringBuilder()
        val directChildren = (childrenMap[bean.id] ?: emptyList()).sortedBy { it.index }
        serializeViewBean(sb, bean, childrenMap, 0, directChildren)
        return sb.toString().trimEnd()
    }

    // ── Internal ──────────────────────────────────────────────────────

    private fun serializeViewBean(
        sb: StringBuilder,
        bean: ViewBean,
        allChildren: Map<String, List<ViewBean>>,
        indent: Int,
        precomputedChildren: List<ViewBean>? = null
    ) {
        val prefix = INDENT_UNIT.repeat(indent)
        val children = precomputedChildren
            ?: (allChildren[bean.id] ?: emptyList()).sortedBy { it.index }

        sb.append("$prefix<${bean.simpleClassName}")

        val isRoot = indent == 1
        appendAttributes(sb, bean, indent, isRoot)

        if (children.isEmpty() && !hasTextContent(bean) && !needsClosingTag(bean)) {
            sb.append(" />\n")
        } else {
            sb.appendLine(">")
            if (hasTextContent(bean)) {
                sb.append("$prefix$INDENT_UNIT${escapeXml(bean.text.text)}\n")
            }
            for (child in children) {
                serializeViewBean(sb, child, allChildren, indent + 1)
            }
            sb.append("$prefix</${bean.simpleClassName}>\n")
        }
    }

    /**
     * Append all android: attributes for a [ViewBean].
     */
    private fun appendAttributes(
        sb: StringBuilder,
        bean: ViewBean,
        indent: Int,
        isRoot: Boolean
    ) {
        val prefix = INDENT_UNIT.repeat(indent)
        val nl = "\n$prefix    "

        val attrs = mutableListOf<Pair<String, String>>()

        // ID (skip namespace for root element id, already in opening tag area)
        attrs.add("android:id" to "@+id/${bean.id}")

        // Layout dimensions
        attrs.add("android:layout_width" to bean.layout.widthToXml())
        attrs.add("android:layout_height" to bean.layout.heightToXml())

        // Layout orientation (LinearLayout)
        if (bean.type == ViewBean.TYPE_LINEAR_LAYOUT) {
            val orient = if (bean.layout.orientation == LayoutBean.ORIENTATION_HORIZONTAL) "horizontal" else "vertical"
            attrs.add("android:orientation" to orient)
            if (bean.layout.weightSum >= 0f) {
                attrs.add("android:weightSum" to bean.layout.weightSum.toString())
            }
        }

        // Gravity
        val gravStr = bean.layout.gravityToXml()
        if (gravStr != "none") attrs.add("android:gravity" to gravStr)

        val lgStr = bean.layout.layoutGravityToXml()
        if (lgStr != "none") attrs.add("android:layout_gravity" to lgStr)

        // Weight
        if (bean.layout.weight > 0f) {
            attrs.add("android:layout_weight" to bean.layout.weight.toString())
        }

        // Padding
        val allSamePadding = bean.layout.paddingLeft == bean.layout.paddingTop &&
                bean.layout.paddingTop == bean.layout.paddingRight &&
                bean.layout.paddingRight == bean.layout.bottomPadding
        if (allSamePadding && bean.layout.paddingLeft > 0) {
            attrs.add("android:padding" to bean.layout.paddingLeftToXml())
        } else {
            if (bean.layout.paddingLeft > 0) attrs.add("android:paddingLeft" to bean.layout.paddingLeftToXml())
            if (bean.layout.paddingTop > 0) attrs.add("android:paddingTop" to bean.layout.paddingTopToXml())
            if (bean.layout.paddingRight > 0) attrs.add("android:paddingRight" to bean.layout.paddingRightToXml())
            if (bean.layout.bottomPadding > 0) attrs.add("android:paddingBottom" to bean.layout.bottomPaddingToXml())
        }

        // Margin
        val allSameMargin = bean.layout.marginLeft == bean.layout.marginTop &&
                bean.layout.marginTop == bean.layout.marginRight &&
                bean.layout.marginRight == bean.layout.marginBottom
        if (allSameMargin && bean.layout.marginLeft > 0) {
            attrs.add("android:layout_margin" to bean.layout.marginLeftToXml())
        } else {
            if (bean.layout.marginLeft > 0) attrs.add("android:layout_marginLeft" to bean.layout.marginLeftToXml())
            if (bean.layout.marginTop > 0) attrs.add("android:layout_marginTop" to bean.layout.marginTopToXml())
            if (bean.layout.marginRight > 0) attrs.add("android:layout_marginRight" to bean.layout.marginRightToXml())
            if (bean.layout.marginBottom > 0) attrs.add("android:layout_marginBottom" to bean.layout.marginBottomToXml())
        }

        // Background
        if (bean.layout.backgroundResource.isNotEmpty()) {
            attrs.add("android:background" to bean.layout.backgroundResource)
        } else if (bean.layout.backgroundColor != 0x00000000) {
            attrs.add("android:background" to colorToHex(bean.layout.backgroundColor))
        }
        if (bean.layout.borderColor != 0) {
            attrs.add("android:background" to colorToHex(bean.layout.borderColor))
        }

        // Transform
        if (!bean.enabled) attrs.add("android:enabled" to "false")
        if (bean.clickable) attrs.add("android:clickable" to "true")
        if (bean.alpha != 1f) attrs.add("android:alpha" to bean.alpha.toString())
        if (bean.translationX != 0f) attrs.add("android:translationX" to "${bean.translationX}dp")
        if (bean.translationY != 0f) attrs.add("android:translationY" to "${bean.translationY}dp")
        if (bean.scaleX != 1f) attrs.add("android:scaleX" to bean.scaleX.toString())
        if (bean.scaleY != 1f) attrs.add("android:scaleY" to bean.scaleY.toString())

        // Text properties
        if (bean.isTextWidget) {
            val tb = bean.text
            if (tb.text.isNotEmpty()) attrs.add("android:text" to escapeXml(tb.text))
            if (tb.textSize != 14f) attrs.add("android:textSize" to "${tb.textSize}sp")
            if (tb.textColor != 0xFF000000.toInt()) attrs.add("android:textColor" to colorToHex(tb.textColor))
            val style = tb.textStyleToXml()
            if (style != "normal") attrs.add("android:textStyle" to style)
            if (tb.fontFamily.isNotEmpty()) attrs.add("android:fontFamily" to tb.fontFamily)
            if (tb.textAlign != "view_start" && tb.textAlign != "none") {
                attrs.add("android:textAlignment" to tb.textAlign)
            }
            if (tb.hint.isNotEmpty()) attrs.add("android:hint" to escapeXml(tb.hint))
            if (tb.hintColor != 0x80808080) attrs.add("android:textColorHint" to colorToHex(tb.hintColor))
            if (tb.maxLines != Integer.MAX_VALUE && tb.maxLines > 0) {
                attrs.add("android:maxLines" to tb.maxLines.toString())
            }
            if (tb.ellipsize != "none") attrs.add("android:ellipsize" to tb.ellipsize)
            if (tb.lineSpacing != 1f) attrs.add("android:lineSpacingMultiplier" to tb.lineSpacing.toString())
            if (tb.letterSpacing != 0f) attrs.add("android:letterSpacing" to tb.letterSpacing.toString())
        }

        // Image properties
        if (bean.isImageWidget) {
            val ib = bean.image
            if (ib.src.isNotEmpty()) attrs.add("android:src" to ib.src)
            if (ib.scaleType != "fitCenter") attrs.add("android:scaleType" to ib.scaleType)
            if (ib.tint != 0) attrs.add("android:tint" to colorToHex(ib.tint))
            if (ib.contentDescription.isNotEmpty()) attrs.add("android:contentDescription" to escapeXml(ib.contentDescription))
            if (ib.adjustViewBounds) attrs.add("android:adjustViewBounds" to "true")
            if (ib.cropToPadding) attrs.add("android:cropToPadding" to "true")
        }

        // ProgressBar specifics
        if (bean.type == ViewBean.TYPE_PROGRESS_BAR) {
            attrs.add("android:indeterminate" to "true")
        }

        // Write all attributes
        for ((key, value) in attrs) {
            sb.append("$nl$key=\"$value\"")
        }
    }

    private fun hasTextContent(bean: ViewBean): Boolean =
        bean.isTextWidget && bean.text.text.isNotEmpty()

    private fun needsClosingTag(bean: ViewBean): Boolean {
        // Layouts always need closing tags (even if empty, for potential child adding)
        return bean.isLayout
    }

    private fun escapeXml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    /** Convert 0xAARRGGBB to #AARRGGBB hex string. */
    private fun colorToHex(color: Int): String {
        return "#${color.toUInt().toString(16).uppercase().padStart(8, '0')}"
    }

    /** Generate a minimal valid empty layout. */
    @JvmStatic
    fun emptyLayoutXml(): String = """<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/rootLayout"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical" />
""".trimIndent() + "\n"
}
