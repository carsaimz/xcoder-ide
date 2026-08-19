package com.xcoder.ide.navigation

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.xcoder.editor.sora.EditorScreen
import com.xcoder.core.terminal.TermuxTerminalScreen
import com.xcoder.ide.ui.visual.VisualEditorScreen

/**
 * Main navigation graph that wires every [Screen] to its composable.
 *
 * The UI is split into:
 *  - A **bottom bar** with the 2 primary modes (Editor, Visual).
 *  - A **side drawer** with secondary tools (Terminal, Git, AI, Plugins, Settings).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavigation(
    navController: NavHostController = rememberNavController(),
    initialFileUri: Uri? = null,
    onFileHandled: () -> Unit = {}
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            DrawerContent(
                currentRoute = currentRoute,
                onNavigate = { screen ->
                    scope.launch { drawerState.close() }
                    navController.navigate(screen.route) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onCloseDrawer = { scope.launch { drawerState.close() } }
            )
        }
    ) {
        Scaffold(
            bottomBar = {
                BottomNavigationBar(
                    currentRoute = currentRoute,
                    onNavigate = { screen ->
                        navController.navigate(screen.route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onOpenDrawer = { scope.launch { drawerState.open() } }
                )
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Screen.CodeEditor.route,
                modifier = Modifier.padding(innerPadding),
                enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left) },
                exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left) },
                popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right) },
                popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right) }
            ) {
                // ── Editor (Rosemoe sora-editor) ──────────────────────
                composable(Screen.CodeEditor.route) {
                    EditorScreen(
                        filePath = "",
                        initialContent = "// Welcome to XCoder IDE\n// Powered by Rosemoe sora-editor\n\nfun main() {\n    println(\"Hello, XCoder!\")\n}\n",
                        onBack = { navController.popBackStack() }
                    )
                }

                // ── Visual Editor ────────────────────────────────────
                composable(Screen.VisualEditor.route) {
                    VisualEditorScreen()
                }

                // ── Terminal (Termux terminal-emulator) ──────────────
                composable(Screen.Terminal.route) {
                    TermuxTerminalScreen(
                        fontSize = 14f,
                        onSessionExit = { exitCode ->
                            // Handle session exit
                        }
                    )
                }

                // ── Git Manager ──────────────────────────────────────
                composable(Screen.GitManager.route) {
                    PlaceholderScreen(
                        icon = Icons.Default.AccountTree,
                        title = "Git Manager",
                        description = "Branch, commit, push, pull, and view history.\nPowered by JGit."
                    )
                }

                // ── AI Assistant ──────────────────────────────────────
                composable(Screen.AiChat.route) {
                    PlaceholderScreen(
                        icon = Icons.Default.SmartToy,
                        title = "AI Assistant",
                        description = "Ask questions, generate code, and get suggestions.\nSupports OpenAI, Gemini, Claude."
                    )
                }

                // ── Plugins ──────────────────────────────────────────
                composable(Screen.PluginManager.route) {
                    PlaceholderScreen(
                        icon = Icons.Default.Extension,
                        title = "Plugins",
                        description = "Browse, install, and manage IDE plugins."
                    )
                }

                // ── Settings ─────────────────────────────────────────
                composable(Screen.Settings.route) {
                    PlaceholderScreen(
                        icon = Icons.Default.Settings,
                        title = "Settings",
                        description = "Customize your IDE experience."
                    )
                }

                // ── Projects ─────────────────────────────────────────
                composable(Screen.ProjectList.route) {
                    PlaceholderScreen(
                        icon = Icons.Default.FolderOpen,
                        title = "Projects",
                        description = "Open recent projects or create a new one.\nFile tree powered by AndroidTreeView."
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Bottom Navigation Bar
// ---------------------------------------------------------------------------

@Composable
private fun BottomNavigationBar(
    currentRoute: String?,
    onNavigate: (Screen) -> Unit,
    onOpenDrawer: () -> Unit
) {
    val primaryScreens = listOf(
        Triple(Screen.CodeEditor, Icons.Default.Edit, "Editor"),
        Triple(Screen.VisualEditor, Icons.Default.Dashboard, "Visual"),
    )

    NavigationBar {
        primaryScreens.forEach { (screen, icon, label) ->
            val selected = currentRoute == screen.route
            NavigationBarItem(
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label) },
                selected = selected,
                onClick = { onNavigate(screen) },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
        NavigationBarItem(
            icon = { Icon(Icons.Default.Menu, contentDescription = "More") },
            label = { Text("More") },
            selected = false,
            onClick = onOpenDrawer,
            colors = NavigationBarItemDefaults.colors(
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}

// ---------------------------------------------------------------------------
// Side Drawer
// ---------------------------------------------------------------------------

@Composable
private fun DrawerContent(
    currentRoute: String?,
    onNavigate: (Screen) -> Unit,
    onCloseDrawer: () -> Unit
) {
    val drawerItems = listOf(
        Triple(Screen.Terminal, Icons.Default.Terminal, "Terminal"),
        Triple(Screen.GitManager, Icons.Default.AccountTree, "Git Manager"),
        Triple(Screen.AiChat, Icons.Default.SmartToy, "AI Assistant"),
        Triple(Screen.PluginManager, Icons.Default.Extension, "Plugins"),
        Triple(Screen.ProjectList, Icons.Default.FolderOpen, "Projects"),
        Triple(Screen.Settings, Icons.Default.Settings, "Settings"),
    )

    ModalDrawerSheet(
        modifier = Modifier.width(280.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
            text = "XCoder IDE",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Text(
            text = "Powered by sora-editor, Termux, AndroidTreeView",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        LazyColumn {
            items(drawerItems) { (screen, icon, label) ->
                val selected = currentRoute == screen.route
                NavigationDrawerItem(
                    icon = { Icon(icon, contentDescription = label) },
                    label = { Text(label) },
                    selected = selected,
                    onClick = { onNavigate(screen) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }

        // ── Credits ────────────────────────────────────────────────
        Spacer(Modifier.weight(1f))
        HorizontalDivider()
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                "Thanks to",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Rosemoe for the awesome CodeEditor",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
            Text(
                "Termux for Terminal Emulator",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
            Text(
                "Bogdan Melnychuk for AndroidTreeView",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
            Text(
                "George Fraser for the Java Language Server",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Placeholder Screen
// ---------------------------------------------------------------------------

@Composable
private fun PlaceholderScreen(
    icon: ImageVector,
    title: String,
    description: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
