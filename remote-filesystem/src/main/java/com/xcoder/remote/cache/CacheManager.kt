package com.xcoder.remote.cache

import android.content.Context
import com.xcoder.remote.model.RemoteConnectionInfo
import com.xcoder.remote.model.RemoteFileEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages on-disk caching of downloaded remote files.
 *
 * Files are cached in `context.cacheDir/remote_cache/{connectionId}/{remotePath}`.
 * This allows offline access to recently viewed files.
 */
@Singleton
class CacheManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val cacheRoot: File by lazy {
        File(context.cacheDir, "remote_cache").also { it.mkdirs() }
    }

    /**
     * Get the local cache file path for a remote file.
     */
    fun getCachePath(connectionId: String, remotePath: String): File {
        // Sanitize the remote path to form a valid file path
        val sanitized = remotePath
            .replace("/", File.separator)
            .trim(File.separatorChar)
        return File(cacheRoot, "${connectionId}${File.separator}$sanitized")
    }

    /**
     * Check if a cached copy exists and is not expired.
     */
    fun isCached(connectionId: String, remotePath: String, maxAgeMs: Long = 300_000L): Boolean {
        val file = getCachePath(connectionId, remotePath)
        if (!file.exists()) return false
        if (maxAgeMs <= 0) return true
        return System.currentTimeMillis() - file.lastModified() <= maxAgeMs
    }

    /**
     * Read a cached file. Returns null if not cached.
     */
    fun readCachedFile(connectionId: String, remotePath: String): File? {
        val file = getCachePath(connectionId, remotePath)
        return if (file.exists()) file else null
    }

    /**
     * Read cached text content. Returns null if not cached.
     */
    fun readCachedText(connectionId: String, remotePath: String, encoding: String = "UTF-8"): String? {
        val file = readCachedFile(connectionId, remotePath) ?: return null
        return try { file.readText(Charsets.forName(encoding)) } catch (_: Exception) { null }
    }

    /**
     * Write content to the cache.
     */
    fun writeCacheFile(connectionId: String, remotePath: String, content: ByteArray): File {
        val file = getCachePath(connectionId, remotePath)
        file.parentFile?.mkdirs()
        file.writeBytes(content)
        return file
    }

    /**
     * Write text content to the cache.
     */
    fun writeCacheText(connectionId: String, remotePath: String, content: String, encoding: String = "UTF-8"): File {
        return writeCacheFile(connectionId, remotePath, content.toByteArray(Charsets.forName(encoding)))
    }

    /**
     * Copy a local file into the cache.
     */
    fun copyToCache(connectionId: String, remotePath: String, sourceFile: File): File {
        val file = getCachePath(connectionId, remotePath)
        file.parentFile?.mkdirs()
        sourceFile.copyTo(file, overwrite = true)
        return file
    }

    /**
     * Delete a cached file.
     */
    fun deleteCachedFile(connectionId: String, remotePath: String): Boolean {
        val file = getCachePath(connectionId, remotePath)
        return file.delete()
    }

    /**
     * Clear all cached files for a specific connection.
     */
    fun clearConnectionCache(connectionId: String) {
        val dir = File(cacheRoot, connectionId)
        dir.deleteRecursively()
    }

    /**
     * Clear the entire cache.
     */
    fun clearAll() {
        cacheRoot.deleteRecursively()
        cacheRoot.mkdirs()
    }

    /**
     * Get the total size of the cache in bytes.
     */
    fun getCacheSizeBytes(): Long {
        return cacheRoot.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    /**
     * Get the cache size for a specific connection.
     */
    fun getConnectionCacheSize(connectionId: String): Long {
        val dir = File(cacheRoot, connectionId)
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    /**
     * Evict old cache entries until the total size is under [maxBytes].
     */
    fun evictToSize(maxBytes: Long): Long {
        val files = cacheRoot.walkTopDown()
            .filter { it.isFile }
            .sortedBy { it.lastModified() }
            .toMutableList()

        var totalSize = files.sumOf { it.length() }
        var evicted = 0L
        for (file in files) {
            if (totalSize <= maxBytes) break
            evicted += file.length()
            file.delete()
            totalSize -= file.length()
        }
        return evicted
    }

    /**
     * Get the number of cached files.
     */
    fun getCachedFileCount(): Int {
        return cacheRoot.walkTopDown().count { it.isFile }
    }

    companion object {
        /** Default maximum cache size: 100 MB. */
        const val DEFAULT_MAX_CACHE_SIZE = 100L * 1024L * 1024L
    }
}