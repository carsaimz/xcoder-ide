package com.xcoder.remote.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xcoder.remote.cache.CacheManager
import com.xcoder.remote.cache.RemoteFileCache
import com.xcoder.remote.connection.ConnectionManager
import com.xcoder.remote.connection.ConnectionState
import com.xcoder.remote.connection.ErrorCode
import com.xcoder.remote.connection.RemoteFileSystem
import com.xcoder.remote.connection.RemoteResult
import com.xcoder.remote.model.*
import com.xcoder.remote.util.EncryptionUtils
import com.xcoder.remote.util.PathUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for the remote file browser screen.
 */
data class BrowserUiState(
    val connectionId: String = "",
    val connectionInfo: RemoteConnectionInfo? = null,
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val currentPath: String = "/",
    val directoryEntries: List<RemoteFileEntry> = emptyList(),
    val selectedEntries: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val showHiddenFiles: Boolean = false,
    val sortBy: SortBy = SortBy.NAME,
    val sortOrder: SortOrder = SortOrder.ASCENDING,
    val showConnectionDialog: Boolean = false,
    val editingConnection: RemoteConnectionInfo? = null,
    val showCreateDialog: Boolean = false,
    val newEntryName: String = "",
    val newEntryIsDirectory: Boolean = true,
    val transferItems: List<TransferItem> = emptyList(),
    val showTransfersPanel: Boolean = false,
    val diskUsage: DiskUsage? = null,
    val serverInfo: String? = null
) {
    val isConnected: Boolean get() = connectionState == ConnectionState.CONNECTED
    val canGoUp: Boolean get() = currentPath != "/"
    val currentDirectoryName: String get() = PathUtils.fileName(currentPath).ifBlank { "/" }
    val selectedCount: Int get() = selectedEntries.size
    val hasSelection: Boolean get() = selectedEntries.isNotEmpty()
}

enum class SortBy { NAME, SIZE, DATE, TYPE }
enum class SortOrder { ASCENDING, DESCENDING }

@HiltViewModel
class RemoteBrowserViewModel @Inject constructor(
    private val connectionManager: ConnectionManager,
    private val fileCache: RemoteFileCache,
    private val cacheManager: CacheManager,
    private val encryptionUtils: EncryptionUtils
) : ViewModel() {

    var uiState by mutableStateOf(BrowserUiState())
        private set

    private var listJob: Job? = null
    private var currentFileSystem: RemoteFileSystem? = null

    init {
        viewModelScope.launch {
            connectionManager.allTransferEvents.collect { event ->
                when (event) {
                    is TransferEvent.Progress -> updateTransferItem(event.item)
                    is TransferEvent.Completed -> updateTransferItem(event.item)
                    is TransferEvent.Failed -> updateTransferItem(event.item)
                    is TransferEvent.Queued -> addTransferItem(event.item)
                    is TransferEvent.AllCompleted -> { }
                }
            }
        }

        viewModelScope.launch {
            connectionManager.savedConnections.collect { connections ->
                val current = uiState.connectionInfo
                if (current != null) {
                    val updated = connections.firstOrNull { it.id == current.id }
                    if (updated != null) {
                        uiState = uiState.copy(connectionInfo = updated)
                    }
                }
            }
        }
    }

    // ── Connection ────────────────────────────────────────────────────

    fun connect(info: RemoteConnectionInfo) {
        viewModelScope.launch {
            uiState = uiState.copy(
                connectionState = ConnectionState.CONNECTING,
                error = null,
                isLoading = true
            )
            val result = connectionManager.connect(info)
            if (result.isSuccess) {
                currentFileSystem = result.data
                uiState = uiState.copy(
                    connectionId = info.id,
                    connectionInfo = info,
                    connectionState = ConnectionState.CONNECTED,
                    currentPath = info.initialPath,
                    isLoading = false
                )
                observeFileSystemState(result.data)
                navigateTo(info.initialPath)
                loadServerInfo()
            } else {
                uiState = uiState.copy(
                    connectionState = ConnectionState.ERROR,
                    error = result.error?.message,
                    isLoading = false
                )
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            val id = uiState.connectionId
            connectionManager.disconnect(id)
            currentFileSystem = null
            uiState = uiState.copy(
                connectionState = ConnectionState.DISCONNECTED,
                connectionId = "",
                connectionInfo = null,
                currentPath = "/",
                directoryEntries = emptyList(),
                diskUsage = null,
                serverInfo = null
            )
        }
    }

    // ── Navigation ────────────────────────────────────────────────────

    fun navigateTo(path: String) {
        val normalized = PathUtils.normalize(path)
        uiState = uiState.copy(
            currentPath = normalized,
            isLoading = true,
            selectedEntries = emptySet(),
            error = null
        )
        listDirectory(normalized)
    }

    fun navigateUp() {
        if (uiState.currentPath != "/") {
            navigateTo(PathUtils.parent(uiState.currentPath))
        }
    }

    fun refresh() {
        fileCache.invalidateListing(uiState.currentPath)
        navigateTo(uiState.currentPath)
    }

    // ── Directory Listing ─────────────────────────────────────────────

    private fun listDirectory(path: String) {
        listJob?.cancel()
        listJob = viewModelScope.launch(Dispatchers.IO) {
            val fs = currentFileSystem ?: return@launch

            // Check memory cache first
            val cached = fileCache.getListing(path)
            if (cached != null) {
                uiState = uiState.copy(
                    directoryEntries = applySortingAndFiltering(cached),
                    isLoading = false
                )
                // Refresh in background
                refreshDirectory(fs, path)
                return@launch
            }

            val result = fs.listDirectory(path)
            if (result.isSuccess) {
                val listing = result.data
                fileCache.putListing(path, listing.entries)
                uiState = uiState.copy(
                    directoryEntries = applySortingAndFiltering(listing.entries),
                    currentPath = listing.path,
                    isLoading = false
                )
            } else {
                uiState = uiState.copy(
                    error = result.error?.message,
                    isLoading = false
                )
            }
        }
    }

    private suspend fun refreshDirectory(fs: RemoteFileSystem, path: String) {
 val result = fs.listDirectory(path)
        if (result.isSuccess) {
            fileCache.putListing(path, result.data.entries)
            val entries = applySortingAndFiltering(result.data.entries)
            if (uiState.currentPath == path && !uiState.isLoading) {
                uiState = uiState.copy(directoryEntries = entries)
            }
        }
    }

    // ── File Operations ───────────────────────────────────────────────

    fun createNewEntry(name: String, isDirectory: Boolean) {
        viewModelScope.launch {
            val fs = currentFileSystem ?: return@launch
            val fullPath = PathUtils.join(uiState.currentPath, name)
            val result = if (isDirectory) {
                fs.makeDirectory(fullPath)
            } else {
                fs.writeTextFile(fullPath, "")
            }
            if (result.isError) {
                uiState = uiState.copy(error = result.error?.message)
            } else {
                uiState = uiState.copy(showCreateDialog = false, newEntryName = "")
                refresh()
            }
        }
    }

    fun deleteSelected() {
        viewModelScope.launch {
            val fs = currentFileSystem ?: return@launch
            val paths = uiState.selectedEntries.toList()
            val errors = mutableListOf<String>()
            for (path in paths) {
                // Check if it's a directory or file from the entries list
                val entry = uiState.directoryEntries.firstOrNull { it.fullPath == path }
                val result = if (entry != null && entry.isDirectory) {
                    fs.removeDirectory(path, recursive = true)
                } else {
                    fs.deleteFile(path)
                }
                if (result.isError) errors.add("$path: ${result.error?.message}")
            }
            uiState = uiState.copy(
                selectedEntries = emptySet(),
                error = if (errors.isNotEmpty()) errors.joinToString("\n") else null
            )
            refresh()
        }
    }

    fun renameEntry(oldPath: String, newName: String) {
        viewModelScope.launch {
            val fs = currentFileSystem ?: return@launch
            val parent = PathUtils.parent(oldPath)
            val newPath = PathUtils.join(parent, newName)
            val result = fs.rename(oldPath, newPath)
            if (result.isError) {
                uiState = uiState.copy(error = result.error?.message)
            } else {
                refresh()
            }
        }
    }

    // ── Selection ─────────────────────────────────────────────────────

    fun toggleSelection(path: String) {
        val current = uiState.selectedEntries.toMutableSet()
        if (current.contains(path)) current.remove(path) else current.add(path)
        uiState = uiState.copy(selectedEntries = current)
    }

    fun selectAll() {
        uiState = uiState.copy(
            selectedEntries = uiState.directoryEntries.map { it.fullPath }.toSet()
        )
    }

    fun clearSelection() {
        uiState = uiState.copy(selectedEntries = emptySet())
    }

    // ── Transfers ─────────────────────────────────────────────────────

    fun downloadSelected(localBasePath: String) {
        viewModelScope.launch {
            val fs = currentFileSystem ?: return@launch
            for (path in uiState.selectedEntries) {
                val entry = uiState.directoryEntries.firstOrNull { it.fullPath == path } ?: continue
                val localPath = java.io.File(localBasePath, entry.name).absolutePath
                fs.downloadFile(path, localPath, overwrite = true)
            }
        }
    }

    fun uploadFiles(localPaths: List<String>, remoteDirectory: String) {
        viewModelScope.launch {
            val fs = currentFileSystem ?: return@launch
            for (localPath in localPaths) {
                val file = java.io.File(localPath)
                val remotePath = PathUtils.join(remoteDirectory, file.name)
                fs.uploadFile(localPath, remotePath, overwrite = true)
            }
            refresh()
        }
    }

    // ── Search ────────────────────────────────────────────────────────

    fun setSearchActive(active: Boolean) {
        uiState = uiState.copy(isSearchActive = active, searchQuery = "")
        if (!active) refresh()
    }

    fun search(query: String) {
        uiState = uiState.copy(searchQuery = query)
        if (query.isBlank()) {
            refresh()
            return
        }
        val lowerQuery = query.lowercase()
        val filtered = uiState.directoryEntries.filter {
            it.name.lowercase().contains(lowerQuery)
        }
        uiState = uiState.copy(directoryEntries = filtered)
    }

    // ── Sorting & Filtering ───────────────────────────────────────────

    fun setSortBy(sortBy: SortBy) {
        uiState = uiState.copy(sortBy = sortBy)
        uiState = uiState.copy(directoryEntries =
            applySortingAndFiltering(uiState.directoryEntries))
    }

    fun toggleSortOrder() {
        val newOrder = if (uiState.sortOrder == SortOrder.ASCENDING) SortOrder.DESCENDING else SortOrder.ASCENDING
        uiState = uiState.copy(sortOrder = newOrder)
        uiState = uiState.copy(directoryEntries =
            applySortingAndFiltering(uiState.directoryEntries))
    }

    fun toggleShowHidden() {
        uiState = uiState.copy(showHiddenFiles = !uiState.showHiddenFiles)
        refresh()
    }

    // ── UI State ──────────────────────────────────────────────────────

    fun showConnectionDialog(editing: RemoteConnectionInfo? = null) {
        uiState = uiState.copy(showConnectionDialog = true, editingConnection = editing)
    }

    fun hideConnectionDialog() {
        uiState = uiState.copy(showConnectionDialog = false, editingConnection = null)
    }

    fun showCreateDialog(isDirectory: Boolean) {
        uiState = uiState.copy(showCreateDialog = true, newEntryIsDirectory = isDirectory, newEntryName = "")
    }

    fun hideCreateDialog() {
        uiState = uiState.copy(showCreateDialog = false, newEntryName = "")
    }

    fun setNewEntryName(name: String) {
        uiState = uiState.copy(newEntryName = name)
    }

    fun toggleTransfersPanel() {
        uiState = uiState.copy(showTransfersPanel = !uiState.showTransfersPanel)
    }

    fun clearError() {
        uiState = uiState.copy(error = null)
    }

    // ── Server Info ───────────────────────────────────────────────────

    private fun loadServerInfo() {
        viewModelScope.launch {
            val fs = currentFileSystem ?: return@launch
            val infoResult = fs.getServerInfo()
            if (infoResult.isSuccess) {
                uiState = uiState.copy(serverInfo = infoResult.data)
            }
            val diskResult = fs.getDiskUsage(uiState.currentPath)
            if (diskResult.isSuccess) {
                uiState = uiState.copy(diskUsage = diskResult.data)
            }
        }
    }

    // ── Private ───────────────────────────────────────────────────────

    private fun observeFileSystemState(fs: RemoteFileSystem) {
        viewModelScope.launch {
            fs.session.collect { session ->
                uiState = uiState.copy(
                    connectionState = session.state,
                    currentPath = session.currentWorkingDirectory
                )
            }
        }
    }

    private fun applySortingAndFiltering(entries: List<RemoteFileEntry>): List<RemoteFileEntry> {
        var filtered = entries.filter {
            uiState.showHiddenFiles || !it.name.startsWith(".")
        }
        if (uiState.isSearchActive && uiState.searchQuery.isNotBlank()) {
            val query = uiState.searchQuery.lowercase()
            filtered = filtered.filter { it.name.lowercase().contains(query) }
        }
        val comparator = when (uiState.sortBy) {
            SortBy.NAME -> compareBy<RemoteFileEntry> { !it.isDirectory }.thenBy { it.name.lowercase() }
            SortBy.SIZE -> compareBy<RemoteFileEntry> { !it.isDirectory }.thenByDescending { it.size }
            SortBy.DATE -> compareBy<RemoteFileEntry> { !it.isDirectory }.thenByDescending { it.lastModified }
            SortBy.TYPE -> compareBy<RemoteFileEntry> { !it.isDirectory }.thenBy { it.extension.lowercase() }.thenBy { it.name.lowercase() }
        }
        val sorted = if (uiState.sortOrder == SortOrder.DESCENDING) {
            filtered.sortedWith(comparator.reversed())
        } else {
            filtered.sortedWith(comparator)
        }
        return sorted
    }

    private fun updateTransferItem(item: TransferItem) {
        val current = uiState.transferItems.toMutableList()
        val index = current.indexOfFirst { it.id == item.id }
        if (index >= 0) {
            current[index] = item
        } else {
            current.add(item)
        }
        // Remove completed/failed items older than 30 seconds
        val now = System.currentTimeMillis()
        uiState = uiState.copy(
            transferItems = current.filter {
                !it.isTerminal || (now - it.completedAt) < 30_000
            }
        )
    }

    private fun addTransferItem(item: TransferItem) {
        uiState = uiState.copy(
            transferItems = uiState.transferItems + item
        )
    }

    // ── Saved Connections ─────────────────────────────────────────────

    fun saveConnection(info: RemoteConnectionInfo) {
        val encrypted = encryptConnection(info)
        connectionManager.saveConnection(encrypted)
    }

    fun deleteConnection(id: String) {
        connectionManager.removeConnection(id)
        if (uiState.connectionId == id) disconnect()
    }

    fun getSavedConnections(): Flow<List<RemoteConnectionInfo>> {
        return connectionManager.savedConnections
    }

    private fun encryptConnection(info: RemoteConnectionInfo): RemoteConnectionInfo {
        return if (info.encryptedPassword.isNotBlank()) {
            info.copy(encryptedPassword = encryptionUtils.encrypt(info.encryptedPassword))
        } else {
            info
        }
    }

    override fun onCleared() {
        super.onCleared()
        listJob?.cancel()
    }
}