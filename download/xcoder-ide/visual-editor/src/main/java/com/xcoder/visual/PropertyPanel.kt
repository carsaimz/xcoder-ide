package com.xcoder.visual

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PropertyPanel(
    selectedBlock: PlacedBlock?,
    onValueChanged: (blockId: String, key: String, value: String) -> Unit,
    onDeleteBlock: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (selectedBlock == null) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Select a block to edit properties",
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
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Block header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = selectedBlock.definition.color
            ) {
                Text(
                    selectedBlock.definition.category.displayName,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = { onDeleteBlock(selectedBlock.id) }) {
                Icon(Icons.Default.Delete, "Delete block", tint = MaterialTheme.colorScheme.error)
            }
        }

        Text(
            selectedBlock.definition.name,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        HorizontalDivider()

        // Block ID (read-only)
        LabeledText("Block ID", selectedBlock.id)

        // Position
        Text(
            "Position",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary
        )
        LabeledText("X", "${selectedBlock.x.toInt()}")
        LabeledText("Y", "${selectedBlock.y.toInt()}")

        HorizontalDivider()

        // Editable properties
        if (selectedBlock.definition.inputs.isNotEmpty()) {
            Text(
                "Inputs",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
            selectedBlock.definition.inputs.forEach { port ->
                val currentValue = selectedBlock.values[port.name] ?: port.defaultValue
                LabeledEditableText(
                    label = "${port.name} (${port.type.name.lowercase()})",
                    value = currentValue,
                    onValueChange = { newValue ->
                        onValueChanged(selectedBlock.id, port.name, newValue)
                    }
                )
            }
        }

        if (selectedBlock.definition.outputs.isNotEmpty()) {
            Text(
                "Outputs",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
            selectedBlock.definition.outputs.forEach { port ->
                LabeledText(port.name, port.type.name)
            }
        }

        // Connections info
        Text(
            "Connections",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "${selectedBlock.connections.size} connection(s)",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LabeledText(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun LabeledEditableText(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
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