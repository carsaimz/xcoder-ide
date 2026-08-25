package com.xcoder.remote.connection

import com.xcoder.remote.model.ConnectionProtocol
import com.xcoder.remote.model.ConnectionSession
import com.xcoder.remote.model.ConnectionState
import com.xcoder.remote.model.RemoteConnectionInfo
import com.xcoder.remote.model.RemoteFileEntry
import com.xcoder.remote.model.TransferEvent
import com.xcoder.remote.util.EncryptionUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages multiple concurrent remote connections.
 *
 * Responsibilities:
 * - Create, cache, and destroy [RemoteFileSystem] instances
 * - Persist and load saved connection configurations
 * - Expose a unified view of all connection states
 * - Provide a [RemoteFileSystemProvider] factory
 *
 * This is the central point through which the application interacts with remote servers.
 */
@Singleton
class ConnectionManager @Inject constructor(
    private val encryptionUtils: EncryptionUtils
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Active filesystem instances keyed by connection ID. */
    private val activeFileSystems = ConcurrentHashMap<String, RemoteFileSystem>()

    /** Saved connection configurations. */
    private val _savedConnections = MutableStateFlow<List<RemoteConnectionInfo>>(emptyList())
    val savedConnections: Flow<List<RemoteConnectionInfo>> = _savedConnections.asStateFlow()

    /** Currently connected filesystem instances. */
    val connectedFileSystems: List<RemoteFileSystem>
        get() = activeFileSystems.values.filter { it.connectionState == ConnectionState.CONNECTED }

    /** All transfer events from all connections merged. */
    private val _allTransferEvents = MutableSharedFlow<TransferEvent>(extraBufferCapacity = 128)
    val allTransferEvents: Flow<TransferEvent> = _allTransferEvents.asSharedFlow()

    private val passwordDecryptor: (String) -> String = { encrypted ->
        try { encryptionUtils.decrypt(encrypted) } catch (_: Exception) { encrypted }
    }

    // ── Connection CRUD ───────────────────────────────────────────────

    /**
     * Save a new connection configuration or update an existing one.
     */
    fun saveConnection(info: RemoteConnectionInfo) {
        val current = _savedConnections.value.toMutableList()
        val existingIndex = current.indexOfFirst { it.id == info.id }
        if (existingIndex >= 0) {
            current[existingIndex] = info
        } else {
            current.add(info)
        }
        _savedConnections.value = current
    }

    /**
     * Remove a saved connection. If currently connected, disconnects first.
     */
    fun removeConnection(connectionId: String) {
        scope.launch {
            getFileSystem(connectionId)?.disconnect()
        }
        activeFileSystems.remove(connectionId)
        _savedConnections.value = _savedConnections.value.filter { it.id != connectionId }
    }

    /**
     * Get a saved connection by ID.
     */
    fun getConnectionInfo(connectionId: String): RemoteConnectionInfo? {
        return _savedConnections.value.firstOrNull { it.id == connectionId }
    }

    /**
     * Get a saved connection by nickname.
     */
    fun getConnectionByNickname(nickname: String): RemoteConnectionInfo? {
        return _savedConnections.value.firstOrNull { it.nickname == nickname }
    }

    /**
     * Get favorite connections, sorted by last connected.
     */
    fun getFavoriteConnections(): List<RemoteConnectionInfo> {
        return _savedConnections.value
            .filter { it.isFavorite }
            .sortedByDescending { it.lastConnectedAt }
    }

    /**
     * Get recently used connections (non-favorites), sorted by last connected.
     */
    fun getRecentConnections(limit: Int = 10): List<RemoteConnectionInfo> {
        return _savedConnections.value
            .filter { !it.isFavorite && it.lastConnectedAt > 0 }
            .sortedByDescending { it.lastConnectedAt }
            .take(limit)
    }

    /**
     * Search connections by host, nickname, or username.
     */
    fun searchConnections(query: String): List<RemoteConnectionInfo> {
        val lowerQuery = query.lowercase()
        return _savedConnections.value.filter {
            it.nickname.lowercase().contains(lowerQuery) ||
            it.host.lowercase().contains(lowerQuery) ||
            it.username.lowercase().contains(lowerQuery)
        }
    }

    // ── Filesystem Lifecycle ──────────────────────────────────────────

    /**
     * Get or create a [RemoteFileSystem] for the given connection ID.
     * Does NOT connect; the caller must call [RemoteFileSystem.connect].
     */
    fun getFileSystem(connectionId: String): RemoteFileSystem? {
        return activeFileSystems[connectionId]
    }

    /**
     * Create a [RemoteFileSystem] for the given [info], cache it, and connect.
     */
    suspend fun connect(info: RemoteConnectionInfo): RemoteResult<RemoteFileSystem> {
        val existing = activeFileSystems[info.id]
        if (existing != null) {
            val connected = existing.isConnected()
            if (connected) return RemoteResult.Success(existing)
        }

        val fs = createFileSystem(info)
        activeFileSystems[info.id] = fs

        // Collect transfer events
        scope.launch {
            fs.transferEvents.collect { event ->
                _allTransferEvents.emit(event)
            }
        }

        val result = fs.connect()
        if (result.isSuccess) {
            saveConnection(info.recordConnection())
        }
        return result.map { fs }
    }

    /**
     * Disconnect and remove a cached filesystem.
     */
    suspend fun disconnect(connectionId: String) {
        val fs = activeFileSystems.remove(connectionId)
        fs?.disconnect()
    }

    /**
     * Disconnect all active connections.
     */
    suspend fun disconnectAll() {
        for ((id, fs) in activeFileSystems) {
            try { fs.disconnect() } catch (_: Exception) { }
        }
        activeFileSystems.clear()
    }

    /**
     * Test a connection without caching the filesystem.
     */
    suspend fun testConnection(info: RemoteConnectionInfo): RemoteResult<String> {
        val fs = createFileSystem(info)
        return try {
            val result = fs.connect()
            if (result.isSuccess) {
                val serverInfo = fs.getServerInfo()
                fs.disconnect()
                serverInfo
            } else {
                try { fs.disconnect() } catch (_: Exception) { }
                RemoteResult.Error(result.error!!.code, result.error.message)
            }
        } catch (e: Exception) {
            RemoteResult.Error(ErrorCode.UNKNOWN, e.message ?: "Test failed", e)
        }
    }

    /**
     * Create the appropriate [RemoteFileSystem] implementation for the protocol.
     */
    fun createFileSystem(info: RemoteConnectionInfo): RemoteFileSystem {
        return when (info.protocol) {
            ConnectionProtocol.SFTP -> {
                com.xcoder.remote.sftp.SftpClient(
                    connectionInfo = info,
                    passwordDecryptor = passwordDecryptor
                )
            }
            ConnectionProtocol.FTP, ConnectionProtocol.FTPS_IMPLICIT, ConnectionProtocol.FTPS_EXPLICIT -> {
                com.xcoder.remote.ftp.FtpClient(
                    connectionInfo = info,
                    passwordDecryptor = passwordDecryptor
                )
            }
        }
    }

    /**
     * Factory method implementing [RemoteFileSystemProvider].
     */
    fun createProvider(): RemoteFileSystemProvider = object : RemoteFileSystemProvider {
        override fun create(connectionInfo: RemoteConnectionInfo): RemoteFileSystem {
            return createFileSystem(connectionInfo)
        }

        override suspend fun createAndConnect(connectionInfo: RemoteConnectionInfo): RemoteResult<RemoteFileSystem> {
            return connect(connectionInfo)
        }

        override suspend fun testConnection(connectionInfo: RemoteConnectionInfo): RemoteResult<String> {
            return this@ConnectionManager.testConnection(connectionInfo)
        }
    }

    /**
     * Load saved connections from a serialized JSON string.
     */
    fun loadConnectionsFromJson(json: String) {
        if (json.isBlank()) return
        try {
            val type = object : com.google.gson.reflect.TypeToken<List<RemoteConnectionInfo>>() {}.type
            com.google.gson.Gson().fromJson<List<RemoteConnectionInfo>>(json, type)
        } catch (_: Exception) {
            emptyList()
        }.let { _savedConnections.value = it }
    }

    /**
     * Serialize all saved connections to JSON.
     */
    fun serializeConnectionsToJson(): String {
        return try {
            com.google.gson.Gson().toJson(_savedConnections.value)
        } catch (_: Exception) {
            "[]"
        }
    }

    companion object {
        private const val TAG = "ConnectionManager"
    }
}
