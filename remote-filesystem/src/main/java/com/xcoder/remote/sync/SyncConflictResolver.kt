package com.xcoder.remote.sync

import com.xcoder.remote.model.RemoteFileEntry

/**
 * Describes how a sync conflict should be resolved.
 */
enum class SyncResolution {
    /** Keep the remote version (download and overwrite local). */
    KEEP_REMOTE,
    /** Keep the local version (upload and overwrite remote). */
    KEEP_LOCAL,
    /** Keep the newer version based on modification time. */
    KEEP_NEWER,
    /** Keep the larger version (for cases where timestamp is unreliable). */
    KEEP_LARGER,
    /** Rename the conflicting file. */
    RENAME_LOCAL,
    /** Skip this file entirely. */
    SKIP,
    /** Prompt the user (cannot be applied automatically). */
    PROMPT
}

/**
 * A detected conflict between a local and remote file.
 */
data class SyncConflict(
    val remotePath: String,
    val localPath: String,
    val fileName: String,
    val remoteEntry: RemoteFileEntry?,
    val localSize: Long,
    val localLastModified: Long,
    val remoteSize: Long,
    val remoteLastModified: Long,
    val resolution: SyncResolution = SyncResolution.PROMPT
) {
    val hasRemote: Boolean get() = remoteEntry != null
    val sizesMatch: Boolean get() = localSize == remoteSize && localSize > 0
    val timesMatch: Boolean get() = localLastModified == remoteLastModified && localLastModified > 0
    val isIdentical: Boolean get() = sizesMatch && timesMatch

    val localIsNewer: Boolean get() = localLastModified > remoteLastModified && localLastModified > 0
    val remoteIsNewer: Boolean get() = remoteLastModified > localLastModified && remoteLastModified > 0

    val localFormattedSize: String get() = com.xcoder.remote.model.TransferItem.formatFileSize(localSize)
    val remoteFormattedSize: String get() = com.xcoder.remote.model.TransferItem.formatFileSize(remoteSize)

    /** Apply an automatic resolution and return the resolved conflict. */
    fun resolveAutomatically(strategy: SyncResolution): SyncConflict {
 val effectiveStrategy = when (strategy) {
            SyncResolution.PROMPT -> when {
                isIdentical -> SyncResolution.SKIP
                sizesMatch -> SyncResolution.SKIP
                !hasRemote -> SyncResolution.KEEP_LOCAL
                remoteIsNewer -> SyncResolution.KEEP_REMOTE
                localIsNewer -> SyncResolution.KEEP_LOCAL
                else -> SyncResolution.KEEP_NEWER
            }
            else -> strategy
        }
        return copy(resolution = effectiveStrategy)
    }
}

/**
 * Determines how to resolve sync conflicts based on configurable rules.
 */
class SyncConflictResolver {

    private val conflictListeners = mutableListOf<(SyncConflict) -> SyncResolution>()

    /**
     * Register a listener that will be called for conflicts requiring a prompt.
     * The listener returns the user's chosen resolution.
     */
    fun addConflictListener(listener: (SyncConflict) -> SyncResolution) {
        conflictListeners.add(listener)
    }

    fun removeConflictListener(listener: (SyncConflict) -> SyncResolution) {
        conflictListeners.remove(listener)
    }

    /**
     * Resolve a conflict using the default automatic strategy.
     */
    fun resolve(conflict: SyncConflict): SyncConflict {
        val resolved = conflict.resolveAutomatically(SyncResolution.KEEP_NEWER)
        if (resolved.resolution == SyncResolution.PROMPT) {
            for (listener in conflictListeners) {
                val userChoice = listener(resolved)
                if (userChoice != SyncResolution.PROMPT) {
                    return resolved.copy(resolution = userChoice)
                }
            }
        }
        return resolved
    }

    /**
     * Resolve multiple conflicts, prompting for any that need user input.
     */
    fun resolveAll(conflicts: List<SyncConflict>): List<SyncConflict> {
        return conflicts.map { resolve(it) }
    }
}
