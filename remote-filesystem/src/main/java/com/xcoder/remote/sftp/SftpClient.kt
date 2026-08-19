package com.xcoder.remote.sftp

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.ChannelSftp.LsEntry
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.SftpATTRS
import com.jcraft.jsch.SftpException
import com.xcoder.remote.connection.ConnectionState
import com.xcoder.remote.connection.DiskUsage
import com.xcoder.remote.connection.ErrorCode
import com.xcoder.remote.connection.RemoteFileSystem
import com.xcoder.remote.connection.RemoteResult
import com.xcoder.remote.connection.RemoteResult.Error
import com.xcoder.remote.connection.RemoteResult.Success
import com.xcoder.remote.model.AuthMethod
import com.xcoder.remote.model.ConnectionSession
import com.xcoder.remote.model.DirectoryListing
import com.xcoder.remote.model.RemoteConnectionInfo
import com.xcoder.remote.model.RemoteFileEntry
import com.xcoder.remote.model.TransferDirection
import com.xcoder.remote.model.TransferEvent
import com.xcoder.remote.model.TransferItem
import com.xcoder.remote.model.TransferStatus
import com.xcoder.remote.util.PathUtils
import kotlinx.coroutines.CancellationException
import java.util.Vector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * SFTP implementation of [RemoteFileSystem] using JSch.
 *
 * Provides full SFTP v3 support including:
 * - Public key and password authentication
 * - Host key verification
 * - File transfers with progress reporting
 * - Directory operations (recursive create/delete)
 * - Permission changes
 * - Streaming file content
 */
class SftpClient(
    override val connectionInfo: RemoteConnectionInfo,
    private val hostKeyVerifier: HostKeyVerifier = HostKeyVerifier(
        knownHostsFile = File("/data/local/tmp/xcoder_known_hosts"),
        mode = if (connectionInfo.verifyHostKey) HostKeyVerifier.VerificationMode.ACCEPT_NEW
               else HostKeyVerifier.VerificationMode.TRUST_ALL
    ),
    private val passwordDecryptor: (String) -> String = { it }
) : RemoteFileSystem {

    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: ConnectionState get() = _state.value

    private val _session = MutableStateFlow(
        ConnectionSession(
            connectionId = connectionInfo.id,
            currentWorkingDirectory = connectionInfo.initialPath
        )
    )
    override val session: Flow<ConnectionSession> = _session.asStateFlow()

    private val _transferEvents = MutableSharedFlow<TransferEvent>(extraBufferCapacity = 64)
    override val transferEvents: Flow<TransferEvent> = _transferEvents.asSharedFlow()

    private val jschSession = AtomicReference<Session?>(null)
    private val sftpChannel = AtomicReference<ChannelSftp?>(null)
    private val cancelled = AtomicBoolean(false)
    private val keyAuth = SshKeyAuthenticator()

    private val channel: ChannelSftp? get() = sftpChannel.get()
    private val session: Session? get() = jschSession.get()

    // ── Connection Lifecycle ──────────────────────────────────────────

    override suspend fun connect(): RemoteResult<Unit> = withContext(Dispatchers.IO) {
        if (channel?.isConnected == true) {
            return@withContext Error(ErrorCode.CONNECTION_ALREADY_ACTIVE, "Already connected")
        }
        try {
            _state.value = ConnectionState.CONNECTING

            val jsch = JSch()

            // Configure authentication
            when (connectionInfo.authMethod) {
                AuthMethod.PUBLIC_KEY -> {
                    if (connectionInfo.privateKeyPath.isNotBlank()) {
                        val passphrase = if (connectionInfo.encryptedKeyPassphrase.isNotBlank()) {
                            passwordDecryptor(connectionInfo.encryptedKeyPassphrase)
                        } else null
                        when (val result = keyAuth.addIdentity(
                            connectionInfo.privateKeyPath, passphrase
                        )) {
                            is SshKeyAuthenticator.LoadResult.Success -> { /* ok */ }
                            is SshKeyAuthenticator.LoadResult.WrongPassphrase -> {
                                _state.value = ConnectionState.ERROR
                                return@withContext Error(
                                    ErrorCode.AUTHENTICATION_FAILED,
                                    "Wrong passphrase for private key"
                                )
                            }
                            is SshKeyAuthenticator.LoadResult.InvalidKeyFile -> {
                                _state.value = ConnectionState.ERROR
                                return@withContext Error(
                                    ErrorCode.INVALID_CREDENTIALS,
                                    result.error
                                )
                            }
                            is SshKeyAuthenticator.LoadResult.UnsupportedKeyType -> {
                                _state.value = ConnectionState.ERROR
                                return@withContext Error(
                                    ErrorCode.OPERATION_UNSUPPORTED,
                                    "Unsupported key type: ${result.keyType}"
                                )
                            }
                        }
                        jsch.identityRepository = keyAuth.getJsch().identityRepository
                    }
                }
                else -> { /* Password auth or keyboard-interactive handled below */ }
            }

            // Configure host key verification
            if (connectionInfo.verifyHostKey) {
                jsch.setKnownHosts(hostKeyVerifier.createJschKnownHosts())
            } else {
                jsch.setHostKeyRepository(null)
            }

            // Create session
            val sshSession = jsch.getSession(
                connectionInfo.username,
                connectionInfo.host,
                connectionInfo.port
            )
            sshSession.setConfig("StrictHostKeyChecking", if (connectionInfo.verifyHostKey) "ask" else "no")
            sshSession.setConfig("PreferredAuthentications", buildAuthMethods())

            // Set password for password-based auth
            if (connectionInfo.authMethod == AuthMethod.PASSWORD ||
                connectionInfo.authMethod == AuthMethod.KEYBOARD_INTERACTIVE
            ) {
                sshSession.setPassword(passwordDecryptor(connectionInfo.encryptedPassword))
            }

            // Timeouts
            sshSession.timeout = connectionInfo.connectTimeoutMs
            sshSession.setServerAliveInterval(connectionInfo.keepAliveIntervalSec * 1000)
            sshSession.setServerAliveCountMax(3)

            // Connect
            sshSession.connect()

            // Verify host key manually if we have a known fingerprint
            if (connectionInfo.verifyHostKey && connectionInfo.knownHostFingerprint.isNotBlank()) {
                val hostKeys = sshSession.hostKey
                if (hostKeys != null) {
                    val fingerprint = hostKeyVerifier.getFingerprint(hostKeys.key)
                    if (fingerprint != connectionInfo.knownHostFingerprint) {
                        sshSession.disconnect()
                        _state.value = ConnectionState.ERROR
                        return@withContext Error(
                            ErrorCode.HOST_KEY_MISMATCH,
                            "Host key mismatch: expected ${connectionInfo.knownHostFingerprint}, got $fingerprint"
                        )
                    }
                }
            }

            jschSession.set(sshSession)
            _state.value = ConnectionState.AUTHENTICATING

            // Open SFTP channel
            val sftp = sshSession.openChannel("sftp") as ChannelSftp
            sftp.connect(connectionInfo.dataTimeoutMs)
            sftpChannel.set(sftp)

            // Navigate to initial path
            if (connectionInfo.initialPath.isNotBlank() && connectionInfo.initialPath != "/") {
                try { sftp.cd(connectionInfo.initialPath) } catch (_: SftpException) { }
            }

            val pwd = sftp.pwd()
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
        } catch (e: com.jcraft.jsch.JSchException) {
            cleanup()
            _state.value = ConnectionState.ERROR
            val code = when {
                e.message?.contains("Auth fail", ignoreCase = true) == true -> ErrorCode.AUTHENTICATION_FAILED
                e.message?.contains("timeout", ignoreCase = true) == true -> ErrorCode.CONNECTION_TIMEOUT
                e.message?.contains("refused", ignoreCase = true) == true -> ErrorCode.CONNECTION_REFUSED
                e.message?.contains("HostKey", ignoreCase = true) == true -> ErrorCode.HOST_KEY_MISMATCH
                else -> ErrorCode.NETWORK_UNREACHABLE
            }
            _session.value = _session.value.copy(lastError = e.message)
            Error(code, "Connection failed: ${e.message}", e)
        } catch (e: Exception) {
            cleanup()
            _state.value = ConnectionState.ERROR
            _session.value = _session.value.copy(lastError = e.message)
            Error(ErrorCode.UNKNOWN, "Connection failed: ${e.message}", e)
        }
    }

    override suspend fun disconnect(): RemoteResult<Unit> = withContext(Dispatchers.IO) {
        try {
            _state.value = ConnectionState.DISCONNECTING
            cancelled.set(true)
            cleanup()
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
            val ch = channel ?: return@withContext false
            val sess = session ?: return@withContext false
            ch.isConnected && sess.isConnected
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
        withChannel { ch ->
            val startTime = System.currentTimeMillis()
            val normalizedPath = PathUtils.normalize(path)
            val rawEntries = try {
                @Suppress("UNCHECKED_CAST")
                ch.ls(normalizedPath) as Vector<LsEntry>
            } catch (e: SftpException) {
                return@withChannel Error(
                    if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) ErrorCode.DIRECTORY_NOT_FOUND
                    else ErrorCode.IO_ERROR,
                    "Cannot list directory: ${e.message}", e
                )
            }
            val entries = rawEntries
                .filter { it.filename != "." && it.filename != ".." }
                .map { it.toRemoteFileEntry(normalizedPath, connectionInfo.id) }
            val elapsed = System.currentTimeMillis() - startTime
            Success(DirectoryListing(normalizedPath, entries, listingTimeMs = elapsed))
        }

    override suspend fun changeDirectory(path: String): RemoteResult<String> =
        withChannel { ch ->
            val normalizedPath = PathUtils.normalize(path)
            try {
                ch.cd(normalizedPath)
                val pwd = ch.pwd()
                _session.value = _session.value.copy(currentWorkingDirectory = pwd)
                Success(pwd)
            } catch (e: SftpException) {
                Error(
                    if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) ErrorCode.DIRECTORY_NOT_FOUND
                    else ErrorCode.PERMISSION_DENIED,
                    "Cannot change to directory: ${e.message}", e
                )
            }
        }

    override suspend fun printWorkingDirectory(): RemoteResult<String> =
        withChannel { ch ->
            try {
                val pwd = ch.pwd()
                _session.value = _session.value.copy(currentWorkingDirectory = pwd)
                Success(pwd)
            } catch (e: SftpException) {
                Error(ErrorCode.PROTOCOL_ERROR, "PWD failed: ${e.message}", e)
            }
        }

    override suspend fun makeDirectory(path: String, recursive: Boolean): RemoteResult<Unit> =
        withChannel { ch ->
            if (recursive) {
                val parts = PathUtils.split(path)
                var current = ""
                for (part in parts) {
                    current = PathUtils.join(current, part)
                    try {
                        ch.mkdir(current)
                    } catch (e: SftpException) {
                        if (e.id != ChannelSftp.SSH_FX_FILE_ALREADY_EXISTS) {
                            return@withChannel Error(ErrorCode.PERMISSION_DENIED,
                                "Failed to create $current: ${e.message}", e)
                        }
                    }
                }
                Success(Unit)
            } else {
                try {
                    ch.mkdir(path)
                    Success(Unit)
                } catch (e: SftpException) {
                    Error(
                        if (e.id == ChannelSftp.SSH_FX_FILE_ALREADY_EXISTS) ErrorCode.FILE_EXISTS
                        else ErrorCode.PERMISSION_DENIED,
                        "Failed to create directory: ${e.message}", e
                    )
                }
            }
        }

    override suspend fun removeDirectory(path: String, recursive: Boolean): RemoteResult<Unit> =
        withChannel { ch ->
            if (recursive) {
                removeDirectoryRecursive(ch, path)
            } else {
                try {
                    ch.rmdir(path)
                    Success(Unit)
                } catch (e: SftpException) {
                    Error(
                        if (e.id == ChannelSftp.SSH_FX_FAILURE) ErrorCode.DIRECTORY_NOT_EMPTY
                        else ErrorCode.PERMISSION_DENIED,
                        "Cannot remove directory: ${e.message}", e
                    )
                }
            }
        }

    override suspend fun isDirectory(path: String): RemoteResult<Boolean> =
        withChannel { ch ->
            try {
                val attrs = ch.stat(path)
                Success(attrs.isDir)
            } catch (e: SftpException) {
                if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) Success(false)
                else Error(ErrorCode.IO_ERROR, "Cannot stat: ${e.message}", e)
            }
        }

    // ── File Operations ───────────────────────────────────────────────

    override suspend fun getFileMetadata(path: String): RemoteResult<RemoteFileEntry> =
        withChannel { ch ->
            try {
                val attrs = ch.stat(path)
                Success(attrs.toRemoteFileEntry(path, connectionInfo.id))
            } catch (e: SftpException) {
                Error(
                    if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) ErrorCode.FILE_NOT_FOUND
                    else ErrorCode.IO_ERROR,
                    "Cannot stat: ${e.message}", e
                )
            }
        }

    override suspend fun exists(path: String): RemoteResult<Boolean> =
        withChannel { ch ->
            try {
                ch.stat(path)
                Success(true)
            } catch (e: SftpException) {
                if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) Success(false)
                else Error(ErrorCode.IO_ERROR, "Stat failed: ${e.message}", e)
            }
        }

    override suspend fun deleteFile(path: String): RemoteResult<Unit> =
        withChannel { ch ->
            try {
                ch.rm(path)
                Success(Unit)
            } catch (e: SftpException) {
                Error(ErrorCode.PERMISSION_DENIED, "Cannot delete: ${e.message}", e)
            }
        }

    override suspend fun rename(oldPath: String, newPath: String): RemoteResult<Unit> =
        withChannel { ch ->
            try {
                ch.rename(oldPath, newPath)
                Success(Unit)
            } catch (e: SftpException) {
                Error(ErrorCode.PERMISSION_DENIED, "Rename failed: ${e.message}", e)
            }
        }

    override suspend fun copy(sourcePath: String, destPath: String): RemoteResult<Unit> {
        // SFTP v3 has no native copy; download then upload
        val tempFile = File.createTempFile("sftp_copy_", ".tmp")
        return try {
            val downloadResult = downloadFile(sourcePath, tempFile.absolutePath)
            if (downloadResult.isError) return downloadResult.map { }
            uploadFile(tempFile.absolutePath, destPath)
        } finally {
            tempFile.delete()
        }
    }

    override suspend fun changePermissions(path: String, permissions: Int): RemoteResult<Unit> =
        withChannel { ch ->
            try {
                ch.chmod(permissions, path)
                Success(Unit)
            } catch (e: SftpException) {
                Error(ErrorCode.PERMISSION_DENIED, "CHMOD failed: ${e.message}", e)
            }
        }

    // ── File Content Operations ───────────────────────────────────────

    override suspend fun openInputStream(remotePath: String): RemoteResult<InputStream> =
        withChannel { ch ->
            try {
                val stream = ch.get(remotePath)
                Success(BufferedInputStream(stream))
            } catch (e: SftpException) {
                Error(ErrorCode.FILE_NOT_FOUND, "Cannot read: ${e.message}", e)
            }
        }

    override suspend fun openOutputStream(remotePath: String, append: Boolean): RemoteResult<OutputStream> =
        withChannel { ch ->
            try {
                val mode = if (append) ChannelSftp.APPEND else ChannelSftp.OVERWRITE
                val stream = ch.put(remotePath, mode)
                Success(BufferedOutputStream(stream))
            } catch (e: SftpException) {
                Error(ErrorCode.PERMISSION_DENIED, "Cannot write: ${e.message}", e)
            }
        }

    override suspend fun readTextFile(remotePath: String, encoding: String): RemoteResult<String> =
        withChannel { ch ->
            try {
                val bais = java.io.ByteArrayOutputStream()
                ch.get(remotePath, bais)
                val text = bais.toString(encoding)
                Success(text)
            } catch (e: SftpException) {
                Error(ErrorCode.FILE_NOT_FOUND, "Cannot read file: ${e.message}", e)
            }
        }

    override suspend fun writeTextFile(remotePath: String, content: String, encoding: String): RemoteResult<Unit> =
        withChannel { ch ->
            try {
                val bytes = content.toByteArray(Charsets.UTF_8)
                ch.put(java.io.ByteArrayInputStream(bytes), remotePath)
                Success(Unit)
            } catch (e: SftpException) {
                Error(ErrorCode.PERMISSION_DENIED, "Cannot write file: ${e.message}", e)
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
            direction = TransferDirection.DOWNLOAD,
            remotePath = remotePath,
            localPath = localPath,
            fileName = PathUtils.fileName(remotePath),
            overwrite = overwrite,
            resumeSupported = true
        )
        _transferEvents.emit(TransferEvent.Queued(item))
        _session.value = _session.value.copy(activeTransfers = _session.value.activeTransfers + 1)

        try {
            val ch = channel ?: return@withContext Error(ErrorCode.NOT_CONNECTED, "Not connected")

            val localFile = File(localPath)
            val parentDir = localFile.parentFile
            if (parentDir != null && !parentDir.exists()) parentDir.mkdirs()

            // Get remote file size
            val remoteSize = try { ch.stat(remotePath).size } catch (_: SftpException) { -1L }
            val itemWithSize = if (remoteSize >= 0) item.copy(fileSize = remoteSize, resumeSupported = true) else item

            val outputStream = if (resume && localFile.exists() && remoteSize > 0) {
                java.io.FileOutputStream(localFile, true)
            } else {
                java.io.FileOutputStream(localFile, false)
            }
            val skipBytes = if (resume && localFile.exists()) localFile.length() else 0L

            val monitor = SftpProgressMonitor(itemWithSize.id) { progress ->
                val updatedItem = progress.copy(transferredBytes = progress.transferredBytes + skipBytes)
                _transferEvents.emit(TransferEvent.Progress(updatedItem))
            }

            val mode = if (resume && skipBytes > 0) {
                ChannelSftp.RESUME
            } else {
                ChannelSftp.OVERWRITE
            }

            ch.get(remotePath, outputStream, monitor, mode)
            outputStream.flush()
            outputStream.close()

            val finalSize = localFile.length()
            val completedItem = itemWithSize.copy(
                transferredBytes = finalSize,
                fileSize = if (itemWithSize.fileSize <= 0) finalSize else itemWithSize.fileSize,
                status = TransferStatus.COMPLETED,
                completedAt = System.currentTimeMillis()
            )
            _transferEvents.emit(TransferEvent.Completed(completedItem))
            _session.value = _session.value.copy(activeTransfers = _session.value.activeTransfers - 1)
            Success(completedItem)
        } catch (e: CancellationException) {
            _session.value = _session.value.copy(activeTransfers = _session.value.activeTransfers - 1)
            val cancelledItem = item.withStatus(TransferStatus.CANCELLED)
            _transferEvents.emit(TransferEvent.Failed(cancelledItem, e))
            throw e
        } catch (e: Exception) {
            _session.value = _session.value.copy(activeTransfers = _session.value.activeTransfers - 1)
            val failedItem = item.withStatus(TransferStatus.FAILED, e.message)
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
            direction = TransferDirection.UPLOAD,
            remotePath = remotePath,
            localPath = localPath,
            fileName = localFile.name,
            fileSize = localFile.length(),
            overwrite = overwrite,
            resumeSupported = true
        )
        _transferEvents.emit(TransferEvent.Queued(item))
        _session.value = _session.value.copy(activeTransfers = _session.value.activeTransfers + 1)

        try {
            val ch = channel ?: return@withContext Error(ErrorCode.NOT_CONNECTED, "Not connected")

            val inputStream = java.io.FileInputStream(localFile)
            val monitor = SftpProgressMonitor(item.id) { progress ->
                _transferEvents.emit(TransferEvent.Progress(progress))
            }

            val mode = when {
                resume -> ChannelSftp.APPEND
                overwrite -> ChannelSftp.OVERWRITE
                else -> ChannelSftp.RESUME
            }

            ch.put(inputStream, remotePath, monitor, mode)
            inputStream.close()

            val completedItem = item.copy(
                transferredBytes = localFile.length(),
                status = TransferStatus.COMPLETED,
                completedAt = System.currentTimeMillis()
            )
            _transferEvents.emit(TransferEvent.Completed(completedItem))
            _session.value = _session.value.copy(activeTransfers = _session.value.activeTransfers - 1)
            Success(completedItem)
        } catch (e: CancellationException) {
            _session.value = _session.value.copy(activeTransfers = _session.value.activeTransfers - 1)
            val cancelledItem = item.withStatus(TransferStatus.CANCELLED)
            _transferEvents.emit(TransferEvent.Failed(cancelledItem, e))
            throw e
        } catch (e: Exception) {
            _session.value = _session.value.copy(activeTransfers = _session.value.activeTransfers - 1)
            val failedItem = item.withStatus(TransferStatus.FAILED, e.message)
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
        try { channel?.exit() } catch (_: Exception) { }
    }

    // ── Server Info ───────────────────────────────────────────────────

    override suspend fun getServerInfo(): RemoteResult<String> =
        withChannel { _ ->
            val sess = session ?: return@withChannel Error(ErrorCode.NOT_CONNECTED, "No SSH session")
            val serverVersion = sess.serverVersion ?: "Unknown"
            val clientVersion = sess.clientVersion ?: "Unknown"
            Success("Server: $serverVersion\nClient: $clientVersion")
        }

    override suspend fun getDiskUsage(path: String): RemoteResult<DiskUsage> =
        withChannel { ch ->
            try {
                val statvfs = ch.statvfs(path)
                val blockSize = statvfs.bsize().toLong() and 0xFFFFFFFFL
                val totalBlocks = statvfs.blocks().toLong() and 0xFFFFFFFFL
                val freeBlocks = statvfs.bavail().toLong() and 0xFFFFFFFFL
                val usedBlocks = totalBlocks - (statvfs.bfree().toLong() and 0xFFFFFFFFL)
                Success(DiskUsage(
                    totalBytes = totalBlocks * blockSize,
                    usedBytes = usedBlocks * blockSize,
                    availableBytes = freeBlocks * blockSize,
                    path = path
                ))
            } catch (e: SftpException) {
                Error(ErrorCode.OPERATION_UNSUPPORTED,
                    "Server does not support statvfs: ${e.message}", e)
            }
        }

    override suspend fun abort() {
        cancelled.set(true)
        try { channel?.exit() } catch (_: Exception) { }
    }

    // ── Private Helpers ───────────────────────────────────────────────

    private fun buildAuthMethods(): String {
        return when (connectionInfo.authMethod) {
            AuthMethod.PUBLIC_KEY -> "publickey,password,keyboard-interactive"
            AuthMethod.KEYBOARD_INTERACTIVE -> "keyboard-interactive,password,publickey"
            AuthMethod.PASSWORD -> "password,keyboard-interactive,publickey"
            AuthMethod.ANONYMOUS -> "password"
        }
    }

    private fun cleanup() {
        try { sftpChannel.getAndSet(null)?.disconnect() } catch (_: Exception) { }
        try { jschSession.getAndSet(null)?.disconnect() } catch (_: Exception) { }
        keyAuth.clearIdentities()
    }

    private suspend fun <T> withChannel(block: suspend (ChannelSftp) -> RemoteResult<T>): RemoteResult<T> {
        val ch = channel
        if (ch == null || !ch.isConnected) {
            return Error(ErrorCode.NOT_CONNECTED, "Not connected")
        }
        return try {
            block(ch)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SftpException) {
            val code = when (e.id) {
                ChannelSftp.SSH_FX_NO_SUCH_FILE -> ErrorCode.FILE_NOT_FOUND
                ChannelSftp.SSH_FX_PERMISSION_DENIED -> ErrorCode.PERMISSION_DENIED
                ChannelSftp.SSH_FX_NO_SPACE_ON_FILESYSTEM -> ErrorCode.NO_SPACE_ON_SERVER
                ChannelSftp.SSH_FX_QUOTA_EXCEEDED -> ErrorCode.QUOTA_EXCEEDED
                ChannelSftp.SSH_FX_FAILURE -> ErrorCode.PROTOCOL_ERROR
                ChannelSftp.SSH_FX_CONNECTION_LOST -> ErrorCode.NETWORK_UNREACHABLE
                ChannelSftp.SSH_FX_OP_UNSUPPORTED -> ErrorCode.OPERATION_UNSUPPORTED
                else -> ErrorCode.IO_ERROR
            }
            Error(code, e.message ?: "SFTP error", e)
        } catch (e: Exception) {
            Error(ErrorCode.IO_ERROR, e.message ?: "Unknown error", e)
        }
    }

    private suspend fun downloadDirectoryRecursive(
        remotePath: String,
        localPath: String,
        overwrite: Boolean,
        results: MutableList<TransferItem>
    ) {
        val ch = channel ?: return
        val localDir = File(localPath)
        if (!localDir.exists()) localDir.mkdirs()
        try {
            @Suppress("UNCHECKED_CAST")
            val entries = ch.ls(remotePath) as Vector<LsEntry>
            for (entry in entries) {
                if (entry.filename == "." || entry.filename == "..") continue
                val childRemote = PathUtils.join(remotePath, entry.filename)
                val childLocal = File(localDir, entry.filename).absolutePath
                if (entry.attrs.isDir) {
                    downloadDirectoryRecursive(childRemote, childLocal, overwrite, results)
                } else {
                    val result = downloadFile(childRemote, childLocal, overwrite)
                    result.data?.let { results.add(it) }
                }
            }
        } catch (_: SftpException) { }
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

    private suspend fun removeDirectoryRecursive(ch: ChannelSftp, path: String): RemoteResult<Unit> {
        return try {
            @Suppress("UNCHECKED_CAST")
            val entries = ch.ls(path) as Vector<LsEntry>
            for (entry in entries) {
                if (entry.filename == "." || entry.filename == "..") continue
                val fullPath = PathUtils.join(path, entry.filename)
                if (entry.attrs.isDir) {
                    val result = removeDirectoryRecursive(ch, fullPath)
                    if (result.isError) return result
                } else {
                    try { ch.rm(fullPath) } catch (_: SftpException) { }
                }
            }
            ch.rmdir(path)
            Success(Unit)
        } catch (e: SftpException) {
            Error(ErrorCode.IO_ERROR, "Failed to remove directory: ${e.message}", e)
        }
    }

    /**
     * Progress monitor that reports back to the SftpClient via a callback.
     */
    private class SftpProgressMonitor(
        private val transferId: String,
        private val onProgress: (TransferItem) -> Unit
    ) : com.jcraft.jsch.SftpProgressMonitor {
        private var totalTransferred = 0L
        private var startTime = 0L

        override fun init(op: Int, src: String, dest: String, max: Long) {
            totalTransferred = 0L
            startTime = System.currentTimeMillis()
        }

        override fun count(count: Long): Boolean {
            totalTransferred += count
            onProgress(TransferItem(
                id = transferId,
                connectionId = "",
                direction = TransferDirection.DOWNLOAD,
                remotePath = src,
                localPath = dest,
                fileName = "",
                transferredBytes = totalTransferred,
                status = TransferStatus.TRANSFERRING,
                startedAt = startTime
            ))
            return true
        }

        override fun end() { }
    }

    private fun LsEntry.toRemoteFileEntry(parentPath: String, connId: String): RemoteFileEntry {
        val fullPath = if (parentPath.endsWith("/")) "$parentPath$filename" else "$parentPath/$filename"
        val perms = attrs.permissions
        return RemoteFileEntry(
            fullPath = fullPath,
            name = filename,
            isDirectory = attrs.isDir,
            size = attrs.size,
            lastModified = attrs.mtime.toLong() * 1000L,
            permissions = perms,
            isSymbolicLink = attrs.isLink,
            linkTarget = if (attrs.isLink) filename else "",
            canRead = (perms and 0o400) != 0,
            canWrite = (perms and 0o200) != 0,
            canExecute = (perms and 0o100) != 0,
            connectionId = connId
        )
    }

    private fun SftpATTRS.toRemoteFileEntry(path: String, connId: String): RemoteFileEntry {
        val perms = permissions
        return RemoteFileEntry(
            fullPath = path,
            name = PathUtils.fileName(path),
            isDirectory = isDir,
            size = size,
            lastModified = mtime.toLong() * 1000L,
            permissions = perms,
            canRead = (perms and 0o400) != 0,
            canWrite = (perms and 0o200) != 0,
            canExecute = (perms and 0o100) != 0,
            connectionId = connId
        )
    }

    companion object {
        private const val TAG = "SftpClient"
    }
}
