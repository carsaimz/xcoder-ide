package com.xcoder.remote.cache

import com.xcoder.remote.model.RemoteFileEntry
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory LRU cache for remote file metadata and directory listings.
 *
 * Prevents redundant LIST/stat calls when the user navigates back
 * to previously visited directories. Entries are evicted when the
 * cache exceeds [maxEntries].
 */
@Singleton
class RemoteFileCache @Inject constructor(
    private val maxEntries: Int = 500
) {
    /** Cache entry for a single file's metadata. */
    data class CacheEntry(
        val entry: RemoteFileEntry,
        val cachedAt: Long,
        val ttlMs: Long = 30_000L // 30 seconds by default
    ) {
        val isExpired: Boolean
            get() = System.currentTimeMillis() - cachedAt > ttlMs
    }

    /** Cache entry for a directory listing. */
    data class ListingCacheEntry(
        val entries: List<RemoteFileEntry>,
        val path: String,
        val cachedAt: Long,
        val ttlMs: Long = 15_000L // 15 seconds
    ) {
        val isExpired: Boolean
            get() = System.currentTimeMillis() - cachedAt > ttlMs
    }

    private val fileCache = ConcurrentHashMap<String, CacheEntry>()
    private val listingCache = ConcurrentHashMap<String, ListingCacheEntry>()
    private val accessOrder = ArrayDeque<String>()
    private val mutex = Mutex()

    // ── File Metadata Cache ───────────────────────────────────────────

    /**
     * Get cached file metadata. Returns null if not cached or expired.
     */
    suspend fun getFile(path: String): RemoteFileEntry? = mutex.withLock {
        val entry = fileCache[path]
        if (entry == null || entry.isExpired) {
            fileCache.remove(path)
            return null
        }
        touch(path)
        entry.entry
    }

    /**
     * Put file metadata into cache.
     */
    suspend fun putFile(entry: RemoteFileEntry, ttlMs: Long = 30_000L) = mutex.withLock {
        evictIfNeeded()
        fileCache[entry.fullPath] = CacheEntry(entry, System.currentTimeMillis(), ttlMs)
        accessOrder.addLast(entry.fullPath)
    }

    /**
     * Remove a file from cache.
     */
    suspend fun removeFile(path: String) = mutex.withLock {
        fileCache.remove(path)
        accessOrder.removeAll { it == path }
    }

    // ── Directory Listing Cache ───────────────────────────────────────

    /**
     * Get a cached directory listing. Returns null if not cached or expired.
     */
    suspend fun getListing(path: String): List<RemoteFileEntry>? = mutex.withLock {
        val entry = listingCache[path]
        if (entry == null || entry.isExpired) {
            listingCache.remove(path)
            return null
        }
        touch(path)
        entry.entries
    }

    /**
     * Put a directory listing into cache.
     */
    suspend fun putListing(
        path: String,
        entries: List<RemoteFileEntry>,
        ttlMs: Long = 15_000L
    ) = mutex.withLock {
        evictIfNeeded()
        listingCache[path] = ListingCacheEntry(entries, path, System.currentTimeMillis(), ttlMs)
        accessOrder.addLast(path)
    }

    /**
     * Invalidate the listing cache for a specific directory.
     */
    suspend fun invalidateListing(path: String) = mutex.withLock {
        listingCache.remove(path)
        accessOrder.removeAll { it == path }
    }

    /**
     * Invalidate the listing cache for a directory and all subdirectories.
     */
    suspend fun invalidateListingRecursive(path: String) = mutex.withLock {
        val normalized = path.trimEnd('/')
        val keysToRemove = listingCache.keys.filter { it.startsWith(normalized) }
        keysToRemove.forEach { key ->
            listingCache.remove(key)
            accessOrder.removeAll { it == key }
        }
    }

    /**
     * Invalidate file cache entries under a directory.
     */
    suspend fun invalidateFilesUnder(path: String) = mutex.withLock {
        val normalized = path.trimEnd('/')
        val keysToRemove = fileCache.keys.filter { it.startsWith(normalized) }
        keysToRemove.forEach { key ->
            fileCache.remove(key)
            accessOrder.removeAll { it == key }
        }
    }

    /**
     * Clear all cached data.
     */
    suspend fun clearAll() = mutex.withLock {
        fileCache.clear()
        listingCache.clear()
        accessOrder.clear()
    }

    /**
     * Clear all cache entries for a specific connection.
     */
    suspend fun clearForConnection(connectionId: String) = mutex.withLock {
        val fileKeys = fileCache.entries.filter { it.value.entry.connectionId == connectionId }.map { it.key }
        val listKeys = listingCache.entries.filter { it.value.entries.any { e -> e.connectionId == connectionId } }.map { it.key }
        fileKeys.forEach { fileCache.remove(it) }
        listKeys.forEach { listingCache.remove(it) }
        accessOrder.removeAll { it in fileKeys || it in listKeys }
    }

    /**
     * Get cache statistics.
     */
    fun getStats(): CacheStats = CacheStats(
        fileEntries = fileCache.size,
        listingEntries = listingCache.size,
        totalEntries = fileCache.size + listingCache.size,
        maxEntries = maxEntries
    )

    private fun touch(key: String) {
        accessOrder.remove(key)
        accessOrder.addLast(key)
    }

    private suspend fun evictIfNeeded() {
        val totalSize = fileCache.size + listingCache.size
        val toRemove = totalSize - maxEntries + 1
        if (toRemove <= 0) return
        repeat(minOf(toRemove, 50)) {
            val oldest = accessOrder.removeFirstOrNull() ?: return
            fileCache.remove(oldest)
            listingCache.remove(oldest)
        }
    }
}

data class CacheStats(
    val fileEntries: Int,
    val listingEntries: Int,
    val totalEntries: Int,
    val maxEntries: Int
) {
    val usagePercent: Float get() = if (maxEntries <= 0) 0f else (totalEntries.toFloat() / maxEntries.toFloat()) * 100f
    val isNearCapacity: Boolean get() = usagePercent > 80f
}
