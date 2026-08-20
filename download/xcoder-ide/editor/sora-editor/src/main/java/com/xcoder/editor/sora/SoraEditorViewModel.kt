package com.xcoder.editor.sora

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rosemoe.sora.widget.CodeEditor
import java.io.File
import javax.inject.Inject

/**
 * ViewModel managing sora-editor state across configuration changes.
 * Holds the [CodeEditor] reference and file state.
 */
@HiltViewModel
class SoraEditorViewModel @Inject constructor() : ViewModel() {

    /** Currently opened file path. */
    var currentFilePath by mutableStateOf("")
        private set

    /** Whether the file has unsaved modifications. */
    var isModified by mutableStateOf(false)
        private set

    /** Encoding of the current file. */
    var encoding by mutableStateOf("UTF-8")
        private set

    /** Direct reference to the CodeEditor for operations. */
    var editor: CodeEditor? = null
        private set

    /** Register the editor instance (called from Compose wrapper). */
    fun attachEditor(editor: CodeEditor) {
        this.editor = editor
        editor.setOnTextChangedListener { _, _ ->
            isModified = true
        }
    }

    /** Open a file in the editor. */
    fun openFile(path: String, content: String) {
        currentFilePath = path
        encoding = detectEncoding(path)
        isModified = false
        editor?.let { e ->
            EditorOperations.setText(e, content)
            e.setLang(detectLanguageForFile(e.context, path))
        }
    }

    /** Get current text content. */
    fun getText(): String = editor?.let { EditorOperations.getText(it) } ?: ""

    /** Save the current file. */
    fun saveFile(): String {
        val text = getText()
        File(currentFilePath).writeText(text, charset(encoding))
        isModified = false
        return text
    }

    /** Detect encoding using BOM and heuristic analysis. */
    private fun detectEncoding(path: String): String {
        val file = File(path)
        if (!file.exists()) return "UTF-8"
        val bytes = file.readBytes().take(4).toByteArray()
        return when {
            bytes.size >= 3 && bytes[0] == 0xEF.toByte() &&
                bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() -> "UTF-8"
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() &&
                bytes[1] == 0xFE.toByte() -> "UTF-16LE"
            bytes.size >= 2 && bytes[0] == 0xFE.toByte() &&
                bytes[1] == 0xFF.toByte() -> "UTF-16BE"
            bytes.size >= 4 && bytes[0] == 0x00.toByte() &&
                bytes[1] == 0x00.toByte() && bytes[2] == 0xFE.toByte() &&
                bytes[3] == 0xFF.toByte() -> "UTF-32BE"
            bytes.size >= 4 && bytes[0] == 0xFF.toByte() &&
                bytes[1] == 0xFE.toByte() && bytes[2] == 0x00.toByte() &&
                bytes[3] == 0x00.toByte() -> "UTF-32LE"
            else -> "UTF-8"
        }
    }

    /** Search in the editor. */
    fun search(query: String, caseSensitive: Boolean = false) {
        editor?.let { EditorOperations.search(it, query, caseSensitive) }
    }

    /** Replace in the editor. */
    fun replace(query: String, replacement: String, all: Boolean = false) {
        editor?.let { EditorOperations.replace(it, query, replacement, all) }
    }

    /** Format code. */
    fun format() {
        editor?.let { EditorOperations.format(it) }
    }

    /** Go to line. */
    fun goToLine(line: Int) {
        editor?.let { EditorOperations.goToLine(it, line) }
    }

    /** Toggle word wrap. */
    fun toggleWordWrap() {
        editor?.let { EditorOperations.toggleWordWrap(it) }
    }

    /** Toggle minimap. */
    fun toggleMinimap() {
        editor?.let { EditorOperations.toggleMinimap(it) }
    }

    /** Undo. */
    fun undo() = editor?.let { EditorOperations.undo(it) }

    /** Redo. */
    fun redo() = editor?.let { EditorOperations.redo(it) }

    /** Duplicate line. */
    fun duplicateLine() = editor?.let { EditorOperations.duplicateLine(it) }

    /** Delete line. */
    fun deleteLine() = editor?.let { EditorOperations.deleteLine(it) }

    /** Move line up. */
    fun moveLineUp() = editor?.let { EditorOperations.moveLineUp(it) }

    /** Move line down. */
    fun moveLineDown() = editor?.let { EditorOperations.moveLineDown(it) }

    /** Toggle comment. */
    fun toggleComment() = editor?.let { EditorOperations.toggleComment(it) }

    /** Insert snippet at cursor. */
    fun insertSnippet(snippet: String) {
        editor?.let { EditorOperations.insertAtCursor(it, snippet) }
    }

    override fun onCleared() {
        super.onCleared()
        editor = null
    }
}
