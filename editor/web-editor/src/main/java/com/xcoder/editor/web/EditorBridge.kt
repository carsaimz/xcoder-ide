package com.xcoder.editor.web

import android.webkit.JavascriptInterface
import android.util.Log

class EditorBridge(
    private val onContentChanged: ((String) -> Unit)? = null,
    private val onCursorChanged: ((Int, Int) -> Unit)? = null,
    private val onSaveRequest: (() -> Unit)? = null,
    private val onConsoleLog: ((String) -> Unit)? = null,
    private val onFileDropped: ((String) -> Unit)? = null,
    private val onSnippetInserted: ((String) -> Unit)? = null,
    private val onCommandExecuted: ((String) -> Unit)? = null,
    private val onSearchResultReceived: ((Int) -> Unit)? = null,
    private val onGotoFileRequest: ((String, Int) -> Unit)? = null,
    private val onProblemsUpdated: ((String) -> Unit)? = null
) {
    private val tag = "EditorBridge"
    private var webViewRef: android.webkit.WebView? = null
    private val pendingCommands = ArrayDeque<String>()
    private var editorReady = false

    fun attachWebView(webView: android.webkit.WebView) {
        webViewRef = webView
        webView.addJavascriptInterface(this, "androidBridge")
    }

    fun detachWebView() {
        webViewRef?.removeJavascriptInterface("androidBridge")
        webViewRef = null
        editorReady = false
    }

    fun onEditorReady() {
        editorReady = true
        while (pendingCommands.isNotEmpty()) {
            val cmd = pendingCommands.removeFirst()
            executeJs(cmd)
        }
    }

    fun sendCommand(jsCommand: String) {
        if (editorReady && webViewRef != null) {
            executeJs(jsCommand)
        } else {
            pendingCommands.addLast(jsCommand)
        }
    }

    fun setContent(content: String, language: String) {
        val escaped = content.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")
        sendCommand("setEditorContent(\"$escaped\", \"$language\");")
    }

    fun setTheme(theme: String) {
        sendCommand("setEditorTheme(\"$theme\");")
    }

    fun setFontSize(size: Int) {
        sendCommand("editor.setFontSize($size);")
    }

    fun setTabSize(size: Int) {
        sendCommand("editor.getSession().setTabSize($size);")
    }

    fun setWordWrap(enabled: Boolean) {
        sendCommand("editor.getSession().setUseWrapMode($enabled);")
    }

    fun setReadOnly(readOnly: Boolean) {
        sendCommand("editor.setReadOnly($readOnly);")
    }

    fun setLanguage(mode: String) {
        sendCommand("editor.getSession().setMode(\"ace/mode/$mode\");")
    }

    fun gotoLine(line: Int) {
        sendCommand("editor.gotoLine($line);")
    }

    fun insertText(text: String) {
        val escaped = text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        sendCommand("editor.insert(\"$escaped\");")
    }

    fun focus() {
        sendCommand("editor.focus();")
    }

    fun undo() { sendCommand("editor.undo();") }
    fun redo() { sendCommand("editor.redo();") }
    fun selectAll() { sendCommand("editor.selectAll();") }
    fun clearSelection() { sendCommand("editor.clearSelection();") }

    fun search(query: String, caseSensitive: Boolean = false, regex: Boolean = false, wholeWord: Boolean = false) {
        val escaped = query.replace("\\", "\\\\").replace("\"", "\\\"")
        val options = buildString {
            append("{needle: \"$escaped\"")
            append(", caseSensitive: $caseSensitive")
            append(", regex: $regex")
            append(", wholeWord: $wholeWord")
            append("}")
        }
        sendCommand("editor.find($options);")
    }

    fun findNext() { sendCommand("editor.findNext();") }
    fun findPrevious() { sendCommand("editor.findPrevious();") }

    fun loadSnippets(language: String, jsonSnippets: String) {
        val escaped = jsonSnippets.replace("\\", "\\\\").replace("\'", "\\\'")
        sendCommand("loadSnippets(\"$language\", '$escaped');")
    }

    fun getCursorPosition() {
        sendCommand("getCursorPosition();")
    }

    private fun executeJs(script: String) {
        try {
            webViewRef?.evaluateJavascript(script, null)
        } catch (e: Exception) {
            Log.e(tag, "JS execution error", e)
        }
    }

    @JavascriptInterface
    fun onContentChanged(content: String) {
        Log.d(tag, "Content changed: ${content.length} chars")
        onContentChanged?.invoke(content)
    }

    @JavascriptInterface
    fun onCursorChanged(row: Int, col: Int) {
        Log.d(tag, "Cursor: $row:$col")
        onCursorChanged?.invoke(row, col)
    }

    @JavascriptInterface
    fun onSave() {
        Log.d(tag, "Save requested")
        onSaveRequest?.invoke()
    }

    @JavascriptInterface
    fun onConsoleLog(message: String) {
        Log.d(tag, "Console: $message")
        onConsoleLog?.invoke(message)
    }

    @JavascriptInterface
    fun onFileDropped(path: String) {
        Log.d(tag, "File dropped: $path")
        onFileDropped?.invoke(path)
    }

    @JavascriptInterface
    fun onSnippetInsert(name: String) {
        Log.d(tag, "Snippet inserted: $name")
        onSnippetInserted?.invoke(name)
    }

    @JavascriptInterface
    fun onCommandExecute(commandId: String) {
        Log.d(tag, "Command: $commandId")
        onCommandExecuted?.invoke(commandId)
    }

    @JavascriptInterface
    fun onSearchResult(count: Int) {
        Log.d(tag, "Search results: $count")
        onSearchResultReceived?.invoke(count)
    }

    @JavascriptInterface
    fun onGotoFile(path: String, line: Int) {
        Log.d(tag, "Goto file: $path:$line")
        onGotoFileRequest?.invoke(path, line)
    }

    @JavascriptInterface
    fun onProblemsUpdate(problemsJson: String) {
        Log.d(tag, "Problems: $problemsJson")
        onProblemsUpdated?.invoke(problemsJson)
    }
}
