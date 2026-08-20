package com.xcoder.core.terminal

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages multiple terminal sessions using Termux terminal-emulator.
 *
 * Features:
 * - Create/destroy sessions
 * - Switch between sessions (tabs)
 * - Session naming
 * - Auto-restore on configuration change
 * - Shared environment variables
 */
@Singleton
class TerminalSessionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val sessions = ConcurrentHashMap<String, XCoderTerminalSession>()
    private val sessionNames = ConcurrentHashMap<String, String>()
    private var sessionCounter = 0

    /** Shared environment for all sessions. */
    val sharedEnv = mutableMapOf(
        "TERM" to "xterm-256color",
        "HOME" to (System.getenv("HOME") ?: "/data/data/com.xcoder.ide/files"),
        "LANG" to "en_US.UTF-8",
        "ANDROID_ROOT" to (System.getenv("ANDROID_ROOT") ?: "/system"),
        "ANDROID_DATA" to (System.getenv("ANDROID_DATA") ?: "/data"),
        "ANDROID_ASSETS" to (System.getenv("ANDROID_ASSETS") ?: context.filesDir.absolutePath)
    )

    /** Default working directory. */
    var defaultWorkingDir: String = context.filesDir.absolutePath

    /** Default shell command. */
    var defaultShell: String = findShell()

    /** All active session IDs. */
    val activeSessionIds: List<String> get() = sessions.keys.toList()

    /** Create a new terminal session. */
    fun createSession(
        workingDir: String = defaultWorkingDir,
        shell: String = defaultShell,
        name: String? = null
    ): String {
        val id = "session_${++sessionCounter}"
        val session = XCoderTerminalSession(
            context = context,
            workingDirectory = workingDir,
            shellCommand = shell,
            env = sharedEnv
        )
        sessions[id] = session
        sessionNames[id] = name ?: "Terminal $sessionCounter"
        session.start()
        return id
    }

    /** Get a session by ID. */
    fun getSession(id: String): XCoderTerminalSession? = sessions[id]

    /** Get display name for a session. */
    fun getSessionName(id: String): String = sessionNames[id] ?: id

    /** Rename a session. */
    fun renameSession(id: String, name: String) {
        sessionNames[id] = name
    }

    /** Destroy a session. */
    fun destroySession(id: String) {
        sessions[id]?.destroy()
        sessions.remove(id)
        sessionNames.remove(id)
    }

    /** Destroy all sessions. */
    fun destroyAll() {
        sessions.values.forEach { it.destroy() }
        sessions.clear()
        sessionNames.clear()
    }

    /** Write to a session. */
    fun writeToSession(id: String, text: String) {
        sessions[id]?.writeInput(text)
    }

    /** Send special key to a session. */
    fun sendKey(id: String, keyCode: Int, ctrl: Boolean = false, alt: Boolean = false) {
        sessions[id]?.sendSpecialKey(keyCode, ctrl, alt)
    }

    /** Find the best available shell. */
    private fun findShell(): String {
        val candidates = listOf(
            "/data/data/com.termux/files/usr/bin/bash",
            "/system/bin/sh",
            "/system/bin/bash"
        )
        return candidates.firstOrNull { File(it).exists() } ?: "/system/bin/sh"
    }
}