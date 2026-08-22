package com.xcoder.core.file

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.BufferedWriter

fun DocumentFile.toFileNode(): FileNode {
    val fileName = name ?: uri.lastPathSegment ?: "unknown"
    val lastDot = fileName.lastIndexOf('.')
    val ext = if (lastDot >= 0) fileName.substring(lastDot + 1) else ""
    val nameOnly = if (lastDot >= 0) fileName.substring(0, lastDot) else fileName
    return FileNode(
        uri = uri,
        name = fileName,
        displayName = nameOnly,
        extension = ext,
        mimeType = type ?: "",
        size = length(),
        lastModified = lastModified(),
        isDirectory = isDirectory,
        isFile = isFile,
        isReadable = canRead(),
        isWritable = canWrite()
    )
}

val DocumentFile.isRegularFile: Boolean
    get() = isFile && !isDirectory && exists()

val DocumentFile.isDirectory: Boolean
    get() = try { this.isDirectory } catch (_: Exception) { false }

val DocumentFile.fileSize: Long
    get() = try { this.length() } catch (_: Exception) { 0L }

val DocumentFile.nameWithoutExtension: String
    get() {
        val fullName = name ?: ""
        val lastDot = fullName.lastIndexOf('.')
        return if (lastDot >= 0) fullName.substring(0, lastDot) else fullName
    }

val DocumentFile.fileExtension: String
    get() {
        val fullName = name ?: ""
        val lastDot = fullName.lastIndexOf('.')
        return if (lastDot >= 0) fullName.substring(lastDot + 1) else ""
    }

suspend fun DocumentFile.readText(context: Context, charset: java.nio.charset.Charset = java.nio.charset.StandardCharsets.UTF_8): String {
    return context.contentResolver.openInputStream(uri)?.use { inputStream ->
        java.io.BufferedReader(java.io.InputStreamReader(inputStream, charset)).readText()
    } ?: ""
}

suspend fun DocumentFile.readBytes(context: Context): ByteArray {
    return context.contentResolver.openInputStream(uri)?.use { inputStream ->
        inputStream.readBytes()
    } ?: ByteArray(0)
}

suspend fun DocumentFile.writeText(context: Context, text: String, charset: java.nio.charset.Charset = java.nio.charset.StandardCharsets.UTF_8) {
    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
        java.io.BufferedWriter(java.io.OutputStreamWriter(outputStream, charset)).use { writer ->
            writer.write(text)
        }
    }
}

suspend fun DocumentFile.writeBytes(context: Context, data: ByteArray) {
    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
        outputStream.write(data)
    }
}

fun DocumentFile.childCount(): Int {
    return if (isDirectory) {
        try { listFiles()?.size ?: 0 } catch (_: Exception) { 0 }
    } else 0
}

fun DocumentFile.resolveChild(context: Context, childName: String): DocumentFile? {
    return if (isDirectory) {
        try { findFile(childName) } catch (_: Exception) { null }
    } else null
}

fun DocumentFile.hasChild(context: Context, childName: String): Boolean {
    return resolveChild(context, childName) != null
}

fun DocumentFile.isEmptyDirectory(context: Context): Boolean {
    return isDirectory && childCount() == 0
}

fun DocumentFile.queryColumnSize(contentResolver: ContentResolver): Long {
    return try {
        contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0) cursor.getLong(sizeIndex) else length()
            } else {
                length()
            }
        } ?: length()
    } catch (_: Exception) {
        length()
    }
}

fun DocumentFile.queryDisplayName(contentResolver: ContentResolver): String {
    return try {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) cursor.getString(nameIndex) else name
            } else {
                name
            }
        } ?: name
    } catch (_: Exception) {
        name
    } ?: "unknown"
}

data class FileNode(
    val uri: Uri,
    val name: String,
    val displayName: String,
    val extension: String,
    val mimeType: String,
    val size: Long,
    val lastModified: Long,
    val isDirectory: Boolean,
    val isFile: Boolean,
    val isReadable: Boolean,
    val isWritable: Boolean
) {
    val sizeFormatted: String
        get() = when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${"%.1f".format(size / 1024.0)} KB"
            size < 1024 * 1024 * 1024 -> "${"%.1f".format(size / (1024.0 * 1024))} MB"
            else -> "${"%.2f".format(size / (1024.0 * 1024 * 1024))} GB"
        }

    val isTextFile: Boolean
        get() {
            val textExtensions = setOf(
                "kt", "java", "xml", "json", "gradle", "kts", "properties",
                "txt", "md", "yaml", "yml", "toml", "csv", "html", "css",
                "js", "ts", "tsx", "jsx", "py", "rb", "go", "rs", "c", "cpp",
                "h", "hpp", "cs", "swift", "sh", "bash", "sql", "graphql",
                "proto", "dockerfile", "makefile", "cmake", "gradle"
            )
            return extension.lowercase() in textExtensions
        }

    val isImageFile: Boolean
        get() = mimeType.startsWith("image/")

    val isBinaryFile: Boolean
        get() = !isTextFile

    fun toFileInfo(): FileInfo = FileInfo(
        uri = uri,
        name = name,
        displayName = displayName,
        extension = extension,
        mimeType = mimeType,
        size = size,
        lastModified = lastModified,
        isDirectory = isDirectory,
        isFile = isFile,
        isReadable = isReadable,
        isWritable = isWritable
    )
}