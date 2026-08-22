@file:Suppress("TooManyFunctions")
package com.xcoder.core.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Rect
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView

private const val TAG = "XCoderTermViewClient"

/** Toast duration for copy/paste feedback. */
private const val TOAST_DURATION = Toast.LENGTH_SHORT

/** Long press timeout in ms for selecting text / showing diagnostic. */
private const val LONG_PRESS_TIMEOUT_MS = 500

/** Font size step for zoom in/out. */
private const val FONT_SIZE_STEP = 2f

/** Minimum font size. */
private const val MIN_FONT_SIZE = 6f

/** Maximum font size. */
private const val MAX_FONT_SIZE = 72f

/** Default font size. */
private const val DEFAULT_FONT_SIZE = 14f

/** Scroll speed multiplier for Page Up/Down. */
private const val PAGE_SCROLL_FRACTION = 0.75f

/**
 * Implementation of [TerminalSessionClient] for XCoder IDE's terminal views.
 *
 * Based on Termux's `TermuxTerminalViewClient` (803 lines), which provides:
 * - Keyboard shortcut handling (Ctrl+C, Ctrl+D, Ctrl+Z, Ctrl+L, Ctrl+A, etc.)
 * - Extra keys toolbar integration (Tab, Esc, Arrow keys, Home, End, PgUp, PgDn)
 * - Text selection and copy/paste via clipboard
 * - Font size adjustment (pinch-to-zoom and menu)
 * - Color scheme management
 * - Session lifecycle event handling
 * - Terminal bell handling
 *
 * This client is installed on a [TerminalView] via
 * [TerminalSession.updateTerminalSessionClient] when the view attaches a session.
 *
 * @param context Android context for clipboard, toast, and preference access.
 * @param sessionManager Reference to the session manager for session operations.
 * @param extraKeysMap Extra keys configuration from [TerminalExtraKeys].
 */
class TerminalViewClient(
    private val context: Context,
    private val sessionManager: TerminalSessionManager,
    private val extraKeysMap: Map<String, String> = TerminalExtraKeys.DEFAULT_EXTRA_KEYS,
) : TerminalSessionClient {

    // ── Configuration ─────────────────────────────────────────────────────

    /** Current font size for the terminal. */
    var fontSize: Float = DEFAULT_FONT_SIZE
        private set

    /** Current color scheme name. */
    var colorScheme: String = COLOR_SCHEME_DEFAULT
        private set

    /** Whether to show the extra keys row above the keyboard. */
    var isExtraKeysVisible: Boolean = true

    /** Whether to use CTRL as a modifier key. */
    var isCtrlWorkaround: Boolean = false

    /** The terminal view this client is attached to. */
    var terminalView: TerminalView? = null
        private set

    /** Callback when the user requests to toggle extra keys visibility. */
    var onToggleExtraKeys: (() -> Unit)? = null

    /** Callback when the terminal should be scrolled. */
    var onScrollTerminal: ((deltaY: Float) -> Unit)? = null

    /** Callback when a session title changes. */
    var onSessionTitleChanged: ((title: String) -> Unit)? = null

    /** Callback for font size changes (to persist preference). */
    var onFontSizeChanged: ((newSize: Float) -> Unit)? = null

    /** Callback for color scheme changes. */
    var onColorSchemeChanged: ((newScheme: String) -> Unit)? = null

    // ── Clipboard ──────────────────────────────────────────────────────────

    private val clipboard: ClipboardManager?
        get() = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager

    // ── Public API: Attachment ─────────────────────────────────────────────

    /**
     * Attach this client to a terminal view and session.
     *
     * Based on Termux's pattern where the Activity sets up the terminal view
     * client when creating or switching sessions.
     */
    fun attach(view: TerminalView, session: TerminalSession) {
        terminalView = view
        session.updateTerminalSessionClient(this)
    }

    /**
     * Detach this client from the terminal view.
     */
    fun detach() {
        terminalView = null
    }

    // ── Public API: Font Size ──────────────────────────────────────────────

    /**
     * Increase font size by [FONT_SIZE_STEP].
     *
     * Termux supports pinch-to-zoom and +/- buttons in the toolbar.
     */
    fun zoomIn() {
        fontSize = (fontSize + FONT_SIZE_STEP).coerceAtMost(MAX_FONT_SIZE)
        applyFontSize()
        onFontSizeChanged?.invoke(fontSize)
    }

    /**
     * Decrease font size by [FONT_SIZE_STEP].
     */
    fun zoomOut() {
        fontSize = (fontSize - FONT_SIZE_STEP).coerceAtLeast(MIN_FONT_SIZE)
        applyFontSize()
        onFontSizeChanged?.invoke(fontSize)
    }

    /**
     * Reset font size to default.
     */
    fun resetFontSize() {
        fontSize = DEFAULT_FONT_SIZE
        applyFontSize()
        onFontSizeChanged?.invoke(fontSize)
    }

    /** Apply the current font size to the terminal view. */
    private fun applyFontSize() {
        terminalView?.let { view ->
            view.setTextSize(fontSize)
        }
    }

    /**
     * Set font size programmatically.
     */
    fun setFontSize(size: Float) {
        fontSize = size.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)
        applyFontSize()
    }

    // ── Public API: Color Scheme ───────────────────────────────────────────

    /**
     * Set the terminal color scheme.
     *
     * Termux supports multiple color schemes (Monokai, Solarized, etc.)
     * stored as JSON files. XCoder IDE provides a similar but simplified
     * set of built-in schemes.
     */
    fun setColorScheme(scheme: String) {
        colorScheme = scheme
        applyColorScheme()
        onColorSchemeChanged?.invoke(scheme)
    }

    /** Apply the current color scheme to the terminal view. */
    private fun applyColorScheme() {
        // TerminalView handles color scheme application
        // In a full implementation, this would load a JSON scheme file
    }

    // ── Public API: Extra Keys ─────────────────────────────────────────────

    /**
     * Process an extra key press.
     *
     * Based on Termux's `TermuxTerminalViewClient.onExtraKeyButtonPress()`.
     * The extra keys map maps button names to either:
     * - A special key code string (e.g. "TAB", "ESC", "ENTER")
     * - A literal string to type (e.g. "|")
     * - A control sequence (e.g. "CTRL C" for Ctrl+C)
     *
     * @param keyName The key name from the extra keys map.
     * @return true if the key was handled.
     */
    fun onExtraKeyButtonPress(keyName: String): Boolean {
        val value = extraKeysMap[keyName] ?: return false
        val session = sessionManager.activeSession ?: return false

        return when {
            // Control sequences: "CTRL C", "CTRL D", etc.
            value.startsWith("CTRL ") -> {
                val controlChar = value.removePrefix("CTRL ")
                sendControlKey(session, controlChar)
                true
            }
            // Special keys
            value == "TAB" -> {
                session.write("\t")
                true
            }
            value == "ESC" || value == "ESCAPE" -> {
                session.write("\u001B")
                true
            }
            value == "ENTER" || value == "RETURN" -> {
                session.write("\r")
                true
            }
            value == "SPACE" -> {
                session.write(" ")
                true
            }
            value == "BACKSPACE" -> {
                session.write("\u007F")
                true
            }
            value == "UP" -> {
                session.write("\u001B[A")
                true
            }
            value == "DOWN" -> {
                session.write("\u001B[B")
                true
            }
            value == "RIGHT" -> {
                session.write("\u001B[C")
                true
            }
            value == "LEFT" -> {
                session.write("\u001B[D")
                true
            }
            value == "HOME" -> {
                session.write("\u001B[H")
                true
            }
            value == "END" -> {
                session.write("\u001B[F")
                true
            }
            value == "PGUP" -> {
                // Page Up: scroll up by 3/4 of the viewport
                val view = terminalView ?: return true
                val scrollLines = (view.mTopRow * PAGE_SCROLL_FRACTION).toInt()
                session.write("\u001B[${scrollLines}S") // Scroll up
                true
            }
            value == "PGDN" -> {
                val view = terminalView ?: return true
                val scrollLines = (view.mTopRow * PAGE_SCROLL_FRACTION).toInt()
                session.write("\u001B[${scrollLines}T") // Scroll down
                true
            }
            value == "INSERT" -> {
                session.write("\u001B[2~")
                true
            }
            value == "DELETE" -> {
                session.write("\u001B[3~")
                true
            }
            value == "F1" through value == "F12" -> {
                val fNum = value.removePrefix("F").toIntOrNull() ?: return false
                // F1-F4: ESC O P/Q/R/S, F5: ESC [ 15 ~, F6-F12: ESC [ 17-24 ~
                val seq = when (fNum) {
                    1 -> "\u001BOP"
                    2 -> "\u001BOQ"
                    3 -> "\u001BOR"
                    4 -> "\u001BOS"
                    5 -> "\u001B[15~"
                    in 6..12 -> "\u001B[${17 + fNum - 6}~"
                    else -> return false
                }
                session.write(seq)
                true
            }
            // Toggle extra keys visibility
            value == "TOGGLE_EXTRA_KEYS" -> {
                onToggleExtraKeys?.invoke()
                true
            }
            // Literal text to type (e.g. "|", "-", "/")
            else -> {
                session.write(value)
                true
            }
        }
    }

    /**
     * Send a Ctrl+key combination to the session.
     *
     * Based on Termux's `sendControlKey()` in `TermuxTerminalViewClient`.
     */
    private fun sendControlKey(session: TerminalSession, key: String) {
        val controlChar = when (key.uppercase()) {
            "A" -> 0x01  // SOH - beginning of line
            "B" -> 0x02  // STX - break
            "C" -> 0x03  // ETX - interrupt (SIGINT)
            "D" -> 0x04  // EOT - end of input (EOF)
            "E" -> 0x05  // ENQ
            "F" -> 0x06  // ACK
            "G" -> 0x07  // BEL - bell
            "H" -> 0x08  // BS  - backspace
            "I" -> 0x09  // HT  - tab
            "J" -> 0x0A  // LF  - newline
            "K" -> 0x0B  // VT  - vertical tab
            "L" -> 0x0C  // FF  - form feed / clear screen
            "M" -> 0x0D  // CR  - carriage return
            "N" -> 0x0E  // SO
            "O" -> 0x0F  // SI
            "P" -> 0x10  // DLE
            "Q" -> 0x11  // DC1
            "R" -> 0x12  // DC2
            "S" -> 0x13  // DC3 - XOFF
            "T" -> 0x14  // DC4
            "U" -> 0x15  // NAK
            "V" -> 0x16  // SYN
            "W" -> 0x17  // ETB
            "X" -> 0x18  // CAN
            "Y" -> 0x19  // EM
            "Z" -> 0x1A  // SUB - suspend (SIGTSTP)
            "\\" -> 0x1C // FS
            "]" -> 0x1D  // GS
            "^" -> 0x1E  // RS
            "_" -> 0x1F  // US
            "SPACE" -> 0x00  // NUL
            else -> return
        }
        session.write(controlChar.toChar().toString())
    }

    // ── TerminalSessionClient implementation ───────────────────────────────

    /**
     * Called when the terminal content changes.
     *
     * Based on Termux's `TermuxTerminalViewClient.onTextChanged()` which
     * invalidates the TerminalView to trigger a redraw.
     */
    override fun onTextChanged(changedSession: TerminalSession) {
        terminalView?.onScreenUpdated()
    }

    /**
     * Called when the terminal title changes (e.g. from `\033]0;title\007`).
     *
     * Termux updates the session name and the tab title when this happens.
     */
    override fun onTitleChanged(changedSession: TerminalSession) {
        val title = changedSession.mSessionName
        onSessionTitleChanged?.invoke(title)
    }

    /**
     * Called when the session finishes (shell exits).
     *
     * Termux shows an "exit" notification and offers to close the session.
     */
    override fun onSessionFinished(finishedSession: TerminalSession) {
        Log.i(
            TAG,
            "Session '${finishedSession.mSessionName}' finished " +
                "(exit=${finishedSession.exitStatus})"
        )
        // Optionally show exit notification or auto-close
    }

    /**
     * Copy text to clipboard.
     *
     * Termux copies selected text and shows a toast confirmation.
     */
    override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
        clipboard?.setPrimaryClip(ClipData.newPlainText("terminal", text))
        Toast.makeText(context, "Copied to clipboard", TOAST_DURATION).show()
    }

    /**
     * Paste text from clipboard.
     *
     * Termux reads from the clipboard and writes the text to the session.
     */
    override fun onPasteTextFromClipboard(session: TerminalSession?) {
        val clip = clipboard?.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).text?.toString() ?: return
            session?.write(text)
        }
    }

    /**
     * Handle terminal bell.
     *
     * Termux flashes the screen or plays a short vibration.
     */
    override fun onBell(session: TerminalSession) {
        // Visual bell: flash the terminal view
        terminalView?.post {
            terminalView?.alpha = 0.7f
            terminalView?.postDelayed({ terminalView?.alpha = 1.0f }, 100)
        }
    }

    /**
     * Handle color changes from the terminal (e.g. OSC 4/10/11/12).
     */
    override fun onColorsChanged(session: TerminalSession) {
        // Color changes from the terminal are handled by the view
    }

    /**
     * Handle cursor state change (show/hide/blink).
     */
    override fun onTerminalCursorStateChange(state: Boolean) {
        // Cursor state is handled by the view
    }

    /**
     * Receive the shell PID after the session starts.
     */
    override fun setTerminalShellPid(session: TerminalSession, pid: Int) {
        Log.d(TAG, "Shell PID for '${session.mSessionName}' = $pid")
    }

    /**
     * Get the cursor style (block, underline, bar).
     *
     * Termux reads this from preferences: 0=block, 1=underline, 2=bar.
     */
    override fun getTerminalCursorStyle(): Integer {
        return 0 // block cursor (most common for terminals)
    }

    // ── Logging ────────────────────────────────────────────────────────────

    override fun logError(tag: String, message: String) = Log.e(tag, message)
    override fun logWarn(tag: String, message: String) = Log.w(tag, message)
    override fun logInfo(tag: String, message: String) = Log.i(tag, message)
    override fun logDebug(tag: String, message: String) = Log.d(tag, message)
    override fun logVerbose(tag: String, message: String) = Log.v(tag, message)
    override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) =
        Log.e(tag, message, e)

    override fun logStackTrace(tag: String, e: Exception) =
        Log.e(tag, e.message ?: "(no message)", e)

    companion object {
        /** Built-in color schemes. */
        const val COLOR_SCHEME_DEFAULT = "default"
        const val COLOR_SCHEME_MONOKAI = "monokai"
        const val COLOR_SCHEME_SOLARIZED_DARK = "solarized_dark"
        const val COLOR_SCHEME_SOLARIZED_LIGHT = "solarized_light"
        const val COLOR_SCHEME_DRACULA = "dracula"
        const val COLOR_SCHEME_NORD = "nord"
        const val COLOR_SCHEME_TANGO = "tango"

        /** All available color scheme names. */
        val AVAILABLE_COLOR_SCHEMES = listOf(
            COLOR_SCHEME_DEFAULT,
            COLOR_SCHEME_MONOKAI,
            COLOR_SCHEME_SOLARIZED_DARK,
            COLOR_SCHEME_SOLARIZED_LIGHT,
            COLOR_SCHEME_DRACULA,
            COLOR_SCHEME_NORD,
            COLOR_SCHEME_TANGO,
        )
    }
}
