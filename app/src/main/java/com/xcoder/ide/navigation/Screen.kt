package com.xcoder.ide.navigation

import androidx.compose.material.icons.Icons
import com.xcoder.ide.R
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Sealed class representing every top-level destination in XCoder IDE.
 *
 * Each screen carries a stable [route] string used by Compose Navigation,
 * plus display metadata for bottom-nav and drawer items.
 */
sealed class Screen(
    val route: String,
    val label: String,
    val labelRes: Int,
    val icon: ImageVector,
    val isDrawerOnly: Boolean = false
) {
    // -----------------------------------------------------------------------
    //  Primary screens — shown in bottom navigation bar
    // -----------------------------------------------------------------------

    /** Code editor powered by Rosemoe sora-editor. */
    data object CodeEditor : Screen(
        route = "editor",
        label = "Editor",
        labelRes = R.string.nav_web_editor,
        icon = Icons.Default.Edit
    )

    /** Integrated terminal emulator (Termux terminal-emulator). */
    data object Terminal : Screen(
        route = "terminal",
        label = "Terminal",
        labelRes = R.string.nav_terminal,
        icon = Icons.Default.Terminal
    )

    /** Block-based / visual drag-and-drop editor. */
    data object VisualEditor : Screen(
        route = "visual_editor",
        label = "Visual",
        labelRes = R.string.nav_visual_editor,
        icon = Icons.Default.Dashboard
    )

    /** APK editor with smali + resource tree. */
    data object ApkEditor : Screen(
        route = "apk_editor",
        label = "APK",
        labelRes = R.string.nav_apk_editor,
        icon = Icons.Default.Android,
        isDrawerOnly = true
    )

    /** Build console with Gradle output. */
    data object Build : Screen(
        route = "build",
        label = "Build",
        labelRes = R.string.nav_build,
        icon = Icons.Default.Build,
        isDrawerOnly = true
    )

    /** Application settings. */
    data object Settings : Screen(
        route = "settings",
        label = "Settings",
        labelRes = R.string.nav_settings,
        icon = Icons.Default.Settings
    )

    /** Project-wide text search. */
    data object Search : Screen(
        route = "search",
        label = "Search",
        labelRes = R.string.nav_search,
        icon = Icons.Default.Search,
        isDrawerOnly = true
    )

    /** Bookmarked lines / files. */
    data object Bookmarks : Screen(
        route = "bookmarks",
        label = "Bookmarks",
        labelRes = R.string.nav_bookmarks,
        icon = Icons.Default.BookmarkBorder,
        isDrawerOnly = true
    )

    /** List of recently opened projects. */
    data object ProjectList : Screen(
        route = "project_list",
        label = "Projects",
        labelRes = R.string.nav_projects,
        icon = Icons.Default.FolderOpen,
        isDrawerOnly = true
    )

    /** Git repository management. */
    data object GitManager : Screen(
        route = "git_manager",
        label = "Git",
        labelRes = R.string.nav_git,
        icon = Icons.Default.AccountTree,
        isDrawerOnly = true
    )

    /** AI-powered chat / code assistant. */
    data object AiChat : Screen(
        route = "ai_chat",
        label = "AI Assistant",
        labelRes = R.string.nav_ai_chat,
        icon = Icons.Default.SmartToy,
        isDrawerOnly = true
    )

    /** Plugin marketplace and management. */
    data object PluginManager : Screen(
        route = "plugin_manager",
        label = "Plugins",
        labelRes = R.string.nav_plugins,
        icon = Icons.Default.Extension,
        isDrawerOnly = true
    )

    companion object {
        /** All screen instances, ordered for drawer presentation. */
        val allScreens: List<Screen> = listOf(
            CodeEditor,
            Terminal,
            VisualEditor,
            ApkEditor,
            Build,
            Search,
            Bookmarks,
            ProjectList,
            GitManager,
            AiChat,
            PluginManager,
            Settings,
        )

        /** Screens that appear in the bottom navigation bar. */
        val bottomNavScreens: List<Screen> = allScreens.filter { !it.isDrawerOnly }

        /** Screens only in the side drawer. */
        val drawerOnlyScreens: List<Screen> = allScreens.filter { it.isDrawerOnly }

        private val routeMap: Map<String, Screen> = allScreens.associateBy { it.route }

        fun fromRoute(route: String?): Screen? = routeMap[route]
    }
}
