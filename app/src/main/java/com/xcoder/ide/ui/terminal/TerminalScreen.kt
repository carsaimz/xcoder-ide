package com.xcoder.ide.ui.terminal

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.xcoder.ide.theme.LocalIdeColors

/**
 * Terminal screen composable.
 *
 * Based on Termux's `activity_termux.xml` layout which uses:
 * - A [DrawerLayout] with session list on the left
 * - A [TerminalView] in the center
 * - An extra-keys toolbar above the keyboard
 *
 * This Compose version replaces the XML DrawerLayout with a
 * [ModalNavigationDrawer] and wraps the native [TerminalView]
 * (from `:core:terminal`) in an [AndroidView].
 *
 * The terminal session management is handled by [TerminalSessionState]
 * which mirrors Termux's `TermuxSessionManager`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    modifier: Modifier = Modifier
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val ideColors = LocalIdeColors.current

    // Session state — mirrors Termux's session manager.
    val sessionState = remember { TerminalSessionState() }
    var activeSessionId by remember { mutableStateOf(sessionState.sessions.firstOrNull()?.id) }

    // ── Drawer shell ──────────────────────────────────────────────
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SessionDrawer(
                sessions = sessionState.sessions,
                activeSessionId = activeSessionId,
                onSelectSession = { id ->
                    activeSessionId = id
                    scope.launch { drawerState.close() }
                },
                onNewSession = {
                    val newSession = sessionState.createNewSession()
                    activeSessionId = newSession.id
                    scope.launch { drawerState.close() }
                },
                onCloseSession = { id ->
                    sessionState.closeSession(id)
                    if (activeSessionId == id) {
                        activeSessionId = sessionState.sessions.firstOrNull()?.id
                    }
                },
                onCloseDrawer = { scope.launch { drawerState.close() } }
            )
        }
    ) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(ideColors.terminalBackground)
        ) {
            // ── Terminal toolbar ───────────────────────────────────
            TerminalToolbar(
                sessionName = sessionState.sessions
                    .firstOrNull { it.id == activeSessionId }?.name ?: "Terminal",
                onToggleDrawer = { scope.launch { drawerState.open() } },
                onNewSession = {
                    val newSession = sessionState.createNewSession()
                    activeSessionId = newSession.id
                },
                onCloseSession = {
                    activeSessionId?.let { id ->
                        sessionState.closeSession(id)
                        activeSessionId = sessionState.sessions.firstOrNull()?.id
                    }
                }
            )

            // ── Terminal view area ─────────────────────────────────
            // In production, this wraps com.xcoder.core.terminal.TermuxTerminalScreen
            // which hosts the real TerminalView. For preview we show a placeholder.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                TerminalPlaceholderView(sessionState, activeSessionId)
            }

            // ── Extra-keys toolbar (above keyboard) ───────────────
            ExtraKeysToolbar(
                onKeyAction = { key ->
                    // In production: activeSession?.write(key)
                }
            )
        }
    }
}

// ==========================================================================
//  Terminal Toolbar
// ==========================================================================

@Composable
private fun TerminalToolbar(
    sessionName: String,
    onToggleDrawer: () -> Unit,
    onNewSession: () -> Unit,
    onCloseSession: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onToggleDrawer) {
                Icon(Icons.Default.Menu, contentDescription = "Sessions")
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = sessionName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onNewSession) {
                Icon(Icons.Default.Add, contentDescription = "New session")
            }
            IconButton(onClick = onCloseSession) {
                Icon(Icons.Default.Close, contentDescription = "Close session")
            }
        }
    }
}

// ==========================================================================
//  Session Drawer (swipe from left)
// ==========================================================================

@Composable
private fun SessionDrawer(
    sessions: List<TerminalSessionInfo>,
    activeSessionId: String?,
    onSelectSession: (String) -> Unit,
    onNewSession: () -> Unit,
    onCloseSession: (String) -> Unit,
    onCloseDrawer: () -> Unit
) {
    ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
        Spacer(Modifier.height(12.dp))

        // Header.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Terminal,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "Sessions",
                style = MaterialTheme.typography.titleLarge
            )
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // New session FAB.
        FilledTonalButton(
            onClick = onNewSession,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("New Session")
        }

        Spacer(Modifier.height(8.dp))

        // Session list.
        if (sessions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No active sessions",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                items(sessions, key = { it.id }) { session ->
                    val isActive = session.id == activeSessionId
                    ListItem(
                        headlineContent = {
                            Text(
                                session.name,
                                style = if (isActive) MaterialTheme.typography.titleSmall
                                else MaterialTheme.typography.bodyMedium
                            )
                        },
                        supportingContent = {
                            Text(
                                session.workingDirectory,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingContent = {
                            Icon(
                                if (isActive) Icons.Default.Terminal else Icons.Default.Terminal,
                                contentDescription = null,
                                tint = if (isActive) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingContent = {
                            IconButton(onClick = { onCloseSession(session.id) }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Close ${session.name}",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer
                            else Color.Transparent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectSession(session.id) }
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))
        HorizontalDivider()
        Text(
            "Powered by Termux terminal-emulator",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(16.dp)
        )
    }
}

// ==========================================================================
//  Extra Keys Toolbar (above keyboard)
// ==========================================================================

/**
 * Extra-keys row modelled after Termux's `ExtraKeysView`.
 *
 * Shows: ESC, TAB, CTRL, ALT, SHIFT, -, /, |, arrow keys.
 * Toggle keys (CTRL/ALT/SHIFT) stay highlighted when active.
 */
@Composable
private fun ExtraKeysToolbar(
    onKeyAction: (String) -> Unit
) {
    var ctrlActive by remember { mutableStateOf(false) }
    var altActive by remember { mutableStateOf(false) }
    var shiftActive by remember { mutableStateOf(false) }

    Surface(
        color = Color(0xFF1A1A2E)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .height(40.dp)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Action keys.
            ExtraKeyButton("ESC", isActive = false) { onKeyAction("\u001b") }
            ExtraKeyButton("TAB", isActive = false) { onKeyAction("\t") }

            // Toggle keys.
            ExtraKeyButton("CTRL", isActive = ctrlActive) {
                ctrlActive = !ctrlActive
            }
            ExtraKeyButton("ALT", isActive = altActive) {
                altActive = !altActive
            }
            ExtraKeyButton("SHIFT", isActive = shiftActive) {
                shiftActive = !shiftActive
            }

            // Symbol keys.
            ExtraKeyButton("-", isActive = false) { onKeyAction("-") }
            ExtraKeyButton("/", isActive = false) { onKeyAction("/") }
            ExtraKeyButton("|", isActive = false) { onKeyAction("|") }
            ExtraKeyButton("~", isActive = false) { onKeyAction("~") }

            // Arrow keys.
            ExtraKeyButton("←", isActive = false) { onKeyAction("\u001b[D") }
            ExtraKeyButton("→", isActive = false) { onKeyAction("\u001b[C") }
            ExtraKeyButton("↑", isActive = false) { onKeyAction("\u001b[A") }
            ExtraKeyButton("↓", isActive = false) { onKeyAction("\u001b[B") }
        }
    }
}

@Composable
private fun ExtraKeyButton(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isActive) Color(0xFF6B5CE7) else Color(0xFF2D2D44)
    val textColor = if (isActive) Color.White else Color(0xFFCCCCCC)

    Surface(
        color = bgColor,
        shape = MaterialTheme.shapes.extraSmall,
        modifier = Modifier.height(34.dp)
    ) {
        TextButton(
            onClick = onClick,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            modifier = Modifier.height(34.dp)
        ) {
            Text(
                text = label,
                color = textColor,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )
        }
    }
}

// ==========================================================================
//  Terminal Placeholder View (preview mode)
// ==========================================================================

/**
 * Placeholder that mimics the terminal view appearance.
 * In production this is replaced by the real [com.xcoder.core.terminal.TermuxTerminalScreen].
 */
@Composable
private fun TerminalPlaceholderView(
    sessionState: TerminalSessionState,
    activeSessionId: String?
) {
    val ideColors = LocalIdeColors.current
    val session = sessionState.sessions.firstOrNull { it.id == activeSessionId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ideColors.terminalBackground)
            .padding(16.dp)
    ) {
        Text(
            "XCoder IDE Terminal v1.0",
            style = com.xcoder.ide.theme.CodeTypography.terminal,
            color = ideColors.terminalGreen
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Type 'help' for available commands.",
            style = com.xcoder.ide.theme.CodeTypography.terminal,
            color = ideColors.terminalForeground
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "${session?.prompt ?: "\$ "} ",
            style = com.xcoder.ide.theme.CodeTypography.terminal,
            color = ideColors.terminalCyan
        )

        Spacer(Modifier.weight(1f))

        Text(
            "[Real TerminalView from :core:terminal will be mounted here]",
            style = com.xcoder.ide.theme.CodeTypography.terminal,
            color = ideColors.terminalBrightBlack
        )
    }
}

// ==========================================================================
//  Session State Management
// ==========================================================================

/** Data class for a terminal session. */
data class TerminalSessionInfo(
    val id: String,
    val name: String,
    val workingDirectory: String,
    val prompt: String = "\$ ",
    val isRunning: Boolean = true
)

/**
 * Manages terminal sessions.
 * Mirrors Termux's `TermuxSessionManager` for session lifecycle.
 */
class TerminalSessionState {
    private var _nextId = 1

    val sessions = mutableStateListOf(
        TerminalSessionInfo(
            id = "session-1",
            name = "Session 1",
            workingDirectory = "/data/data/com.xcoder.ide/files",
            prompt = "$ "
        )
    )

    fun createNewSession(
        workingDirectory: String = "/data/data/com.xcoder.ide/files"
    ): TerminalSessionInfo {
        val session = TerminalSessionInfo(
            id = "session-${_nextId++}",
            name = "Session ${_nextId - 1}",
            workingDirectory = workingDirectory
        )
        sessions.add(session)
        return session
    }

    fun closeSession(id: String) {
        val idx = sessions.indexOfFirst { it.id == id }
        if (idx >= 0) {
            sessions.removeAt(idx)
        }
    }
}
