package com.xcoder.remote.bookmarks

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.xcoder.remote.connection.ConnectionType
import com.xcoder.remote.connection.ServerConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServerBookmarks @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val gson = Gson()
    private val bookmarksFile = File(context.filesDir, "server_bookmarks.json")
    private val _bookmarks = MutableStateFlow<List<ServerConfig>>(emptyList())
    val bookmarks: StateFlow<List<ServerConfig>> = _bookmarks.asStateFlow()
    private val _quickAccess = MutableStateFlow<List<QuickAccessEntry>>(emptyList())
    val quickAccess: StateFlow<List<QuickAccessEntry>> = _quickAccess.asStateFlow()

    data class QuickAccessEntry(
        val serverId: String,
        val serverName: String,
        val remotePath: String,
        val label: String
    )

    init { loadBookmarks() }

    fun loadBookmarks() {
        if (bookmarksFile.exists()) {
            try {
                val json = bookmarksFile.readText()
                val type = object : TypeToken<List<ServerConfig>>() {}.type
                _bookmarks.value = gson.fromJson(json, type) ?: emptyList()
            } catch (_: Exception) {}
        }
    }

    suspend fun saveBookmark(config: ServerConfig) = withContext(Dispatchers.IO) {
        val current = _bookmarks.value.toMutableList()
        val idx = current.indexOfFirst { it.id == config.id }
        if (idx >= 0) current[idx] = config else current.add(config)
        _bookmarks.value = current
        persistBookmarks(current)
    }

    suspend fun removeBookmark(id: String) = withContext(Dispatchers.IO) {
        val current = _bookmarks.value.filter { it.id != id }
        _bookmarks.value = current
        persistBookmarks(current)
    }

    suspend fun addQuickAccess(entry: QuickAccessEntry) = withContext(Dispatchers.IO) {
        val current = _quickAccess.value.toMutableList()
        if (current.none { it.serverId == entry.serverId && it.remotePath == entry.remotePath }) {
            current.add(entry)
            _quickAccess.value = current
        }
    }

    suspend fun removeQuickAccess(serverId: String, path: String) = withContext(Dispatchers.IO) {
        _quickAccess.value = _quickAccess.value.filter { !(it.serverId == serverId && it.remotePath == path) }
    }

    private fun persistBookmarks(bookmarks: List<ServerConfig>) {
        try {
            bookmarksFile.writeText(gson.toJson(bookmarks))
        } catch (_: Exception) {}
    }

    fun getBookmark(id: String): ServerConfig? = _bookmarks.value.find { it.id == id }
    fun searchBookmarks(query: String): List<ServerConfig> =
        _bookmarks.value.filter { it.name.contains(query, ignoreCase = true) || it.host.contains(query, ignoreCase = true) }
}