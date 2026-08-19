package com.xcoder.remote.sync

import com.xcoder.remote.cache.CacheManager
import com.xcoder.remote.connection.ConnectionManager
import com.xcoder.remote.connection.RemoteFileSystem
import com.xcoder.remote.model.DirectoryListing
import com.xcoder.remote.model.RemoteConnectionInfo
import com.xcoder.remote.model.RemoteFileEntry
import com.xcoder.remote.model.TransferDirection
import com.xcoder.remote.model.TransferEvent
import com.xcoder.remote.model.TransferItem
import com.xcoder.remote.model.TransferStatus
import com.xcoder.remote.model.TransferSummary
import com.xcoder.remote.util.PathUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages bidirectional synchronization between local and remote directories.
 *
 * Sync strategies:
 * - **Mirror**: Local becomes the source of truth (Acode-style default).
 * - **Two-way**: Changes in both directions are merged.
 * - **Download only**: Remote overwrites local.
 * - **Upload only**: Local overwrites remote.
 */
@Singleton
class SyncManager @Inject constructor(
    private val connectionManager: ConnectionManager,
    private val cacheManager: CacheManager
) {
    enum class SyncStrategy {
        MIRROR_LOCAL,    // Local is source of truth
        MIRROR_REMOTE,   // Remote is source of truth
        TWO_WAY,         // Merge both directions
        DOWNLOAD_ONLY,
        UPLOAD_ONLY
    }

    enum class SyncState {
        IDLE,
        SCANNING,
        RESOLVING_CONFLICTS,
        SYNCING,
        COMPLETED,
        FAILED,
        CANCELLED,
        PAUSED
    }

    data class SyncProgress(
        val state: SyncState = SyncState.IDLE,
        val currentFile: String = "",
        val processedFiles: Int = 0,
        val totalFiles: Int = 0,
        val uploadedBytes: Long = 0L,
        val downloadedBytes: Long = 0L,
        val conflicts: List<SyncConflict> = emptyList(),
        val errors: List<String> = emptyList(),
        val startTimeMs: Long = 0L,
        val elapsedTimeMs: Long = 0L
    ) {
        val progressPercent: Int
            get() = if (totalFiles <= 0) 0
            else ((processedFiles.toFloat() / totalFiles.toFloat()) * 100).toInt()
    }

    private val _progress = MutableStateFlow(SyncProgress())
    val progress: Flow<SyncProgress> = _progress.asStateFlow()

    private val _events = MutableSharedFlow<TransferEvent>(extraBufferCapacity = 64)
    val events: Flow<TransferEvent> = _events.asSharedFlow()

    private val conflictResolver = SyncConflictResolver()
    private val pendingTransfers = ConcurrentLinkedQueue<TransferItem>()
    private var isCancelled = false

    val transferSummary = MutableStateFlow(TransferSummary())

    /**
     * Set a listener to resolve sync conflicts interactively.
     */
    fun setConflictListener(listener: (SyncConflict) -> com.xcoder.remote.sync.SyncResolution) {
        conflictResolver.addConflictListener(listener)
    }

    /**
     * Synchronize a local directory with a remote directory.
     */
    suspend fun sync(
        connectionId: String,
        localPath: String,
        remotePath: String,
        strategy: SyncStrategy = SyncStrategy.TWO_WAY
    ): SyncProgress = withContext(Dispatchers.IO) {
        isCancelled = false
        val startTime = System.currentTimeMillis()

        updateProgress(SyncProgress(state = SyncState.SCANNING, startTimeMs = startTime))

        val fs = connectionManager.getFileSystem(connectionId)
            ?: return@withContext updateProgress(
                SyncProgress(state = SyncState.FAILED, errors = listOf("Not connected"), startTimeMs = startTime)
            )

        // Scan local files
        val localDir = File(localPath)
        if (!localDir.exists()) localDir.mkdirs()
        val localFiles = localDir.walkTopDown()
            .filter { it.isFile }
            .associateBy { it.relativeTo(localDir).path.replace(File.separatorChar, '/') }

        // Scan remote files
        val remoteListing = fs.listDirectory(remotePath)
        if (remoteListing.isError) {
            return@withContext updateProgress(
                SyncProgress(state = SyncState.FAILED,
                    errors = listOf("Cannot list remote: ${remoteListing.error!!.message}"),
                    startTimeMs = startTime)
            )
        }
        val remoteFiles = mutableMapOf<String, RemoteFileEntry>()
        collectRemoteFiles(fs, remoteListing.data, remotePath, remoteFiles)

        val allPaths = (localFiles.keys + remoteFiles.keys).toSet()
        val conflicts = mutableListOf<SyncConflict>()
        val toUpload = mutableListOf<Pair<String, File>>()
        val toDownload = mutableListOf<Pair<String, RemoteFileEntry>>()
        val toDeleteRemote = mutableListOf<String>()
        val toDeleteLocal = mutableListOf<File>()

        for (path in allPaths) {
            if (isCancelled) break
            val localFile = localFiles[path]
            val remoteFile = remoteFiles[path]

            when {
                localFile != null && remoteFile == null -> {
                    // File only exists locally
                    when (strategy) {
                        SyncStrategy.MIRROR_LOCAL, SyncStrategy.TWO_WAY, SyncStrategy.UPLOAD_ONLY ->
                            toUpload.add(path to localFile)
                        SyncStrategy.MIRROR_REMOTE, SyncStrategy.DOWNLOAD_ONLY ->
                            toDeleteLocal.add(localFile)
                    }
                }
                localFile == null && remoteFile != null -> {
                    // File only exists remotely
                    when (strategy) {
                        SyncStrategy.MIRROR_REMOTE, SyncStrategy.TWO_WAY, SyncStrategy.DOWNLOAD_ONLY ->
                            toDownload.add(path to remoteFile)
                        SyncStrategy.MIRROR_LOCAL, SyncStrategy.UPLOAD_ONLY ->
                            toDeleteRemote.add(remoteFile.fullPath)
                    }
                }
                localFile != null && remoteFile != null -> {
                    // File exists in both — check for differences
                    val localModified = localFile.lastModified()
                    val remoteModified = remoteFile.lastModified
                    val localSize = localFile.length()
                    val remoteSize = remoteFile.size

                    if (localSize == remoteSize && (localModified / 1000) == (remoteModified / 1000)) {
                        // Files appear identical
                        continue
                    }

                    val conflict = SyncConflict(
                        remotePath = PathUtils.join(remotePath, path),
                        localPath = localFile.absolutePath,
                        fileName = path.substringAfterLast('/'),
                        remoteEntry = remoteFile,
                        localSize = localSize,
                        localLastModified = localModified,
                        remoteSize = remoteSize,
                        remoteLastModified = remoteModified
                    )

                    when (strategy) {
                        SyncStrategy.MIRROR_LOCAL -> toUpload.add(path to localFile)
                        SyncStrategy.MIRROR_REMOTE -> toDownload.add(path to remoteFile)
                        SyncStrategy.TWO_WAY -> conflicts.add(conflict)
                        SyncStrategy.UPLOAD_ONLY -> toUpload.add(path to localFile)
                        SyncStrategy.DOWNLOAD_ONLY -> toDownload.add(path to remoteFile)
                    }
                }
            }
        }

        if (isCancelled) {
            return@withContext updateProgress(
                SyncProgress(state = SyncState.CANCELLED, startTimeMs = startTime)
            )
        }

        // Resolve conflicts
        val totalFiles = toUpload.size + toDownload.size
        updateProgress(_progress.value.copy(
            state = SyncState.RESOLVING_CONFLICTS,
            totalFiles = totalFiles,
            conflicts = conflicts
        ))

        val resolved = conflictResolver.resolveAll(conflicts)
        for (conflict in resolved) {
            when (conflict.resolution) {
                com.xcoder.remote.sync.SyncResolution.KEEP_LOCAL ->
                    toUpload.add(conflict.fileName to File(conflict.localPath))
                com.xcoder.remote.sync.SyncResolution.KEEP_REMOTE ->
                    toDownload.add(conflict.fileName to conflict.remoteEntry!!)
                com.xcoder.remote.sync.SyncResolution.KEEP_NEWER ->
                    if (conflict.remoteIsNewer) toDownload.add(conflict.fileName to conflict.remoteEntry!!)
                    else toUpload.add(conflict.fileName to File(conflict.localPath))
                else -> { /* SKIP, RENAME */ }
            }
        }

        val finalTotal = toUpload.size + toDownload.size
        var processed = 0

        updateProgress(_progress.value.copy(
            state = SyncState.SYNCING,
            totalFiles = finalTotal,
            conflicts = resolved
        ))

        // Execute uploads
        for ((relPath, file) in toUpload.sortedBy { it.first }) {
            if (isCancelled) break
            updateProgress(_progress.value.copy(currentFile = relPath, processedFiles = processed))
            val remoteDest = PathUtils.join(remotePath, relPath)
            fs.makeDirectory(PathUtils.parent(remoteDest), recursive = true)
            val result = fs.uploadFile(file.absolutePath, remoteDest, overwrite = true)
            if (result.isSuccess) {
                cacheManager.copyToCache(connectionId, remoteDest, file)
            }
            processed++
        }

        // Execute downloads
        for ((relPath, entry) in toDownload.sortedBy { it.first }) {
            if (isCancelled) break
            updateProgress(_progress.value.copy(currentFile = relPath, processedFiles = processed))
            val localDest = File(localDir, relPath)
            localDest.parentFile?.mkdirs()
            val result = fs.downloadFile(entry.fullPath, localDest.absolutePath, overwrite = true)
            if (result.isSuccess) {
                cacheManager.copyToCache(connectionId, entry.fullPath, localDest)
            }
            processed++
        }

        val finalState = if (isCancelled) SyncState.CANCELLED else SyncState.COMPLETED
        updateProgress(SyncProgress(
            state = finalState,
            processedFiles = processed,
            totalFiles = finalTotal,
            startTimeMs = startTime,
            elapsedTimeMs = System.currentTimeMillis() - startTime
        ))

        _progress.value
    }

    /**
     * Cancel an active sync operation.
     */
    fun cancel() {
        isCancelled = true
    }

    private fun updateProgress(progress: SyncProgress): SyncProgress {
        _progress.value = progress
        return progress
    }

    private suspend fun collectRemoteFiles(
        fs: RemoteFileSystem,
        listing: DirectoryListing,
        basePath: String,
        accumulator: MutableMap<String, RemoteFileEntry>
    ) {
        for (entry in listing.entries) {
            val relativePath = if (basePath == "/") entry.name
            else entry.fullPath.removePrefix(basePath).trimStart('/')

            if (entry.isDirectory) {
                val subListing = fs.listDirectory(entry.fullPath)
                if (subListing.isSuccess) {
                    collectRemoteFiles(fs, subListing.data, basePath, accumulator)
                }
            } else {
                accumulator[relativePath] = entry
            }
        }
    }
}