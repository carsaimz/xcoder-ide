package com.xcoder.core.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient

private const val LOG_TAG = "XCoderTerminal"

const val DEFAULT_TERMINAL_FONT_SIZE = 24
const val MIN_TERMINAL_FONT_SIZE = 6
const val MAX_TERMINAL_FONT_SIZE = 128
const val DEFAULT_TRANSCRIPT_ROWS = 1000

// ═══════════════════════════════════════════════════════════════════════════
// Mutable state shared between Compose recomposition and View callbacks
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Holds mutable terminal interaction state that is observable by both
 * Compose (for UI recomposition) and the [TerminalViewClient]/[TerminalSessionClient]
 * callbacks (which run on the main thread but outside Compose).
 */
@Stable
class TerminalInteractionState {
    /** Whether the Ctrl modifier toggle is active. */
    var controlKey: Boolean by mutableStateOf(false)

    /** Whether the Alt modifier toggle is active. */
    var altKey: Boolean by mutableStateOf(false)

    /** Whether the Shift modifier toggle is active. */
    var shiftKey: Boolean by mutableStateOf(false)

    /** Whether the Fn modifier toggle is active. */
    var fnKey: Boolean by mutableStateOf(false)

    /** Current terminal font size in dp. */
    var fontSize: Int by mutableIntStateOf(DEFAULT_TERMINAL_FONT_SIZE)

    /** Current terminal title (set via escape sequences). */
    var title: String by mutableStateOf("Terminal")

    /** Whether the terminal is in text-selection / copy mode. */
    var inCopyMode: Boolean by mutableStateOf(false)

    /** Whether the TerminalView currently has input focus. */
    var isViewSelected: Boolean by mutableStateOf(true)

    /** Whether the shell process has exited. */
    var sessionFinished: Boolean by mutableStateOf(false)

    /** Exit code of the finished shell (valid only when [sessionFinished] is true). */
    var exitCode: Int by mutableIntStateOf(0)

    /** Reset modifier toggles back to released state. */
    fun resetModifiers() {
        controlKey = false
        altKey = false
        shiftKey = false
        fnKey = false
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// TerminalSessionClient – bridges Termux session callbacks to our state
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Full implementation of [TerminalSessionClient] for the XCoder IDE terminal.
 *
 * All 16 interface methods are implemented. Logging delegates to [android.util.Log].
 * Screen updates are forwarded to the attached [TerminalView] via
 * [TerminalView.onScreenUpdated] on the main thread.
 */
class XCoderTerminalSessionClient(
    private val context: Context,
    private val terminalViewProvider: () -> TerminalView?,
    private val interactionState: TerminalInteractionState,
    private val onSessionExit: ((Int) -> Unit)?
) : TerminalSessionClient {

    // ── Core callbacks ─────────────────────────────────────────────────────

    override fun onTextChanged(changedSession: TerminalSession) {
        // Called on the main thread by TerminalSession's MainThreadHandler.
        val view = terminalViewProvider()
        view?.onScreenUpdated()
    }

    override fun onTitleChanged(changedSession: TerminalSession) {
        val newTitle = changedSession.title
        if (!newTitle.isNullOrEmpty()) {
            interactionState.title = newTitle
        }
    }

    override fun onSessionFinished(finishedSession: TerminalSession) {
        val status = finishedSession.exitStatus
        interactionState.sessionFinished = true
        interactionState.exitCode = status
        Log.i(LOG_TAG, "Session '${finishedSession.mSessionName}' finished with exit status $status")
        onSessionExit?.invoke(status)
    }

    override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("terminal", text))
    }

    override fun onPasteTextFromClipboard(session: TerminalSession?) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val clip = clipboard.primaryClip ?: return
        if (clip.itemCount > 0) {
            val text = clip.getItemAt(0).coerceToText(context).toString()
            if (text.isNotEmpty() && session != null) {
                session.write(text)
            }
        }
    }

    override fun onBell(session: TerminalSession) {
        Log.d(LOG_TAG, "Bell received from session '${session.mSessionName}'")
    }

    override fun onColorsChanged(session: TerminalSession) {
        Log.d(LOG_TAG, "Colors changed in session '${session.mSessionName}'")
    }

    override fun onTerminalCursorStateChange(state: Boolean) {
        // Cursor visibility toggled by the terminal via escape sequence.
        // The TerminalView handles rendering; no action needed here.
    }

    override fun setTerminalShellPid(session: TerminalSession, pid: Int) {
        Log.d(LOG_TAG, "Shell PID for '${session.mSessionName}': $pid")
    }

    override fun getTerminalCursorStyle(): Integer {
        // 0 = block, 1 = underline, 2 = bar (vertical line)
        return 1
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
}

// ═══════════════════════════════════════════════════════════════════════════
// TerminalViewClient – bridges Termux view callbacks to our state
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Full implementation of [TerminalViewClient] for the XCoder IDE terminal.
 *
 * All 20 interface methods are implemented. Modifier key state is read from
 * [TerminalInteractionState] so that the Compose extra-keys row can toggle them.
 * Pinch-to-zoom adjusts the font size in real time.
 */
class XCoderTerminalViewClient(
    private val context: Context,
    private val terminalViewProvider: () -> TerminalView?,
    private val interactionState: TerminalInteractionState
) : TerminalViewClient {

    // ── Scale / pinch-zoom ────────────────────────────────────────────────

    override fun onScale(scale: Float): Float {
        // Ignore extreme scale events (e.g. multi-finger gesture start)
        if (scale < 0.9f || scale > 1.1f) {
            return 1.0f
        }
        val current = interactionState.fontSize
        val newSize = (current * scale).toInt().coerceIn(MIN_TERMINAL_FONT_SIZE, MAX_TERMINAL_FONT_SIZE)
        if (newSize != current) {
            interactionState.fontSize = newSize
            terminalViewProvider()?.setTextSize(newSize)
        }
        return scale
    }

    // ── Touch ─────────────────────────────────────────────────────────────

    override fun onSingleTapUp(e: MotionEvent) {
        val view = terminalViewProvider() ?: return
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    override fun onLongPress(event: MotionEvent): Boolean {
        // Return false to let TerminalView handle long-press text selection.
        return false
    }

    // ── Configuration queries ─────────────────────────────────────────────

    override fun shouldBackButtonBeMappedToEscape(): Boolean = true

    override fun shouldEnforceCharBasedInput(): Boolean = true

    override fun shouldUseCtrlSpaceWorkaround(): Boolean = true

    override fun isTerminalViewSelected(): Boolean = interactionState.isViewSelected

    // ── Copy mode ─────────────────────────────────────────────────────────

    override fun copyModeChanged(copyMode: Boolean) {
        interactionState.inCopyMode = copyMode
    }

    // ── Key events ────────────────────────────────────────────────────────

    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean {
        // Return false to let TerminalView apply its own key handling.
        return false
    }

    override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean {
        return false
    }

    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean {
        // Return false to let TerminalView handle code point input.
        return false
    }

    override fun onEmulatorSet() {
        Log.d(LOG_TAG, "TerminalEmulator attached to TerminalView")
    }

    // ── Modifier key reads (called by TerminalView during key processing) ──

    override fun readControlKey(): Boolean = interactionState.controlKey

    override fun readAltKey(): Boolean = interactionState.altKey

    override fun readShiftKey(): Boolean = interactionState.shiftKey

    override fun readFnKey(): Boolean = interactionState.fnKey

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
}

// ═══════════════════════════════════════════════════════════════════════════
// Extra-keys row (Compose) – Ctrl / Alt / Shift / Tab / Esc / Arrows etc.
// ═══════════════════════════════════════════════════════════════════════════

/** A single key in the extra-keys row. */
private enum class ExtraKeyType {
    /** Toggle key that stays active until tapped again (Ctrl, Alt, Shift). */
    TOGGLE,
    /** Key that sends a character or escape sequence immediately. */
    ACTION
}

/** Descriptor for one button in the extra-keys row. */
private data class ExtraKey(
    val label: String,
    val type: ExtraKeyType,
    /** For [ExtraKeyType.TOGGLE]: the state field name in [TerminalInteractionState].
     *  For [ExtraKeyType.ACTION]: the string to write to the session. */
    val payload: String
)

private val EXTRA_KEYS = listOf(
    ExtraKey("ESC", ExtraKeyType.ACTION, "\u001b"),
    ExtraKey("TAB", ExtraKeyType.ACTION, "\t"),
    ExtraKey("CTRL", ExtraKeyType.TOGGLE, "controlKey"),
    ExtraKey("ALT", ExtraKeyType.TOGGLE, "altKey"),
    ExtraKey("SHIFT", ExtraKeyType.TOGGLE, "shiftKey"),
    ExtraKey("-", ExtraKeyType.ACTION, "-"),
    ExtraKey("/", ExtraKeyType.ACTION, "/"),
    ExtraKey("|", ExtraKeyType.ACTION, "|"),
    ExtraKey("←", ExtraKeyType.ACTION, "\u001b[D"),
    ExtraKey("→", ExtraKeyType.ACTION, "\u001b[C"),
    ExtraKey("↑", ExtraKeyType.ACTION, "\u001b[A"),
    ExtraKey("↓", ExtraKeyType.ACTION, "\u001b[B"),
)

/**
 * Horizontal row of extra keys below the terminal, modelled after Termux's
 * extra-keys view.
 *
 * Toggle keys (CTRL, ALT, SHIFT) stay highlighted while active and modify
 * the next IME key press. Action keys send their character or escape
 * sequence immediately to the [session].
 */
@Composable
private fun TerminalExtraKeysRow(
    interactionState: TerminalInteractionState,
    session: TerminalSession,
    modifier: Modifier = Modifier
) {
    val toggleState = mapOf(
        "controlKey" to interactionState.controlKey,
        "altKey" to interactionState.altKey,
        "shiftKey" to interactionState.shiftKey,
    )

    Surface(
        color = Color(0xFF1A1A2E),
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            for (key in EXTRA_KEYS) {
                val isToggled = if (key.type == ExtraKeyType.TOGGLE) {
                    toggleState[key.payload] == true
                } else {
                    false
                }

                val textColor = when {
                    isToggled -> Color(0xFF1A1A2E)
                    else -> Color(0xFFCCCCCC)
                }

                TextButton(
                    onClick = {
                        when (key.type) {
                            ExtraKeyType.TOGGLE -> {
                                when (key.payload) {
                                    "controlKey" -> interactionState.controlKey = !interactionState.controlKey
                                    "altKey" -> interactionState.altKey = !interactionState.altKey
                                    "shiftKey" -> interactionState.shiftKey = !interactionState.shiftKey
                                }
                            }
                            ExtraKeyType.ACTION -> {
                                session.write(key.payload)
                            }
                        }
                    },
                    modifier = Modifier.height(36.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = key.label,
                        color = textColor,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Main Compose entry point – TermuxTerminalScreen
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Composable terminal screen powered by the real Termux [TerminalView] and
 * [TerminalSession].
 *
 * **How it works:**
 * 1. An [AndroidView] factory creates a `com.termux.view.TerminalView`.
 * 2. [XCoderTerminalViewClient] is set via [TerminalView.setTerminalViewClient]
 *    **before** the session is attached (required by Termux).
 * 3. [XCoderTerminalSessionClient] is installed on the [session] via
 *    [TerminalSession.updateTerminalSessionClient] **before** the session is
 *    attached (so that process-start callbacks like `setTerminalShellPid` reach
 *    our client).
 * 4. [TerminalView.attachSession] triggers [TerminalView.updateSize], which in
 *    turn calls [TerminalSession.updateSize] → `initializeEmulator()` →
 *    `JNI.createSubprocess()` (requires `libtermux.so`).
 * 5. From that point on the [TerminalView] handles rendering, gesture detection
 *    (scroll, pinch-zoom, long-press selection), and IME input natively.
 *
 * @param session The [TerminalSession] to display, typically obtained from
 *   [TerminalSessionManager.createSession].
 * @param interactionState Shared state observable by Compose and the
 *   Termux callbacks. Provide your own instance if you need to drive the
 *   terminal from an outer scope (e.g. a ViewModel).
 * @param onSessionExit Called with the shell exit code when the process ends.
 * @param modifier Compose [Modifier] applied to the outer [Column].
 */
@Composable
fun TermuxTerminalScreen(
    session: TerminalSession,
    interactionState: TerminalInteractionState = remember { TerminalInteractionState() },
    onSessionExit: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // Mutable reference to the TerminalView, set once in the factory and
    // read by the session/view client callbacks.
    val viewRef = remember { arrayOfNulls<TerminalView>(1) }

    Column(modifier = modifier.fillMaxSize()) {
        // ── Termux TerminalView (native Android View) ─────────────────────
        AndroidView(
            factory = { ctx ->
                // TerminalView requires (Context, AttributeSet). We pass null
                // for AttributeSet because we are creating it programmatically
                // instead of inflating from XML.
                val view = TerminalView(ctx, null)
                view.isFocusable = true
                view.isFocusableInTouchMode = true
                view.isVerticalScrollBarEnabled = true
                view.scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
                viewRef[0] = view

                // Build the two required client implementations.
                val sessionClient = XCoderTerminalSessionClient(
                    context = ctx,
                    terminalViewProvider = { viewRef[0] },
                    interactionState = interactionState,
                    onSessionExit = onSessionExit
                )
                val viewClient = XCoderTerminalViewClient(
                    context = ctx,
                    terminalViewProvider = { viewRef[0] },
                    interactionState = interactionState
                )

                // CRITICAL ORDER:
                // 1. Set TerminalViewClient first (required by Termux internals)
                view.setTerminalViewClient(viewClient)

                // 2. Set font size → creates TerminalRenderer internally
                view.setTextSize(interactionState.fontSize)

                // 3. Install our full SessionClient on the session so that
                //    process-start callbacks (setTerminalShellPid etc.) reach us
                session.updateTerminalSessionClient(sessionClient)

                // 4. Attach session → triggers updateSize → initializeEmulator →
                //    JNI.createSubprocess (starts the shell)
                view.attachSession(session)

                view
            },
            update = { view ->
                // Font size changes driven by pinch-zoom are handled inside
                // XCoderTerminalViewClient.onScale; no extra work needed here.
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )

        // ── Extra-keys row ─────────────────────────────────────────────────
        TerminalExtraKeysRow(
            interactionState = interactionState,
            session = session,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
