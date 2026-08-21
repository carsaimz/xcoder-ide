package com.xcoder.ide.navigation

/**
 * Sealed class representing every top-level destination in XCoder IDE.
 *
 * Each screen carries a stable [route] string used by Compose Navigation.
 */
sealed class Screen(val route: String) {

    /**
     * Code editor powered by Rosemoe sora-editor.
     * Supports 30+ languages via TextMate grammars, code folding,
     * auto-completion, search/replace, minimap, and more.
     */
    data object CodeEditor : Screen("code_editor")

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
     * Integrated terminal emulator (Termux terminal-emulator).
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
        val routeMap: Map<String, Screen> = values().associateBy { it.route }

        private fun values(): List<Screen> = listOf(
            CodeEditor,
            VisualEditor,
            Settings,
            ProjectList,
            Terminal,
            GitManager,
            AiChat,
            PluginManager
        )

        fun fromRoute(route: String?): Screen? = routeMap[route]
    }
}