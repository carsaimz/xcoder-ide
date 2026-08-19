package com.xcoder.editor.native

import android.view.Gravity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import io.github.rosemoe.sora.editor.CodeEditor
import io.github.rosemoe.sora.widget.CodeEditorView

@Composable
fun NativeEditorComposable(
    viewModel: NativeEditorViewModel,
    modifier: Modifier = Modifier
) {
    var editorRef by remember { mutableStateOf<CodeEditor?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                CodeEditorView(ctx).apply {
                    editor = createEditor()
                    editorRef = editor
                    viewModel.setupEditor(editor)
                }
            },
            modifier = Modifier.weight(1f).fillMaxWidth()
        )
    }

    LaunchedEffect(viewModel.currentFileUri.value) {
        val uri = viewModel.currentFileUri.value ?: return@LaunchedEffect
        val content = viewModel.loadFileContent(uri)
        editorRef?.setText(content)
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.saveIfNeeded(editorRef?.text?.toString() ?: "")
        }
    }
}

private fun createEditor(): CodeEditor {
    return CodeEditor(android.app.Application()).apply {
        typefaceText = android.graphics.Typeface.createFromAsset(
            assets, "fonts/JetBrainsMono-Regular.ttf"
        )
        textSize = 14f
        setLineSpacing(4f, 1.1f)
        isWordWrap = false
        tabWidth = 4
    }
}