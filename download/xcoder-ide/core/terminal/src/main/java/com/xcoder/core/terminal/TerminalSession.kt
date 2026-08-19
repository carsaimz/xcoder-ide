package com.xcoder.core.terminal

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TerminalSession @Inject constructor() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var ptyFd: Long = -1
    private var pid: Int = -1
    private var readJob: Job? = null
    private var isSessionActive = false

    private val _output = MutableStateFlow("")
    val output: StateFlow<String> = _output.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _exitCode = MutableStateFlow<Int?>(null)
    val exitCode: StateFlow<Int?> = _exitCode.asStateFlow()

    private val _currentDirectory = MutableStateFlow("/data/data/com.xcoder.ide/files")
    val currentDirectory: StateFlow<String> = _currentDirectory.asStateFlow()

    private val _title = MutableStateFlow("xsh")
    val title: StateFlow<String> = _title.asStateFlow()

    private val environmentVariables = ConcurrentHashMap<String, String>(System.getenv())
    private val sessionListeners = CopyOnWriteArrayList<(String) -> Unit>()

    init {
        environmentVariables["TERM"] = "xterm-256color"
        environmentVariables["HOME"] = System.getProperty("user.home", "/data/data/com.xcoder.ide/files")
        environmentVariables["LANG"] = "en_US.UTF-8"
        environmentVariables["PATH"] = System.getenv("PATH") ?: "/system/bin:/vendor/bin"
        environmentVariables["SHELL"] = "/system/bin/sh"
        environmentVariables["XCODER"] = "1"
    }

    fun addOutputListener(listener: (String) -> Unit) {
        sessionListeners.add(listener)
    }

    fun removeOutputListener(listener: (String) -> Unit) {
        sessionListeners.remove(listener)
    }

    fun setEnvironmentVariable(key: String, value: String) {
        environmentVariables[key] = value
    }

    fun getEnvironmentVariable(key: String): String? = environmentVariables[key]

    fun getAllEnvironmentVariables(): Map<String, String> = environmentVariables.toMap()

    fun removeEnvironmentVariable(key: String) {
        environmentVariables.remove(key)
    }

    suspend fun startSession(shellPath: String? = null, args: Array<String> = emptyArray()): Boolean {
        return withContext(Dispatchers.IO) {
            if (isSessionActive) {
                closeSession()
            }
            val shell = shellPath ?: "/system/bin/sh"
            val envArray = environmentVariables.map { "${it.key}=${it.value}" }.toTypedArray()
            val fd = nativeCreatePty(shell, args + arrayOf("-l"), envArray, _currentDirectory.value, 80, 24)
            if (fd >= 0) {
                ptyFd = fd
                isSessionActive = true
                _isRunning.value = true
                _exitCode.value = null
                startReading()
                Log.d(TAG, "Terminal session started with fd=$fd")
                true
            } else {
                Log.e(TAG, "Failed to create PTY session")
                false
            }
        }
    }

    private fun startReading() {
        readJob?.cancel()
        readJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(4096)
            while (isActive && isSessionActive) {
                val bytesRead = nativeReadPty(ptyFd, buffer, buffer.size)
                if (bytesRead > 0) {
                    val chunk = String(buffer, 0, bytesRead, Charsets.UTF_8)
                    _output.value = _output.value + chunk
                    sessionListeners.forEach { listener ->
                        try { listener(chunk) } catch (_: Exception) {}
                    }
                } else if (bytesRead < 0) {
                    val exit = nativeGetExitStatus(ptyFd)
                    _exitCode.value = exit
                    _isRunning.value = false
                    isSessionActive = false
                    _output.value = _output.value + "\r\n[Process exited with code $exit]\r\n"
                    break
                } else {
                    delay(1)
                }
            }
        }
    }

    fun writeToPty(data: String): Boolean {
        if (!isSessionActive || ptyFd < 0) return false
        return try {
            val bytes = data.toByteArray(Charsets.UTF_8)
            val written = nativeWritePty(ptyFd, bytes, bytes.size)
            written > 0
        } catch (e: Exception) {
            Log.e(TAG, "Write to PTY failed", e)
            false
        }
    }

    fun writeBytes(data: ByteArray): Boolean {
        if (!isSessionActive || ptyFd < 0) return false
        return try {
            nativeWritePty(ptyFd, data, data.size) > 0
        } catch (e: Exception) {
            Log.e(TAG, "Write bytes to PTY failed", e)
            false
        }
    }

    suspend fun resize(cols: Int, rows: Int): Boolean {
        return withContext(Dispatchers.IO) {
            if (!isSessionActive || ptyFd < 0) return@withContext false
            try {
                nativeResizePty(ptyFd, cols, rows)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Resize PTY failed", e)
                false
            }
        }
    }

    suspend fun closeSession() {
        withContext(Dispatchers.IO) {
            readJob?.cancel()
            readJob = null
            if (ptyFd >= 0) {
                nativeClosePty(ptyFd)
                ptyFd = -1
            }
            isSessionActive = false
            _isRunning.value = false
        }
    }

    fun clearOutput() {
        _output.value = ""
    }

    fun setTitle(newTitle: String) {
        _title.value = newTitle
    }

    fun updateWorkingDirectory(path: String) {
        _currentDirectory.value = path
    }

    fun sendCtrlC() {
        writeToPty("\u0003")
    }

    fun sendCtrlD() {
        writeToPty("\u0004")
    }

    fun sendCtrlZ() {
        writeToPty("\u001A")
    }

    fun sendCtrlL() {
        writeToPty("\u000C")
    }

    fun sendCtrlA() {
        writeToPty("\u0001")
    }

    fun sendCtrlE() {
        writeToPty("\u0005")
    }

    fun sendCtrlU() {
        writeToPty("\u0015")
    }

    fun sendCtrlK() {
        writeToPty("\u000B")
    }

    fun sendCtrlW() {
        writeToPty("\u0017")
    }

    fun sendArrowUp() {
        writeToPty("\u001B[A")
    }

    fun sendArrowDown() {
        writeToPty("\u001B[B")
    }

    fun sendArrowRight() {
        writeToPty("\u001B[C")
    }

    fun sendArrowLeft() {
        writeToPty("\u001B[D")
    }

    fun sendTab() {
        writeToPty("\u0009")
    }

    fun sendEnter() {
        writeToPty("\r")
    }

    fun sendBackspace() {
        writeToPty("\u007F")
    }

    fun sendSpecialKey(keyCode: Int): Boolean {
        val sequence = when (keyCode) {
            android.view.KeyEvent.KEYCODE_DPAD_UP -> "\u001B[A"
            android.view.KeyEvent.KEYCODE_DPAD_DOWN -> "\u001B[B"
            android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> "\u001B[C"
            android.view.KeyEvent.KEYCODE_DPAD_LEFT -> "\u001B[D"
            android.view.KeyEvent.KEYCODE_TAB -> "\u0009"
            android.view.KeyEvent.KEYCODE_ENTER -> "\r"
            android.view.KeyEvent.KEYCODE_DEL -> "\u007F"
            android.view.KeyEvent.KEYCODE_F1 -> "\u001BOP"
            android.view.KeyEvent.KEYCODE_F2 -> "\u001BOQ"
            android.view.KeyEvent.KEYCODE_F3 -> "\u001BOR"
            android.view.KeyEvent.KEYCODE_F4 -> "\u001BOS"
            android.view.KeyEvent.KEYCODE_F5 -> "\u001B[15~"
            android.view.KeyEvent.KEYCODE_F6 -> "\u001B[17~"
            android.view.KeyEvent.KEYCODE_F7 -> "\u001B[18~"
            android.view.KeyEvent.KEYCODE_F8 -> "\u001B[19~"
            android.view.KeyEvent.KEYCODE_F9 -> "\u001B[20~"
            android.view.KeyEvent.KEYCODE_F10 -> "\u001B[21~"
            android.view.KeyEvent.KEYCODE_PAGE_UP -> "\u001B[5~"
            android.view.KeyEvent.KEYCODE_PAGE_DOWN -> "\u001B[6~"
            android.view.KeyEvent.KEYCODE_MOVE_HOME -> "\u001B[H"
            android.view.KeyEvent.KEYCODE_MOVE_END -> "\u001B[F"
            android.view.KeyEvent.KEYCODE_INSERT -> "\u001B[2~"
            else -> return false
        }
        return writeToPty(sequence)
    }

    fun getActiveSessionInfo(): SessionInfo {
        return SessionInfo(
            fd = ptyFd,
            pid = pid,
            isRunning = isSessionActive,
            workingDirectory = _currentDirectory.value,
            title = _title.value,
            exitCode = _exitCode.value
        )
    }

    companion object {
        private const val TAG = "TerminalSession"

        init {
            System.loadLibrary("xcoder_terminal")
        }
    }

    @JvmExternal
    private external fun nativeCreatePty(
        shellPath: String,
        args: Array<String>,
        env: Array<String>,
        cwd: String,
        cols: Int,
        rows: Int
    ): Long

    @JvmExternal
    private external fun nativeWritePty(fd: Long, data: ByteArray, len: Int): Int

    @JvmExternal
    private external fun nativeReadPty(fd: Long, buffer: ByteArray, bufferSize: Int): Int

    @JvmExternal
    private external fun nativeResizePty(fd: Long, cols: Int, rows: Int)

    @JvmExternal
    private external fun nativeClosePty(fd: Long)

    @JvmExternal
    private external fun nativeGetExitStatus(fd: Long): Int
}

data class SessionInfo(
    val fd: Long,
    val pid: Int,
    val isRunning: Boolean,
    val workingDirectory: String,
    val title: String,
    val exitCode: Int?
)