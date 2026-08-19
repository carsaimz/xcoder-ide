package com.xcoder.editor.web

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@Suppress("unused")
data class EditorFile(
    val uri: String,
    val name: String,
    val content: String,
    val language: String,
    val isModified: Boolean = false,
    val isReadOnly: Boolean = false
)

@Suppress("unused")
enum class EditorTheme(val value: String, val displayName: String) {
    MONOKAI("monokai", "Monokai"),
    DRACULA("dracula", "Dracula"),
    GITHUB_DARK("github_dark", "GitHub Dark"),
    ONE_DARK("one_dark", "One Dark"),
    TWILIGHT("twilight", "Twilight"),
    TOMORROW_NIGHT("tomorrow_night", "Tomorrow Night")
}

@HiltViewModel
class WebEditorViewModel @Inject constructor() : ViewModel() {

    private val _openFiles = MutableStateFlow<List<EditorFile>>(emptyList())
    val openFiles: StateFlow<List<EditorFile>> = _openFiles.asStateFlow()

    private val _currentFileIndex = MutableStateFlow(0)
    val currentFileIndex: StateFlow<Int> = _currentFileIndex.asStateFlow()

    val currentFile: StateFlow<EditorFile?> = combine(_openFiles, _currentFileIndex) { files, index ->
        files.getOrNull(index)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _cursorPosition = MutableStateFlow(Pair(1, 1))
    val cursorPosition: StateFlow<Pair<Int, Int>> = _cursorPosition.asStateFlow()

    private val _isModified = MutableStateFlow(false)
    val isModified: StateFlow<Boolean> = _isModified.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private var editorReady = false
    private val pendingCommands = mutableListOf<String>()

    fun onEditorReady() {
        editorReady = true
        pendingCommands.forEach { command ->
            // Execute pending JS commands
            pendingCommands.remove(command)
        }
    }

    fun openFile(uri: String, name: String, content: String) {
        val existing = _openFiles.value.indexOfFirst { it.uri == uri }
        if (existing >= 0) {
            _currentFileIndex.value = existing
            return
        }
        val language = detectLanguage(name)
        val file = EditorFile(uri = uri, name = name, content = content, language = language)
        val updated = _openFiles.value + file
        _openFiles.value = updated
        _currentFileIndex.value = updated.size - 1
    }

    fun closeFile(index: Int) {
        val files = _openFiles.value.toMutableList()
        if (index !in files.indices) return
        files.removeAt(index)
        _openFiles.value = files
        if (_currentFileIndex.value >= files.size) {
            _currentFileIndex.value = (files.size - 1).coerceAtLeast(0)
        }
    }

    fun switchToTab(index: Int) {
        if (index in _openFiles.value.indices) {
            _currentFileIndex.value = index
        }
    }

    fun onContentChanged(content: String) {
        val index = _currentFileIndex.value
        val files = _openFiles.value.toMutableList()
        if (index in files.indices) {
            files[index] = files[index].copy(content = content, isModified = true)
            _openFiles.value = files
            _isModified.value = true
        }
    }

    fun onCursorChanged(row: Int, column: Int) {
        _cursorPosition.value = Pair(row, column)
    }

    fun saveCurrentFile() {
        // File save is handled by the parent through a callback
        val index = _currentFileIndex.value
        val files = _openFiles.value.toMutableList()
        if (index in files.indices) {
            files[index] = files[index].copy(isModified = false)
            _openFiles.value = files
            _isModified.value = false
        }
    }

    fun setTheme(theme: EditorTheme) {
        // Bridge would send JS command: editor.setTheme('${theme.value}')
    }

    fun setFontSize(size: Int) {
        // Bridge would send JS command: editor.setFontSize($size)
    }

    fun setTabSize(size: Int) {
        // Bridge would send JS command: editor.getSession().setTabSize($size)
    }

    fun setWordWrap(enabled: Boolean) {
        // Bridge would send JS command: editor.getSession().setUseWrapMode($enabled)
    }

    fun undo() {
        // Bridge: editor.undo()
    }

    fun redo() {
        // Bridge: editor.redo()
    }

    fun search(query: String) {
        // Bridge: editor.find('$query')
    }

    fun log(message: String) {
        val current = _logs.value.toMutableList()
        current.add(message)
        if (current.size > 500) current.removeAt(0)
        _logs.value = current
    }

    fun getCurrentContent(): String {
        return currentFile.value?.content ?: ""
    }

    private fun detectLanguage(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "kt", "kts" -> "kotlin"
            "java" -> "java"
            "xml" -> "xml"
            "json" -> "json"
            "js" -> "javascript"
            "ts", "tsx" -> "typescript"
            "css" -> "css"
            "html", "htm" -> "html"
            "py" -> "python"
            "c", "h" -> "c_cpp"
            "cpp", "cc", "cxx", "hpp" -> "c_cpp"
            "md", "markdown" -> "markdown"
            "sql" -> "sql"
            "sh", "bash" -> "sh"
            "yml", "yaml" -> "yaml"
            "properties", "gradle" -> "properties"
            "groovy" -> "groovy"
            "dart" -> "dart"
            "rs" -> "rust"
            "go" -> "golang"
            "rb" -> "ruby"
            "php" -> "php"
            "swift" -> "swift"
            else -> "text"
        }
    }
}