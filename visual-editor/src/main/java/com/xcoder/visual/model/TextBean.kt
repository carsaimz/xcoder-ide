package com.xcoder.visual.model

import android.os.Parcel
import android.os.Parcelable

/**
 * Text properties model for widgets that display text.
 * Based on Sketchware-IA's text attribute handling in ViewBean.
 *
 * Used by TextView, EditText, Button, CheckBox, RadioButton, Switch, etc.
 */
data class TextBean(
    /** Display text content */
    var text: String = "",
    /** Text size in SP (scaled pixels) */
    var textSize: Float = 14f,
    /** Text color as 0xAARRGGBB */
    var textColor: Int = 0xFF000000.toInt(),
    /** Whether text is bold */
    var bold: Boolean = false,
    /** Whether text is italic */
    var italic: Boolean = false,
    /** Font family name (system font name, or path to .ttf/.otf) */
    var fontFamily: String = "",
    /** Text alignment: "start", "center", "end", "view_start", "view_end", "text_start", "text_end" */
    var textAlign: String = "view_start",
    /** Hint text (primarily for EditText) */
    var hint: String = "",
    /** Hint color as 0xAARRGGBB */
    var hintColor: Int = 0x80808080.toInt(),
    /** Maximum number of lines. 0 or negative means no limit. */
    var maxLines: Int = Integer.MAX_VALUE,
    /** Ellipsize mode: "none", "start", "middle", "end", "marquee" */
    var ellipsize: String = "none",
    /** Extra line spacing multiplier (e.g. 1.2f for 20% extra) */
    var lineSpacing: Float = 1f,
    /** Letter spacing in EM units (e.g. 0.05f) */
    var letterSpacing: Float = 0f
) : Parcelable {

    companion object {
        @JvmField val CREATOR: Parcelable.Creator<TextBean> = object : Parcelable.Creator<TextBean> {
            override fun createFromParcel(source: Parcel): TextBean = TextBean(source)
            override fun newArray(size: Int): Array<TextBean?> = arrayOfNulls(size)
        }
    }

    constructor(source: Parcel) : this(
        text = source.readString() ?: "",
        textSize = source.readFloat(),
        textColor = source.readInt(),
        bold = source.readInt() != 0,
        italic = source.readInt() != 0,
        fontFamily = source.readString() ?: "",
        textAlign = source.readString() ?: "view_start",
        hint = source.readString() ?: "",
        hintColor = source.readInt(),
        maxLines = source.readInt(),
        ellipsize = source.readString() ?: "none",
        lineSpacing = source.readFloat(),
        letterSpacing = source.readFloat()
    )

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.apply {
            writeString(text)
            writeFloat(textSize)
            writeInt(textColor)
            writeInt(if (bold) 1 else 0)
            writeInt(if (italic) 1 else 0)
            writeString(fontFamily)
            writeString(textAlign)
            writeString(hint)
            writeInt(hintColor)
            writeInt(maxLines)
            writeString(ellipsize)
            writeFloat(lineSpacing)
            writeFloat(letterSpacing)
        }
    }

    override fun describeContents(): Int = 0

    /** Returns the android:textStyle XML value: "normal", "bold", "italic", or "bold|italic". */
    fun textStyleToXml(): String = when {
        bold && italic -> "bold|italic"
        bold -> "bold"
        italic -> "italic"
        else -> "normal"
    }

    /** Returns true if this TextBean has any non-default text properties. */
    fun isNotEmpty(): Boolean =
        text.isNotEmpty() || textSize != 14f || textColor != 0xFF000000.toInt() ||
                bold || italic || fontFamily.isNotEmpty() || textAlign != "view_start" ||
                hint.isNotEmpty() || maxLines != Integer.MAX_VALUE ||
                ellipsize != "none" || lineSpacing != 1f || letterSpacing != 0f

    /** Deep clone. */
    fun clone(): TextBean = copy()
}
