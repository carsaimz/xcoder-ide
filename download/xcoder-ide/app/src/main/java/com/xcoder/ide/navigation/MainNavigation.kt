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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.xcoder.ide.ui.native.NativeEditorScreen
import com.xcoder.ide.ui.visual.VisualEditorScreen
import com.xcoder.ide.ui.web.WebEditorScreen
import kotlin.reflect.KClass

/**
 * Main navigation graph that wires every [Screen] to its composable.
 *
 * The UI is split into:
 *  - A **bottom bar** with the 3 primary editor modes (Web, Native, Visual).
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

    // Remember whether the initial file has been dispatched so we only do it once.
    var fileDispatched by remember { mutableStateOf(false) }
    LaunchedEffect(initialFileUri) {
        if (initialFileUri != null && !fileDispatched) {
            fileDispatched = true
            // Default: open in web editor. The screen composable picks up the URI.
            navController.navigate(Screen.WebEditor.route) {
                popUpTo(Screen.WebEditor.route) { inclusive = true }
            }
        }
    }

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
                onCloseDrawer = {
                    scope.launch { drawerState.close() }
                }
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
                    onOpenDrawer = {
                        scope.launch { drawerState.open() }
                    }
                )
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Screen.WebEditor.route,
                modifier = Modifier.padding(innerPadding),
                enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left) },
                exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left) },
                popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right) },
                popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right) }
            ) {
                composable(Screen.WebEditor.route) {
                    WebEditorScreen(fileUri = initialFileUri?.takeIf { !fileDispatched })
                }
                composable(Screen.NativeEditor.route) {
                    NativeEditorScreen(fileUri = initialFileUri?.takeIf { !fileDispatched })
                }
                composable(Screen.VisualEditor.route) {
                    VisualEditorScreen()
                }
                composable(Screen.Terminal.route) {
                    PlaceholderScreen(
                        icon = Icons.Default.Terminal,
                        title = "Terminal",
                        description = "Integrated terminal emulator for running commands."
                    )
                }
                composable(Screen.GitManager.route) {
                    PlaceholderScreen(
                        icon = Icons.Default.AccountTree,
                        title = "Git Manager",
                        description = "Branch, commit, push, pull, and view history."
                    )
                }
                composable(Screen.AiChat.route) {
                    PlaceholderScreen(
                        icon = Icons.Default.SmartToy,
                        title = "AI Assistant",
                        description = "Ask questions, generate code, and get suggestions."
                    )
                }
                composable(Screen.PluginManager.route) {
                    PlaceholderScreen(
                        icon = Icons.Default.Extension,
                        title = "Plugins",
                        description = "Browse, install, and manage IDE plugins."
                    )
                }
                composable(Screen.Settings.route) {
                    PlaceholderScreen(
                        icon = Icons.Default.Settings,
                        title = "Settings",
                        description = "Customize your IDE experience."
                    )
                }
                composable(Screen.ProjectList.route) {
                    PlaceholderScreen(
                        icon = Icons.Default.FolderOpen,
                        title = "Projects",
                        description = "Open recent projects or create a new one."
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
        Triple(Screen.WebEditor, Icons.Default.Language, "Web"),
        Triple(Screen.NativeEditor, Icons.Default.PhoneAndroid, "Native"),
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
        // Header
        Spacer(Modifier.height(16.dp))
        Text(
            text = "XCoder IDE",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Text(
            text = "Tools & Settings",
            style = MaterialTheme.typography.bodyMedium,
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
    }
}

// ---------------------------------------------------------------------------
// Placeholder Screen (for module screens not yet wired)
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
