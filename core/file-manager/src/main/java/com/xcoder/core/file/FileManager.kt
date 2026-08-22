package com.xcoder.core.file

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val contentResolver: ContentResolver get() = context.contentResolver
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _fileEvents = MutableSharedFlow<FileChangeEvent>(extraBufferCapacity = 64)
    val fileEvents: SharedFlow<FileChangeEvent> = _fileEvents.asSharedFlow()

    suspend fun createFile(
        parentUri: Uri,
        displayName: String,
        mimeType: String = "application/octet-stream"
    ): Result<DocumentFile> = withContext(Dispatchers.IO) {
        try {
            val parentDoc = DocumentFile.fromTreeUri(context, parentUri)
            if (parentDoc == null || !parentDoc.canWrite()) {
                return@withContext Result.failure(
                    IllegalArgumentException("Parent directory is invalid or not writable: $parentUri")
                )
            }
            val existing = parentDoc.findFile(displayName)
            if (existing != null) {
                return@withContext Result.success(existing)
            }
            val created = parentDoc.createFile(mimeType, displayName)
            if (created != null) {
                persistUriPermission(created.uri)
                _fileEvents.emit(FileChangeEvent(FileChangeType.CREATED, created.uri, displayName, System.currentTimeMillis()))
                Result.success(created)
            } else {
                Result.failure(IOException("Failed to create file: $displayName in $parentUri"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun readFile(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            val content = contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).readText()
            }
            if (content != null) {
                Result.success(content)
            } else {
                Result.failure(IOException("Cannot open input stream for URI: $uri"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun readFileBytes(uri: Uri): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null) {
                Result.success(bytes)
            } else {
                Result.failure(IOException("Cannot open input stream for URI: $uri"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun writeFile(uri: Uri, content: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                BufferedWriter(OutputStreamWriter(outputStream, Charsets.UTF_8)).use { writer ->
                    writer.write(content)
                }
            }
            _fileEvents.emit(FileChangeEvent(FileChangeType.MODIFIED, uri, uri.lastPathSegment ?: "unknown", System.currentTimeMillis()))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun writeFileBytes(uri: Uri, data: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            contentResolver.openOutputStream(uri)?.use { it.write(data) }
            _fileEvents.emit(FileChangeEvent(FileChangeType.MODIFIED, uri, uri.lastPathSegment ?: "unknown", System.currentTimeMillis()))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteFile(uri: Uri): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val doc = DocumentFile.fromSingleUri(context, uri)
            if (doc == null) {
                return@withContext Result.failure(IllegalArgumentException("Cannot resolve document: $uri"))
            }
            val name = doc.name ?: "unknown"
            val deleted = doc.delete()
            if (deleted) {
                releaseUriPermission(uri)
                _fileEvents.emit(FileChangeEvent(FileChangeType.DELETED, uri, name, System.currentTimeMillis()))
            }
            Result.success(deleted)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listFiles(directoryUri: Uri): Result<List<DocumentFile>> = withContext(Dispatchers.IO) {
        try {
            val dirDoc = DocumentFile.fromTreeUri(context, directoryUri)
            if (dirDoc == null || !dirDoc.isDirectory) {
                return@withContext Result.failure(
                    IllegalArgumentException("Not a directory or cannot access: $directoryUri")
                )
            }
            val files = dirDoc.listFiles().toList()
            Result.success(files)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createDirectory(parentUri: Uri, directoryName: String): Result<DocumentFile> = withContext(Dispatchers.IO) {
        try {
            val parentDoc = DocumentFile.fromTreeUri(context, parentUri)
            if (parentDoc == null || !parentDoc.canWrite()) {
                return@withContext Result.failure(
                    IllegalArgumentException("Parent directory is invalid or not writable: $parentUri")
                )
            }
            val existing = parentDoc.findFile(directoryName)
            if (existing != null && existing.isDirectory) {
                return@withContext Result.success(existing)
            }
            val created = parentDoc.createDirectory(directoryName)
            if (created != null) {
                persistUriPermission(created.uri)
                _fileEvents.emit(
                    FileChangeEvent(FileChangeType.CREATED, created.uri, directoryName, System.currentTimeMillis())
                )
                Result.success(created)
            } else {
                Result.failure(IOException("Failed to create directory: $directoryName in $parentUri"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun copyFile(sourceUri: Uri, destinationDirUri: Uri, newName: String? = null): Result<DocumentFile> =
        withContext(Dispatchers.IO) {
            try {
                val sourceDoc = DocumentFile.fromSingleUri(context, sourceUri)
                if (sourceDoc == null || !sourceDoc.exists()) {
                    return@withContext Result.failure(
                        IllegalArgumentException("Source file does not exist: $sourceUri")
                    )
                }
                val mimeType = sourceDoc.type ?: "application/octet-stream"
                val displayName = newName ?: sourceDoc.name ?: "copied_file"
                val destDir = DocumentFile.fromTreeUri(context, destinationDirUri)
                    ?: return@withContext Result.failure(
                        IllegalArgumentException("Destination directory invalid: $destinationDirUri")
                    )
                val destFile = destDir.createFile(mimeType, displayName)
                    ?: return@withContext Result.failure(
                        IOException("Failed to create destination file: $displayName")
                    )
                contentResolver.openInputStream(sourceUri)?.use { input ->
                    contentResolver.openOutputStream(destFile.uri)?.use { output ->
                        input.copyTo(output, DEFAULT_BUFFER_SIZE)
                    }
                }
                persistUriPermission(destFile.uri)
                _fileEvents.emit(
                    FileChangeEvent(FileChangeType.CREATED, destFile.uri, displayName, System.currentTimeMillis())
                )
                Result.success(destFile)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun moveFile(sourceUri: Uri, destinationDirUri: Uri, newName: String? = null): Result<DocumentFile> =
        withContext(Dispatchers.IO) {
            try {
                val copyResult = copyFile(sourceUri, destinationDirUri, newName)
                if (copyResult.isFailure) {
                    return@withContext copyResult
                }
                val deleteResult = deleteFile(sourceUri)
                if (deleteResult.isFailure || !deleteResult.getOrDefault(false)) {
                    deleteFile(copyResult.getOrThrow().uri)
                    return@withContext Result.failure(
                        IOException("Failed to delete source file after copy: $sourceUri")
                    )
                }
                copyResult
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun renameFile(uri: Uri, newName: String): Result<DocumentFile> = withContext(Dispatchers.IO) {
        try {
            val doc = DocumentFile.fromSingleUri(context, uri)
                ?: return@withContext Result.failure(
                    IllegalArgumentException("Cannot resolve document: $uri")
                )
            val parentUri = doc.parentUri
                ?: return@withContext Result.failure(
                    IOException("Cannot determine parent directory for: $uri")
                )
            val parentDoc = DocumentFile.fromTreeUri(context, parentUri)
                ?: return@withContext Result.failure(
                    IOException("Cannot access parent directory: $parentUri")
                )
            val mimeType = doc.type ?: "application/octet-stream"
            val renamed = parentDoc.createFile(mimeType, newName)
                ?: return@withContext Result.failure(
                    IOException("Failed to create file with new name: $newName")
                )
            contentResolver.openInputStream(uri)?.use { input ->
                contentResolver.openOutputStream(renamed.uri)?.use { output ->
                    input.copyTo(output, DEFAULT_BUFFER_SIZE)
                }
            }
            val oldName = doc.name ?: "unknown"
            doc.delete()
            persistUriPermission(renamed.uri)
            _fileEvents.emit(
                FileChangeEvent(FileChangeType.RENAMED, renamed.uri, newName, System.currentTimeMillis(), oldName)
            )
            Result.success(renamed)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun exists(uri: Uri): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val doc = DocumentFile.fromSingleUri(context, uri)
            Result.success(doc?.exists() == true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getFileSize(uri: Uri): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val doc = DocumentFile.fromSingleUri(context, uri)
                ?: return@withContext Result.failure(IllegalArgumentException("Cannot resolve: $uri"))
            Result.success(doc.length())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getFileInfo(uri: Uri): Result<FileInfo> = withContext(Dispatchers.IO) {
        try {
            val doc = DocumentFile.fromSingleUri(context, uri)
                ?: return@withContext Result.failure(IllegalArgumentException("Cannot resolve: $uri"))
            val name = doc.name ?: uri.lastPathSegment ?: "unknown"
            val lastSegment = uri.lastPathSegment ?: ""
            val lastDot = lastSegment.lastIndexOf('.')
            val extension = if (lastDot >= 0) lastSegment.substring(lastDot + 1) else ""
            val nameWithoutExt = if (lastDot >= 0) lastSegment.substring(0, lastDot) else lastSegment
            val info = FileInfo(
                uri = uri,
                name = name,
                displayName = nameWithoutExt,
                extension = extension,
                mimeType = doc.type ?: "",
                size = doc.length(),
                lastModified = doc.lastModified(),
                isDirectory = doc.isDirectory,
                isFile = doc.isFile,
                isReadable = doc.canRead(),
                isWritable = doc.canWrite()
            )
            Result.success(info)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchFiles(
        directoryUri: Uri,
        query: String,
        recursive: Boolean = true,
        matcher: ((String) -> Boolean)? = null
    ): Result<List<DocumentFile>> = withContext(Dispatchers.IO) {
        try {
            val results = mutableListOf<DocumentFile>()
            val predicate = matcher ?: { fileName -> fileName.contains(query, ignoreCase = true) }
            searchFilesRecursive(directoryUri, predicate, recursive, results)
            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun searchFilesRecursive(
        directoryUri: Uri,
        predicate: (String) -> Boolean,
        recursive: Boolean,
        results: MutableList<DocumentFile>
    ) {
        val dirDoc = DocumentFile.fromTreeUri(context, directoryUri) ?: return
        val children = dirDoc.listFiles()
        for (child in children) {
            val childName = child.name ?: ""
            if (child.isFile && predicate(childName)) {
                results.add(child)
            }
            if (recursive && child.isDirectory) {
                searchFilesRecursive(child.uri, predicate, true, results)
            }
        }
    }

    fun watchFile(uri: Uri): FileWatcherHandle {
        val handle = FileWatcherHandle(uri)
        scope.launch {
            handle.startWatching(contentResolver) { event ->
                _fileEvents.emit(event)
            }
        }
        return handle
    }

    suspend fun openFileDescriptor(uri: Uri, mode: String = "r"): Result<ParcelFileDescriptor> =
        withContext(Dispatchers.IO) {
            try {
                val pfd = contentResolver.openFileDescriptor(uri, mode)
                if (pfd != null) {
                    Result.success(pfd)
                } else {
                    Result.failure(IOException("Cannot open file descriptor for URI: $uri"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun appendToFile(uri: Uri, content: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            contentResolver.openOutputStream(uri, "wa")?.use { outputStream ->
                BufferedWriter(OutputStreamWriter(outputStream, Charsets.UTF_8)).use { writer ->
                    writer.write(content)
                }
            }
            _fileEvents.emit(FileChangeEvent(FileChangeType.MODIFIED, uri, uri.lastPathSegment ?: "unknown", System.currentTimeMillis()))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun persistUriPermission(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            context.contentResolver.takePersistableUriPermission(uri, flags)
        } catch (_: Exception) {
        }
    }

    private fun releaseUriPermission(uri: Uri) {
        val readFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        val writeFlags = Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            context.contentResolver.releasePersistableUriPermission(uri, readFlags)
        } catch (_: Exception) {
        }
        try {
            context.contentResolver.releasePersistableUriPermission(uri, writeFlags)
        } catch (_: Exception) {
        }
    }

    companion object {
        const val DEFAULT_BUFFER_SIZE = 8192
    }
}

class FileWatcherHandle(private val uri: Uri) {
    private var observer: ContentObserver? = null
    @Volatile
    private var isWatching = false

    suspend fun startWatching(
        contentResolver: ContentResolver,
        onEvent: (FileChangeEvent) -> Unit
    ) {
        if (isWatching) return
        isWatching = true
        callbackFlow<FileChangeEvent> {
            val handler = Handler(Looper.getMainLooper())
            val obs = object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean, changedUri: Uri?) {
                    val eventUri = changedUri ?: uri
                    try {
                        val changeEvent = FileChangeEvent(
                            type = FileChangeType.MODIFIED,
                            uri = eventUri,
                            name = eventUri.lastPathSegment ?: "unknown",
                            timestamp = System.currentTimeMillis()
                        )
                        trySend(changeEvent)
                    } catch (_: Exception) {
                    }
                }
            }
            observer = obs
            contentResolver.registerContentObserver(uri, true, obs)
            awaitClose {
                contentResolver.unregisterContentObserver(obs)
                isWatching = false
            }
        }.collect { onEvent(it) }
    }

    fun stopWatching(contentResolver: ContentResolver) {
        observer?.let { contentResolver.unregisterContentObserver(it) }
        observer = null
        isWatching = false
    }

    val watching: Boolean get() = isWatching
}
