package com.xcoder.visual.parser

import android.content.Context
import android.view.View
import android.widget.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Inflates Android XML layouts for preview rendering in a Compose container.
 */
object LayoutPreviewInflater {

    fun inflateFromXml(context: Context, xml: String): View? {
        return try {
            val factory = android.view.LayoutInflater.from(context)
            val parser = context.resources.getXml(0) // Would need proper XML resource
            // For preview, we build views programmatically from parsed XML
            val parsedViews = XmlParser.parse(xml)
            val container = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
                setPadding(16, 16, 16, 16)
            }
            parsedViews.forEach { pv ->
                val view = createView(context, pv)
                container.addView(view)
            }
            container
        } catch (e: Exception) {
            TextView(context).apply {
                text = "Preview error: ${e.message}"
                setTextColor(0xFFE74C3C.toInt())
            }
        }
    }

    private fun createView(context: Context, pv: XmlParser.ParsedView): View {
        return when (pv.type) {
            "TextView" -> TextView(context).apply {
                text = pv.text ?: "TextView"
                textSize = pv.attributes["android:textSize"]?.removeSuffix("sp")?.toFloatOrNull() ?: 16f
                pv.attributes["android:textColor"]?.let { setTextColor(parseColor(it)) }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 8
                }
            }
            "EditText" -> EditText(context).apply {
                pv.attributes["android:hint"]?.let { hint = it }
                pv.attributes["android:inputType"]?.let {
                    inputType = when (it) {
                        "number" -> android.text.InputType.TYPE_CLASS_NUMBER
                        "phone" -> android.text.InputType.TYPE_CLASS_PHONE
                        "textPassword" -> android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                        "textEmailAddress" -> android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                        else -> android.text.InputType.TYPE_CLASS_TEXT
                    }
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 8
                }
            }
            "Button" -> Button(context).apply {
                text = pv.text ?: "Button"
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 8
                }
            }
            "ImageView" -> ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    48, 48
                ).apply {
                    bottomMargin = 8
                }
                setImageResource(android.R.drawable.ic_menu_gallery)
            }
            else -> View(context)
        }
    }

    private fun parseColor(colorStr: String): Int {
        return try {
            android.graphics.Color.parseColor(colorStr)
        } catch (e: Exception) {
            0xFFFFFFFF.toInt()
        }
    }
}

@Composable
fun LayoutPreviewComposable(
    xml: String,
    context: Context,
    modifier: Modifier = Modifier.fillMaxSize()
) {
    AndroidView(
        factory = { ctx ->
            LayoutPreviewInflater.inflateFromXml(ctx, xml) ?: FrameLayout(ctx)
        },
        modifier = modifier
    )
}