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
import com.xcoder.ide.ui.editor.EditorScreen
import com.xcoder.ide.ui.terminal.TerminalScreen
import com.xcoder.ide.ui.visual.VisualEditorScreen

/**
 * Main navigation graph that wires every [Screen] to its composable.
 *
 * Layout model:
 * - **Bottom bar**: 3 primary destinations (Editor, Terminal, Visual).
 * - **Side drawer**: all remaining tools (APK Editor, Build, Search,
 *   Bookmarks, Git, AI, Plugins, Settings).
 *
 * Pattern from AndroidIDE's `BaseEditorActivity` tab model and
 * Sketchware-IA's `DrawerLayout` navigation pattern.
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
                enterTransition = {
                    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left)
                },
                exitTransition = {
                    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left)
                },
                popEnterTransition = {
                    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right)
                },
                popExitTransition = {
                    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right)
                }
            ) {
                // ── Editor (Rosemoe sora-editor) ──────────────────────
                composable(Screen.CodeEditor.route) {
                    EditorScreen(
                        filePath = initialFileUri?.toString() ?: "",
                        onBack = { navController.popBackStack() },
                        onOpenFile = { /* navigate to editor with file */ }
                    )
                }

                // ── Terminal (Termux terminal-emulator) ──────────────
                composable(Screen.Terminal.route) {
                    TerminalScreen()
                }

                // ── Visual Editor (Sketchware-IA pattern) ────────────
                composable(Screen.VisualEditor.route) {
                    VisualEditorScreen()
                }

                // ── APK Editor ──────────────────────────────────────
                composable(Screen.ApkEditor.route) {
                    PlaceholderScreen(
                        icon = Icons.Default.Android,
                        title = "APK Editor",
                        description = "Decompile, edit smali/resources, and re-sign APKs.\n" +
                            "Powered by apktool and baksmali."
                    )
                }

                // ── Build Console ──────────────────────────────────
                composable(Screen.Build.route) {
                    PlaceholderScreen(
                        icon = Icons.Default.Build,
                        title = "Build",
                        description = "Gradle build console with real-time output.\n" +
                            "Supports Gradle, Maven, and direct javac/kotlinc."
                    )
                }

                // ── Search ──────────────────────────────────────────
                composable(Screen.Search.route) {
                    PlaceholderScreen(
                        icon = Icons.Default.Search,
                        title = "Search in Project",
                        description = "Full-text search across all project files.\n" +
                            "Regex support, file filtering, and result navigation."
                    )
                }

                // ── Bookmarks ───────────────────────────────────────
                composable(Screen.Bookmarks.route) {
                    PlaceholderScreen(
                        icon = Icons.Default.BookmarkBorder,
                        title = "Bookmarks",
                        description = "Quick access to bookmarked lines and files."
                    )
                }

                // ── Projects ────────────────────────────────────────
                composable(Screen.ProjectList.route) {
                    PlaceholderScreen(
                        icon = Icons.Default.FolderOpen,
                        title = "Projects",
                        description = "Open recent projects or create a new one.\n" +
                            "File tree powered by AndroidTreeView."
                    )
                }

                // ── Git Manager ──────────────────────────────────────
                composable(Screen.GitManager.route) {
                    PlaceholderScreen(
                        icon = Icons.Default.AccountTree,
                        title = "Git Manager",
                        description = "Branch, commit, push, pull, and view history.\n" +
                            "Powered by JGit."
                    )
                }

                // ── AI Assistant ────────────────────────────────────
                composable(Screen.AiChat.route) {
                    PlaceholderScreen(
                        icon = Icons.Default.SmartToy,
                        title = "AI Assistant",
                        description = "Ask questions, generate code, and get suggestions.\n" +
                            "Supports OpenAI, Gemini, Claude."
                    )
                }

                // ── Plugins ─────────────────────────────────────────
                composable(Screen.PluginManager.route) {
                    PlaceholderScreen(
                        icon = Icons.Default.Extension,
                        title = "Plugins",
                        description = "Browse, install, and manage IDE plugins."
                    )
                }

                // ── Settings ────────────────────────────────────────
                composable(Screen.Settings.route) {
                    PlaceholderScreen(
                        icon = Icons.Default.Settings,
                        title = "Settings",
                        description = "Customize your IDE experience.\n" +
                            "Theme, font size, key bindings, terminal, LSP, and more."
                    )
                }
            }
        }
    }
}

// ==========================================================================
//  Bottom Navigation Bar
// ==========================================================================

@Composable
private fun BottomNavigationBar(
    currentRoute: String?,
    onNavigate: (Screen) -> Unit,
    onOpenDrawer: () -> Unit
) {
    NavigationBar {
        Screen.bottomNavScreens.forEach { screen ->
            val selected = currentRoute == screen.route
            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector = screen.icon,
                        contentDescription = screen.label
                    )
                },
                label = { Text(screen.label) },
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
        // "More" button opens the drawer.
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

// ==========================================================================
//  Side Drawer
// ==========================================================================

@Composable
private fun DrawerContent(
    currentRoute: String?,
    onNavigate: (Screen) -> Unit,
    onCloseDrawer: () -> Unit
) {
    ModalDrawerSheet(modifier = Modifier.width(280.dp)) {
        // ── Header ────────────────────────────────────────────────
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Code,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = "XCoder IDE",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "v1.0.0  •  Kotlin + Compose",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        // ── Drawer items ─────────────────────────────────────────
        LazyColumn {
            items(Screen.drawerOnlyScreens) { screen ->
                val selected = currentRoute == screen.route
                NavigationDrawerItem(
                    icon = { Icon(screen.icon, contentDescription = screen.label) },
                    label = { Text(screen.label) },
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

        // ── Credits ──────────────────────────────────────────────
        Spacer(Modifier.weight(1f))
        HorizontalDivider()
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Powered by",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Rosemoe sora-editor  •  Termux terminal-emulator",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
            Text(
                "AndroidTreeView  •  Java Language Server",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
    }
}

// ==========================================================================
//  Placeholder Screen
// ==========================================================================

@Composable
internal fun PlaceholderScreen(
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
