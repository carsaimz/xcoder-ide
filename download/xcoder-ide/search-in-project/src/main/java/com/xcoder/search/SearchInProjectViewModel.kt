package com.xcoder.search

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xcoder.core.file.FileManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject

data class ProjectSearchMatch(
    val filePath: String,
    val fileName: String,
    val line: Int,
    val startCol: Int,
    val endCol: Int,
    val lineText: String,
    val matchedText: String
)

data class ProjectSearchResult(
    val fileResults: Map<String, List<ProjectSearchMatch>>,
    val totalMatches: Int,
    val totalFiles: Int,
    val query: String,
    val elapsedMs: Long = 0
) {
    val isEmpty: Boolean get() = totalMatches == 0
}

enum class SearchState { IDLE, SEARCHING, DONE, ERROR }

data class SearchFilter(
    val includeExtensions: Set<String> = emptySet(),
    val excludeExtensions: Set<String> = setOf("png", "jpg", "jpeg", "gif", "svg", "ico", "webp", "bmp", "mp3", "mp4", "apk", "dex", "so", "aar", "jar", "class", "zip", "tar", "gz"),
    val includePatterns: List<String> = emptyList(),
    val excludePatterns: List<String> = listOf("build/", ".gradle/", ".idea/", "node_modules/"),
    val caseSensitive: Boolean = false,
    val useRegex: Boolean = false,
    val maxResultsPerFile: Int = 500
)

@HiltViewModel
class SearchInProjectViewModel @Inject constructor(
    private val fileManager: FileManager
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _replaceQuery = MutableStateFlow("")
    val replaceQuery: StateFlow<String> = _replaceQuery.asStateFlow()

    private val _filter = MutableStateFlow(SearchFilter())
    val filter: StateFlow<SearchFilter> = _filter.asStateFlow()

    private val _searchState = MutableStateFlow(SearchState.IDLE)
    val searchState: StateFlow<SearchState> = _searchState.asStateFlow()

    private val _result = MutableStateFlow(ProjectSearchResult(emptyMap(), 0, 0, ""))
    val result: StateFlow<ProjectSearchResult> = _result.asStateFlow()

    private val _progress = MutableStateFlow("")
    val progress: StateFlow<String> = _progress.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun setQuery(query: String) { _searchQuery.value = query }

    fun setReplaceQuery(query: String) { _replaceQuery.value = query }

    fun setFilter(filter: SearchFilter) { _filter.value = filter }

    fun updateFilter(transform: (SearchFilter) -> SearchFilter) {
        _filter.value = transform(_filter.value)
    }

    fun search(projectUri: Uri) {
        val query = _searchQuery.value
        if (query.isBlank()) return

        viewModelScope.launch {
            _searchState.value = SearchState.SEARCHING
            _error.value = null
            val startTime = System.currentTimeMillis()
            val fileResults = mutableMapOf<String, List<ProjectSearchMatch>>()
            var totalMatches = 0
            val filter = _filter.value

            try {
                val files = fileManager.searchFiles(projectUri, "", recursive = true).getOrThrow()
                    .filter { file ->
                        if (!file.isFile) return@filter false
                        val name = (file.name ?: "").lowercase()
                        val ext = name.substringAfterLast('.', "")
                        val path = file.uri.path ?: file.uri.toString()

                        if (ext in filter.excludeExtensions) return@filter false
                        if (filter.includeExtensions.isNotEmpty() && ext !in filter.includeExtensions) return@filter false
                        if (filter.excludePatterns.any { pattern -> path.contains(pattern) }) return@filter false
                        if (filter.includePatterns.isNotEmpty() && filter.includePatterns.none { pattern -> path.contains(pattern) }) return@filter false
                        true
                    }

                val total = files.size
                files.forEachIndexed { index, file ->
                    val path = file.uri.toString()
                    _progress.value = "${index + 1}/$total: ${file.name}"

                    try {
                        val content = fileManager.readFile(file.uri).getOrThrow()
                        val matches = searchInContent(content, query, filter)
                        if (matches.isNotEmpty()) {
                            fileResults[path] = matches
                            totalMatches += matches.size
                        }
                    } catch (_: Exception) {
                    }

                    if (totalMatches > 10000) return@forEachIndexed
                }

                val elapsed = System.currentTimeMillis() - startTime
                _result.value = ProjectSearchResult(
                    fileResults = fileResults,
                    totalMatches = totalMatches,
                    totalFiles = fileResults.size,
                    query = query,
                    elapsedMs = elapsed
                )
                _searchState.value = SearchState.DONE
            } catch (e: Exception) {
                _error.value = e.message ?: "Search failed"
                _searchState.value = SearchState.ERROR
            }
        }
    }

    private fun searchInContent(content: String, query: String, filter: SearchFilter): List<ProjectSearchMatch> {
        val matches = mutableListOf<ProjectSearchMatch>()
        val regex = buildSearchRegex(query, filter) ?: return emptyList()
        val lines = content.split('\n')
        for (i in lines.indices) {
            for (match in regex.findAll(lines[i])) {
                matches.add(
                    ProjectSearchMatch(
                        filePath = "",
                        fileName = "",
                        line = i + 1,
                        startCol = match.range.first,
                        endCol = match.range.last + 1,
                        lineText = lines[i],
                        matchedText = match.value
                    )
                )
                if (matches.size >= filter.maxResultsPerFile) return matches
            }
        }
        return matches
    }

    private fun buildSearchRegex(query: String, filter: SearchFilter): Regex? {
        return try {
            val pattern = if (filter.useRegex) query else Regex.escape(query)
            val flags = if (filter.caseSensitive) setOf() else setOf(RegexOption.IGNORE_CASE)
            Regex(pattern, flags)
        } catch (_: Exception) {
            null
        }
    }

    fun replaceInFile(filePath: String, matches: List<ProjectSearchMatch>, replacement: String, projectUri: Uri, onDone: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val uri = Uri.parse(filePath)
                val content = fileManager.readFile(uri).getOrThrow()
                val lines = content.split('\n').toMutableList()
                val sortedMatches = matches.sortedByDescending { it.line }
                for (match in sortedMatches) {
                    val lineIndex = match.line - 1
                    if (lineIndex !in lines.indices) continue
                    val lineText = lines[lineIndex]
                    val start = match.startCol.coerceIn(0, lineText.length)
                    val end = match.endCol.coerceIn(start, lineText.length)
                    lines[lineIndex] = lineText.substring(0, start) + replacement + lineText.substring(end)
                }
                fileManager.writeFile(uri, lines.joinToString("\n"))
                onDone()
            } catch (_: Exception) {
            }
        }
    }

    fun replaceAllInProject(projectUri: Uri, onDone: () -> Unit) {
        val replacement = _replaceQuery.value
        val query = _searchQuery.value
        if (query.isBlank()) return
        val filter = _filter.value
        val regex = buildSearchRegex(query, filter) ?: return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val files = fileManager.searchFiles(projectUri, "", recursive = true).getOrThrow()
                    .filter { it.isFile }
                for (file in files) {
                    val uri = file.uri
                    val content = fileManager.readFile(uri).getOrThrow()
                    val newContent = try {
                        regex.replace(content, replacement)
                    } catch (_: Exception) {
                        continue
                    }
                    if (newContent != content) {
                        fileManager.writeFile(uri, newContent)
                    }
                }
                onDone()
            } catch (_: Exception) {
            }
        }
    }

    fun clearResults() {
        _result.value = ProjectSearchResult(emptyMap(), 0, 0, "")
        _searchState.value = SearchState.IDLE
        _progress.value = ""
        _error.value = null
    }
}
