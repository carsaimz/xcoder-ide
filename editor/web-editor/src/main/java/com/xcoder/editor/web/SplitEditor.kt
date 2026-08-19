package com.xcoder.editor.web

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.webkit.WebView

enum class SplitMode {
    NONE,
    HORIZONTAL,
    VERTICAL
}

@Stable
class SplitEditorState {
    var splitMode by mutableStateOf(SplitMode.NONE)
    var pane1File by mutableStateOf<String?>(null)
    var pane2File by mutableStateOf<String?>(null)
    var pane1Cursor by mutableStateOf(Pair(1, 1))
    var pane2Cursor by mutableStateOf(Pair(1, 1))
    var pane1Scroll by mutableStateOf(0f)
    var pane2Scroll by mutableStateOf(0f)
    var dividerPosition by mutableStateOf(0.5f)
    var activePane by mutableStateOf(1)

    fun toggleHorizontalSplit() {
        splitMode = if (splitMode == SplitMode.HORIZONTAL) SplitMode.NONE else SplitMode.HORIZONTAL
    }

    fun toggleVerticalSplit() {
        splitMode = if (splitMode == SplitMode.VERTICAL) SplitMode.NONE else SplitMode.VERTICAL
    }

    fun closeSplit() {
        splitMode = SplitMode.NONE
        pane2File = null
    }
}

@Composable
fun SplitEditor(
    state: SplitEditorState,
    webViewProvider: (pane: Int) -> WebView,
    modifier: Modifier = Modifier
) {
    if (state.splitMode == SplitMode.NONE) {
        AndroidView(
            factory = { webViewProvider(1) },
            modifier = modifier.fillMaxSize()
        )
        return
    }

    val isHorizontal = state.splitMode == SplitMode.HORIZONTAL
    val fraction = state.dividerPosition

    if (isHorizontal) {
        Row(modifier = modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxHeight().weight(fraction)) {
                AndroidView(factory = { webViewProvider(1) }, modifier = Modifier.fillMaxSize())
            }
            DividerHandle(isHorizontal = true) { dragAmount ->
                state.dividerPosition = (state.dividerPosition + dragAmount).coerceIn(0.2f, 0.8f)
            }
            Box(modifier = Modifier.fillMaxHeight().weight(1f - fraction)) {
                AndroidView(factory = { webViewProvider(2) }, modifier = Modifier.fillMaxSize())
            }
        }
    } else {
        Column(modifier = modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxWidth().weight(fraction)) {
                AndroidView(factory = { webViewProvider(1) }, modifier = Modifier.fillMaxSize())
            }
            DividerHandle(isHorizontal = false) { dragAmount ->
                state.dividerPosition = (state.dividerPosition + dragAmount).coerceIn(0.2f, 0.8f)
            }
            Box(modifier = Modifier.fillMaxWidth().weight(1f - fraction)) {
                AndroidView(factory = { webViewProvider(2) }, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun DividerHandle(
    isHorizontal: Boolean,
    onDrag: (Float) -> Unit
) {
    val dividerColor = MaterialTheme.colorScheme.outlineVariant

    Box(
        modifier = Modifier
            .then(
                if (isHorizontal) {
                    Modifier.width(6.dp).fillMaxHeight()
                } else {
                    Modifier.height(6.dp).fillMaxWidth()
                }
            )
            .background(dividerColor)
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val delta = if (isHorizontal) {
                        change.position.x / size.width
                    } else {
                        change.position.y / size.height
                    }
                    onDrag(delta)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.DragHandle,
            contentDescription = "Drag to resize",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(16.dp)
        )
    }
}
