package com.xcoder.core.file

import android.net.Uri

data class FileInfo(
    val uri: Uri,
    val name: String,
    val displayName: String = "",
    val extension: String = "",
    val mimeType: String = "",
    val size: Long = 0L,
    val lastModified: Long = 0L,
    val isDirectory: Boolean = false,
    val isFile: Boolean = true,
    val isReadable: Boolean = false,
    val isWritable: Boolean = false
)

enum class FileChangeType {
    CREATED, MODIFIED, DELETED, RENAMED
}

data class FileChangeEvent(
    val type: FileChangeType,
    val uri: Uri,
    val name: String,
    val timestamp: Long,
    val oldName: String? = null
)
