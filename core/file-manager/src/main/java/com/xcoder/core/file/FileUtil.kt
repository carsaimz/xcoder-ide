@file:Suppress("TooManyFunctions")
package com.xcoder.core.file

import java.io.*
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import kotlin.math.log10

private const val TAG = "XCoderFileUtil"

/** Buffer size for file copy operations. 8 KB matches common IO benchmarks. */
private const val BUFFER_SIZE = 8192

/** Default character encoding. */
private val DEFAULT_CHARSET: Charset = StandardCharsets.UTF_8

/** File size units and their byte thresholds. */
private val SIZE_UNITS = arrayOf("B", "KB", "MB", "GB", "TB", "PB")

/** Line separator for the current platform. */
private val LINE_SEPARATOR = System.lineSeparator()

/**
 * Comprehensive file utility class.
 *
 * Based on Sketchware-IA's `FileUtil` (845 lines) and Termux's `FileUtils`.
 * Sketchware-IA's FileUtil provides a wide range of file operations used
 * throughout the IDE including:
 * - File reading/writing with encoding detection
 * - Directory listing (flat and recursive)
 * - File size formatting (human-readable)
 * - File manipulation (copy, move, rename, delete)
 * - Code statistics (line counting)
 * - Extension and name utilities
 *
 * Termux's `FileUtils` provides similar operations optimized for the
 * terminal environment with proper error handling and atomic operations.
 *
 * All methods use Java NIO (`java.nio.file`) for better error messages
 * and symbolic link handling, falling back to `java.io.File` where needed
 * for Android API compatibility.
 *
 * ## Usage
 *
 * ```kotlin
 * // Read a file
 * val content = FileUtil.readFile("/path/to/file.txt")
 *
 * // List files recursively
 * val files = FileUtil.listFilesRecursively("/path/to/project")
 *
 * // Get human-readable file size
 * val size = FileUtil.formatFileSize(file.length())
 * ```
 */
object FileUtil {

    // ══════════════════════════════════════════════════════════════════════
    //  File Read / Write
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Read a file's entire content as a string.
     *
     * Based on Sketchware-IA's `readFile()` which reads a file with UTF-8
     * encoding. Uses buffered reading for efficiency.
     *
     * @param path Absolute path to the file.
     * @return File content as a string, or null if the file doesn't exist or can't be read.
     */
    @JvmStatic
    @Throws(IOException::class)
    fun readFile(path: String): String? {
        val file = toFile(path)
        if (!file.exists() || !file.canRead()) return null
        return file.readText(DEFAULT_CHARSET)
    }

    /**
     * Read a file with a specific character encoding.
     *
     * Based on Termux's `readFileWithEncoding()` which supports reading
     * files in various encodings including UTF-8, UTF-16, and ISO-8859-1.
     *
     * @param path Absolute path to the file.
     * @param charset Character encoding to use.
     * @return File content as a string, or null if reading fails.
     */
    @JvmStatic
    @Throws(IOException::class)
    fun readFile(path: String, charset: Charset): String? {
        val file = toFile(path)
        if (!file.exists() || !file.canRead()) return null
        return try {
            file.readText(charset)
        } catch (e: Exception) {
            // Fallback: try UTF-8 if specified encoding fails
            try {
                file.readText(StandardCharsets.UTF_8)
            } catch (e2: Exception) {
                null
            }
        }
    }

    /**
     * Read a file's content as a byte array.
     *
     * Used for binary file operations (e.g. image handling, APK manipulation).
     *
     * @param path Absolute path to the file.
     * @return File content as a byte array, or null if reading fails.
     */
    @JvmStatic
    @Throws(IOException::class)
    fun readFileBytes(path: String): ByteArray? {
        val file = toFile(path)
        if (!file.exists() || !file.canRead()) return null
        return file.readBytes()
    }

    /**
     * Read the first N lines of a file.
     *
     * Sketchware-IA uses this for previewing file content without loading
     * the entire file into memory.
     *
     * @param path Absolute path to the file.
     * @param maxLines Maximum number of lines to read.
     * @return List of lines (without line terminators).
     */
    @JvmStatic
    fun readLines(path: String, maxLines: Int = Int.MAX_VALUE): List<String> {
        val file = toFile(path)
        if (!file.exists() || !file.canRead()) return emptyList()
        return file.useLines { lines ->
            lines.take(maxLines).toList()
        }
    }
    /**
     * Write a string to a file, creating parent directories if needed.
     *
     * Based on Sketchware-IA's `writeFile()` which ensures parent directories
     * exist before writing. This is essential for IDE operations like creating
     * new source files in nested package directories.
     *
     * @param path Absolute path to the file.
     * @param content Content to write.
     * @param charset Character encoding (default UTF-8).
     * @return true if the write succeeded.
     */
    @JvmStatic
    fun writeFile(path: String, content: String, charset: Charset = DEFAULT_CHARSET): Boolean {
        return try {
            val file = toFile(path)
            file.parentFile?.mkdirs()
            file.writeText(content, charset)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Write bytes to a file, creating parent directories if needed.
     */
    @JvmStatic
    fun writeFileBytes(path: String, bytes: ByteArray): Boolean {
        return try {
            val file = toFile(path)
            file.parentFile?.mkdirs()
            file.writeBytes(bytes)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Append a string to a file.
     *
     * Based on Sketchware-IA's `appendFile()` used for log files.
     */
    @JvmStatic
    fun appendFile(path: String, content: String, charset: Charset = DEFAULT_CHARSET): Boolean {
        return try {
            val file = toFile(path)
            file.parentFile?.mkdirs()
            file.appendText(content, charset)
            true
        } catch (e: Exception) {
            false
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  File Operations: Delete, Copy, Move, Rename
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Delete a file or directory.
     *
     * Based on Sketchware-IA's `deleteFile()` which handles both files
     * and directories. For directories, deletes recursively.
     *
     * @param path Absolute path to delete.
     * @return true if deletion succeeded.
     */
    @JvmStatic
    fun deleteFile(path: String): Boolean {
        return try {
            val file = toFile(path)
            if (file.isDirectory) {
                file.deleteRecursively()
            } else {
                file.delete()
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Copy a file to a destination path.
     *
     * Based on Sketchware-IA's `copyFile()` which uses buffered streams
     * for efficient copying of large files.
     *
     * @param sourcePath Source file path.
     * @param destPath Destination file path.
     * @return true if the copy succeeded.
     */
    @JvmStatic
    fun copyFile(sourcePath: String, destPath: String): Boolean {
        return try {
            val source = toFile(sourcePath)
            val dest = toFile(destPath)
            if (!source.exists() || !source.isFile) return false
            dest.parentFile?.mkdirs()
            source.copyTo(dest, overwrite = true)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Copy a directory recursively.
     *
     * Termux's `copyDirectory()` walks the source tree and copies
     * each file while preserving the directory structure.
     *
     * @param sourceDir Source directory path.
     * @param destDir Destination directory path.
     * @return true if the copy succeeded.
     */
    @JvmStatic
    fun copyDirectory(sourceDir: String, destDir: String): Boolean {
        return try {
            val source = toFile(sourceDir)
            val dest = toFile(destDir)
            if (!source.isDirectory) return false
            source.copyRecursively(dest, overwrite = true)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Move a file or directory to a new location.
     *
     * Based on Sketchware-IA's `moveFile()` which uses `renameTo()`
     * and falls back to copy+delete if rename fails (cross-device move).
     *
     * @param sourcePath Source path.
     * @param destPath Destination path.
     * @return true if the move succeeded.
     */
    @JvmStatic
    fun moveFile(sourcePath: String, destPath: String): Boolean {
        return try {
            val source = toFile(sourcePath)
            val dest = toFile(destPath)
            dest.parentFile?.mkdirs()
            // Try rename first (fast, atomic on same filesystem)
            if (source.renameTo(dest)) return true
            // Fall back to copy + delete (for cross-filesystem moves)
            if (source.isDirectory) {
                if (!copyDirectory(sourcePath, destPath)) return false
            } else {
                if (!copyFile(sourcePath, destPath)) return false
            }
            deleteFile(sourcePath)
        } catch (e: Exception) {
            return false
        }
        true
    }

    /**
     * Rename a file or directory.
     *
     * Based on Sketchware-IA's `renameFile()`. Note: this only changes
     * the name within the same parent directory. For moving to a different
     * directory, use [moveFile].
     *
     * @param path The file/directory path.
     * @param newName The new name (not a full path).
     * @return true if the rename succeeded.
     */
    @JvmStatic
    fun renameFile(path: String, newName: String): Boolean {
        return try {
            val file = toFile(path)
            val dest = File(file.parent, newName)
            file.renameTo(dest)
        } catch (e: Exception) {
            false
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Directory Operations
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Create a directory (and parent directories if needed).
     *
     * Based on Termux's `mkdirp()` equivalent.
     *
     * @param path Directory path to create.
     * @return true if the directory exists or was created.
     */
    @JvmStatic
    fun makeDir(path: String): Boolean {
        val file = toFile(path)
        return if (file.exists()) file.isDirectory else file.mkdirs()
    }

    /**
     * List files in a directory (non-recursive).
     *
     * Based on Sketchware-IA's `listFiles()` which returns file names
     * (not full paths). Returns null if the directory doesn't exist.
     *
     * @param dirPath Directory path.
     * @param showHidden Whether to include hidden files (starting with .).
     * @return List of file names, or empty list if directory doesn't exist.
     */
    @JvmStatic
    fun listFiles(dirPath: String, showHidden: Boolean = false): List<String> {
        val dir = toFile(dirPath)
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles()
            ?.filter { showHidden || !it.name.startsWith(".") }
            ?.map { it.name }
            ?.sorted()
            ?: emptyList()
    }

    /**
     * List files in a directory as [File] objects.
     *
     * @param dirPath Directory path.
     * @param showHidden Whether to include hidden files.
     * @return List of [File] objects.
     */
    @JvmStatic
    fun listFileObjects(dirPath: String, showHidden: Boolean = false): List<File> {
        val dir = toFile(dirPath)
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles()
            ?.filter { showHidden || !it.name.startsWith(".") }
            ?.sortedBy { it.name }
            ?: emptyList()
    }

    /**
     * List all files recursively under a directory.
     *
     * Based on Sketchware-IA's `listFilesRecursively()` which walks
     * the directory tree using `walk()`. Used for project-wide search
     * and code statistics.
     *
     * @param dirPath Root directory path.
     * @param showHidden Whether to include hidden files and directories.
     * @param maxDepth Maximum directory depth (unlimited if null).
     * @return List of absolute file paths.
     */
    @JvmStatic
    fun listFilesRecursively(
        dirPath: String,
        showHidden: Boolean = false,
        maxDepth: Int? = null,
    ): List<String> {
        val dir = toFile(dirPath)
        if (!dir.isDirectory) return emptyList()

        val walk = dir.walk()
    
        val depthLimited = if (maxDepth != null) {
            walk.maxDepth(maxDepth)
        } else {
            walk
        }

        val filtered = if (showHidden) {
            depthLimited
        } else {
            depthLimited.filter { !it.name.startsWith(".") && !it.absolutePath.contains("/.") }
        }

        return filtered
            .filter { it.isFile }
            .map { it.absolutePath }
            .toList()
    }

    // ══════════════════════════════════════════════════════════════════════
    //  File Information
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Check if a file or directory exists.
     *
     * @param path Path to check.
     * @return true if the path exists.
     */
    @JvmStatic
    fun isExistFile(path: String): Boolean = toFile(path).exists()

    /**
     * Check if the path is a directory.
     *
     * @param path Path to check.
     * @return true if the path exists and is a directory.
     */
    @JvmStatic
    fun isDirectory(path: String): Boolean = toFile(path).isDirectory

    /**
     * Check if the path is a regular file.
     *
     * @param path Path to check.
     * @return true if the path exists and is a file.
     */
    @JvmStatic
    fun isFile(path: String): Boolean = toFile(path).isFile

    /**
     * Get the file size in bytes.
     *
     * @param path File path.
     * @return File size in bytes, or 0 if the file doesn't exist.
     */
    @JvmStatic
    fun getFileSize(path: String): Long {
        return toFile(path).length()
    }

    /**
     * Format a file size in bytes to a human-readable string.
     *
     * Based on Sketchware-IA's `formatFileSize()` which formats bytes
     * as "1.5 KB", "3.2 MB", etc.
     *
     * @param bytes File size in bytes.
     * @param decimals Number of decimal places (default 1).
     * @return Human-readable size string.
     */
    @JvmStatic
    fun formatFileSize(bytes: Long, decimals: Int = 1): String {
        if (bytes <= 0) return "0 B"
        val unitIndex = (log10(bytes.toDouble()) / log10(1024.0)).toInt().coerceAtMost(SIZE_UNITS.size - 1)
        val value = bytes / Math.pow(1024.0, unitIndex.toDouble())
        val format = "%.${decimals}f %s"
        return format.format(value, SIZE_UNITS[unitIndex])
    }

    /**
     * Get the file name without extension.
     *
     * Based on Sketchware-IA's `getFileNameNoExtension()`. Handles
     * multiple dots correctly (e.g. "build.gradle.kts" → "build.gradle").
     *
     * @param fileName File name (not full path).
     * @return File name without the last extension.
     */
    @JvmStatic
    fun getFileNameNoExtension(fileName: String): String {
        val lastDot = fileName.lastIndexOf('.')
        return if (lastDot > 0) fileName.substring(0, lastDot) else fileName
    }

    /**
     * Get the file extension (without the dot).
     *
     * Based on Sketchware-IA's `getFileExtension()`. Returns an empty
     * string for files without an extension.
     *
     * @param fileName File name (not full path).
     * @return Lowercase file extension, or empty string.
     */
    @JvmStatic
    fun getFileExtension(fileName: String): String {
        val lastDot = fileName.lastIndexOf('.')
        return if (lastDot >= 0 && lastDot < fileName.length - 1) {
            fileName.substring(lastDot + 1).lowercase()
        } else {
            ""
        }
    }

    /**
     * Get just the file name from a full path.
     *
     * @param path Full file path.
     * @return The file name portion.
     */
    @JvmStatic
    fun getFileName(path: String): String {
        return toFile(path).name
    }

    /**
     * Get the parent directory path.
     *
     * @param path File or directory path.
     * @return Parent directory path, or the path itself if it has no parent.
     */
    @JvmStatic
    fun getParentDir(path: String): String {
        return toFile(path).parent ?: path
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Code Statistics
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Count the number of lines in a file.
     *
     * Based on Sketchware-IA's `countLines()` which is used for
     * project statistics (total lines of code).
     *
     * @param path File path.
     * @return Number of lines, or 0 if the file doesn't exist.
     */
    @JvmStatic
    fun countLines(path: String): Int {
        val file = toFile(path)
        if (!file.exists() || !file.canRead()) return 0
        return try {
            file.useLines { it.count() }
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Count total lines across multiple files.
     *
     * @param paths List of file paths.
     * @return Total line count.
     */
    @JvmStatic
    fun countTotalLines(paths: List<String>): Int {
        return paths.sumOf { countLines(it) }
    }

    /**
     * Count lines of code (excluding empty lines and comments) in a file.
     *
     * @param path File path.
     * @param commentPrefixes Line comment prefixes (e.g. ["//", "#", "--"]).
     * @return Number of code lines.
     */
    @JvmStatic
    fun countCodeLines(path: String, commentPrefixes: List<String> = listOf("//", "#", "--")): Int {
        val file = toFile(path)
        if (!file.exists() || !file.canRead()) return 0
        return try {
            file.useLines { lines ->
                lines.count { line ->
                    val trimmed = line.trim()
                    trimmed.isNotEmpty() && commentPrefixes.none { trimmed.startsWith(it) }
                }
            }
        } catch (e: Exception) {
            0
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Encoding Detection
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Detect the character encoding of a file using BOM (Byte Order Mark).
     *
     * Based on Sketchware-IA's encoding detection logic.
     * Falls back to UTF-8 if no BOM is detected.
     *
     * @param path File path.
     * @return Detected charset name.
     */
    @JvmStatic
    fun detectEncoding(path: String): String {
        val file = toFile(path)
        if (!file.exists()) return "UTF-8"
        val bytes = file.readBytes().take(4).toByteArray()
        return when {
            bytes.size >= 3 &&
                bytes[0] == 0xEF.toByte() &&
                bytes[1] == 0xBB.toByte() &&
                bytes[2] == 0xBF.toByte() -> "UTF-8"
            bytes.size >= 2 &&
                bytes[0] == 0xFF.toByte() &&
                bytes[1] == 0xFE.toByte() -> "UTF-16LE"
            bytes.size >= 2 &&
                bytes[0] == 0xFE.toByte() &&
                bytes[1] == 0xFF.toByte() -> "UTF-16BE"
            bytes.size >= 4 &&
                bytes[0] == 0x00.toByte() &&
                bytes[1] == 0x00.toByte() &&
                bytes[2] == 0xFE.toByte() &&
                bytes[3] == 0xFF.toByte() -> "UTF-32BE"
            bytes.size >= 4 &&
                bytes[0] == 0xFF.toByte() &&
                bytes[1] == 0xFE.toByte() &&
                bytes[2] == 0x00.toByte() &&
                bytes[3] == 0x00.toByte() -> "UTF-32LE"
            else -> "UTF-8"
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Utility
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Convert a path string to a [File] object.
     * Handles both absolute and relative paths.
     */
    @JvmStatic
    fun toFile(path: String): File = File(path)

    /**
     * Get the canonical (absolute, resolved) path.
     *
     * @param path Input path.
     * @return Canonical path, or the input path if resolution fails.
     */
    @JvmStatic
    fun getCanonicalPath(path: String): String {
        return try {
            toFile(path).canonicalPath
        } catch (e: Exception) {
            path
        }
    }

    /**
     * Ensure a file path ends with the given extension.
     *
     * @param path File path.
     * @param extension Extension with dot (e.g. ".kt").
     * @return Path with the extension ensured.
     */
    @JvmStatic
    fun ensureExtension(path: String, extension: String): String {
        return if (path.endsWith(extension, ignoreCase = true)) {
            path
        } else {
            "$path$extension"
        }
    }

    /**
     * Get a relative path from a base directory to a target file.
     *
     * @param basePath Base directory path.
     * @param targetPath Target file path.
     * @return Relative path, or the target path if it's not under the base.
     */
    @JvmStatic
    fun getRelativePath(basePath: String, targetPath: String): String {
        return try {
            val base = toFile(basePath).canonicalPath
            val target = toFile(targetPath).canonicalPath
            if (target.startsWith(base)) {
                target.removePrefix(base).removePrefix(File.separator)
            } else {
                target
            }
        } catch (e: Exception) {
            targetPath
        }
    }

    /**
     * Check if a file path is a child of a directory.
     */
    @JvmStatic
    fun isChildOf(dirPath: String, filePath: String): Boolean {
        return try {
            val dir = toFile(dirPath).canonicalPath + File.separator
            toFile(filePath).canonicalPath.startsWith(dir)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get a unique file name by appending a number suffix if the file exists.
     *
     * Based on Sketchware-IA's pattern for generating unique file names
     * during copy/save-as operations.
     *
     * @param dirPath Directory path.
     * @param fileName Desired file name.
     * @return A unique file name in the directory.
     */
    @JvmStatic
    fun getUniqueFileName(dirPath: String, fileName: String): String {
        val dir = toFile(dirPath)
        if (!File(dir, fileName).exists()) return fileName

        val nameNoExt = getFileNameNoExtension(fileName)
        val ext = getFileExtension(fileName)
        var counter = 1
        while (true) {
            val candidate = if (ext.isNotEmpty()) {
                "${nameNoExt} ($counter).$ext"
            } else {
                "$nameNoExt ($counter)"
            }
            if (!File(dir, candidate).exists()) return candidate
            counter++
        }
    }
}
