package com.xcoder.remote.ftp

import com.xcoder.remote.connection.ConnectionState
import com.xcoder.remote.connection.DiskUsage
import com.xcoder.remote.connection.ErrorCode
import com.xcoder.remote.connection.RemoteFileSystem
import com.xcoder.remote.connection.RemoteResult
import com.xcoder.remote.connection.RemoteResult.Error
import com.xcoder.remote.connection.RemoteResult.Success
import com.xcoder.remote.model.ConnectionProtocol
import com.xcoder.remote.model.ConnectionSession
import com.xcoder.remote.model.DirectoryListing
import com.xcoder.remote.model.RemoteConnectionInfo
import com.xcoder.remote.model.RemoteFileEntry
import com.xcoder.remote.model.TransferEvent
import com.xcoder.remote.model.TransferItem
import com.xcoder.remote.util.PathUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile
import org.apache.commons.net.ftp.FTPReply
import org.apache.commons.net.ftp.FTPSClient
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * FTP/FTPS implementation of [RemoteFileSystem].
 *
 * Wraps Apache Commons Net [FTPClient] / [FTPSClient] with coroutine support,
 * structured error codes, and progress reporting.
 */
class FtpClient(
    override val connectionInfo: RemoteConnectionInfo,
    private val passwordDecryptor: (String) -> String = { it }
) : RemoteFileSystem {

    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: ConnectionState get() = _state.value

    private val _session = MutableStateFlow(
        ConnectionSession(connectionId = connectionInfo.id, currentWorkingDirectory = connectionInfo.initialPath)
    )
    override val session: Flow<ConnectionSession> = _session.asStateFlow()

    private val _transferEvents = MutableSharedFlow<TransferEvent>(extraBufferCapacity = 64)
    override val transferEvents: Flow<TransferEvent> = _transferEvents.asSharedFlow()

    private val clientRef = AtomicReference<FTPClient?>(null)
    private val cancelled = AtomicBoolean(false)

    private val ftpClient: FTPClient? get() = clientRef.get()

    // ── Connection Lifecycle ──────────────────────────────────────────

    override suspend fun connect(): RemoteResult<Unit> = withContext(Dispatchers.IO) {
        if (ftpClient?.isConnected == true) {
            return@withContext Error(ErrorCode.CONNECTION_ALREADY_ACTIVE, "Already connected")
        }
        try {
            _state.value = ConnectionState.CONNECTING
            val client = createClient()
            clientRef.set(client)

            client.connect(connectionInfo.host, connectionInfo.port)
            if (!FTPReply.isPositiveCompletion(client.replyCode)) {
                val msg = client.replyString.trim()
                client.disconnect()
                clientRef.set(null)
                _state.value = ConnectionState.ERROR
                _session.value = _session.value.copy(lastError = msg)
                return@withContext Error(ErrorCode.CONNECTION_REFUSED, msg)
            }

            _state.value = ConnectionState.AUTHENTICATING
            val password = if (connectionInfo.authMethod == com.xcoder.remote.model.AuthMethod.ANONYMOUS) {
                "anonymous@"
            } else {
                passwordDecryptor(connectionInfo.encryptedPassword)
            }
            val loggedIn = client.login(connectionInfo.username, password)
            if (!loggedIn) {
                val msg = client.replyString.trim()
                client.disconnect()
                clientRef.set(null)
                _state.value = ConnectionState.ERROR
                _session.value = _session.value.copy(lastError = msg)
                return@withContext Error(ErrorCode.AUTHENTICATION_FAILED, msg)
            }

            // Configure transfer settings
            client.setFileType(FTP.BINARY_FILE_TYPE)
            client.setControlKeepAliveTimeout(connectionInfo.keepAliveIntervalSec.toLong())
            client.setDataTimeout(connectionInfo.dataTimeoutMs.toLong())

            if (connectionInfo.transferMode == com.xcoder.remote.model.TransferMode.PASSIVE) {
                client.enterLocalPassiveMode()
                if (client is FTPSClient) {
                    client.execPBSZ(0)
                    client.execPROT("P")
                }
            } else {
                client.enterLocalActiveMode()
            }

            // Navigate to initial path
            if (connectionInfo.initialPath.isNotBlank() && connectionInfo.initialPath != "/") {
                client.changeWorkingDirectory(connectionInfo.initialPath)
            }

            val pwd = client.printWorkingDirectory() ?: "/"
            _state.value = ConnectionState.CONNECTED
            _session.value = ConnectionSession(
                connectionId = connectionInfo.id,
                state = ConnectionState.CONNECTED,
                currentWorkingDirectory = pwd,
                connectedAt = System.currentTimeMillis()
            )
            Success(Unit)
        } catch (e: CancellationException) {
            _state.value = ConnectionState.DISCONNECTED
            throw e
        } catch (e: SocketTimeoutException) {
            _state.value = ConnectionState.ERROR
            _session.value = _session.value.copy(lastError = e.message)
            Error(ErrorCode.CONNECTION_TIMEOUT, "Connection timed out", e)
        } catch (e: Exception) {
            _state.value = ConnectionState.ERROR
            _session.value = _session.value.copy(lastError = e.message)
            Error(ErrorCode.NETWORK_UNREACHABLE, "Failed to connect: ${e.message}", e)
        }
    }

    override suspend fun disconnect(): RemoteResult<Unit> = withContext(Dispatchers.IO) {
        try {
            _state.value = ConnectionState.DISCONNECTING
            cancelled.set(true)
            val client = ftpClient
            if (client?.isConnected == true) {
                try { client.logout() } catch (_: Exception) {}
                try { client.disconnect() } catch (_: Exception) {}
            }
            clientRef.set(null)
            _state.value = ConnectionState.DISCONNECTED
            _session.value = _session.value.copy(
                state = ConnectionState.DISCONNECTED,
                activeTransfers = 0
            )
            Success(Unit)
        } catch (e: Exception) {
            _state.value = ConnectionState.DISCONNECTED
            Error(ErrorCode.IO_ERROR, "Error during disconnect: ${e.message}", e)
        }
    }

    override suspend fun isConnected(): Boolean = withContext(Dispatchers.IO) {
        try {
            val client = ftpClient ?: return@withContext false
            if (!client.isConnected) return@withContext false
            client.sendNoOp()
            FTPReply.isPositiveCompletion(client.replyCode)
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun reconnect(): RemoteResult<Unit> {
        disconnect()
        return connect()
    }

    // ── Directory Operations ──────────────────────────────────────────

    override suspend fun listDirectory(path: String): RemoteResult<DirectoryListing> =
        withClient { client ->
            val startTime = System.currentTimeMillis()
            val normalizedPath = PathUtils.normalize(path)
            val files = client.listFiles(normalizedPath)
            if (files == null) {
                return@withClient Error(ErrorCode.DIRECTORY_NOT_FOUND, "Cannot list directory: $path")
            }
            val entries = files
                .filter { it.name != "." && it.name != ".." }
                .map { it.toRemoteFileEntry(normalizedPath, connectionInfo.id) }
            val elapsed = System.currentTimeMillis() - startTime
            Success(DirectoryListing(normalizedPath, entries, listingTimeMs = elapsed))
        }

    override suspend fun changeDirectory(path: String): RemoteResult<String> =
        withClient { client ->
            val normalizedPath = PathUtils.normalize(path)
            val success = client.changeWorkingDirectory(normalizedPath)
            if (!success) {
                return@withClient Error(ErrorCode.DIRECTORY_NOT_FOUND, "Cannot change to: $path")
            }
            val pwd = client.printWorkingDirectory() ?: normalizedPath
            _session.value = _session.value.copy(currentWorkingDirectory = pwd)
            Success(pwd)
        }

    override suspend fun printWorkingDirectory(): RemoteResult<String> =
        withClient { client ->
            val pwd = client.printWorkingDirectory()
            if (pwd != null) {
                _session.value = _session.value.copy(currentWorkingDirectory = pwd)
                Success(pwd)
            } else {
                Error(ErrorCode.PROTOCOL_ERROR, "PWD command failed")
            }
        }

    override suspend fun makeDirectory(path: String, recursive: Boolean): RemoteResult<Unit> =
        withClient { client ->
            if (recursive) {
                val parts = PathUtils.split(path)
                var current = ""
                for (part in parts) {
                    current = PathUtils.join(current, part)
                    if (!isFtpDirectoryExists(client, current)) {
                        val created = client.makeDirectory(current)
                        if (!created) {
                            val reply = client.replyString.trim()
                            if (!FTPReply.isPositiveCompletion(client.replyCode)) {
                                return@withClient Error(ErrorCode.PERMISSION_DENIED,
                                    "Failed to create directory: $current — $reply")
                            }
                        }
                    }
                }
                Success(Unit)
            } else {
                val created = client.makeDirectory(path)
                if (created || FTPReply.isPositiveCompletion(client.replyCode)) {
                    Success(Unit)
                } else {
                    Error(ErrorCode.PERMISSION_DENIED, "Failed to create directory: $path")
                }
            }
        }

    override suspend fun removeDirectory(path: String, recursive: Boolean): RemoteResult<Unit> =
        withClient { client ->
            if (recursive) {
                removeDirectoryRecursive(client, path)
            } else {
                val removed = client.removeDirectory(path)
                if (removed || FTPReply.isPositiveCompletion(client.replyCode)) {
                    Success(Unit)
                } else {
                    Error(ErrorCode.DIRECTORY_NOT_EMPTY, "Cannot remove directory: $path")
                }
            }
        }

    override suspend fun isDirectory(path: String): RemoteResult<Boolean> =
        withClient { client ->
            Success(isFtpDirectoryExists(client, path))
        }

    // ── File Operations ───────────────────────────────────────────────

    override suspend fun getFileMetadata(path: String): RemoteResult<RemoteFileEntry> =
        withClient { client ->
            val parent = PathUtils.parent(path)
            val name = PathUtils.fileName(path)
            val files = client.listFiles(parent)
            val match = files?.firstOrNull {
                it.name == name
            }
            if (match != null) {
                Success(match.toRemoteFileEntry(parent, connectionInfo.id))
            } else {
                // Try mlistFile for servers that support it
                val mdtm = client.getModificationTime(path)
                val size = client.getSize(path)
                if (mdtm != null || size >= 0) {
                    Success(RemoteFileEntry(
                        fullPath = path,
                        name = name,
                        isDirectory = false,
                        size = size.coerceAtLeast(0L),
                        connectionId = connectionInfo.id
                    ))
                } else {
                    Error(ErrorCode.FILE_NOT_FOUND, "File not found: $path")
                }
            }
        }

    override suspend fun exists(path: String): RemoteResult<Boolean> =
        withClient { client ->
            val parent = PathUtils.parent(path)
            val name = PathUtils.fileName(path)
            if (parent.isBlank()) {
                Success(true)
            } else {
                val files = client.listFiles(parent)
                Success(files?.any { it.name == name } == true)
            }
        }

    override suspend fun deleteFile(path: String): RemoteResult<Unit> =
        withClient { client ->
            val deleted = client.deleteFile(path)
            if (deleted) {
                Success(Unit)
            } else {
                Error(ErrorCode.PERMISSION_DENIED, "Failed to delete file: $path")
            }
        }

    override suspend fun rename(oldPath: String, newPath: String): RemoteResult<Unit> =
        withClient { client ->
            val renamed = client.rename(oldPath, newPath)
            if (renamed) {
                Success(Unit)
            } else {
                Error(ErrorCode.PERMISSION_DENIED, "Failed to rename: $oldPath -> $newPath")
            }
        }

    override suspend fun copy(sourcePath: String, destPath: String): RemoteResult<Unit> {
        // FTP has no native copy command; download then upload
        val tempFile = File.createTempFile("ftp_copy_", ".tmp")
        return try {
            val downloadResult = downloadFile(sourcePath, tempFile.absolutePath)
            if (downloadResult.isError) {
                return downloadResult.map { }
            }
            uploadFile(tempFile.absolutePath, destPath)
        } finally {
            tempFile.delete()
        }
    }

    override suspend fun changePermissions(path: String, permissions: Int): RemoteResult<Unit> =
        withClient { client ->
            val success = client.sendSiteCommand("CHMOD ${permissions.toString(8).padStart(4, '0')} $path")
            if (success || FTPReply.isPositiveCompletion(client.replyCode)) {
                Success(Unit)
            } else {
                Error(ErrorCode.OPERATION_UNSUPPORTED, "CHMOD not supported by server")
            }
        }

    // ── File Content Operations ───────────────────────────────────────

    override suspend fun openInputStream(remotePath: String): RemoteResult<InputStream> =
        withClient { client ->
            val stream = client.retrieveFileStream(remotePath)
            if (stream != null) {
                Success(BufferedInputStream(stream))
            } else {
                Error(ErrorCode.FILE_NOT_FOUND, "Cannot open file for reading: $remotePath")
            }
        }

    override suspend fun openOutputStream(remotePath: String, append: Boolean): RemoteResult<OutputStream> =
        withClient { client ->
            val stream = if (append) {
                client.appendFileStream(remotePath)
            } else {
                client.storeFileStream(remotePath)
            }
            if (stream != null) {
                Success(BufferedOutputStream(stream))
            } else {
                Error(ErrorCode.PERMISSION_DENIED, "Cannot open file for writing: $remotePath")
            }
        }

    override suspend fun readTextFile(remotePath: String, encoding: String): RemoteResult<String> =
        withClient { client ->
            val inputStream = client.retrieveFileStream(remotePath)
            if (inputStream == null) {
                return@withClient Error(ErrorCode.FILE_NOT_FOUND, "Cannot read file: $remotePath")
            }
            try {
                val text = inputStream.bufferedReader(Charset.forName(encoding)).readText()
                inputStream.close()
                client.completePendingCommand()
                Success(text)
            } catch (e: Exception) {
                inputStream.close()
                Error(ErrorCode.IO_ERROR, "Failed to read file: ${e.message}", e)
            }
        }

    override suspend fun writeTextFile(remotePath: String, content: String, encoding: String): RemoteResult<Unit> =
        withClient { client ->
            val outputStream = client.storeFileStream(remotePath)
            if (outputStream == null) {
                return@withClient Error(ErrorCode.PERMISSION_DENIED, "Cannot write file: $remotePath")
            }
            try {
                outputStream.write(content.toByteArray(Charset.forName(encoding)))
                outputStream.flush()
                outputStream.close()
                client.completePendingCommand()
                Success(Unit)
            } catch (e: Exception) {
                outputStream.close()
                Error(ErrorCode.IO_ERROR, "Failed to write file: ${e.message}", e)
            }
        }

    // ── Transfer Operations ───────────────────────────────────────────

    override suspend fun downloadFile(
        remotePath: String,
        localPath: String,
        overwrite: Boolean,
        resume: Boolean
    ): RemoteResult<TransferItem> = withContext(Dispatchers.IO) {
        val item = TransferItem(
            connectionId = connectionInfo.id,
            direction = com.xcoder.remote.model.TransferDirection.DOWNLOAD,
            remotePath = remotePath,
            localPath = localPath,
            fileName = PathUtils.fileName(remotePath),
            overwrite = overwrite
        )
        _transferEvents.emit(TransferEvent.Queued(item))
        _session.value = _session.value.copy(activeTransfers = _session.value.activeTransfers + 1)

        try {
            val client = ftpClient
                ?: return@withContext Error(ErrorCode.NOT_CONNECTED, "Not connected")

            val localFile = File(localPath)
            val parentDir = localFile.parentFile
            if (parentDir != null && !parentDir.exists()) parentDir.mkdirs()

            val remoteSize = getFileSize(client, remotePath)
            val itemWithSize = if (remoteSize > 0) item.copy(fileSize = remoteSize) else item

            val outputStream = java.io.FileOutputStream(localFile, resume && localFile.exists())
            val inputStream = client.retrieveFileStream(remotePath)
            if (inputStream == null) {
                outputStream.close()
                localFile.delete()
                return@withContext Error(ErrorCode.FILE_NOT_FOUND, "Cannot download: $remotePath")
            }

            var runningItem = itemWithSize.withStarted()
            _transferEvents.emit(TransferEvent.Progress(runningItem))

            val buffer = ByteArray(8192)
            var totalRead = if (resume && localFile.exists()) localFile.length() else 0L
            while (currentCoroutineContext().isActive) {
                val read = inputStream.read(buffer)
                if (read < 0) break
                outputStream.write(buffer, 0, read)
                totalRead += read
                runningItem = runningItem.withTransferred(totalRead)
                _transferEvents.emit(TransferEvent.Progress(runningItem))
            }

            inputStream.close()
            outputStream.flush()
            outputStream.close()
            client.completePendingCommand()

            val completedItem = runningItem.withStatus(com.xcoder.remote.model.TransferStatus.COMPLETED)
            _transferEvents.emit(TransferEvent.Completed(completedItem))
            _session.value = _session.value.copy(activeTransfers = _session.value.activeTransfers - 1)
            Success(completedItem)
        } catch (e: CancellationException) {
            _session.value = _session.value.copy(activeTransfers = _session.value.activeTransfers - 1)
            val cancelledItem = item.withStatus(com.xcoder.remote.model.TransferStatus.CANCELLED)
            _transferEvents.emit(TransferEvent.Failed(cancelledItem, e))
            throw e
        } catch (e: Exception) {
            _session.value = _session.value.copy(activeTransfers = _session.value.activeTransfers - 1)
            val failedItem = item.withStatus(com.xcoder.remote.model.TransferStatus.FAILED, e.message)
            _transferEvents.emit(TransferEvent.Failed(failedItem, e))
            Error(ErrorCode.TRANSFER_FAILED, "Download failed: ${e.message}", e)
        }
    }

    override suspend fun uploadFile(
        localPath: String,
        remotePath: String,
        overwrite: Boolean,
        resume: Boolean
    ): RemoteResult<TransferItem> = withContext(Dispatchers.IO) {
        val localFile = File(localPath)
        if (!localFile.exists()) {
            return@withContext Error(ErrorCode.FILE_NOT_FOUND, "Local file not found: $localPath")
        }
        val item = TransferItem(
            connectionId = connectionInfo.id,
            direction = com.xcoder.remote.model.TransferDirection.UPLOAD,
            remotePath = remotePath,
            localPath = localPath,
            fileName = localFile.name,
            fileSize = localFile.length(),
            overwrite = overwrite
        )
        _transferEvents.emit(TransferEvent.Queued(item))
        _session.value = _session.value.copy(activeTransfers = _session.value.activeTransfers + 1)

        try {
            val client = ftpClient
                ?: return@withContext Error(ErrorCode.NOT_CONNECTED, "Not connected")

            val inputStream = java.io.FileInputStream(localFile)
            val outputStream = if (resume && overwrite) {
                client.appendFileStream(remotePath)
            } else {
                client.storeFileStream(remotePath)
            }
            if (outputStream == null) {
                inputStream.close()
                return@withContext Error(ErrorCode.PERMISSION_DENIED, "Cannot upload to: $remotePath")
            }

            var runningItem = item.withStarted()
            _transferEvents.emit(TransferEvent.Progress(runningItem))

            val buffer = ByteArray(8192)
            var totalWritten = 0L
            while (currentCoroutineContext().isActive) {
                val read = inputStream.read(buffer)
                if (read < 0) break
                outputStream.write(buffer, 0, read)
                totalWritten += read
                runningItem = runningItem.withTransferred(totalWritten)
                _transferEvents.emit(TransferEvent.Progress(runningItem))
            }

            inputStream.close()
            outputStream.flush()
            outputStream.close()
            client.completePendingCommand()

            val completedItem = runningItem.withStatus(com.xcoder.remote.model.TransferStatus.COMPLETED)
            _transferEvents.emit(TransferEvent.Completed(completedItem))
            _session.value = _session.value.copy(activeTransfers = _session.value.activeTransfers - 1)
            Success(completedItem)
        } catch (e: CancellationException) {
            _session.value = _session.value.copy(activeTransfers = _session.value.activeTransfers - 1)
            val cancelledItem = item.withStatus(com.xcoder.remote.model.TransferStatus.CANCELLED)
            _transferEvents.emit(TransferEvent.Failed(cancelledItem, e))
            throw e
        } catch (e: Exception) {
            _session.value = _session.value.copy(activeTransfers = _session.value.activeTransfers - 1)
            val failedItem = item.withStatus(com.xcoder.remote.model.TransferStatus.FAILED, e.message)
            _transferEvents.emit(TransferEvent.Failed(failedItem, e))
            Error(ErrorCode.TRANSFER_FAILED, "Upload failed: ${e.message}", e)
        }
    }

    override suspend fun downloadDirectory(
        remotePath: String,
        localPath: String,
        overwrite: Boolean
    ): RemoteResult<List<TransferItem>> = withContext(Dispatchers.IO) {
        val results = mutableListOf<TransferItem>()
        downloadDirectoryRecursive(remotePath, localPath, overwrite, results)
        Success(results)
    }

    override suspend fun uploadDirectory(
        localPath: String,
        remotePath: String,
        overwrite: Boolean
    ): RemoteResult<List<TransferItem>> = withContext(Dispatchers.IO) {
        val results = mutableListOf<TransferItem>()
        uploadDirectoryRecursive(localPath, remotePath, overwrite, results)
        Success(results)
    }

    override suspend fun cancelAllTransfers() {
        cancelled.set(true)
    }

    // ── Server Info ───────────────────────────────────────────────────

    override suspend fun getServerInfo(): RemoteResult<String> =
        withClient { client ->
            val syst = client.systemName ?: "Unknown"
            val banner = client.replyString?.trim() ?: ""
            Success("$banner\nSystem: $syst")
        }

    override suspend fun getDiskUsage(path: String): RemoteResult<DiskUsage> =
        withClient { client ->
            val success = client.sendSiteCommand("DF $path")
            if (success) {
                val reply = client.replyString
                if (reply != null) {
                    val parts = reply.trim().split("\\s+".toRegex())
                    if (parts.size >= 4) {
                        return@withClient Success(DiskUsage(
                            totalBytes = parts[1].toLongOrNull() ?: 0L,
                            usedBytes = parts[2].toLongOrNull() ?: 0L,
                            availableBytes = parts[3].toLongOrNull() ?: 0L,
                            path = path
                        ))
                    }
                }
            }
            Error(ErrorCode.OPERATION_UNSUPPORTED, "Server does not support DF command")
        }

    override suspend fun abort() {
        try {
            ftpClient?.abort()
        } catch (_: Exception) {}
        cancelled.set(true)
    }

    // ── Private Helpers ───────────────────────────────────────────────

    private fun createClient(): FTPClient {
        val client: FTPClient = when (connectionInfo.protocol) {
            ConnectionProtocol.FTPS_IMPLICIT, ConnectionProtocol.FTPS_EXPLICIT -> {
                val isImplicit = connectionInfo.protocol == ConnectionProtocol.FTPS_IMPLICIT
                val sslContext = FtpSecurityConfig.createSslContext(
                    connectionInfo.protocol,
                    connectionInfo.verifyCertificate
                )
                val ftpsClient = FTPSClient(isImplicit, sslContext)
                ftpsClient.connectTimeout = connectionInfo.connectTimeoutMs
                ftpsClient.defaultTimeout = connectionInfo.dataTimeoutMs
                ftpsClient
            }
            else -> {
                val ftpClient = FTPClient()
                ftpClient.connectTimeout = connectionInfo.connectTimeoutMs
                ftpClient.defaultTimeout = connectionInfo.dataTimeoutMs
                ftpClient
            }
        }
        client.controlKeepAliveTimeout = connectionInfo.keepAliveIntervalSec.toLong()
        client.setControlEncoding(connectionInfo.encoding)
        return client
    }

    private suspend fun <T> withClient(block: suspend (FTPClient) -> RemoteResult<T>): RemoteResult<T> {
        val client = ftpClient
        if (client == null || !client.isConnected) {
            return Error(ErrorCode.NOT_CONNECTED, "Not connected to server")
        }
        return try {
            block(client)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SocketTimeoutException) {
            Error(ErrorCode.CONNECTION_TIMEOUT, "Operation timed out", e)
        } catch (e: Exception) {
            Error(ErrorCode.IO_ERROR, "Operation failed: ${e.message}", e)
        }
    }

    private suspend fun downloadDirectoryRecursive(
        remotePath: String,
        localPath: String,
        overwrite: Boolean,
        results: MutableList<TransferItem>
    ) {
        val listing = listDirectory(remotePath)
        if (listing.isError) return
        val localDir = File(localPath)
        if (!localDir.exists()) localDir.mkdirs()
        for (entry in listing.data.entries) {
            val childRemote = PathUtils.join(remotePath, entry.name)
            val childLocal = File(localDir, entry.name).absolutePath
            if (entry.isDirectory) {
                downloadDirectoryRecursive(childRemote, childLocal, overwrite, results)
            } else {
                val result = downloadFile(childRemote, childLocal, overwrite)
                result.data?.let { results.add(it) }
            }
        }
    }

    private suspend fun uploadDirectoryRecursive(
        localPath: String,
        remotePath: String,
        overwrite: Boolean,
        results: MutableList<TransferItem>
    ) {
        val localDir = File(localPath)
        if (!localDir.isDirectory) return
        makeDirectory(remotePath, recursive = true)
        val children = localDir.listFiles() ?: emptyArray()
        for (child in children.sortedBy { it.name }) {
            val childRemote = PathUtils.join(remotePath, child.name)
            if (child.isDirectory) {
                uploadDirectoryRecursive(child.absolutePath, childRemote, overwrite, results)
            } else {
                val result = uploadFile(child.absolutePath, childRemote, overwrite)
                result.data?.let { results.add(it) }
            }
        }
    }

    private suspend fun removeDirectoryRecursive(client: FTPClient, path: String): RemoteResult<Unit> {
        val files = client.listFiles(path)
        if (files != null) {
            for (file in files) {
                if (file.name == "." || file.name == "..") continue
                val fullPath = "$path/${file.name}"
                if (file.isDirectory) {
                    val result = removeDirectoryRecursive(client, fullPath)
                    if (result.isError) return result
                } else {
                    client.deleteFile(fullPath)
                }
            }
        }
        val removed = client.removeDirectory(path)
        return if (removed || FTPReply.isPositiveCompletion(client.replyCode)) {
            Success(Unit)
        } else {
            Error(ErrorCode.IO_ERROR, "Failed to remove directory: $path")
        }
    }

    private fun isFtpDirectoryExists(client: FTPClient, path: String): Boolean {
        return try {
            val oldPwd = client.printWorkingDirectory()
            val result = client.changeWorkingDirectory(path)
            if (result && oldPwd != null) {
                client.changeWorkingDirectory(oldPwd)
            }
            result
        } catch (_: Exception) {
            false
        }
    }

    private fun getFileSize(client: FTPClient, path: String): Long {
        return try {
            client.getSize(path)
        } catch (_: Exception) {
            -1L
        }
    }

    private fun FTPFile.toRemoteFileEntry(parentPath: String, connId: String): RemoteFileEntry {
        val fullPath = if (parentPath.endsWith("/")) "$parentPath$name" else "$parentPath/$name"
        return RemoteFileEntry(
            fullPath = fullPath,
            name = name,
            isDirectory = isDirectory,
            size = size,
            lastModified = timestamp.timeInMillis,
            permissions = if (hasPermission(FTPFile.USER_ACCESS, FTPFile.READ_PERMISSION)) 0o400 else 0 or
                    if (hasPermission(FTPFile.USER_ACCESS, FTPFile.WRITE_PERMISSION)) 0o200 else 0 or
                    if (hasPermission(FTPFile.USER_ACCESS, FTPFile.EXECUTE_PERMISSION)) 0o100 else 0 or
                    if (hasPermission(FTPFile.GROUP_ACCESS, FTPFile.READ_PERMISSION)) 0o040 else 0 or
                    if (hasPermission(FTPFile.GROUP_ACCESS, FTPFile.WRITE_PERMISSION)) 0o020 else 0 or
                    if (hasPermission(FTPFile.GROUP_ACCESS, FTPFile.EXECUTE_PERMISSION)) 0o010 else 0 or
                    if (hasPermission(FTPFile.WORLD_ACCESS, FTPFile.READ_PERMISSION)) 0o004 else 0 or
                    if (hasPermission(FTPFile.WORLD_ACCESS, FTPFile.WRITE_PERMISSION)) 0o002 else 0 or
                    if (hasPermission(FTPFile.WORLD_ACCESS, FTPFile.EXECUTE_PERMISSION)) 0o001 else 0,
            owner = user ?: "",
            group = group ?: "",
            isSymbolicLink = isSymbolicLink,
            canRead = hasPermission(FTPFile.USER_ACCESS, FTPFile.READ_PERMISSION),
            canWrite = hasPermission(FTPFile.USER_ACCESS, FTPFile.WRITE_PERMISSION),
            canExecute = hasPermission(FTPFile.USER_ACCESS, FTPFile.EXECUTE_PERMISSION),
            connectionId = connId
        )
    }

    companion object {
        private const val TAG = "FtpClient"
    }
}
