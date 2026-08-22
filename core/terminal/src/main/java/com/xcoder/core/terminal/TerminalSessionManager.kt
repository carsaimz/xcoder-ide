@file:Suppress("TooManyFunctions")
package com.xcoder.core.terminal

import android.content.Context
import android.util.Log
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "XCoderTermSessionMgr"

/** Maximum number of simultaneous terminal sessions. Based on Termux's default. */
private const val MAX_SESSIONS = 8

/** Default number of transcript (scrollback) rows. Based on Termux's default. */
internal const val XCODER_TRANSCRIPT_ROWS = 5000

/**
 * A minimal [TerminalSessionClient] that satisfies all 16 interface methods with
 * safe no-ops and logging.
 *
 * Based on Termux's `TermuxTerminalSessionClient` bootstrap pattern. When a
 * [TerminalSession] is first created by the manager, it needs a client to handle
 * constructor callbacks (like `setTerminalShellPid`). This bootstrap client
 * provides safe implementations until a real view attaches and replaces it via
 * [TerminalSession.updateTerminalSessionClient].
 *
 * Termux calls this the "initial" client in `TermuxTerminalSessionActivityClient`.
 */
private class BootstrapTerminalSessionClient : TerminalSessionClient {

    override fun onTextChanged(changedSession: TerminalSession) {
        // No view attached yet – nothing to redraw.
    }

    override fun onTitleChanged(changedSession: TerminalSession) {
        // Title will be picked up when a view attaches.
    }

    override fun onSessionFinished(finishedSession: TerminalSession) {
        Log.i(
            TAG,
            "Bootstrap client: session '${finishedSession.mSessionName}' " +
                "finished (exit=${finishedSession.exitStatus})"
        )
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

    override fun getTerminalCursorStyle(): Int {
        return 1 // underline
    }

    override fun logError(tag: String, message: String) { Log.e(tag, message) }
    override fun logWarn(tag: String, message: String) { Log.w(tag, message) }
    override fun logInfo(tag: String, message: String) { Log.i(tag, message) }
    override fun logDebug(tag: String, message: String) { Log.d(tag, message) }
    override fun logVerbose(tag: String, message: String) { Log.v(tag, message) }
    override fun logStackTraceWithMessage(
        tag: String,
        message: String,
        e: Exception,
    ) { Log.e(tag, message, e) }

    override fun logStackTrace(tag: String, e: Exception) {
        Log.e(tag, e.message ?: "(no message)", e)
    }
}

/**
 * Listener interface for session lifecycle events.
 *
 * Based on Termux's observer pattern in `TermuxTerminalSessionActivityClient`
 * where the Activity registers callbacks for session creation, finish, and title changes.
 */
interface TerminalSessionListener {
    /** Called when a new session is created. */
    fun onSessionCreated(sessionId: String, session: TerminalSession) {}

    /** Called when a session finishes (shell exits). */
    fun onSessionFinished(sessionId: String, session: TerminalSession) {}

    /** Called when a session's title changes (e.g. from shell escape sequences). */
    fun onSessionTitleChanged(sessionId: String, title: String) {}

    /** Called when a session is removed. */
    fun onSessionRemoved(sessionId: String) {}
}

/**
 * Manages a collection of [TerminalSession] instances for the XCoder IDE.
 *
 * Based on Termux's `TermuxTerminalSessionActivityClient` (803 lines) which manages
 * multiple terminal sessions for the Termux app. Key behaviors ported:
 *
 * - **Session creation**: Creates sessions with a bootstrap [TerminalSessionClient],
 *   then the real client replaces it when a view attaches.
 * - **Session list**: Maintains an ordered list of sessions (max [MAX_SESSIONS]).
 * - **Session switching**: Returns the target session for the view to attach.
 * - **Session lifecycle**: Handles shell process start/stop/finish.
 * - **Shell detection**: Auto-detects the best available shell binary.
 * - **Environment**: Builds a proper PATH and environment for the shell.
 * - **Transcript rows**: Configurable scrollback buffer size.
 * - **Session renaming**: Allows renaming session tabs.
 * - **Working directory**: Per-session working directory support.
 *
 * The manager is a Hilt `@Singleton` and can be injected anywhere.
 *
 * Usage:
 * ```kotlin
 * val sessionId = manager.createSession(workingDir = "/home/user/project")
 * val session = manager.getSession(sessionId)!!
 * manager.removeSession(sessionId)
 * ```
 */
@Singleton
class TerminalSessionManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    // ── Internal state ─────────────────────────────────────────────────────

    /** Session storage: sessionId → TerminalSession. */
    private val sessions = ConcurrentHashMap<String, TerminalSession>()

    /** Session display names: sessionId → name. */
    private val sessionNames = ConcurrentHashMap<String, String>()

    /** Ordered list of session IDs (for tab ordering). */
    private val sessionOrder = CopyOnWriteArrayList<String>()

    /** Session listeners for lifecycle events. */
    private val listeners = CopyOnWriteArrayList<TerminalSessionListener>()

    /** Atomic counter for generating unique session IDs. */
    private val counter = AtomicInteger(0)

    /** Currently active session ID. */
    @Volatile
    private var activeSessionId: String? = null

    // ── Shared environment ─────────────────────────────────────────────────

    /**
     * Shared base environment applied to every session.
     *
     * Based on Termux's environment setup in `TermuxService.onCreate()` which
     * sets TERM, HOME, LANG, ANDROID_ROOT, ANDROID_DATA, and PATH.
     */
    val sharedEnv: Map<String, String>
        get() = _sharedEnv.toMap()

    private val _sharedEnv = mutableMapOf(
        "TERM" to "xterm-256color",
        "COLORTERM" to "truecolor",
        "HOME" to (System.getenv("HOME") ?: context.filesDir.absolutePath),
        "LANG" to "en_US.UTF-8",
        "ANDROID_ROOT" to (System.getenv("ANDROID_ROOT") ?: "/system"),
        "ANDROID_DATA" to (System.getenv("ANDROID_DATA") ?: "/data"),
        "ANDROID_ASSETS" to context.filesDir.absolutePath,
        "PATH" to buildSystemPath(),
        "XCODER" to "1",
    )

    /** Default working directory for new sessions. */
    var defaultWorkingDir: String = context.filesDir.absolutePath

    /** Default shell binary. Auto-detected on first access. */
    val defaultShell: String
        get() = _defaultShell ?: detectShell().also { _defaultShell = it }
    private var _defaultShell: String? = null

    /** Number of transcript (scrollback) rows for new sessions. */
    var transcriptRows: Int = XCODER_TRANSCRIPT_ROWS

    // ── Public API ──────────────────────────────────────────────────────────

    /** All currently tracked session IDs in order. */
    val sessionIds: List<String> get() = sessionOrder.toList()

    /** Number of currently active sessions. */
    val sessionCount: Int get() = sessions.size

    /** The currently active session, or null. */
    val activeSession: TerminalSession?
        get() = activeSessionId?.let { sessions[it] }

    /** The currently active session ID, or null. */
    val activeSessionIdValue: String? get() = activeSessionId

    /** Whether the maximum number of sessions has been reached. */
    val isAtMaxSessions: Boolean get() = sessions.size >= MAX_SESSIONS

    /**
     * Add a listener for session lifecycle events.
     */
    fun addListener(listener: TerminalSessionListener) {
        listeners.add(listener)
    }

    /**
     * Remove a session lifecycle listener.
     */
    fun removeListener(listener: TerminalSessionListener) {
        listeners.remove(listener)
    }

    /**
     * Create a new [TerminalSession] and return its unique ID.
     *
     * Based on Termux's `TermuxTerminalSessionActivityClient.addNewSession()`:
     * - Creates a TerminalSession with the bootstrap client
     * - Sets the initial working directory and shell
     * - Adds the session to the ordered list
     * - Notifies listeners
     *
     * The shell process will not start until a [com.termux.view.TerminalView]
     * attaches the session (which calls `updateSize` → `initializeEmulator`).
     *
     * @param workingDir  Initial working directory for the shell.
     * @param shell       Path to the shell binary. Defaults to [defaultShell].
     * @param name        Human-readable name for the session tab. Auto-generated if null.
     * @param extraEnv    Additional environment variables merged on top of [sharedEnv].
     * @return The unique session ID, or null if max sessions reached.
     */
    fun createSession(
        workingDir: String = defaultWorkingDir,
        shell: String = defaultShell,
        name: String? = null,
        extraEnv: Map<String, String> = emptyMap(),
    ): String? {
        // Enforce max sessions
        if (sessions.size >= MAX_SESSIONS) {
            Log.w(TAG, "Maximum session limit ($MAX_SESSIONS) reached")
            return null
        }

        val id = "term_${counter.incrementAndGet()}"
        val displayName = name ?: "Terminal ${counter.get()}"

        val envArray = buildEnvArray(extraEnv)
        val args = buildShellArgs(shell)

        val session = TerminalSession(
            /* shellPath       = */ shell,
            /* cwd             = */ workingDir,
            /* args            = */ args,
            /* env             = */ envArray,
            /* transcriptRows  = */ transcriptRows,
            /* client          = */ BootstrapTerminalSessionClient(),
        )
        session.mSessionName = displayName

        sessions[id] = session
        sessionNames[id] = displayName
        sessionOrder.add(id)
        activeSessionId = id

        Log.d(
            TAG,
            "Created session '$id' ($displayName): shell=$shell cwd=$workingDir"
        )

        // Notify listeners
        listeners.forEach { it.onSessionCreated(id, session) }

        return id
    }

    /**
     * Retrieve a session by its ID, or null.
     */
    fun getSession(id: String): TerminalSession? = sessions[id]

    /**
     * Get the display name of a session.
     */
    fun getSessionName(id: String): String = sessionNames[id] ?: id

    /**
     * Rename a session.
     *
     * Termux allows renaming sessions from a long-press context menu.
     */
    fun renameSession(id: String, name: String) {
        val session = sessions[id] ?: return
        session.mSessionName = name
        sessionNames[id] = name
    }

    /**
     * Switch to a session by ID.
     *
     * Returns the target [TerminalSession] so the caller can pass it to
     * the TerminalView composable or call `attachSession` directly.
     *
     * Based on Termux's `switchToSession()` which updates the stored index
     * and notifies the view to swap the displayed session.
     */
    fun switchToSession(id: String): TerminalSession? {
        val session = sessions[id] ?: return null
        activeSessionId = id
        Log.d(TAG, "Switched to session '$id' (${session.mSessionName})")
        return session
    }

    /**
     * Switch to the next session in the list (wraps around).
     *
     * Termux supports session switching via swipe gestures.
     */
    fun switchToNextSession(): TerminalSession? {
        val currentIdx = sessionOrder.indexOf(activeSessionId)
        val nextIdx = if (currentIdx < 0) 0 else (currentIdx + 1) % sessionOrder.size
        return switchToSession(sessionOrder[nextIdx])
    }

    /**
     * Switch to the previous session in the list (wraps around).
     */
    fun switchToPreviousSession(): TerminalSession? {
        val currentIdx = sessionOrder.indexOf(activeSessionId)
        val prevIdx = if (currentIdx <= 0) sessionOrder.lastIndex else currentIdx - 1
        return switchToSession(sessionOrder[prevIdx])
    }

    /**
     * Remove and finish a session.
     *
     * Sends SIGKILL if still running, then removes it from tracking.
     * Based on Termux's `removeTerminalSession()` which also handles
     * the case where the removed session was the active one.
     *
     * If this was the active session, automatically switches to the
     * nearest neighbor session.
     */
    fun removeSession(id: String) {
        val session = sessions.remove(id) ?: return
        sessionNames.remove(id)
        sessionOrder.remove(id)
        session.finishIfRunning()

        // If we removed the active session, switch to a neighbor
        if (activeSessionId == id) {
            activeSessionId = sessionOrder.firstOrNull()
        }

        Log.d(TAG, "Removed session '$id' (${session.mSessionName})")
        listeners.forEach { it.onSessionRemoved(id) }
    }

    /**
     * Remove and finish **all** sessions.
     */
    fun removeAllSessions() {
        sessions.values.forEach { it.finishIfRunning() }
        sessions.clear()
        sessionNames.clear()
        sessionOrder.clear()
        activeSessionId = null
        Log.d(TAG, "All sessions removed")
    }

    /**
     * Set or update a shared environment variable that will be included in
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
     * Kill all sessions and recreate a fresh default session.
     *
     * Termux provides a "Reset terminal" option that kills all sessions
     * and starts fresh.
     */
    fun resetAllSessions(workingDir: String? = null): String? {
        removeAllSessions()
        return createSession(workingDir = workingDir ?: defaultWorkingDir)
    }

    // ── Internal helpers ────────────────────────────────────────────────────

    /**
     * Build the full environment array for a new session.
     *
     * Termux builds the environment in `TermuxService.setupShellProcessEnv()`.
     */
    private fun buildEnvArray(extraEnv: Map<String, String>): Array<String> {
        return buildList {
            _sharedEnv.forEach { (k, v) -> add("$k=$v") }
            extraEnv.forEach { (k, v) -> add("$k=$v") }
            if (!contains("PATH=")) {
                add("PATH=${buildSystemPath()}")
            }
        }.toTypedArray()
    }

    /**
     * Build shell arguments. Login shells get `-l` for a proper profile load.
     *
     * Termux always launches shells as login shells to ensure `.profile`
     * and `.bashrc`/`.zshrc` are sourced properly.
     */
    private fun buildShellArgs(shell: String): Array<String> {
        return when {
            shell.endsWith("bash") || shell.endsWith("/bash") -> arrayOf(shell, "-l")
            shell.endsWith("zsh") || shell.endsWith("/zsh") -> arrayOf(shell, "-l")
            shell.endsWith("sh") || shell.endsWith("/sh") -> arrayOf(shell, "-l")
            shell.endsWith("fish") || shell.endsWith("/fish") -> arrayOf(shell, "-l")
            else -> arrayOf(shell)
        }
    }

    /**
     * Build a sensible PATH that includes common Android shell locations.
     *
     * Termux builds PATH from system paths + `$PREFIX/bin` + user PATH.
     * We do the same for the XCoder IDE context.
     */
    private fun buildSystemPath(): String {
        val existing = System.getenv("PATH") ?: ""
        val extraDirs = listOf(
            "/system/bin",
            "/system/xbin",
            "/vendor/bin",
            "/data/data/com.xcoder.ide/files/bin",
            "/data/data/com.xcoder.ide/files/usr/bin",
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
     *
     * Termux installs bash, zsh, and fish as optional packages and
     * falls back to `/system/bin/sh` if none are available.
     */
    private fun detectShell(): String {
        val candidates = listOf(
            "/data/data/com.xcoder.ide/files/usr/bin/bash",
            "/data/data/com.xcoder.ide/files/usr/bin/zsh",
            "/data/data/com.xcoder.ide/files/usr/bin/fish",
            "/system/bin/sh",
            "/system/bin/bash",
        )
        val found = candidates.firstOrNull { File(it).exists() }
        Log.d(TAG, "Detected shell: $found")
        return found ?: "/system/bin/sh"
    }
}
