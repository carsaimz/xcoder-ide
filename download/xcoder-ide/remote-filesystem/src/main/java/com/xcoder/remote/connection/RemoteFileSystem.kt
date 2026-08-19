package com.xcoder.remote.connection

import com.xcoder.remote.model.ConnectionSession
import com.xcoder.remote.model.ConnectionState
import com.xcoder.remote.model.DirectoryListing
import com.xcoder.remote.model.RemoteConnectionInfo
import com.xcoder.remote.model.RemoteFileEntry
import com.xcoder.remote.model.TransferEvent
import com.xcoder.remote.model.TransferItem
import kotlinx.coroutines.flow.Flow
import java.io.InputStream
import java.io.OutputStream

/**
 * Result type for remote operations, inspired by Acode's Result pattern.
 */
sealed class RemoteResult<out T> {
    data class Success<T>(val data: T) : RemoteResult<T>()
    data class Error(val code: ErrorCode, val message: String, val cause: Throwable? = null) : RemoteResult<Nothing>()

    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error
    val data: T? get() = (this as? Success)?.data
    val error: Error? get() = this as? Error

    fun <R> map(transform: (T) -> R): RemoteResult<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> this
    }

    fun getOrNull(): T? = (this as? Success)?.data

    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Error -> throw RemoteOperationException(message, cause)
    }
}

/** Standardized error codes for remote operations. */
enum class ErrorCode {
    NETWORK_UNREACHABLE,
    CONNECTION_REFUSED,
    CONNECTION_TIMEOUT,
    AUTHENTICATION_FAILED,
    AUTHENTICATION_CANCELED,
    PERMISSION_DENIED,
    FILE_NOT_FOUND,
    DIRECTORY_NOT_FOUND,
    DIRECTORY_NOT_EMPTY,
    FILE_EXISTS,
    NO_SPACE_ON_SERVER,
    TRANSFER_FAILED,
    TRANSFER_CANCELLED,
    TRANSFER_TIMEOUT,
    INVALID_PATH,
    INVALID_CREDENTIALS,
    HOST_KEY_MISMATCH,
    CERTIFICATE_ERROR,
    PROTOCOL_ERROR,
    ENCODING_ERROR,
    IO_ERROR,
    UNKNOWN,
    CONNECTION_ALREADY_ACTIVE,
    NOT_CONNECTED,
    OPERATION_UNSUPPORTED,
    QUOTA_EXCEEDED,
    TOO_MANY_CONNECTIONS
}

class RemoteOperationException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

/**
 * Unified interface for all remote file system operations.
 *
 * This is the core abstraction inspired by Acode's FTP/SFTP support.
 * Concrete implementations ([FtpClient], [SftpClient]) provide protocol-specific behavior
 * while the rest of the app codes against this interface.
 *
 * All methods are suspending and execute on the IO dispatcher.
 */
interface RemoteFileSystem {

    /**
     * The connection configuration driving this instance.
     */
    val connectionInfo: RemoteConnectionInfo

    /**
     * Live session state, observable via Flow.
     */
    val session: Flow<ConnectionSession>

    /**
     * Current connection state (convenience non-flow accessor).
     */
    val connectionState: ConnectionState

    /**
     * Stream of transfer events for active transfers.
     */
    val transferEvents: Flow<TransferEvent>

    // ── Connection Lifecycle ──────────────────────────────────────────

    /**
     * Establish a connection and authenticate.
     */
    suspend fun connect(): RemoteResult<Unit>

    /**
     * Gracefully disconnect and release all resources.
     */
    suspend fun disconnect(): RemoteResult<Unit>

    /**
     * Returns true if the underlying connection is alive and authenticated.
     */
    suspend fun isConnected(): Boolean

    /**
     * Reconnect using the same configuration. Disconnects first if connected.
     */
    suspend fun reconnect(): RemoteResult<Unit>

    // ── Directory Operations ──────────────────────────────────────────

    /**
     * List the contents of [path]. Returns an empty listing on error.
     */
    suspend fun listDirectory(path: String = "/"): RemoteResult<DirectoryListing>

    /**
     * Change the current working directory.
     */
    suspend fun changeDirectory(path: String): RemoteResult<String>

    /**
     * Get the current working directory.
     */
    suspend fun printWorkingDirectory(): RemoteResult<String>

    /**
     * Create a new directory at [path]. Creates parent directories if [recursive] is true.
     */
    suspend fun makeDirectory(path: String, recursive: Boolean = false): RemoteResult<Unit>

    /**
     * Delete a directory at [path]. Fails if not empty unless [recursive] is true.
     */
    suspend fun removeDirectory(path: String, recursive: Boolean = false): RemoteResult<Unit>

    /**
     * Check if a path exists and is a directory.
     */
    suspend fun isDirectory(path: String): RemoteResult<Boolean>

    // ── File Operations ───────────────────────────────────────────────

    /**
     * Get metadata for a single file or directory.
     */
    suspend fun getFileMetadata(path: String): RemoteResult<RemoteFileEntry>

    /**
     * Check if a path exists.
     */
    suspend fun exists(path: String): RemoteResult<Boolean>

    /**
     * Delete a file at [path].
     */
    suspend fun deleteFile(path: String): RemoteResult<Unit>

    /**
     * Rename or move a file/directory from [oldPath] to [newPath].
     */
    suspend fun rename(oldPath: String, newPath: String): RemoteResult<Unit>

    /**
     * Copy a file on the remote server from [sourcePath] to [destPath].
     * Some servers may not support server-side copy; falls back to download+upload.
     */
    suspend fun copy(sourcePath: String, destPath: String): RemoteResult<Unit>

    /**
     * Change permissions on a remote file/directory.
     * [permissions] is the numeric Unix mode (e.g. 0o755).
     */
    suspend fun changePermissions(path: String, permissions: Int): RemoteResult<Unit>

    // ── File Content Operations ───────────────────────────────────────

    /**
     * Open an [InputStream] for reading a remote file.
     * The caller is responsible for closing the stream.
     */
    suspend fun openInputStream(remotePath: String): RemoteResult<InputStream>

    /**
     * Open an [OutputStream] for writing to a remote file.
     * The caller is responsible for closing the stream.
     * If [append] is true, data is appended; otherwise the file is overwritten.
     */
    suspend fun openOutputStream(remotePath: String, append: Boolean = false): RemoteResult<OutputStream>

    /**
     * Read the entire contents of a remote text file.
     */
    suspend fun readTextFile(remotePath: String, encoding: String = "UTF-8"): RemoteResult<String>

    /**
     * Write text content to a remote file, creating or overwriting it.
     */
    suspend fun writeTextFile(remotePath: String, content: String, encoding: String = "UTF-8"): RemoteResult<Unit>

    // ── Transfer Operations ───────────────────────────────────────────

    /**
     * Download a file from [remotePath] to a local [localPath].
     * Supports cancellation via [cancellationToken] (Job cancellation).
     * Emits progress via [transferEvents].
     */
    suspend fun downloadFile(
        remotePath: String,
        localPath: String,
        overwrite: Boolean = true,
        resume: Boolean = false
    ): RemoteResult<TransferItem>

    /**
     * Upload a local file from [localPath] to [remotePath].
     */
    suspend fun uploadFile(
        localPath: String,
        remotePath: String,
        overwrite: Boolean = true,
        resume: Boolean = false
    ): RemoteResult<TransferItem>

    /**
     * Download an entire directory recursively from [remotePath] to [localPath].
     */
    suspend fun downloadDirectory(
        remotePath: String,
        localPath: String,
        overwrite: Boolean = false
    ): RemoteResult<List<TransferItem>>

    /**
     * Upload an entire local directory recursively from [localPath] to [remotePath].
     */
    suspend fun uploadDirectory(
        localPath: String,
        remotePath: String,
        overwrite: Boolean = false
    ): RemoteResult<List<TransferItem>>

    /**
     * Cancel all active transfers for this connection.
     */
    suspend fun cancelAllTransfers()

    // ── Server Info ───────────────────────────────────────────────────

    /**
     * Get the server's welcome banner / system type string.
     */
    suspend fun getServerInfo(): RemoteResult<String>

    /**
     * Get the total and available disk space for the remote path.
     */
    suspend fun getDiskUsage(path: String = "/"): RemoteResult<DiskUsage>

    /**
     * Abort the current operation in progress.
     */
    suspend fun abort()
}

/** Disk usage information from the remote server. */
data class DiskUsage(
    val totalBytes: Long,
    val usedBytes: Long,
    val availableBytes: Long,
 val path: String
) {
    val usedPercent: Float
        get() = if (totalBytes <= 0L) 0f else (usedBytes.toFloat() / totalBytes.toFloat()) * 100f

    val formattedTotal: String get() = TransferItem.formatFileSize(totalBytes)
    val formattedUsed: String get() = TransferItem.formatFileSize(usedBytes)
    val formattedAvailable: String get() = TransferItem.formatFileSize(availableBytes)
}
