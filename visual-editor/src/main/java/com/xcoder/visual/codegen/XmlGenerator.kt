package com.xcoder.visual.codegen

import com.xcoder.visual.*

/**
 * Generates Android XML layout files from visual block definitions.
 * Supports LinearLayout, ConstraintLayout, TextView, EditText, Button, ImageView,
 * RecyclerView, ScrollView, and common attributes.
 */
@Suppress("unused")
object XmlGenerator {

    data class ViewNode(
        val type: String,
        val id: String,
        val attributes: MutableMap<String, String> = mutableMapOf(),
        val children: MutableList<ViewNode> = mutableListOf(),
        val text: String? = null
    )

    fun generate(blocks: List<PlacedBlock>, connections: List<BlockConnection>): String {
        val viewBlocks = blocks.filter { it.definition.category == BlockCategory.UI }
        if (viewBlocks.isEmpty()) return defaultLayout()

        val rootType = if (viewBlocks.size > 1) "LinearLayout" else "ConstraintLayout"
        val root = ViewNode(type = rootType, id = "@+id/rootLayout")

        when (rootType) {
            "LinearLayout" -> {
                root.attributes["android:layout_width"] = "match_parent"
                root.attributes["android:layout_height"] = "match_parent"
                root.attributes["android:orientation"] = "vertical"
                root.attributes["android:padding"] = "16dp"
                viewBlocks.forEach { block ->
                    val child = blockToViewNode(block)
                    root.children.add(child)
                }
            }
            "ConstraintLayout" -> {
                root.attributes["android:layout_width"] = "match_parent"
                root.attributes["android:layout_height"] = "match_parent"
                viewBlocks.forEach { block ->
                    val child = blockToViewNode(block)
                    root.children.add(child)
                }
            }
        }

        return serializeNode(root, indent = 0)
    }

    private fun blockToViewNode(block: PlacedBlock): ViewNode {
        val type = when (block.definition.id) {
            "ui_textview" -> "TextView"
            "ui_edittext" -> "EditText"
            "ui_button" -> "Button"
            "ui_imageview" -> "ImageView"
            else -> "View"
        }
        val id = "@+id/${block.values["id"] ?: block.definition.id}"
        val node = ViewNode(type = type, id = id)
        node.attributes["android:layout_width"] = "match_parent"
        node.attributes["android:layout_height"] = "wrap_content"
        node.attributes["android:layout_marginBottom"] = "8dp"

        when (type) {
            "TextView" -> {
                node.text = block.values["text"] ?: "Hello World"
                val textSize = block.values["textSize"] ?: "16"
                node.attributes["android:textSize"] = "${textSize}sp"
                node.attributes["android:textColor"] = "#FFFFFF"
            }
            "EditText" -> {
                val hint = block.values["hint"] ?: "Enter text..."
                node.attributes["android:hint"] = hint
                val inputType = block.values["inputType"] ?: "text"
                node.attributes["android:inputType"] = when (inputType) {
                    "number" -> "number"
                    "phone" -> "phone"
                    "textPassword" -> "textPassword"
                    "textEmailAddress" -> "textEmailAddress"
                    "textMultiLine" -> "textMultiLine"
                    else -> "text"
                }
                node.attributes["android:textColor"] = "#FFFFFF"
                node.attributes["android:backgroundTint"] = "#6C5CE7"
            }
            "Button" -> {
                node.text = block.values["text"] ?: "Click Me"
                node.attributes["android:backgroundTint"] = "#6C5CE7"
                node.attributes["android:textColor"] = "#FFFFFF"
            }
            "ImageView" -> {
                val src = block.values["src"] ?: "@mipmap/ic_launcher"
                node.attributes["android:src"] = src
                node.attributes["android:layout_width"] = "48dp"
                node.attributes["android:layout_height"] = "48dp"
                node.attributes["android:contentDescription"] = block.definition.name
            }
        }

        return node
    }

    private fun serializeNode(node: ViewNode, indent: Int): String {
        val spaces = "    ".repeat(indent)
        val sb = StringBuilder()

        sb.append("$spaces<${node.type}\n")
        sb.append("$spaces    android:id=\"${node.id}\"\n")

        for ((key, value) in node.attributes.toSortedMap()) {
            sb.append("$spaces    $key=\"$value\"\n")
        }

        if (node.children.isEmpty() && node.text == null) {
            sb.append("$spaces    />")
        } else if (node.text != null && node.children.isEmpty()) {
            sb.append("$spaces    >\n")
            sb.append("$spaces        ${escapeXml(node.text!!)}\n")
            sb.append("$spaces</${node.type}>")
        } else {
            sb.append("$spaces    >\n")
            for (child in node.children) {
                sb.append(serializeNode(child, indent + 1))
                sb.append("\n")
            }
            sb.append("$spaces</${node.type}>")
        }

        return sb.toString()
    }

    private fun escapeXml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun defaultLayout(): String = """<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/rootLayout"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="16dp">

    <TextView
        android:id="@+id/textView1"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="Hello World!"
        android:textSize="16sp"
        android:textColor="#FFFFFF"
        android:layout_marginBottom="8dp" />

</LinearLayout>""".trimIndent()
}