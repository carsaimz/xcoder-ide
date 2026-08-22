package com.xcoder.visual.model

import android.os.Parcel
import android.os.Parcelable

/**
 * Layout properties model for a widget in the visual editor.
 * Based on Sketchware-IA's LayoutBean.java.
 *
 * Encapsulates all `android:layout_*` and standard View dimension/spacing attributes.
 */
data class LayoutBean(
    /** Width in pixels, or [MATCH_PARENT] / [WRAP_CONTENT] */
    var width: Int = WRAP_CONTENT,
    /** Height in pixels, or [MATCH_PARENT] / [WRAP_CONTENT] */
    var height: Int = WRAP_CONTENT,
    /** LinearLayout orientation: [ORIENTATION_VERTICAL] or [ORIENTATION_HORIZONTAL] */
    var orientation: Int = ORIENTATION_VERTICAL,
    /** Gravity of content within the view (bitmask, e.g. [GRAVITY_CENTER]) */
    var gravity: Int = GRAVITY_NONE,
    /** Gravity controlling how this view is placed in its parent */
    var layoutGravity: Int = GRAVITY_NONE,
    /** Left padding in pixels */
    var paddingLeft: Int = 0,
    /** Top padding in pixels */
    var paddingTop: Int = 0,
    /** Right padding in pixels */
    var paddingRight: Int = 0,
    /** Bottom padding in pixels */
    var bottomPadding: Int = 0,
    /** Left margin in pixels */
    var marginLeft: Int = 0,
    /** Top margin in pixels */
    var marginTop: Int = 0,
    /** Right margin in pixels */
    var marginRight: Int = 0,
    /** Bottom margin in pixels */
    var marginBottom: Int = 0,
    /** Weight used in LinearLayout */
    var weight: Float = 0f,
    /** WeightSum for LinearLayout */
    var weightSum: Float = -1f,
    /** Background color as 0xAARRGGBB */
    var backgroundColor: Int = 0x00000000,
    /** Border (stroke) color as 0xAARRGGBB, 0 means no border */
    var borderColor: Int = 0,
    /** Background resource name (e.g. "@drawable/bg_rounded") */
    var backgroundResource: String = ""
) : Parcelable {

    companion object {
        @JvmField val MATCH_PARENT: Int = -1
        @JvmField val WRAP_CONTENT: Int = -2

        // Orientation constants (matching LinearLayout constants)
        @JvmField val ORIENTATION_HORIZONTAL: Int = 0
        @JvmField val ORIENTATION_VERTICAL: Int = 1

        // Gravity bitmasks (matching android.view.Gravity)
        @JvmField val GRAVITY_NONE: Int = 0
        @JvmField val GRAVITY_TOP: Int = 0x30            // 48
        @JvmField val GRAVITY_BOTTOM: Int = 0x50         // 80
        @JvmField val GRAVITY_LEFT: Int = 0x03            // 3
        @JvmField val GRAVITY_RIGHT: Int = 0x05           // 5
        @JvmField val GRAVITY_CENTER_VERTICAL: Int = 0x10 // 16
        @JvmField val GRAVITY_CENTER_HORIZONTAL: Int = 0x01 // 1
        @JvmField val GRAVITY_CENTER: Int = 0x11          // 17
        @JvmField val GRAVITY_START: Int = 0x00800003     // 8388659
        @JvmField val GRAVITY_END: Int = 0x00800005       // 8388661

        @JvmField val CREATOR: Parcelable.Creator<LayoutBean> = object : Parcelable.Creator<LayoutBean> {
            override fun createFromParcel(source: Parcel): LayoutBean = LayoutBean(source)
            override fun newArray(size: Int): Array<LayoutBean?> = arrayOfNulls(size)
        }
    }

    constructor(source: Parcel) : this(
        width = source.readInt(),
        height = source.readInt(),
        orientation = source.readInt(),
        gravity = source.readInt(),
        layoutGravity = source.readInt(),
        paddingLeft = source.readInt(),
        paddingTop = source.readInt(),
        paddingRight = source.readInt(),
        bottomPadding = source.readInt(),
        marginLeft = source.readInt(),
        marginTop = source.readInt(),
        marginRight = source.readInt(),
        marginBottom = source.readInt(),
        weight = source.readFloat(),
        weightSum = source.readFloat(),
        backgroundColor = source.readInt(),
        borderColor = source.readInt(),
        backgroundResource = source.readString() ?: ""
    )

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.apply {
            writeInt(width)
            writeInt(height)
            writeInt(orientation)
            writeInt(gravity)
            writeInt(layoutGravity)
            writeInt(paddingLeft)
            writeInt(paddingTop)
            writeInt(paddingRight)
            writeInt(bottomPadding)
            writeInt(marginLeft)
            writeInt(marginTop)
            writeInt(marginRight)
            writeInt(marginBottom)
            writeFloat(weight)
            writeFloat(weightSum)
            writeInt(backgroundColor)
            writeInt(borderColor)
            writeString(backgroundResource)
        }
    }

    override fun describeContents(): Int = 0

    /**
     * Convert dimension value to XML attribute string.
     * [MATCH_PARENT] → "match_parent", [WRAP_CONTENT] → "wrap_content", else → "{value}dp"
     */
    fun widthToXml(): String = dimensionToXml(width)
    fun heightToXml(): String = dimensionToXml(height)

    /** Convert margin/padding pixel value to XML dp string (assumes mdpi baseline). */
    fun paddingLeftToXml(): String = pxToDp(paddingLeft)
    fun paddingTopToXml(): String = pxToDp(paddingTop)
    fun paddingRightToXml(): String = pxToDp(paddingRight)
    fun bottomPaddingToXml(): String = pxToDp(bottomPadding)
    fun marginLeftToXml(): String = pxToDp(marginLeft)
    fun marginTopToXml(): String = pxToDp(marginTop)
    fun marginRightToXml(): String = pxToDp(marginRight)
    fun marginBottomToXml(): String = pxToDp(marginBottom)

    /** Convert gravity bitmask to a comma-separated XML gravity string. */
    fun gravityToXml(): String = gravityFlagsToXml(gravity)
    fun layoutGravityToXml(): String = gravityFlagsToXml(layoutGravity)

    /** Deep clone. */
    fun clone(): LayoutBean = copy()

    // ---- internal helpers ----

    private fun dimensionToXml(value: Int): String = when (value) {
        MATCH_PARENT -> "match_parent"
        WRAP_CONTENT -> "wrap_content"
        else -> "${value}dp"
    }

    private fun pxToDp(px: Int): String = if (px == 0) "0dp" else "${px}dp"

    private fun gravityFlagsToXml(gravity: Int): String {
        if (gravity == GRAVITY_NONE) return "none"
        val parts = mutableListOf<String>()
        if (gravity and GRAVITY_LEFT != 0) parts.add("left")
        if (gravity and GRAVITY_RIGHT != 0) parts.add("right")
        if (gravity and GRAVITY_TOP != 0) parts.add("top")
        if (gravity and GRAVITY_BOTTOM != 0) parts.add("bottom")
        if (gravity and GRAVITY_CENTER_HORIZONTAL != 0 && gravity and GRAVITY_CENTER_VERTICAL != 0) {
            parts.clear()
            parts.add("center")
        } else {
            if (gravity and GRAVITY_CENTER_VERTICAL != 0) parts.add("center_vertical")
            if (gravity and GRAVITY_CENTER_HORIZONTAL != 0) parts.add("center_horizontal")
        }
        if (gravity and GRAVITY_START != 0) parts.add("start")
        if (gravity and GRAVITY_END != 0) parts.add("end")
        return if (parts.isEmpty()) "none" else parts.joinToString("|")
    }
}
