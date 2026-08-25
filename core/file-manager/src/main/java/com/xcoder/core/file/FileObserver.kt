package com.xcoder.core.file

import android.content.ContentResolver
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileObserver @Inject constructor() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val observedUris = ConcurrentHashMap<Uri, ContentObserver>()
    private val _events = MutableStateFlow<FileChangeEvent?>(null)
    val events: StateFlow<FileChangeEvent?> = _events.asStateFlow()

    private val listeners = CopyOnWriteArrayList<(FileChangeEvent) -> Unit>()

    fun addListener(listener: (FileChangeEvent) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (FileChangeEvent) -> Unit) {
        listeners.remove(listener)
    }

    fun registerContentObserver(
        contentResolver: ContentResolver,
        uri: Uri,
        notifyForDescendants: Boolean = true
    ) {
        if (observedUris.containsKey(uri)) return
        val handler = Handler(Looper.getMainLooper())
        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, changeUri: Uri?) {
                val eventUri = changeUri ?: uri
                val event = FileChangeEvent(
                    type = FileChangeType.MODIFIED,
                    uri = eventUri,
                    name = eventUri.lastPathSegment ?: "unknown",
                    timestamp = System.currentTimeMillis()
                )
                _events.value = event
                listeners.forEach { listener ->
                    try { listener(event) } catch (_: Exception) {}
                }
            }
        }
        observedUris[uri] = observer
        contentResolver.registerContentObserver(uri, notifyForDescendants, observer)
    }

    fun unregisterContentObserver(contentResolver: ContentResolver, uri: Uri) {
        observedUris.remove(uri)?.let { observer ->
            contentResolver.unregisterContentObserver(observer)
        }
    }

    fun unregisterAll(contentResolver: ContentResolver) {
        observedUris.entries.forEach { (_, observer) ->
            try { contentResolver.unregisterContentObserver(observer) } catch (_: Exception) {}
        }
        observedUris.clear()
    }

    fun registerFileObserver(
        path: String,
        mask: Int = android.os.FileObserver.MODIFY or
                android.os.FileObserver.CREATE or
                android.os.FileObserver.DELETE or
                android.os.FileObserver.MOVED_FROM or
                android.os.FileObserver.MOVED_TO,
        onChange: (FileChangeEvent) -> Unit
    ): android.os.FileObserver? {
        val file = File(path)
        if (!file.exists()) return null
        val observer = object : android.os.FileObserver(path, mask) {
            override fun onEvent(event: Int, path: String?) {
                val fullPath = if (path != null) "${file.absolutePath}/$path" else file.absolutePath
                val uri = Uri.fromFile(File(fullPath))
                val eventType = when (event and android.os.FileObserver.ALL_EVENTS) {
                    android.os.FileObserver.CREATE -> FileChangeType.CREATED
                    android.os.FileObserver.DELETE -> FileChangeType.DELETED
                    android.os.FileObserver.MOVED_FROM, android.os.FileObserver.MOVED_TO -> FileChangeType.RENAMED
                    else -> FileChangeType.MODIFIED
                }
                val changeEvent = FileChangeEvent(
                    type = eventType,
                    uri = uri,
                    name = path ?: file.name,
                    timestamp = System.currentTimeMillis()
                )
                _events.value = changeEvent
                listeners.forEach { listener ->
                    try { listener(changeEvent) } catch (_: Exception) {}
                }
                scope.launch(Dispatchers.Main) { onChange(changeEvent) }
            }
        }
        observer.startWatching()
        return observer
    }

    fun clearEvent() {
        _events.value = null
    }

    val isObserving: Boolean get() = observedUris.isNotEmpty()
    val observedUriCount: Int get() = observedUris.size

    fun getObservedUris(): Set<Uri> = observedUris.keys.toSet()
}
