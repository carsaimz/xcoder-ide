package com.xcoder.bookmarks

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookmarkManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val gson = Gson()
    private val file = File(context.filesDir, "file_bookmarks.json")
    private val _bookmarks = MutableStateFlow<List<FileBookmark>>(emptyList())
    val bookmarks: StateFlow<List<FileBookmark>> = _bookmarks.asStateFlow()
    private val _lineBookmarks = MutableStateFlow<Map<String, List<LineBookmark>>>(emptyMap())
    val lineBookmarks: StateFlow<Map<String, List<LineBookmark>>> = _lineBookmarks.asStateFlow()

    data class FileBookmark(
        val path: String,
        val name: String,
        val addedAt: Long = System.currentTimeMillis(),
        val lastOpened: Long = System.currentTimeMillis(),
        val openCount: Int = 1,
        val tags: List<String> = emptyList(),
        val color: Int = 0xFF89B4FA.toInt(),
        val note: String = ""
    ) {
        val isFavorite: Boolean get() = tags.contains("favorite")
    }

    data class LineBookmark(
        val filePath: String,
        val lineNumber: Int,
        val label: String = "",
        val lineContent: String = "",
        val createdAt: Long = System.currentTimeMillis()
    )

    init { load() }

    private fun load() {
        if (file.exists()) {
            try {
                val json = file.readText()
                val type = object : TypeToken<List<FileBookmark>>() {}.type
                _bookmarks.value = gson.fromJson(json, type) ?: emptyList()
            } catch (_: Exception) {}
        }
    }

    suspend fun addBookmark(path: String, name: String, tags: List<String> = emptyList(), color: Int? = null, note: String = "") {
        withContext(Dispatchers.IO) {
            val current = _bookmarks.value.toMutableList()
            val existing = current.find { it.path == path }
            if (existing != null) {
                val idx = current.indexOf(existing)
                current[idx] = existing.copy(lastOpened = System.currentTimeMillis(), openCount = existing.openCount + 1, tags = (existing.tags + tags).distinct())
            } else {
                current.add(FileBookmark(path, name, tags = tags, color = color ?: 0xFF89B4FA.toInt(), note = note))
            }
            _bookmarks.value = current
            persist(current)
        }
    }

    suspend fun removeBookmark(path: String) {
        withContext(Dispatchers.IO) {
            val current = _bookmarks.value.filter { it.path != path }
            _bookmarks.value = current
            persist(current)
        }
    }

    suspend fun toggleFavorite(path: String) {
        withContext(Dispatchers.IO) {
            val current = _bookmarks.value.toMutableList()
            val idx = current.indexOfFirst { it.path == path }
            if (idx >= 0) {
                val bm = current[idx]
                val newTags = if ("favorite" in bm.tags) bm.tags - "favorite" else bm.tags + "favorite"
                current[idx] = bm.copy(tags = newTags)
                _bookmarks.value = current
                persist(current)
            }
        }
    }

    suspend fun addLineBookmark(filePath: String, lineNumber: Int, label: String = "", lineContent: String = "") {
        withContext(Dispatchers.IO) {
            val current = (_lineBookmarks.value[filePath] ?: emptyList()).toMutableList()
            if (current.none { it.lineNumber == lineNumber }) {
                current.add(LineBookmark(filePath, lineNumber, label, lineContent))
                val all = _lineBookmarks.value.toMutableMap()
                all[filePath] = current.sortedBy { it.lineNumber }
                _lineBookmarks.value = all
            }
        }
    }

    suspend fun removeLineBookmark(filePath: String, lineNumber: Int) {
        withContext(Dispatchers.IO) {
            val current = (_lineBookmarks.value[filePath] ?: emptyList()).filter { it.lineNumber != lineNumber }
            val all = _lineBookmarks.value.toMutableMap()
            all[filePath] = current
            _lineBookmarks.value = all
        }
    }

    suspend fun updateBookmarkNote(path: String, note: String) {
        withContext(Dispatchers.IO) {
            val current = _bookmarks.value.toMutableList()
            val idx = current.indexOfFirst { it.path == path }
            if (idx >= 0) {
                current[idx] = current[idx].copy(note = note)
                _bookmarks.value = current
                persist(current)
            }
        }
    }

    suspend fun addTag(path: String, tag: String) {
        withContext(Dispatchers.IO) {
            val current = _bookmarks.value.toMutableList()
            val idx = current.indexOfFirst { it.path == path }
            if (idx >= 0 && tag !in current[idx].tags) {
                current[idx] = current[idx].copy(tags = current[idx].tags + tag)
                _bookmarks.value = current
                persist(current)
            }
        }
    }

    fun getBookmarksForFile(path: String): List<LineBookmark> = _lineBookmarks.value[path] ?: emptyList()
    fun isBookmarked(path: String): Boolean = _bookmarks.value.any { it.path == path }
    fun isFavorite(path: String): Boolean = _bookmarks.value.find { it.path == path }?.isFavorite == true
    fun search(query: String): List<FileBookmark> = _bookmarks.value.filter { it.name.contains(query, ignoreCase = true) || it.path.contains(query, ignoreCase = true) || it.note.contains(query, ignoreCase = true) }
    fun getByTag(tag: String): List<FileBookmark> = _bookmarks.value.filter { tag in it.tags }
    fun getRecent(limit: Int = 20): List<FileBookmark> = _bookmarks.value.sortedByDescending { it.lastOpened }.take(limit)
    fun getFavorites(): List<FileBookmark> = _bookmarks.value.filter { it.isFavorite }
    fun getMostOpened(limit: Int = 20): List<FileBookmark> = _bookmarks.value.sortedByDescending { it.openCount }.take(limit)

    private fun persist(bookmarks: List<FileBookmark>) {
        try { file.writeText(gson.toJson(bookmarks)) } catch (_: Exception) {}
    }
}