package com.xcoder.editor.web

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.webkit.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedReader
import java.io.InputStreamReader

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebEditorComposable(
 viewModel: WebEditorViewModel,\n modifier: Modifier = Modifier
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                webViewRef = this
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true
                settings.allowContentAccess = true
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                backgroundColor = Color.TRANSPARENT
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        viewModel.onEditorReady()
                    }
                }
                addJavascriptInterface(EditorBridge(viewModel), "EditorBridge")
                loadUrl("file:///android_asset/editor.html")
            }
        },
        modifier = modifier.fillMaxSize()
    )

    LaunchedEffect(viewModel.currentFile.value) {
        val file = viewModel.currentFile.value ?: return@LaunchedEffect
        val content = file.content
        val language = file.language
        webViewRef?.evaluateJavascript("""
            if (typeof editor !== 'undefined') {
                editor.setValue(` + content.replace("\\"", "\\\\\"" ).replace("`", "\\`").replace("$", "\\$") + `);
                editor.session.setMode('ace/mode/${language}');
                editor.clearSelection();
            }
        """.trimIndent(), null)
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.destroy()
        }
    }
}

class EditorBridge(private val viewModel: WebEditorViewModel) {
    @JavascriptInterface
    fun onContentChanged(content: String) {
        viewModel.onContentChanged(content)
    }

    @JavascriptInterface
    fun onCursorChanged(row: Int, column: Int) {
        viewModel.onCursorChanged(row, column)
    }

    @JavascriptInterface
    fun onSave() {
        viewModel.saveCurrentFile()
    }

    @JavascriptInterface
    fun onConsoleLog(message: String) {
        viewModel.log(message)
    }
}
