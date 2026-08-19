package com.xcoder.remote.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xcoder.remote.bookmarks.ServerBookmarks
import com.xcoder.remote.connection.*
import com.xcoder.remote.files.RemoteFile
import com.xcoder.remote.files.RemoteFileManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RemoteFileViewModel @Inject constructor(
    private val connectionManager: ConnectionManager,
    private val remoteFileManager: RemoteFileManager,
    private val serverBookmarks: ServerBookmarks
) : ViewModel() {

    private val _uiState = MutableStateFlow(RemoteFileUiState())
    val uiState: StateFlow<RemoteFileUiState> = _uiState.asStateFlow()
    private val _serverConfigs = MutableStateFlow<List<ServerConfig>>(emptyList())
    val serverConfigs: StateFlow<List<ServerConfig>> = _serverConfigs.asStateFlow()
    private val pathHistory = mutableMapOf<String, MutableList<String>>()
    private val pathHistoryIndex = mutableMapOf<String, Int>()

    data class RemoteFileUiState(
        val currentPath: String = "/",
        val files: List<RemoteFile> = emptyList(),
        val isLoading: Boolean = false,
        val selectedFiles: Set<String> = emptySet(),
        val showHidden: Boolean = false,
        val sortBy: SortBy = SortBy.NAME,
        val sortOrder: SortOrder = SortOrder.ASCENDING,
        val error: String? = null,
        val transferProgress: ConnectionManager.TransferProgress? = null,
        val multiSelectMode: Boolean = false,
        val searchQuery: String = ""
    )

    enum class SortBy { NAME, SIZE, DATE, TYPE }
    enum class SortOrder { ASCENDING, DESCENDING }

    init {
        viewModelScope.launch {
            serverBookmarks.bookmarks.collect { configs -> _serverConfigs.value = configs }
        }
        viewModelScope.launch {
            connectionManager.transferProgress.collect { _uiState.value = _uiState.value.copy(transferProgress = it) }
        }
    }

    fun connectToServer(config: ServerConfig) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            connectionManager.connect(config).let { result ->
                when (result) {
                    is ConnectionResult.Success -> {
                        serverBookmarks.saveBookmark(config)
                        browseDirectory(config.id, config.remotePath)
                    }
                    is ConnectionResult.Error -> _uiState.value = _uiState.value.copy(error = result.message)
                    else -> {}
                }
            }
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    fun browseDirectory(serverId: String, path: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, currentPath = path, error = null)
            try {
                val result = remoteFileManager.listFiles(serverId, path)
                val filtered = if (_uiState.value.showHidden) result.files else result.files.filter { !it.name.startsWith(".") }
                val searched = if (_uiState.value.searchQuery.isNotBlank()) filtered.filter { it.name.contains(_uiState.value.searchQuery, ignoreCase = true) } else filtered
                val sorted = applySorting(searched, _uiState.value.sortBy, _uiState.value.sortOrder)
                if (!pathHistory.containsKey(serverId)) pathHistory[serverId] = mutableListOf()
                if (pathHistory[serverId]!!.lastOrNull() != path) {
                    pathHistory[serverId]!!.add(path)
                    pathHistoryIndex[serverId] = pathHistory[serverId]!!.size - 1
                }
                _uiState.value = _uiState.value.copy(files = sorted, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message, isLoading = false)
            }
        }
    }

    fun navigateUp(serverId: String) {
        val currentPath = _uiState.value.currentPath
        if (currentPath != "/") {
            val parent = currentPath.substringBeforeLast("/").ifEmpty { "/" }
            browseDirectory(serverId, parent)
        }
    }

    fun navigateBack(serverId: String) {
        val history = pathHistory[serverId] ?: return
        val idx = (pathHistoryIndex[serverId] ?: 0) - 1
        if (idx >= 0) {
            pathHistoryIndex[serverId] = idx
            browseDirectory(serverId, history[idx])
        }
    }

    fun navigateForward(serverId: String) {
        val history = pathHistory[serverId] ?: return
        val idx = (pathHistoryIndex[serverId] ?: 0) + 1
        if (idx < history.size) {
            pathHistoryIndex[serverId] = idx
            browseDirectory(serverId, history[idx])
        }
    }

    fun refresh(serverId: String) = browseDirectory(serverId, _uiState.value.currentPath)

    fun createFolder(serverId: String, folderName: String) {
        viewModelScope.launch {
            remoteFileManager.createDirectory(serverId, _uiState.value.currentPath, folderName)
            refresh(serverId)
        }
    }

    fun deleteSelected(serverId: String) {
        val state = _uiState.value
        viewModelScope.launch {
            state.selectedFiles.forEach { path ->
                val file = state.files.find { it.path == path }
                if (file != null) remoteFileManager.deleteFile(serverId, path, file.isDirectory)
            }
            _uiState.value = state.copy(selectedFiles = emptySet(), multiSelectMode = false)
            refresh(serverId)
        }
    }

    fun toggleSort(sortBy: SortBy) {
        val state = _uiState.value
        val newOrder = if (state.sortBy == sortBy && state.sortOrder == SortOrder.ASCENDING) SortOrder.DESCENDING else SortOrder.ASCENDING
        _uiState.value = state.copy(sortBy = sortBy, sortOrder = newOrder, files = applySorting(state.files, sortBy, newOrder))
    }

    fun toggleHiddenFiles() {
        _uiState.value = _uiState.value.copy(showHidden = !_uiState.value.showHidden)
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun toggleSelection(filePath: String) {
        val state = _uiState.value
        val newSelection = if (filePath in state.selectedFiles) state.selectedFiles - filePath else state.selectedFiles + filePath
        _uiState.value = state.copy(selectedFiles = newSelection, multiSelectMode = newSelection.isNotEmpty())
    }

    fun disconnect(serverId: String) {
        viewModelScope.launch { connectionManager.disconnect(serverId) }
    }

    fun deleteBookmark(id: String) {
        viewModelScope.launch { serverBookmarks.removeBookmark(id) }
    }

    private fun applySorting(files: List<RemoteFile>, sortBy: SortBy, order: SortOrder): List<RemoteFile> {
        val comparator = when (sortBy) {
            SortBy.NAME -> compareBy<RemoteFile> { it.name.lowercase() }
            SortBy.SIZE -> compareBy { it.size }
            SortBy.DATE -> compareBy { it.lastModified }
            SortBy.TYPE -> compareBy { it.extension.lowercase() }.thenBy { it.name.lowercase() }
        }
        return if (order == SortOrder.ASCENDING) files.sortedWith(comparator) else files.sortedWith(comparator).reversed()
    }
}