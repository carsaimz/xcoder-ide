package com.xcoder.remote.model

import kotlinx.serialization.Serializable

/**
 * Supported remote connection protocol types.
 */
enum class ConnectionProtocol {
    FTP,
    FTPS_IMPLICIT,
    FTPS_EXPLICIT,
    SFTP
}

/**
 * Authentication method for remote connections.
 */
enum class AuthMethod {
    PASSWORD,
    PUBLIC_KEY,
    KEYBOARD_INTERACTIVE,
    ANONYMOUS
}

/**
 * FTP/SFTP data transfer mode.
 */
enum class TransferMode {
    ACTIVE,
    PASSIVE
}

/**
 * Complete configuration for a single remote server connection.
 *
 * Serialized to/from JSON for persistent storage via DataStore.
 */
@Serializable
data class RemoteConnectionInfo(
    /** Unique identifier for this saved connection. */
    val id: String,
    /** Human-readable label shown in the UI connection list. */
    val nickname: String,
    /** Protocol to use when connecting. */
    val protocol: ConnectionProtocol,
    /** Server hostname or IP address. */
    val host: String,
    /** Server port number. Defaults depend on protocol. */
    val port: Int,
    /** Username for authentication. Empty for anonymous FTP. */
    val username: String,
    /** Encrypted password. Use [AuthMethod.PUBLIC_KEY] for key-based auth. */
    val encryptedPassword: String = "",
    /** Authentication method. */
    val authMethod: AuthMethod = AuthMethod.PASSWORD,
    /** Path to a private key file on local storage (SFTP only). */
    val privateKeyPath: String = "",
    /** Passphrase protecting the private key (SFTP only). */
    val encryptedKeyPassphrase: String = "",
    /** Initial remote directory to navigate to after connect. */
    val initialPath: String = "/",
    /** FTP data transfer mode (FTP/FTPS only). */
    val transferMode: TransferMode = TransferMode.PASSIVE,
    /** Whether to verify the server's TLS/SSL certificate (FTPS only). */
    val verifyCertificate: Boolean = true,
    /** Whether to verify the SSH host key (SFTP only). */
    val verifyHostKey: Boolean = true,
    /** Known host key fingerprint for SFTP host key verification. */
    val knownHostFingerprint: String = "",
    /** Connection timeout in milliseconds. */
    val connectTimeoutMs: Int = 15_000,
    /** Data transfer timeout in milliseconds. */
    val dataTimeoutMs: Int = 30_000,
    /** Keep-alive interval in seconds. 0 = disabled. */
    val keepAliveIntervalSec: Int = 30,
    /** Maximum number of concurrent transfer threads. */
    val maxConcurrentTransfers: Int = 3,
    /** Encoding for file names and content. */
    val encoding: String = "UTF-8",
    /** Timestamp when this connection was first saved (epoch millis). */
    val createdAt: Long = System.currentTimeMillis(),
    /** Timestamp of last successful connection (epoch millis). */
    val lastConnectedAt: Long = 0L,
    /** Total number of times this connection has been used. */
    val connectCount: Int = 0,
    /** Whether the user has favorited/pinned this connection. */
    val isFavorite: Boolean = false
) {
    val displayHost: String get() = if (port == defaultPort) host else "$host:$port"

    val defaultPort: Int
        get() = when (protocol) {
            ConnectionProtocol.FTP -> 21
            ConnectionProtocol.FTPS_IMPLICIT -> 990
            ConnectionProtocol.FTPS_EXPLICIT -> 21
            ConnectionProtocol.SFTP -> 22
        }

    val schemePrefix: String
        get() = when (protocol) {
            ConnectionProtocol.FTP -> "ftp"
            ConnectionProtocol.FTPS_IMPLICIT, ConnectionProtocol.FTPS_EXPLICIT -> "ftps"
            ConnectionProtocol.SFTP -> "sftp"
        }

    /** Returns a sanitized copy with resolved default port. */
    fun withDefaultPort(): RemoteConnectionInfo {
        if (port == 0) return copy(port = defaultPort)
        return this
    }

    /** Returns a copy with bumped connection metadata. */
    fun recordConnection(): RemoteConnectionInfo = copy(
        lastConnectedAt = System.currentTimeMillis(),
        connectCount = connectCount + 1
    )

    companion object {
        /** Anonymous FTP preset. */
        fun anonymousFtp(host: String, port: Int = 21, nickname: String = host): RemoteConnectionInfo =
            RemoteConnectionInfo(
                id = generateId(),
                nickname = nickname,
                protocol = ConnectionProtocol.FTP,
                host = host,
                port = port,
                username = "anonymous",
                authMethod = AuthMethod.ANONYMOUS,
                transferMode = TransferMode.PASSIVE
            )

        fun generateId(): String = java.util.UUID.randomUUID().toString()
    }
}

/**
 * Runtime state of a connection, separate from the persisted configuration.
 */
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    AUTHENTICATING,
    AUTHENTICATED,
    IDLE,
    BUSY,
    ERROR,
    DISCONNECTING
}

/**
 * Stores the runtime state associated with a connection instance.
 */
data class ConnectionSession(
    val connectionId: String,
    var state: ConnectionState = ConnectionState.DISCONNECTED,
    var currentWorkingDirectory: String = "/",
    var lastError: String? = null,
    val connectedAt: Long = 0L,
    val activeTransfers: Int = 0
) {
    val isConnected: Boolean
        get() = state == ConnectionState.CONNECTED ||
                state == ConnectionState.AUTHENTICATED ||
                state == ConnectionState.IDLE ||
                state == ConnectionState.BUSY

    val isBusy: Boolean get() = state == ConnectionState.BUSY
}
