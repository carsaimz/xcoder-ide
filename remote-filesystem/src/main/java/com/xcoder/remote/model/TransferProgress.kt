package com.xcoder.remote.model

/**
 * Direction of a file transfer.
 */
enum class TransferDirection {
    UPLOAD,
    DOWNLOAD
}

/**
 * Current status of an individual transfer operation.
 */
enum class TransferStatus {
    QUEUED,
    CONNECTING,
    TRANSFERRING,
    COMPLETED,
    FAILED,
    CANCELLED,
    PAUSED,
    RETRYING,
    VERIFYING,
    MERGING
}

/**
 * Represents a single file transfer (upload or download).
 */
data class TransferItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val connectionId: String,
    val direction: TransferDirection,
    val remotePath: String,
    val localPath: String,
    val fileName: String,
    val fileSize: Long = 0L,
    val transferredBytes: Long = 0L,
    val status: TransferStatus = TransferStatus.QUEUED,
    val error: String? = null,
    val startedAt: Long = 0L,
    val completedAt: Long = 0L,
    val retryCount: Int = 0,
    val maxRetries: Int = 3,
    val priority: Int = 0,
    val overwrite: Boolean = false,
    val resumeSupported: Boolean = false,
    val checksum: String? = null
) {
    val progress: Float
        get() = if (fileSize <= 0L) 0f
        else (transferredBytes.toFloat() / fileSize.toFloat()).coerceIn(0f, 1f)

    val progressPercent: Int get() = (progress * 100).toInt()

    val remainingBytes: Long get() = (fileSize - transferredBytes).coerceAtLeast(0L)

    val formattedSize: String
        get() = formatFileSize(fileSize)

    val formattedTransferred: String
        get() = formatFileSize(transferredBytes)

    val formattedRemaining: String
        get() = formatFileSize(remainingBytes)

    val isTerminal: Boolean
        get() = status == TransferStatus.COMPLETED ||
                status == TransferStatus.FAILED ||
                status == TransferStatus.CANCELLED

    val isActive: Boolean
        get() = status == TransferStatus.TRANSFERRING ||
                status == TransferStatus.CONNECTING ||
                status == TransferStatus.RETRYING ||
                status == TransferStatus.VERIFYING ||
                status == TransferStatus.MERGING

    fun withTransferred(bytes: Long): TransferItem = copy(transferredBytes = bytes)

    fun withStatus(newStatus: TransferStatus, error: String? = null): TransferItem =
        copy(
            status = newStatus,
            error = error,
            completedAt = if (newStatus in listOf(TransferStatus.COMPLETED, TransferStatus.FAILED, TransferStatus.CANCELLED)) System.currentTimeMillis() else completedAt
        )

    fun withStarted(): TransferItem = copy(
        status = TransferStatus.TRANSFERRING,
        startedAt = System.currentTimeMillis()
    )

    fun withRetry(): TransferItem = copy(
        retryCount = retryCount + 1,
        status = TransferStatus.RETRYING
    )

    companion object {
        private const val KB = 1024L
        private const val MB = 1024L * KB
        private const val GB = 1024L * MB

        fun formatFileSize(bytes: Long): String = when {
            bytes < KB -> "$bytes B"
            bytes < MB -> "%.1f KB".format(bytes / KB.toDouble())
            bytes < GB -> "%.1f MB".format(bytes / MB.toDouble())
            else -> "%.2f GB".format(bytes / GB.toDouble())
        }
    }
}

/**
 * Aggregate summary of all active/completed transfers.
 */
data class TransferSummary(
    val activeUploads: Int = 0,
    val activeDownloads: Int = 0,
    val queuedUploads: Int = 0,
    val queuedDownloads: Int = 0,
    val completedTransfers: Int = 0,
    val failedTransfers: Int = 0,
    val totalBytesTransferred: Long = 0L,
    val currentSpeedBytesPerSec: Long = 0L
) {
    val isActive: Boolean get() = activeUploads > 0 || activeDownloads > 0

    val totalActive: Int get() = activeUploads + activeDownloads

    val totalQueued: Int get() = queuedUploads + queuedDownloads

    val formattedSpeed: String
        get() {
            if (currentSpeedBytesPerSec <= 0) return "--"
            return when {
                currentSpeedBytesPerSec < 1024 -> "$currentSpeedBytesPerSec B/s"
                currentSpeedBytesPerSec < 1024 * 1024 -> "%.1f KB/s".format(currentSpeedBytesPerSec / 1024.0)
                else -> "%.1f MB/s".format(currentSpeedBytesPerSec / (1024.0 * 1024.0))
            }
        }

    val formattedTotalTransferred: String
        get() = TransferItem.formatFileSize(totalBytesTransferred)
}

/**
 * Events emitted during a transfer for UI observation.
 */
sealed class TransferEvent {
    data class Progress(val item: TransferItem) : TransferEvent()
    data class Completed(val item: TransferItem) : TransferEvent()
    data class Failed(val item: TransferItem, val error: Throwable) : TransferEvent()
    data class Queued(val item: TransferItem) : TransferEvent()
    data class AllCompleted(val summary: TransferSummary) : TransferEvent()
}
