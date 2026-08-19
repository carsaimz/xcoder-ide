package com.xcoder.editor.web

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SnippetLoader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val gson = Gson()
    private val snippetStore = mutableMapOf<String, MutableMap<String, String>>()
    private var loaded = false

    suspend fun loadSnippets(context: Context) = withContext(Dispatchers.IO) {
        if (loaded) return@withContext
        snippetStore.clear()
        val assetManager = context.assets
        val snippetFiles = try {
            assetManager.list("snippets") ?: emptyArray()
        } catch (_: Exception) {
            emptyArray()
        }
        for (fileName in snippetFiles) {
            if (!fileName.endsWith(".json")) continue
            val language = fileName.removeSuffix(".json")
            try {
                val inputStream = assetManager.open("snippets/$fileName")
                val content = BufferedReader(InputStreamReader(inputStream, "UTF-8")).use { it.readText() }
                val type = object : TypeToken<Map<String, String>>() {}.type
                val snippets: Map<String, String> = gson.fromJson(content, type) ?: emptyMap()
                snippetStore[language] = snippets.toMutableMap()
            } catch (_: Exception) {
                snippetStore[language] = mutableMapOf()
            }
        }
        loaded = true
    }

    fun getSnippets(language: String): Map<String, String> {
        return snippetStore[language]?.toMap() ?: emptyMap()
    }

    fun getSnippetNames(language: String): List<String> {
        return snippetStore[language]?.keys?.sorted() ?: emptyList()
    }

    fun getAvailableLanguages(): List<String> {
        return snippetStore.keys.sorted()
    }

    fun getSnippetBody(language: String, name: String): String? {
        return snippetStore[language]?.get(name)
    }

    fun clearCache() {
        snippetStore.clear()
        loaded = false
    }
}
