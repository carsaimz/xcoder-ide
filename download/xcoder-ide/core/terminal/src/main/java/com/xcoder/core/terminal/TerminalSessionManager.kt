package com.xcoder.core.terminal

import android.content.Context
import android.util.Log
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "XCoderTermSessionMgr"

/**
 * A minimal [TerminalSessionClient] that satisfies all 16 interface methods with
 * safe no-ops and logging. It is used as the **bootstrap** client when a
 * [TerminalSession] is first created by the manager (before a real view has
 * attached). Once the view attaches, it replaces this client via
 * [TerminalSession.updateTerminalSessionClient].
 */
private class BootstrapTerminalSessionClient : TerminalSessionClient {

    override fun onTextChanged(changedSession: TerminalSession) {
        // No view attached yet – nothing to redraw.
    }

    override fun onTitleChanged(changedSession: TerminalSession) {
        // Ignored until a view picks up the session.
    }

    override fun onSessionFinished(finishedSession: TerminalSession) {
        Log.i(TAG, "Bootstrap client: session '${finishedSession.mSessionName}' finished (exit=${finishedSession.exitStatus})")
    }

    override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
        // No UI to copy to while bootstrapping.
    }

    override fun onPasteTextFromClipboard(session: TerminalSession?) {
        // No UI to paste from while bootstrapping.
    }

    override fun onBell(session: TerminalSession) {
        // No UI for visual bell while bootstrapping.
    }

    override fun onColorsChanged(session: TerminalSession) {
        // No UI to react to color changes while bootstrapping.
    }

    override fun onTerminalCursorStateChange(state: Boolean) {
        // No view to toggle cursor blinker.
    }

    override fun setTerminalShellPid(session: TerminalSession, pid: Int) {
        Log.d(TAG, "Bootstrap client: shell PID for '${session.mSessionName}' = $pid")
    }

    override fun getTerminalCursorStyle(): Integer {
        return 1 // underline
    }

    override fun logError(tag: String, message: String) = Log.e(tag, message)
    override fun logWarn(tag: String, message: String) = Log.w(tag, message)
    override fun logInfo(tag: String, message: String) = Log.i(tag, message)
    override fun logDebug(tag: String, message: String) = Log.d(tag, message)
    override fun logVerbose(tag: String, message: String) = Log.v(tag, message)
    override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) = Log.e(tag, message, e)
    override fun logStackTrace(tag: String, e: Exception) = Log.e(tag, e.message ?: "(no message)", e)
}

/**
 * Manages a collection of [TerminalSession] instances for the XCoder IDE.
 *
 * Sessions are created with a **bootstrap** [TerminalSessionClient] so that
 * the heavy `TerminalSession` constructor succeeds immediately. When a
 * [com.xcoder.core.terminal.TermuxTerminalScreen] composable attaches a
 * session to its `TerminalView`, it calls
 * [TerminalSession.updateTerminalSessionClient] to install the full
 * [XCoderTerminalSessionClient] that can trigger view redraws, clipboard
 * operations, etc.
 *
 * The manager is a Hilt `@Singleton` and can be injected anywhere.
 *
 * Usage:
 * ```kotlin
 * // Create a session
 * val sessionId = manager.createSession(workingDir = "/home/user/project")
 *
 * // Later, pass the session to the composable
 * val session = manager.getSession(sessionId)!!
 * TermuxTerminalScreen(session = session)
 *
 * // Clean up
 * manager.removeSession(sessionId)
 * ```
 */
@Singleton
class TerminalSessionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val sessions = ConcurrentHashMap<String, TerminalSession>()
    private val sessionNames = ConcurrentHashMap<String, String>()
    private val counter = AtomicInteger(0)

    /** Shared base environment applied to every session. */
    val sharedEnv: Map<String, String>
        get() = _sharedEnv.toMap()

    private val _sharedEnv = mutableMapOf(
        "TERM" to "xterm-256color",
        "HOME" to (System.getenv("HOME") ?: context.filesDir.absolutePath),
        "LANG" to "en_US.UTF-8",
        "ANDROID_ROOT" to (System.getenv("ANDROID_ROOT") ?: "/system"),
        "ANDROID_DATA" to (System.getenv("ANDROID_DATA") ?: "/data"),
        "ANDROID_ASSETS" to context.filesDir.absolutePath,
        "PATH" to buildSystemPath()
    )

    /** Default working directory for new sessions. */
    var defaultWorkingDir: String = context.filesDir.absolutePath

    /** Default shell binary. Auto-detected on first access. */
    val defaultShell: String
        get() = _defaultShell ?: detectShell().also { _defaultShell = it }
    private var _defaultShell: String? = null

    /** Number of transcript (scrollback) rows for new sessions. */
    var transcriptRows: Int = DEFAULT_TRANSCRIPT_ROWS

    // ── Public API ──────────────────────────────────────────────────────────

    /** All currently tracked session IDs. */
    val sessionIds: List<String> get() = sessions.keys.toList()

    /**
 * Create a new [TerminalSession] and return its unique ID.
 *
 * The session is created with a bootstrap client. The shell process will
 * **not** start until a [com.termux.view.TerminalView] attaches the session
 * (which calls `updateSize` → `initializeEmulator` → `JNI.createSubprocess`).
 *
 * @param workingDir  Initial working directory for the shell.
 * @param shell       Path to the shell binary. Defaults to [defaultShell].
 * @param name        Human-readable name for the session tab. Auto-generated
 *   if null.
 * @param extraEnv    Additional environment variables merged on top of
 *   [sharedEnv].
 * @return The unique session ID that can be passed to [getSession].
 */
    fun createSession(
        workingDir: String = defaultWorkingDir,
        shell: String = defaultShell,
        name: String? = null,
        extraEnv: Map<String, String> = emptyMap()
    ): String {
        val id = "term_${counter.incrementAndGet()}"
        val displayName = name ?: "Terminal ${counter.get()}"

        val envArray = buildEnvArray(extraEnv)
        val args = buildShellArgs(shell)

        val session = TerminalSession(
            /* shellPath   = */ shell,
            /* cwd         = */ workingDir,
            /* args        = */ args,
            /* env         = */ envArray,
            /* transcriptRows = */ transcriptRows,
            /* client      = */ BootstrapTerminalSessionClient()
        )
        session.mSessionName = displayName

        sessions[id] = session
        sessionNames[id] = displayName

        Log.d(TAG, "Created session '$id' ($displayName): shell=$shell cwd=$workingDir")
        return id
    }

    /**
 * Retrieve a session by its ID, or null if it does not exist.
 */
    fun getSession(id: String): TerminalSession? = sessions[id]

    /**
 * Get the display name of a session.
 */
    fun getSessionName(id: String): String = sessionNames[id] ?: id

    /**
 * Rename a session.
 */
    fun renameSession(id: String, name: String) {
        val session = sessions[id] ?: return
        session.mSessionName = name
        sessionNames[id] = name
    }

    /**
 * Remove and finish a session. Sends SIGKILL if still running, then
 * removes it from the manager's internal tracking.
 */
    fun removeSession(id: String) {
        val session = sessions.remove(id) ?: return
        sessionNames.remove(id)
        session.finishIfRunning()
        Log.d(TAG, "Removed session '$id' (${session.mSessionName})")
    }

    /**
 * Remove and finish **all** sessions.
 */
    fun removeAllSessions() {
        sessions.values.forEach { it.finishIfRunning() }
        sessions.clear()
        sessionNames.clear()
        Log.d(TAG, "All sessions removed")
    }

    /**
 * Add or update a shared environment variable that will be included in
 * every **future** session created by [createSession].
 */
    fun setSharedEnvVar(key: String, value: String) {
        _sharedEnv[key] = value
    }

    /**
 * Remove a shared environment variable.
 */
    fun removeSharedEnvVar(key: String) {
        _sharedEnv.remove(key)
    }

    /**
 * Switch the session displayed in a [com.termux.view.TerminalView].
 *
 * This simply returns the target [TerminalSession] so the caller can
 * pass it to [com.xcoder.core.terminal.TermuxTerminalScreen] or call
 * [com.termux.view.TerminalView.attachSession] directly.
 *
 * @return The [TerminalSession] for the given ID, or null.
 */
    fun switchToSession(id: String): TerminalSession? = sessions[id]

    // ── Internal helpers ────────────────────────────────────────────────────

    /**
 * Build the full environment array for a new session.
 */
    private fun buildEnvArray(extraEnv: Map<String, String>): Array<String> {
        return buildList {
            // Shared env first (so extraEnv can override)
            _sharedEnv.forEach { (k, v) -> add("$k=$v") }
            // Caller overrides
            extraEnv.forEach { (k, v) -> add("$k=$v") }
            // Ensure PATH always includes the system paths
            if (!contains("PATH=")) {
                add("PATH=${buildSystemPath()}")
            }
        }.toTypedArray()
    }

    /**
 * Build shell arguments. Login shells get `-l` for a proper profile load.
 */
    private fun buildShellArgs(shell: String): Array<String> {
        return when {
            shell.endsWith("bash") || shell.endsWith("/bash") -> arrayOf(shell, "-l")
            shell.endsWith("zsh")  || shell.endsWith("/zsh")  -> arrayOf(shell, "-l")
            shell.endsWith("sh")   || shell.endsWith("/sh")   -> arrayOf(shell, "-l")
            shell.endsWith("fish") || shell.endsWith("/fish") -> arrayOf(shell, "-l")
            else -> arrayOf(shell)
        }
    }

    /**
 * Build a sensible PATH that includes common Android shell locations.
 */
    private fun buildSystemPath(): String {
        val existing = System.getenv("PATH") ?: ""
        val extraDirs = listOf(
            "/system/bin",
            "/system/xbin",
            "/vendor/bin",
            "/data/data/com.xcoder.ide/files/bin",
            "/data/data/com.xcoder.ide/files/usr/bin"
        ).filter { File(it).exists() || it.startsWith("/system") }

        val combined = if (existing.isNotBlank()) {
            "$existing:${extraDirs.joinToString(":")}"
        } else {
            extraDirs.joinToString(":")
        }
        return combined.removeSuffix(":")
    }

    /**
 * Auto-detect the best available shell binary.
 */
    private fun detectShell(): String {
        val candidates = listOf(
            "/data/data/com.xcoder.ide/files/usr/bin/bash",
            "/data/data/com.xcoder.ide/files/usr/bin/zsh",
            "/data/data/com.xcoder.ide/files/usr/bin/fish",
            "/system/bin/sh",
            "/system/bin/bash"
        )
        return candidates.firstOrNull { File(it).exists() } ?: "/system/bin/sh"
    }
}
