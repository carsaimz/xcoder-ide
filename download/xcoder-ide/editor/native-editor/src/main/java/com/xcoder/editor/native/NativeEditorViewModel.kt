package com.xcoder.editor.native

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rosemoe.sora.editor.CodeEditor
import io.github.rosemoe.sora.langs.java.JavaLanguage
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class NativeEditorViewModel @Inject constructor() : ViewModel() {

    private val _currentFileUri = MutableStateFlow<String?>(null)
    val currentFileUri: StateFlow<String?> = _currentFileUri.asStateFlow()

    private val _currentFileName = MutableStateFlow("")
    val currentFileName: StateFlow<String> = _currentFileName.asStateFlow()

    private val _isModified = MutableStateFlow(false)
    val isModified: StateFlow<Boolean> = _isModified.asStateFlow()

    fun openFile(uri: String, name: String) {
        _currentFileUri.value = uri
        _currentFileName.value = name
        _isModified.value = false
    }

    fun loadFileContent(uri: String): String {
        return try {
            java.io.File(uri).readText()
        } catch (e: Exception) {
            "// Error loading file: ${e.message}"
        }
    }

    fun setupEditor(editor: CodeEditor) {
        editor.setEditorLanguage(JavaLanguage())
        editor.colorScheme = DarkColorScheme()
        editor.subscribeEvent { event, _ ->
            when (event) {
                is io.github.rosemoe.sora.event.ContentChangeEvent -> {
                    _isModified.value = true
                }
                is io.github.rosemoe.sora.event.SelectionChangeEvent -> {}
                else -> {}
            }
        }
    }

    fun saveIfNeeded(content: String) {
        if (_isModified.value) {
            val uri = _currentFileUri.value ?: return
            try {
                java.io.File(uri).writeText(content)
                _isModified.value = false
            } catch (_: Exception) {}
        }
    }

    fun setLanguage(editor: CodeEditor, fileName: String) {
        val ext = fileName.substringAfterLast('.', '').lowercase()
        val language = when (ext) {
            "kt", "kts" -> io.github.rosemoe.sora.langs.kotlin.KotlinLanguage()
            "java" -> JavaLanguage()
            "xml" -> io.github.rosemoe.sora.langs.xml.XMLLanguage()
            "json" -> io.github.rosemoe.sora.langs.text.TextUtils.TextLanguage()
            else -> io.github.rosemoe.sora.langs.text.TextUtils.TextLanguage()
        }
        editor.setEditorLanguage(language)
    }

    class DarkColorScheme : EditorColorScheme() {
        override fun getColor(type: Int): Int {
            return when (type) {
                LINE_NUMBER -> 0xFF636D83.toInt()
                LINE_NUMBER_CURRENT -> 0xFFABB2BF.toInt()
                LINE_DIVIDER -> 0xFF191B20.toInt()
                WHOLE_BACKGROUND -> 0xFF1E1E2E.toInt()
                TEXT_NORMAL -> 0xFFABB2BF.toInt()
                SELECTION_INSERT -> 0xFF6C5CE7.toInt()
                SELECTION_BACKGROUND -> 0x666C5CE7.toInt()
                MATCHED_TEXT_BACKGROUND -> 0x33FFFFFF.toInt()
                CURRENT_LINE -> 0xFF1E1E2E.toInt()
                AUTO_COMPLETION_PANEL_BACKGROUND -> 0xFF2D2D44.toInt()
                AUTO_COMPLETION_CURRENT -> 0xFF3D3D5C.toInt()
                HIGHLIGHTED_DELIMITERS_FOREGROUND -> 0xFFFFC857.toInt()
                BLOCK_LINE_CURRENT -> 0xFF1E1E2E.toInt()
                else -> super.getColor(type)
            }
        }
    }
}