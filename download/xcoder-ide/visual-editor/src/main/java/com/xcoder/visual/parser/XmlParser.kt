package com.xcoder.visual.parser

import android.content.Context
import com.xcoder.visual.*
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

/**
 * Parses Android XML layout files into visual block representations.
 */
object XmlParser {

    data class ParsedView(
        val type: String,
        val id: String,
        val text: String?,
        val attributes: Map<String, String>
    )

    fun parse(xml: String): List<ParsedView> {
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))
        val views = mutableListOf<ParsedView>()
        var currentAttrs = mutableMapOf<String, String>()
        var currentType = ""
        var depth = 0

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    currentType = parser.name
                    depth++
                    currentAttrs = mutableMapOf()
                    for (i in 0 until parser.attributeCount) {
                        currentAttrs[parser.getAttributeName(i)] = parser.getAttributeValue(i)
                    }
                }
                XmlPullParser.TEXT -> {
                    val text = parser.text?.trim()
                    if (!text.isNullOrEmpty() && depth > 1 && currentType in VIEW_TYPES) {
                        val id = currentAttrs["android:id"]?.removePrefix("@+id/") ?: ""
                        views.add(ParsedView(currentType, id, text, currentAttrs.toMap()))
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name in VIEW_TYPES && views.none { it.type == currentType && it.id == (currentAttrs["android:id"]?.removePrefix("@+id/") ?: "") && it.text != null }) {
                        val id = currentAttrs["android:id"]?.removePrefix("@+id/") ?: ""
                        views.add(ParsedView(currentType, id, null, currentAttrs.toMap()))
                    }
                    depth--
                }
            }
            eventType = parser.next()
        }
        return views
    }

    fun toBlocks(parsedViews: List<ParsedView>): List<PlacedBlock> {
        return parsedViews.mapIndexed { index, view ->
            val blockDefId = when (view.type) {
                "TextView" -> "ui_textview"
                "EditText" -> "ui_edittext"
                "Button" -> "ui_button"
                "ImageView" -> "ui_imageview"
                else -> "ui_textview"
            }
            val definition = BlockDefinitions.findById(blockDefId) ?: BlockDefinitions.allBlocks.first()
            val values = mutableMapOf(
                "id" to view.id,
                "x" to "0",
                "y" to "${index * 60}"
            )
            if (view.text != null) {
                values["text"] = "\"${view.text}\""
            }
            view.attributes["android:hint"]?.let { values["hint"] = "\"$it\"" }
            view.attributes["android:textSize"]?.let {
                values["textSize"] = it.removeSuffix("sp")
            }
            view.attributes["android:inputType"]?.let { values["inputType"] = it }
            view.attributes["android:src"]?.let { values["src"] = "\"$it\"" }
            PlacedBlock(
                id = "parsed_${index}",
                definition = definition,
                x = 100f,
                y = (index * 70f),
                values = values
            )
        }
    }

    private val VIEW_TYPES = setOf(
        "TextView", "EditText", "Button", "ImageView", "RecyclerView",
        "ScrollView", "LinearLayout", "FrameLayout", "ConstraintLayout",
        "RelativeLayout", "CheckBox", "RadioButton", "Switch", "SeekBar",
        "ProgressBar", "Spinner", "ListView", "GridView", "WebView", "VideoView"
    )
}
