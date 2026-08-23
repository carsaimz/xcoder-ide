package com.xcoder.ai.context

import com.xcoder.ai.ChatMessage
import com.xcoder.ai.MessageRole
import com.xcoder.core.file.FileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContextManager @Inject constructor(
    private val fileManager: FileManager
) {
    companion object {
        private const val MAX_CONTEXT_TOKENS = 100_000
        private const val CHARS_PER_TOKEN = 4
        private const val MAX_CONTEXT_CHARS = MAX_CONTEXT_TOKENS * CHARS_PER_TOKEN
        private val IGNORED_DIRS = setOf(".git", ".gradle", "build", ".idea", "node_modules")
        private val IGNORED_EXTENSIONS = setOf(".class", ".apk", ".dex", ".jar", ".png", ".jpg", ".gif")
    }

    private val _projectRoot = MutableStateFlow<File?>(null)
    val projectRoot: StateFlow<File?> = _projectRoot.asStateFlow()

    private val _openFiles = MutableStateFlow<List<String>>(emptyList())
    val openFiles: StateFlow<List<String>> = _openFiles.asStateFlow()

    private val _recentChanges = MutableStateFlow<List<FileChange>>(emptyList())
    val recentChanges: StateFlow<List<FileChange>> = _recentChanges.asStateFlow()

    fun setProjectRoot(root: File) { _projectRoot.value = root }

    suspend fun indexProject(): ProjectContext = withContext(Dispatchers.IO) {
        val root = _projectRoot.value ?: return@withContext ProjectContext()
        val structure = buildProjectTree(root, "", 0, 3)
        ProjectContext(structure = structure)
    }

    fun addOpenFile(path: String) {
        val current = _openFiles.value.toMutableList()
        if (path !in current) current.add(path)
        _openFiles.value = current
    }

    fun removeOpenFile(path: String) {
        _openFiles.value = _openFiles.value.filter { it != path }
    }

    fun recordChange(path: String, changeType: FileChangeType) {
        val current = _recentChanges.value.toMutableList()
        current.add(FileChange(path, changeType, System.currentTimeMillis()))
        if (current.size > 100) current.removeAt(0)
        _recentChanges.value = current
    }

    suspend fun buildContextForMessage(
        userMessage: String,
        maxFiles: Int = 10
    ): List<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()
        val root = _projectRoot.value ?: return messages

        val projectCtx = indexProject()
        messages.add(ChatMessage(
            role = MessageRole.SYSTEM,
            content = """You are XCoder AI, an intelligent coding assistant integrated into the XCoder IDE for Android.

Current project structure:
${projectCtx.structure}

Open files: ${_openFiles.value.joinToString(", ")}
Recent changes: ${_recentChanges.value.takeLast(10).joinToString { "${it.path} (${it.type})" }}

You have access to tools for reading, writing, creating, and deleting files, and running shell commands.
Always use the appropriate tools when the user asks you to work with files."""
        ))

        val relevantFiles = findRelevantFiles(userMessage, root, maxFiles)
        for (file in relevantFiles) {
            try {
                val content = File(file).readText().take(20_000)
                messages.add(ChatMessage(
                    role = MessageRole.SYSTEM,
                    content = "File: $file\n```\n$content\n```"
                ))
            } catch (_: Exception) {}
        }

        return messages
    }

    private suspend fun findRelevantFiles(query: String, root: File, maxFiles: Int): List<String> =
        withContext(Dispatchers.IO) {
            val queryWords = query.lowercase().split("\\W+").filter { it.length > 2 }
            val scoredFiles = mutableListOf<Pair<String, Int>>()
            root.walkTopDown()
                .filter { it.isFile && it.length() < 100_000 }
                .filter { it.extension !in IGNORED_EXTENSIONS }
                .filter { it.parentFile?.name !in IGNORED_DIRS }
                .take(500)
                .forEach { file ->
                    val relativePath = file.relativeTo(root).path
                    val score = queryWords.count { word ->
                        relativePath.lowercase().contains(word)
                    }
                    if (score > 0) scoredFiles.add(relativePath to score)
                }
            scoredFiles.sortedByDescending { it.second }.take(maxFiles).map { it.first }
        }

    private suspend fun buildProjectTree(dir: File, prefix: String, depth: Int, maxDepth: Int): String =
        withContext(Dispatchers.IO) {
            if (depth > maxDepth) return@withContext ""
            val children = dir.listFiles()?.sortedBy { it.name } ?: return@withContext ""
            val sb = StringBuilder()
            children.forEach { child ->
                if (child.name in IGNORED_DIRS) return@forEach
                val connector = if (child.isDirectory) "" else "  "
                sb.append("$prefix${child.name}${connector}/\n")
                if (child.isDirectory) {
                    sb.append(buildProjectTree(child, "$prefix  ", depth + 1, maxDepth))
                }
            }
            sb.toString()
        }
}

enum class FileChangeType { CREATED, MODIFIED, DELETED, RENAMED }
data class FileChange(val path: String, val type: FileChangeType, val timestamp: Long)