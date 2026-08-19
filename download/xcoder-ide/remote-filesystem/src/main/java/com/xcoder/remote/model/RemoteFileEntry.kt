package com.xcoder.remote.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Represents a single file or directory on a remote server.
 * Analogous to `java.io.File` but for remote paths.
 */
data class RemoteFileEntry(
    /** Full absolute path on the remote server (e.g. `/var/www/index.html`). */
    val fullPath: String,
    /** Simple file/directory name (e.g. `index.html`). */
    val name: String,
    /** Whether this entry is a directory. */
    val isDirectory: Boolean,
    /** File size in bytes. 0 for directories. */
    val size: Long = 0L,
    /** Last modification timestamp in epoch milliseconds. */
    val lastModified: Long = 0L,
    /** Unix-style file permissions (e.g. `rwxr-xr-x` -> 0o755). */
    val permissions: Int = 0,
    /** Owner name (may not be available on all servers). */
    val owner: String = "",
    /** Group name (may not be available on all servers). */
    val group: String = "",
    /** Whether this is a symbolic link. */
    val isSymbolicLink: Boolean = false,
    /** Target path if [isSymbolicLink] is true. */
    val linkTarget: String = "",
    /** Whether the current user has read permission. */
    val canRead: Boolean = true,
    /** Whether the current user has write permission. */
    val canWrite: Boolean = false,
    /** Whether the current user can execute (or enter, if directory). */
    val canExecute: Boolean = false,
    /** Connection ID that this entry belongs to. */
    val connectionId: String = ""
) {
    /** Parent directory path, or empty string if at root. */
    val parentPath: String
        get() = fullPath.substringBeforeLast('/', "")

    /** File extension (e.g. `kt`) without the dot, or empty string. */
    val extension: String
        get() = if (isDirectory || !name.contains('.')) ""
        else name.substringAfterLast('.', "")

    /** Human-readable file size (e.g. `1.5 MB`). */
    val formattedSize: String
        get() = when {
            isDirectory -> "--"
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "%.1f KB".format(size / 1024.0)
            size < 1024 * 1024 * 1024 -> "%.1f MB".format(size / (1024.0 * 1024.0))
            else -> "%.2f GB".format(size / (1024.0 * 1024.0 * 1024.0))
        }

    /** Formatted last-modified date. */
    val formattedDate: String
        get() = if (lastModified <= 0) "--"
        else dateFormat.format(Date(lastModified))

    /** Unix permission string (e.g. `drwxr-xr-x`). */
    val permissionString: String
        get() {
            val sb = StringBuilder(10)
            sb.append(if (isDirectory) 'd' else if (isSymbolicLink) 'l' else '-')
            sb.append(if (permissions and 0o400 != 0) 'r' else '-')
            sb.append(if (permissions and 0o200 != 0) 'w' else '-')
            sb.append(if (permissions and 0o100 != 0) 'x' else '-')
            sb.append(if (permissions and 0o040 != 0) 'r' else '-')
            sb.append(if (permissions and 0o020 != 0) 'w' else '-')
            sb.append(if (permissions and 0o010 != 0) 'x' else '-')
            sb.append(if (permissions and 0o004 != 0) 'r' else '-')
            sb.append(if (permissions and 0o002 != 0) 'w' else '-')
            sb.append(if (permissions and 0o001 != 0) 'x' else '-')
            return sb.toString()
        }

    /** Octal permission string (e.g. `0755`). */
    val octalPermissions: String
        get() = "${permissions.toString(8).padStart(3, '0')}"

    /** MIME type guessed from the extension. */
    val mimeType: String
        get() = when (extension.lowercase()) {
            "kt", "java", "c", "cpp", "h", "hpp", "rs", "go", "py", "rb", "js",
            "ts", "swift", "dart", "scala", "lua", "r", "m", "mm", "sh",
            "bash", "zsh", "fish", "ps1" -> "text/x-code"
            "html", "htm", "xhtml", "xml", "svg", "xsl", "xslt", "xaml" -> "text/xml"
            "css", "scss", "sass", "less", "styl" -> "text/css"
            "json", "yaml", "yml", "toml", "ini", "cfg", "conf", "properties",
            "env" -> "text/config"
            "md", "mdx", "rst", "txt", "log", "csv" -> "text/plain"
            "pdf" -> "application/pdf"
            "png", "jpg", "jpeg", "gif", "bmp", "webp", "ico", "tiff",
            "tif" -> "image/*"
            "mp4", "avi", "mkv", "mov", "wmv", "flv", "webm" -> "video/*"
            "mp3", "wav", "ogg", "flac", "aac", "wma", "m4a" -> "audio/*"
            "zip", "tar", "gz", "bz2", "xz", "7z", "rar", "tgz" -> "application/archive"
            "jar", "aar", "apk", "dex" -> "application/java-archive"
            "so", "dll", "dylib", "o", "obj" -> "application/binary"
            else -> "application/octet-stream"
        }

    companion object {
        private val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

        /** Build a synthetic parent-directory entry (".."). */
        fun parentDirectory(currentPath: String, connectionId: String): RemoteFileEntry {
            val parent = currentPath.substringBeforeLast('/', "")
            return RemoteFileEntry(
                fullPath = if (parent.isEmpty()) "/" else parent,
                name = "..",
                isDirectory = true,
                connectionId = connectionId
            )
        }
    }
}

/**
 * Result of a directory listing operation.
 */
data class DirectoryListing(
    val path: String,
    val entries: List<RemoteFileEntry>,
    val totalEntries: Int = entries.size,
    val hasMore: Boolean = false,
    val listingTimeMs: Long = 0L
) {
    /** Only the directory entries. */
    val directories: List<RemoteFileEntry> get() = entries.filter { it.isDirectory }

    /** Only the file entries, sorted by name. */
    val files: List<RemoteFileEntry> get() = entries.filter { !it.isDirectory }.sortedBy { it.name.lowercase() }

    /** Entries sorted: directories first, then files, each group alphabetically. */
    val sorted: List<RemoteFileEntry>
        get() = entries.sortedWith(compareByDescending<RemoteFileEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
}
