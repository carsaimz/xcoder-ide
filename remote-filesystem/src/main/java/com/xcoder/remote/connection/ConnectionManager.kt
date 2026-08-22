package com.xcoder.remote.connection

import android.content.Context
import android.util.Log
import com.xcoder.remote.files.RemoteFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import net.schmizz.sshj.xfer.FileSystemFile
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPSClient
import java.io.*
import java.net.InetSocketAddress
import java.net.Socket
import java.security.KeyPair
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

enum class ConnectionType(val displayName: String) {
    FTP("FTP"), FTPS("FTPS"), SFTP("SFTP"), WEBDAV("WebDAV")
}

data class ServerConfig(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val type: ConnectionType,
    val host: String,
    val port: Int,
    val username: String,
    val password: String = "",
    val privateKeyPath: String = "",
    val privateKeyPassphrase: String = "",
    val remotePath: String = "/",
    val useCompression: Boolean = true,
    val connectTimeout: Int = 15000,
    val keepAliveInterval: Int = 30,
    val maxRetries: Int = 3,
    val encoding: String = "UTF-8",
    val isActiveMode: Boolean = false,
    val trustAllCerts: Boolean = false,
    val lastConnected: Long = 0L
) {
    val displayName: String get() = "$name ($type)"
}

sealed class ConnectionResult {
    data class Success(val message: String = "Connected") : ConnectionResult()
    data class Error(val message: String, val throwable: Throwable? = null) : ConnectionResult()
    object Disconnected : ConnectionResult()
}

data class ConnectionInfo(
    val server: ServerConfig,
    val connectedAt: Long = System.currentTimeMillis(),
    val latencyMs: Long = 0
)

@Singleton
class ConnectionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connections = ConcurrentHashMap<String, ConnectionHolder>()
    private val _connectionStates = MutableStateFlow<Map<String, ConnectionState>>(emptyMap())
    val connectionStates: StateFlow<Map<String, ConnectionState>> = _connectionStates.asStateFlow()
    private val _activeConnections = MutableStateFlow<List<ConnectionInfo>>(emptyList())
    val activeConnections: StateFlow<List<ConnectionInfo>> = _activeConnections.asStateFlow()
    private val _transferProgress = MutableStateFlow<TransferProgress?>(null)
    val transferProgress: StateFlow<TransferProgress?> = _transferProgress.asStateFlow()

    data class ConnectionHolder(
        val config: ServerConfig,
        val ftpClient: FTPClient? = null,
        val sftpClient: SFTPClient? = null,
        val sshClient: SSHClient? = null,
        val createdAt: Long = System.currentTimeMillis()
    ) {
        val isConnected: Boolean
            get() = when {
                ftpClient != null -> ftpClient.isConnected
                sftpClient != null -> sshClient?.isConnected == true
                else -> false
            }
    }

    data class TransferProgress(
        val serverId: String,
        val fileName: String,
        val bytesTransferred: Long,
        val totalBytes: Long,
        val isUpload: Boolean,
        val speedBytesPerSec: Long = 0
    ) {
        val progress: Float get() = if (totalBytes > 0) bytesTransferred.toFloat() / totalBytes else 0f
        val formattedSpeed: String get() = formatBytes(speedBytesPerSec) + "/s"
        val formattedTransferred: String get() = "${formatBytes(bytesTransferred)} / ${formatBytes(totalBytes)}"
        val percentage: String get() = "${(progress * 100).toInt()}%"
    }

    fun getConnectedServers(): List<ServerConfig> =
        connections.values.filter { it.isConnected }.map { it.config }

    fun isConnected(serverId: String): Boolean = connections[serverId]?.isConnected == true

    suspend fun connect(config: ServerConfig): ConnectionResult = withContext(Dispatchers.IO) {
        updateState(config.id, ConnectionState.Connecting)
        var lastError: Throwable? = null
        repeat(config.maxRetries.coerceAtLeast(1)) { attempt ->
            try {
                val startTime = System.currentTimeMillis()
                when (config.type) {
                    ConnectionType.FTP -> connectFtp(config)
                    ConnectionType.FTPS -> connectFtps(config)
                    ConnectionType.SFTP -> connectSftp(config)
                    ConnectionType.WEBDAV -> connectWebDav(config)
                }
                val latency = System.currentTimeMillis() - startTime
                updateState(config.id, ConnectionState.Connected(latency))
                _activeConnections.value = _activeConnections.value + ConnectionInfo(
                    server = config.copy(lastConnected = System.currentTimeMillis()),
                    latencyMs = latency
                )
                Log.d(TAG, "Connected to ${config.name} in ${latency}ms")
                return@withContext ConnectionResult.Success("Connected to ${config.name}")
            } catch (e: Exception) {
                Log.w(TAG, "Connection attempt ${attempt + 1} failed for ${config.name}", e)
                lastError = e
                if (attempt < config.maxRetries - 1) delay(1000L * (attempt + 1))
            }
        }
        updateState(config.id, ConnectionState.Error(lastError?.message ?: "Connection failed"))
        ConnectionResult.Error("Failed to connect after ${config.maxRetries} attempts", lastError)
    }

    private fun connectFtp(config: ServerConfig) {
        val client = FTPClient().apply {
            connectTimeout = config.connectTimeout
            defaultTimeout = config.connectTimeout
            controlKeepAliveTimeout = config.keepAliveInterval.toLong()
            if (config.isActiveMode) enterLocalActiveMode() else enterLocalPassiveMode()
        }
        client.connect(config.host, config.port)
        if (!client.login(config.username, config.password)) {
            throw IOException("FTP login failed for ${config.host}")
        }
        client.setFileType(FTP.BINARY_FILE_TYPE)
        client.setControlEncoding(config.encoding)
        client.execPBSZ(0)
        client.execPROT("P")
        connections[config.id] = ConnectionHolder(config, ftpClient = client)
    }

    private fun connectFtps(config: ServerConfig) {
        val client = FTPSClient(config.trustAllCerts).apply {
            connectTimeout = config.connectTimeout
        }
        client.connect(config.host, config.port)
        if (!client.login(config.username, config.password)) {
            throw IOException("FTPS login failed")
        }
        client.setFileType(FTP.BINARY_FILE_TYPE)
        client.execPBSZ(0)
        client.execPROT("P")
        connections[config.id] = ConnectionHolder(config, ftpClient = client)
    }

    private fun connectSftp(config: ServerConfig) {
        val ssh = SSHClient().apply {
            addHostKeyVerifier(PromiscuousVerifier())
            connectTimeout = config.connectTimeout * 1000
            timeout = config.connectTimeout * 1000
        }
        ssh.connect(config.host, config.port)
        if (config.privateKeyPath.isNotBlank()) {
            val keyFile = File(config.privateKeyPath)
            if (config.privateKeyPassphrase.isBlank()) ssh.authPublickey(config.username, ssh.loadKeys(keyFile.path))
            else ssh.authPublickey(config.username, ssh.loadKeys(keyFile.path, config.privateKeyPassphrase))
        } else {
            ssh.authPassword(config.username, config.password)
        }
        val sftp = ssh.newSFTPClient()
        if (config.useCompression) {
            sftp.useCompression()
        }
        connections[config.id] = ConnectionHolder(config, sftpClient = sftp, sshClient = ssh)
    }

    private fun connectWebDav(config: ServerConfig) {
        val url = "${if (config.port == 443) "https" else "http"}://${config.host}:${config.port}${config.remotePath}"
        java.net.URL(url).openConnection().connect()
        connections[config.id] = ConnectionHolder(config)
    }

    suspend fun disconnect(serverId: String) = withContext(Dispatchers.IO) {
        connections[serverId]?.let { holder ->
            try { holder.ftpClient?.disconnect() } catch (_: Exception) {}
            try { holder.sftpClient?.close() } catch (_: Exception) {}
            try { holder.sshClient?.disconnect() } catch (_: Exception) {}
            connections.remove(serverId)
            updateState(serverId, ConnectionState.Disconnected)
            _activeConnections.value = _activeConnections.value.filter { it.server.id != serverId }
        }
    }

    suspend fun disconnectAll() = withContext(Dispatchers.IO) {
        connections.keys.toList().forEach { disconnect(it) }
    }

    fun getFtpClient(serverId: String): FTPClient? = connections[serverId]?.ftpClient
    fun getSftpClient(serverId: String): SFTPClient? = connections[serverId]?.sftpClient
    fun getHolder(serverId: String): ConnectionHolder? = connections[serverId]

    private fun updateState(serverId: String, state: ConnectionState) {
        _connectionStates.value = _connectionStates.value + (serverId to state)
    }

    fun testConnection(config: ServerConfig, timeoutMs: Long = 5000): Boolean {
        return try {
            val socket = Socket()
            socket.connect(InetSocketAddress(config.host, config.port), timeoutMs.toInt())
            socket.close()
            true
        } catch (e: Exception) { false }
    }

    companion object {
        const val TAG = "ConnectionManager"
        fun formatBytes(bytes: Long): String = when {
            bytes < 1024 -> "$bytes B"
            bytes < 1048576 -> "${"%.1f".format(bytes / 1024.0)} KB"
            bytes < 1073741824 -> "${"%.1f".format(bytes / 1048576.0)} MB"
            else -> "${"%.2f".format(bytes / 1073741824.0)} GB"
        }
    }
}

sealed class ConnectionState {
    object Idle : ConnectionState()
    object Connecting : ConnectionState()
    data class Connected(val latencyMs: Long = 0) : ConnectionState()
    data class Transferring(val progress: Float) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
    object Disconnected : ConnectionState()
}