package com.xcoder.ide.navigation

/**
 * Sealed class representing every top-level destination in XCoder IDE.
 *
 * Each screen carries a stable [route] string used by Compose Navigation.
 * Optional arguments are appended with "/{argName}" syntax and read via
 * `NavBackStackEntry.arguments`.
 */
sealed class Screen(val route: String) {

    /**
 * Web-based code editor powered by CodeMirror / Monaco.
 * Supports JavaScript, TypeScript, HTML, CSS, and more.
 */
    data object WebEditor : Screen("web_editor")

    /**
     * Native Android editor with syntax highlighting via a custom
     * EditText-based or Spannable-based implementation.
     */
    data object NativeEditor : Screen("native_editor")

    /**
     * Block-based / visual drag-and-drop editor for beginners or
     * rapid prototyping.
     */
    data object VisualEditor : Screen("visual_editor")

    /**
     * Application settings (theme, font size, key bindings, etc.).
     */
    data object Settings : Screen("settings")

    /**
     * List of recently opened and available projects on device.
     */
    data object ProjectList : Screen("project_list")

    /**
     * Integrated terminal emulator session.
     */
    data object Terminal : Screen("terminal")

    /**
     * Git repository management (branch, commit, push, pull, log).
     */
    data object GitManager : Screen("git_manager")

    /**
     * AI-powered chat / code assistant.
     */
    data object AiChat : Screen("ai_chat")

    /**
     * Plugin marketplace and management screen.
     */
    data object PluginManager : Screen("plugin_manager")

    companion object {
        /**
         * Map of route strings to their corresponding [Screen] instances.
         * Useful for looking up a screen from a nav back stack entry.
         */
        val routeMap: Map<String, Screen> = values().associateBy { it.route }

        private fun values(): List<Screen> = listOf(
            WebEditor,
            NativeEditor,
            VisualEditor,
            Settings,
            ProjectList,
            Terminal,
            GitManager,
            AiChat,
            PluginManager
        )

        /**
         * Find a [Screen] from a route string, or null if unknown.
         */
        fun fromRoute(route: String?): Screen? = routeMap[route]
    }
}
