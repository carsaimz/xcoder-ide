package com.xcoder.remote.files

import com.xcoder.remote.connection.ConnectionManager
import com.xcoder.remote.connection.ConnectionType
import com.xcoder.remote.connection.ServerConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import net.schmizz.sshj.sftp.RemoteFile as SftpRemoteFile
import net.schmizz.sshj.xfer.FileSystemFile
import org.apache.commons.net.ftp.FTPFile
import java.io.*
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteFileManager @Inject constructor(
    private val connectionManager: ConnectionManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    data class RemoteFile(
        val name: String,
        val path: String,
        val isDirectory: Boolean,
        val size: Long = 0,
        val lastModified: Long = 0,
        val permissions: String = "",
        val owner: String = "",
        val group: String = ""
    ) {
        val extension: String get() = name.substringAfterLast('.', "")
        val displayName: String get() = if (isDirectory) "$name/" else name
        val formattedSize: String get() = ConnectionManager.formatBytes(size)
        val formattedDate: String get() = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date(lastModified))
    }

    data class ListResult(
        val files: List<RemoteFile>,
        val path: String,
        val totalCount: Int = files.size
    )

    data class TransferResult(
        val success: Boolean,
        val localPath: String? = null,
        val remotePath: String? = null,
        val bytesTransferred: Long = 0,
        val durationMs: Long = 0,
        val error: String? = null
    )

    suspend fun listFiles(serverId: String, path: String = "/"): ListResult =
        withContext(Dispatchers.IO) {
            val holder = connectionManager.getHolder(serverId)
                ?: throw IllegalStateException("Not connected to server")
            val files = when {
                holder.ftpClient != null -> listFtp(holder.ftpClient, path)
                holder.sftpClient != null -> listSftp(holder.sftpClient, path)
                else -> emptyList()
            }
            ListResult(files.sortedWith(compareByDescending<RemoteFile> { it.isDirectory }.thenBy { it.name.lowercase() }), path)
        }

    private fun listFtp(ftp: org.apache.commons.net.ftp.FTPClient, path: String): List<RemoteFile> {
        val ftpFiles = ftp.listFiles(path) ?: return emptyList()
        return ftpFiles.mapNotNull { f ->
            if (f.name == "." || f.name == "..") null
            else RemoteFile(
                name = f.name,
                path = if (path.endsWith("/")) "$path${f.name}" else "$path/${f.name}",
                isDirectory = f.isDirectory,
                size = f.size,
                lastModified = f.timestamp.timeInMillis,
                permissions = "${if (f.hasPermission(FTPFile.USER_ACCESS, FTPFile.READ_PERMISSION)) 'r' else '-'}${if (f.hasPermission(FTPFile.USER_ACCESS, FTPFile.WRITE_PERMISSION)) 'w' else '-'}${if (f.hasPermission(FTPFile.USER_ACCESS, FTPFile.EXECUTE_PERMISSION)) 'x' else '-'}"
            )
        }
    }

    private fun listSftp(sftp: net.schmizz.sshj.sftp.SFTPClient, path: String): List<RemoteFile> {
        val entries = sftp.ls(path)
        return entries.mapNotNull { e ->
            if (e.name == "." || e.name == "..") null
            else RemoteFile(
                name = e.name,
                path = if (path.endsWith("/")) "$path${e.name}" else "$path/${e.name}",
                isDirectory = e.isDirectory,
                size = if (e.isDirectory) 0 else e.attributes.size,
                lastModified = e.attributes.mtime * 1000L,
                permissions = e.attributes.permissions.toString(),
                owner = e.attributes.uid.toString()
            )
        }
    }

    suspend fun downloadFile(serverId: String, remotePath: String, localPath: String): TransferResult =
        withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            val holder = connectionManager.getHolder(serverId)
                ?: return@withContext TransferResult(false, error = "Not connected")
            val outFile = File(localPath)
            outFile.parentFile?.mkdirs()
            try {
                val transferred = when {
                    holder.ftpClient != null -> downloadFtp(holder.ftpClient, remotePath, outFile)
                    holder.sftpClient != null -> downloadSftp(holder.sftpClient, remotePath, outFile)
                    else -> throw IllegalStateException("No client available")
                }
                TransferResult(true, localPath, remotePath, transferred, System.currentTimeMillis() - startTime)
            } catch (e: Exception) {
                TransferResult(false, error = e.message)
            }
        }

    private fun downloadFtp(ftp: org.apache.commons.net.ftp.FTPClient, remotePath: String, localFile: File): Long {
        FileOutputStream(localFile).use { out ->
            ftp.retrieveFile(remotePath, out)
        }
        return localFile.length()
    }

    private fun downloadSftp(sftp: net.schmizz.sshj.sftp.SFTPClient, remotePath: String, localFile: File): Long {
        sftp.get(remotePath, FileSystemFile(localFile))
        return localFile.length()
    }

    suspend fun uploadFile(serverId: String, localPath: String, remotePath: String): TransferResult =
        withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            val holder = connectionManager.getHolder(serverId)
                ?: return@withContext TransferResult(false, error = "Not connected")
            val inFile = File(localPath)
            if (!inFile.exists()) return@withContext TransferResult(false, error = "Local file not found")
            try {
                when {
                    holder.ftpClient != null -> uploadFtp(holder.ftpClient, inFile, remotePath)
                    holder.sftpClient != null -> uploadSftp(holder.sftpClient, inFile, remotePath)
                    else -> throw IllegalStateException("No client available")
                }
                TransferResult(true, localPath, remotePath, inFile.length(), System.currentTimeMillis() - startTime)
            } catch (e: Exception) {
                TransferResult(false, error = e.message)
            }
        }

    private fun uploadFtp(ftp: org.apache.commons.net.ftp.FTPClient, localFile: File, remotePath: String) {
        FileInputStream(localFile).use { inp -> ftp.storeFile(remotePath, inp) }
    }

    private fun uploadSftp(sftp: net.schmizz.sshj.sftp.SFTPClient, localFile: File, remotePath: String) {
        sftp.put(FileSystemFile(localFile), remotePath)
    }

    suspend fun deleteFile(serverId: String, remotePath: String, isDirectory: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            val holder = connectionManager.getHolder(serverId) ?: return@withContext false
            try {
                when {
                    holder.ftpClient != null -> {
                        if (isDirectory) holder.ftpClient.removeDirectory(remotePath)
                        else holder.ftpClient.deleteFile(remotePath)
                    }
                    holder.sftpClient != null -> {
                        if (isDirectory) holder.sftpClient.rmdir(remotePath)
                        else holder.sftpClient.rm(remotePath)
                    }
                }
                true
            } catch (e: Exception) { false }
        }

    suspend fun createDirectory(serverId: String, parentPath: String, dirName: String): Boolean =
        withContext(Dispatchers.IO) {
            val holder = connectionManager.getHolder(serverId) ?: return@withContext false
            val fullPath = if (parentPath.endsWith("/")) "$parentPath$dirName" else "$parentPath/$dirName"
            try {
                when {
                    holder.ftpClient != null -> holder.ftpClient.makeDirectory(fullPath)
                    holder.sftpClient != null -> holder.sftpClient.mkdir(fullPath)
                }
                true
            } catch (e: Exception) { false }
        }

    suspend fun renameFile(serverId: String, oldPath: String, newPath: String): Boolean =
        withContext(Dispatchers.IO) {
            val holder = connectionManager.getHolder(serverId) ?: return@withContext false
            try {
                when {
                    holder.ftpClient != null -> holder.ftpClient.rename(oldPath, newPath)
                    holder.sftpClient != null -> holder.sftpClient.rename(oldPath, newPath)
                }
                true
            } catch (e: Exception) { false }
        }

    suspend fun readFileContent(serverId: String, remotePath: String, maxSize: Long = 5 * 1024 * 1024): String =
        withContext(Dispatchers.IO) {
            val holder = connectionManager.getHolder(serverId) ?: throw IllegalStateException("Not connected")
            val localCache = File.createTempFile("xcoder_remote_", ".tmp")
            try {
                downloadFile(serverId, remotePath, localCache.absolutePath)
                if (localCache.length() > maxSize) throw IOException("File too large to read in memory: ${localCache.length()} bytes")
                localCache.readText(Charsets.UTF_8)
            } finally { localCache.delete() }
        }

    suspend fun saveFileContent(serverId: String, remotePath: String, content: String): Boolean =
        withContext(Dispatchers.IO) {
            val tempFile = File.createTempFile("xcoder_upload_", ".tmp")
            try {
                tempFile.writeText(content, Charsets.UTF_8)
                val result = uploadFile(serverId, tempFile.absolutePath, remotePath)
                result.success
            } finally { tempFile.delete() }
        }
}