/**
 * Compose-based property panel for editing [ViewBean] properties.
 *
 * Based on Sketchware-IA's property editor. Provides organized sections for:
 * - Widget properties (id, enabled, clickable, alpha)
 * - Layout properties (width, height, orientation, gravity, weight, padding, margin)
 * - Text properties (text, textSize, textColor, textStyle, font, alignment, hint)
 * - Image properties (src, scaleType, tint, contentDescription)
 */
package com.xcoder.visual

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xcoder.visual.model.*

/**
 * A property change callback: (viewId, propertyPath, newValue).
 * PropertyPath uses dot notation, e.g. "layout.width", "text.textSize".
 */
fun interface OnPropertyChangeListener {
    fun onPropertyChanged(viewId: String, propertyPath: String, newValue: String)
}

@Composable
fun PropertyPanel(
    selectedView: ViewBean?,
    onPropertyChange: OnPropertyChangeListener,
    onDeleteView: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (selectedView == null) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Select a widget to edit properties",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ── Header ────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = if (selectedView.isLayout) {
                    MaterialTheme.colorScheme.tertiaryContainer
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                }
            ) {
                Text(
                    if (selectedView.isLayout) "Layout" else "Widget",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    fontSize = 11.sp
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = { onDeleteView(selectedView.id) }) {
                Icon(Icons.Default.Delete, "Delete widget", tint = MaterialTheme.colorScheme.error)
            }
        }

        Text(
            "${selectedView.simpleClassName}  (${selectedView.id})",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // ── Widget Section ────────────────────────────────────────────
        SectionHeader("Widget")
        PropTextField("ID", selectedView.id) { newValue ->
            onPropertyChange.onPropertyChanged(selectedView.id, "id", newValue)
        }

        // Enabled toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Enabled", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton(onClick = {
                onPropertyChange.onPropertyChanged(selectedView.id, "enabled", (!selectedView.enabled).toString())
            }) {
                Icon(
                    if (selectedView.enabled) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = "Toggle enabled"
                )
            }
        }

        // Clickable toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Clickable", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Switch(
                checked = selectedView.clickable,
                onCheckedChange = { newValue ->
                    onPropertyChange.onPropertyChanged(selectedView.id, "clickable", newValue.toString())
                }
            )
        }

        PropSlider("Alpha", 0f, 1f, selectedView.alpha) { newValue ->
            onPropertyChange.onPropertyChanged(selectedView.id, "alpha", newValue.toString())
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // ── Layout Section ────────────────────────────────────────────
        SectionHeader("Layout")

        // Width
        PropDropdown(
            label = "Width",
            options = listOf("match_parent", "wrap_content", "100dp", "200dp", "0dp"),
            selected = selectedView.layout.widthToXml()
        ) { newValue ->
            onPropertyChange.onPropertyChanged(selectedView.id, "layout.width", newValue)
        }

        // Height
        PropDropdown(
            label = "Height",
            options = listOf("match_parent", "wrap_content", "48dp", "100dp", "0dp"),
            selected = selectedView.layout.heightToXml()
        ) { newValue ->
            onPropertyChange.onPropertyChanged(selectedView.id, "layout.height", newValue)
        }

        // Orientation (LinearLayout only)
        if (selectedView.type == ViewBean.TYPE_LINEAR_LAYOUT) {
            PropDropdown(
                label = "Orientation",
                options = listOf("vertical", "horizontal"),
                selected = if (selectedView.layout.orientation == LayoutBean.ORIENTATION_HORIZONTAL) "horizontal" else "vertical"
            ) { newValue ->
                onPropertyChange.onPropertyChanged(selectedView.id, "layout.orientation", newValue)
            }
        }

        // Gravity
        PropDropdown(
            label = "Gravity",
            options = listOf("none", "top", "bottom", "left", "right", "center", "center_vertical", "center_horizontal"),
            selected = selectedView.layout.gravityToXml()
        ) { newValue ->
            onPropertyChange.onPropertyChanged(selectedView.id, "layout.gravity", newValue)
        }

        // Weight
        PropTextField("Weight", if (selectedView.layout.weight > 0f) selectedView.layout.weight.toString() else "") { newValue ->
            onPropertyChange.onPropertyChanged(selectedView.id, "layout.weight", newValue)
        }

        // Padding
        SectionSubHeader("Padding")
        PropDimensionField("Left", selectedView.layout.paddingLeft) { newValue ->
            onPropertyChange.onPropertyChanged(selectedView.id, "layout.paddingLeft", newValue)
        }
        PropDimensionField("Top", selectedView.layout.paddingTop) { newValue ->
            onPropertyChange.onPropertyChanged(selectedView.id, "layout.paddingTop", newValue)
        }
        PropDimensionField("Right", selectedView.layout.paddingRight) { newValue ->
            onPropertyChange.onPropertyChanged(selectedView.id, "layout.paddingRight", newValue)
        }
        PropDimensionField("Bottom", selectedView.layout.bottomPadding) { newValue ->
            onPropertyChange.onPropertyChanged(selectedView.id, "layout.bottomPadding", newValue)
        }

        // Margin
        SectionSubHeader("Margin")
        PropDimensionField("Left", selectedView.layout.marginLeft) { newValue ->
            onPropertyChange.onPropertyChanged(selectedView.id, "layout.marginLeft", newValue)
        }
        PropDimensionField("Top", selectedView.layout.marginTop) { newValue ->
            onPropertyChange.onPropertyChanged(selectedView.id, "layout.marginTop", newValue)
        }
        PropDimensionField("Right", selectedView.layout.marginRight) { newValue ->
            onPropertyChange.onPropertyChanged(selectedView.id, "layout.marginRight", newValue)
        }
        PropDimensionField("Bottom", selectedView.layout.marginBottom) { newValue ->
            onPropertyChange.onPropertyChanged(selectedView.id, "layout.marginBottom", newValue)
        }

        // Background
        PropTextField("Background", selectedView.layout.backgroundResource.ifEmpty {
            if (selectedView.layout.backgroundColor != 0x00000000) {
                "#${selectedView.layout.backgroundColor.toUInt().toString(16).uppercase().padStart(8, '0')}"
            } else ""
            }) { newValue ->
            onPropertyChange.onPropertyChanged(selectedView.id, "layout.background", newValue)
        }

        // ── Text Section ───────────────────────────────────────────────
        if (selectedView.isTextWidget) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            SectionHeader("Text")
            val tb = selectedView.text

            PropTextField("Text", tb.text) { newValue ->
                onPropertyChange.onPropertyChanged(selectedView.id, "text.text", newValue)
            }

            PropTextField("Text Size (sp)", tb.textSize.toString()) { newValue ->
                onPropertyChange.onPropertyChanged(selectedView.id, "text.textSize", newValue)
            }

            // Text color preview + input
            PropColorField("Text Color", tb.textColor) { newValue ->
                onPropertyChange.onPropertyChanged(selectedView.id, "text.textColor", newValue)
            }

            // Text style
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = tb.bold,
                    onClick = { onPropertyChange.onPropertyChanged(selectedView.id, "text.bold", (!tb.bold).toString()) },
                    label = { Text("B", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                )
                FilterChip(
                    selected = tb.italic,
                    onClick = { onPropertyChange.onPropertyChanged(selectedView.id, "text.italic", (!tb.italic).toString()) },
                    label = { Text("I", fontStyle = androidx.compose.ui.text.FontStyle.Italic, fontSize = 13.sp) }
                )
            }

            PropTextField("Font Family", tb.fontFamily) { newValue ->
                onPropertyChange.onPropertyChanged(selectedView.id, "text.fontFamily", newValue)
            }

            PropDropdown(
                label = "Text Align",
                options = listOf("view_start", "center", "view_end", "text_start", "text_end"),
                selected = tb.textAlign
            ) { newValue ->
                onPropertyChange.onPropertyChanged(selectedView.id, "text.textAlign", newValue)
            }

            PropTextField("Hint", tb.hint) { newValue ->
                onPropertyChange.onPropertyChanged(selectedView.id, "text.hint", newValue)
            }

            PropColorField("Hint Color", tb.hintColor) { newValue ->
                onPropertyChange.onPropertyChanged(selectedView.id, "text.hintColor", newValue)
            }

            PropTextField("Max Lines", if (tb.maxLines != Integer.MAX_VALUE) tb.maxLines.toString() else "") { newValue ->
                onPropertyChange.onPropertyChanged(selectedView.id, "text.maxLines", newValue)
            }

            PropDropdown(
                label = "Ellipsize",
                options = listOf("none", "start", "middle", "end", "marquee"),
                selected = tb.ellipsize
            ) { newValue ->
                onPropertyChange.onPropertyChanged(selectedView.id, "text.ellipsize", newValue)
            }

            PropTextField("Line Spacing", tb.lineSpacing.toString()) { newValue ->
                onPropertyChange.onPropertyChanged(selectedView.id, "text.lineSpacing", newValue)
            }

            PropTextField("Letter Spacing", tb.letterSpacing.toString()) { newValue ->
                onPropertyChange.onPropertyChanged(selectedView.id, "text.letterSpacing", newValue)
            }
        }

        // ── Image Section ──────────────────────────────────────────────
        if (selectedView.isImageWidget) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            SectionHeader("Image")
            val ib = selectedView.image

            PropTextField("Src (resource)", ib.src) { newValue ->
                onPropertyChange.onPropertyChanged(selectedView.id, "image.src", newValue)
            }

            PropDropdown(
                label = "Scale Type",
                options = listOf("center", "centerCrop", "centerInside", "fitCenter", "fitStart", "fitEnd", "fitXY", "matrix"),
                selected = ib.scaleType
            ) { newValue ->
                onPropertyChange.onPropertyChanged(selectedView.id, "image.scaleType", newValue)
            }

            PropColorField("Tint", ib.tint) { newValue ->
                onPropertyChange.onPropertyChanged(selectedView.id, "image.tint", newValue)
            }

            PropTextField("Content Description", ib.contentDescription) { newValue ->
                onPropertyChange.onPropertyChanged(selectedView.id, "image.contentDescription", newValue)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Adjust View Bounds", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Switch(
                    checked = ib.adjustViewBounds,
                    onCheckedChange = { newValue ->
                        onPropertyChange.onPropertyChanged(selectedView.id, "image.adjustViewBounds", newValue.toString())
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Crop To Padding", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Switch(
                    checked = ib.cropToPadding,
                    onCheckedChange = { newValue ->
                        onPropertyChange.onPropertyChanged(selectedView.id, "image.cropToPadding", newValue.toString())
                    }
                )
            }
        }

        // Bottom spacer for scroll
        Spacer(modifier = Modifier.height(32.dp))
    }
}

// ── Reusable property editor components ─────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
    )
}

@Composable
private fun SectionSubHeader(title: String) {
    Text(
        title,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp)
    )
}

@Composable
private fun PropTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().height(40.dp),
            textStyle = MaterialTheme.typography.bodySmall,
            singleLine = true
        )
    }
}

@Composable
private fun PropDimensionField(
    label: String,
    valuePx: Int,
    onValueChange: (String) -> Unit
) {
    val displayValue = if (valuePx > 0) "${valuePx}dp" else "0dp"
    PropTextField(label, displayValue, onValueChange)
}

@Composable
private fun PropDropdown(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth().height(40.dp),
            textStyle = MaterialTheme.typography.bodySmall,
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            }
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            for (option in options) {
                DropdownMenuItem(
                    text = { Text(option, fontSize = 13.sp) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun PropSlider(
    label: String,
    min: Float,
    max: Float,
    value: Float,
    onValueChange: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${"%.2f".format(value)}", fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        Slider(
            value = value.coerceIn(min, max),
            onValueChange = { onValueChange(it.toString()) },
            valueRange = min..max,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun PropColorField(
    label: String,
    colorArgb: Int,
    onValueChange: (String) -> Unit
) {
    val hexString = "#${colorArgb.toUInt().toString(16).uppercase().padStart(8, '0')}"
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = hexString,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth().height(40.dp),
                textStyle = MaterialTheme.typography.bodySmall,
                singleLine = true
            )
        }
        // Color swatch preview
        Surface(
            modifier = Modifier.size(40.dp),
            shape = MaterialTheme.shapes.small,
            color = androidx.compose.ui.graphics.Color(colorArgb)
        ) {}
    }
}
