/**
 * Comprehensive preference key definitions for XCoder IDE.
 *
 * Based on AndroidIDE's `IDEPreferences` which organizes all preference keys
 * into categories (editor, terminal, build, UI, etc.) and provides typed
 * access with default values.
 *
 * AndroidIDE uses `SharedPreferences` with string keys and reads them in
 * various places (editor initialization, terminal setup, build configuration).
 * XCoder IDE uses Jetpack DataStore but follows the same organizational pattern.
 *
 * ## Key Categories
 *
 * - [EditorPrefs]: Code editor configuration (font, tabs, colors, features)
 * - [TerminalPrefs]: Terminal emulator configuration (shell, font, colors)
 * - [BuildPrefs]: Build system configuration (Gradle, JDK, JVM args)
 * - [UIPrefs]: User interface preferences (theme, language, layout)
 * - [FileExplorerPrefs]: File tree and explorer configuration
 * - [GitPrefs]: Git integration configuration
 * - [LspPrefs]: Language Server Protocol configuration
 * - [AiPrefs]: AI copilot configuration
 *
 * ## Usage
 *
 * ```kotlin
 * // Read a preference
 * val fontSize = prefs[IDEPreferences.EditorPrefs.FONT_SIZE] ?: 14f
 *
 * // Or use the defaults directly
 * val tabSize = IDEPreferences.EditorPrefs.DEFAULT_TAB_SIZE
 * ```
 */
package com.xcoder.core.settings

/**
 * Editor preference keys and defaults.
 *
 * Based on AndroidIDE's editor preferences, which configure:
 * - Font (size, family, ligatures)
 * - Tabs (size, spaces vs tabs)
 * - Display (word wrap, line numbers, minimap, indent guides)
 * - Features (auto-completion, auto-indent, smart backspace)
 * - Auto-save behavior
 * - Keymap selection
 *
 * AndroidIDE reads these in `CodeEditorView` constructor and `EditorPreferencesFragment`.
 */
object EditorPrefs {

    // ── Font ───────────────────────────────────────────────────────────
    /** Font size in sp. Default: 14. */
    const val KEY_FONT_SIZE = "editor_font_size"
    const val DEFAULT_FONT_SIZE = 14f

    /** Font family name (e.g. "JetBrains Mono", "Fira Code", "Monospace"). Default: JetBrains Mono. */
    const val KEY_FONT_FAMILY = "editor_font_family"
    const val DEFAULT_FONT_FAMILY = "JetBrains Mono"

    /** Enable font ligatures (e.g. ->  becomes →). Default: true. */
    const val KEY_FONT_LIGATURES = "editor_font_ligatures"
    const val DEFAULT_FONT_LIGATURES = true

    // ── Tabs & Indentation ────────────────────────────────────────────
    /** Tab width in spaces. Default: 4. */
    const val KEY_TAB_SIZE = "editor_tab_size"
    const val DEFAULT_TAB_SIZE = 4

    /** Use spaces instead of tabs. Default: true. */
    const val KEY_USE_SPACES = "editor_use_spaces"
    const val DEFAULT_USE_SPACES = true

    // ── Display ────────────────────────────────────────────────────────
    /** Enable soft word wrap. Default: false (AndroidIDE default). */
    const val KEY_WORD_WRAP = "editor_word_wrap"
    const val DEFAULT_WORD_WRAP = false

    /** Show line numbers gutter. Default: true. */
    const val KEY_SHOW_LINE_NUMBERS = "editor_show_line_numbers"
    const val DEFAULT_SHOW_LINE_NUMBERS = true

    /** Show code minimap. Default: true (for large screens). */
    const val KEY_SHOW_MINIMAP = "editor_show_minimap"
    const val DEFAULT_SHOW_MINIMAP = true

    /** Show indent guide lines. Default: true. */
    const val KEY_SHOW_INDENT_GUIDES = "editor_show_indent_guides"
    const val DEFAULT_SHOW_INDENT_GUIDES = true

    /** Highlight the current line. Default: true. */
    const val KEY_HIGHLIGHT_CURRENT_LINE = "editor_highlight_current_line"
    const val DEFAULT_HIGHLIGHT_CURRENT_LINE = true

    /** Highlight matching bracket pairs. Default: true. */
    const val KEY_BRACKET_MATCHING = "editor_bracket_matching"
    const val DEFAULT_BRACKET_MATCHING = true

    /** Show whitespace characters. Default: false. */
    const val KEY_SHOW_WHITESPACE = "editor_show_whitespace"
    const val DEFAULT_SHOW_WHITESPACE = false

    /** Enable sticky scroll for block headers. Default: true. */
    const val KEY_STICKY_SCROLL = "editor_sticky_scroll"
    const val DEFAULT_STICKY_SCROLL = true

    // ── Features ───────────────────────────────────────────────────────
    /** Enable auto-completion popup. Default: true. */
    const val KEY_AUTO_COMPLETION = "editor_auto_completion"
    const val DEFAULT_AUTO_COMPLETION = true

    /** Enable auto-indent on Enter/newline. Default: true. */
    const val KEY_AUTO_INDENT = "editor_auto_indent"
    const val DEFAULT_AUTO_INDENT = true

    /** Enable auto-close brackets and quotes. Default: true. */
    const val KEY_SYMBOL_COMPLETION = "editor_symbol_completion"
    const val DEFAULT_SYMBOL_COMPLETION = true

    /** Enable smart backspace (delete indent). Default: true. */
    const val KEY_SMART_BACKSPACE = "editor_smart_backspace"
    const val DEFAULT_SMART_BACKSPACE = true

    /** Enable pinch-to-zoom for font size. Default: true. */
    const val KEY_PINCH_ZOOM = "editor_pinch_zoom"
    const val DEFAULT_PINCH_ZOOM = true

    // ── Auto-save ──────────────────────────────────────────────────────
    /** Enable auto-save. Default: true. */
    const val KEY_AUTO_SAVE = "editor_auto_save"
    const val DEFAULT_AUTO_SAVE = true

    /** Auto-save interval in milliseconds. Default: 3000 (3 seconds). */
    const val KEY_AUTO_SAVE_INTERVAL = "editor_auto_save_interval_ms"
    const val DEFAULT_AUTO_SAVE_INTERVAL = 3000

    // ── Keymap ─────────────────────────────────────────────────────────
    /** Keymap mode: "default", "vim", "emacs", "sublime". Default: "default". */
    const val KEY_KEYMAP = "editor_keymap"
    const val DEFAULT_KEYMAP = "default"

    // ── Color Scheme ───────────────────────────────────────────────────
    /** Editor color scheme name. Default: "catppuccin-mocha". */
    const val KEY_COLOR_SCHEME = "editor_color_scheme"
    const val DEFAULT_COLOR_SCHEME = "catppuccin-mocha"
}

/**
 * Terminal preference keys and defaults.
 *
 * Based on Termux's `~/.termux/termux.properties` and AndroidIDE's terminal settings.
 * Termux stores terminal preferences in a properties file; we use DataStore
 * for consistency with the rest of the app.
 */
object TerminalPrefs {

    // ── Shell ──────────────────────────────────────────────────────────
    /** Path to the shell binary. Default: auto-detected. */
    const val KEY_SHELL_PATH = "terminal_shell_path"
    const val DEFAULT_SHELL_PATH = "/system/bin/sh"

    // ── Font ───────────────────────────────────────────────────────────
    /** Terminal font size in sp. Default: 14. */
    const val KEY_FONT_SIZE = "terminal_font_size"
    const val DEFAULT_FONT_SIZE = 14f

    /** Terminal font family. Default: "Monospace". */
    const val KEY_FONT_FAMILY = "terminal_font_family"
    const val DEFAULT_FONT_FAMILY = "Monospace"

    // ── Display ────────────────────────────────────────────────────────
    /** Terminal color scheme. Default: "default". */
    const val KEY_COLOR_SCHEME = "terminal_color_scheme"
    const val DEFAULT_COLOR_SCHEME = "default"

    /** Cursor style: "block" (0), "underline" (1), "bar" (2). Default: "block". */
    const val KEY_CURSOR_STYLE = "terminal_cursor_style"
    const val DEFAULT_CURSOR_STYLE = "block"

    /** Number of scrollback (transcript) lines. Default: 5000. */
    const val KEY_SCROLLBACK_LINES = "terminal_scrollback_lines"
    const val DEFAULT_SCROLLBACK_LINES = 5000

    // ── Behavior ───────────────────────────────────────────────────────
    /** Enable terminal bell. Default: false. */
    const val KEY_BELL = "terminal_bell"
    const val DEFAULT_BELL = false

    /** Show extra keys toolbar. Default: true. */
    const val KEY_SHOW_EXTRA_KEYS = "terminal_show_extra_keys"
    const val DEFAULT_SHOW_EXTRA_KEYS = true

    /** Extra keys layout configuration (serialized string). */
    const val KEY_EXTRA_KEYS_LAYOUT = "terminal_extra_keys_layout"
    const val DEFAULT_EXTRA_KEYS_LAYOUT = "[[ESC,/, -, HOME, UP, END, PGUP],[TAB, CTRL, ALT, LEFT, DOWN, RIGHT, PGDN]]"

    // ── Sessions ───────────────────────────────────────────────────────
    /** Default working directory. Default: app files dir. */
    const val KEY_DEFAULT_WORKING_DIR = "terminal_default_working_dir"
    const val DEFAULT_DEFAULT_WORKING_DIR = ""

    /** Maximum number of sessions. Default: 8. */
    const val KEY_MAX_SESSIONS = "terminal_max_sessions"
    const val DEFAULT_MAX_SESSIONS = 8
}

/**
 * Build system preference keys and defaults.
 *
 * Based on AndroidIDE's build configuration, which allows configuring:
 * - JDK and Android SDK paths
 * - Gradle installation
 * - JVM arguments for the build process
 * - Build tool selection
 *
 * AndroidIDE stores these in `SharedPreferences` and reads them in
 * `BuildHandler` and `GradleExecutor`.
 */
object BuildPrefs {

    // ── Tool Paths ─────────────────────────────────────────────────────
    /** Path to JDK installation. */
    const val KEY_JDK_PATH = "build_jdk_path"
    const val DEFAULT_JDK_PATH = "/data/data/com.xcoder.ide/files/jdk"

    /** Path to Android SDK. */
    const val KEY_SDK_PATH = "build_sdk_path"
    const val DEFAULT_SDK_PATH = "/data/data/com.xcoder.ide/files/android-sdk"

    /** Path to Android NDK (optional). */
    const val KEY_NDK_PATH = "build_ndk_path"
    const val DEFAULT_NDK_PATH = ""

    /** Path to Gradle installation (optional, uses wrapper by default). */
    const val KEY_GRADLE_PATH = "build_gradle_path"
    const val DEFAULT_GRADLE_PATH = ""

    /** Path to CMake (optional, for native builds). */
    const val KEY_CMAKE_PATH = "build_cmake_path"
    const val DEFAULT_CMAKE_PATH = ""

    // ── Build Configuration ────────────────────────────────────────────
    /** Primary build tool: "gradle". Default: "gradle". */
    const val KEY_BUILD_TOOL = "build_tool"
    const val DEFAULT_BUILD_TOOL = "gradle"

    /** JVM arguments for Gradle daemon. */
    const val KEY_GRADLE_JVM_ARGS = "build_gradle_jvm_args"
    const val DEFAULT_GRADLE_JVM_ARGS = "-Xmx2048m -XX:+UseG1GC -XX:MaxMetaspaceSize=512m"

    /** Enable Gradle daemon. Default: true. */
    const val KEY_GRADLE_DAEMON = "build_gradle_daemon"
    const val DEFAULT_GRADLE_DAEMON = true

    /** Enable Gradle parallel execution. Default: true. */
    const val KEY_GRADLE_PARALLEL = "build_gradle_parallel"
    const val DEFAULT_GRADLE_PARALLEL = true

    /** Enable Gradle build cache. Default: true. */
    const val KEY_GRADLE_BUILD_CACHE = "build_gradle_build_cache"
    const val DEFAULT_GRADLE_BUILD_CACHE = true

    /** Gradle log level: "quiet", "lifecycle", "info", "debug". Default: "lifecycle". */
    const val KEY_GRADLE_LOG_LEVEL = "build_gradle_log_level"
    const val DEFAULT_GRADLE_LOG_LEVEL = "lifecycle"
}

/**
 * UI preference keys and defaults.
 *
 * Based on AndroidIDE's UI preferences including theme, language,
 * sidebar configuration, and layout options.
 */
object UIPrefs {

    // ── Theme ──────────────────────────────────────────────────────────
    /** App theme: "system", "light", "dark". Default: "system". */
    const val KEY_THEME = "ui_theme"
    const val DEFAULT_THEME = "system"

    /** App language (locale). Default: "" (system default). */
    const val KEY_LANGUAGE = "ui_language"
    const val DEFAULT_LANGUAGE = ""

    // ── Layout ─────────────────────────────────────────────────────────
    /** Sidebar position: "left", "right". Default: "left". */
    const val KEY_SIDEBAR_POSITION = "ui_sidebar_position"
    const val DEFAULT_SIDEBAR_POSITION = "left"

    /** File tree width in dp. Default: 260. */
    const val KEY_FILE_TREE_WIDTH = "ui_file_tree_width_dp"
    const val DEFAULT_FILE_TREE_WIDTH = 260

    /** Show the editor tabs bar. Default: true. */
    const val KEY_SHOW_TABS = "ui_show_tabs"
    const val DEFAULT_SHOW_TABS = true

    /** Show the status bar. Default: true. */
    const val KEY_SHOW_STATUS_BAR = "ui_show_status_bar"
    const val DEFAULT_SHOW_STATUS_BAR = true

    /** Show the toolbar. Default: true. */
    const val KEY_SHOW_TOOLBAR = "ui_show_toolbar"
    const val DEFAULT_SHOW_TOOLBAR = true

    // ── Behavior ───────────────────────────────────────────────────────
    /** Confirm before closing modified files. Default: true. */
    const val KEY_CONFIRM_CLOSE_MODIFIED = "ui_confirm_close_modified"
    const val DEFAULT_CONFIRM_CLOSE_MODIFIED = true

    /** Restore open files on app start. Default: true. */
    const val KEY_RESTORE_SESSION = "ui_restore_session"
    const val DEFAULT_RESTORE_SESSION = true

    /** Show recent files on launch. Default: false. */
    const val KEY_SHOW_RECENT_ON_LAUNCH = "ui_show_recent_on_launch"
    const val DEFAULT_SHOW_RECENT_ON_LAUNCH = false
}

/**
 * File explorer preference keys and defaults.
 *
 * Based on AndroidIDE's file browser settings and Sketchware-IA's file manager.
 */
object FileExplorerPrefs {

    /** Show hidden files (starting with .). Default: false. */
    const val KEY_SHOW_HIDDEN_FILES = "file_explorer_show_hidden"
    const val DEFAULT_SHOW_HIDDEN_FILES = false

    /** Sort files by: "name", "date", "size", "type". Default: "name". */
    const val KEY_SORT_BY = "file_explorer_sort_by"
    const val DEFAULT_SORT_BY = "name"

    /** Sort order: "asc", "desc". Default: "asc". */
    const val KEY_SORT_ORDER = "file_explorer_sort_order"
    const val DEFAULT_SORT_ORDER = "asc"

    /** Show file extensions. Default: true. */
    const val KEY_SHOW_EXTENSIONS = "file_explorer_show_extensions"
    const val DEFAULT_SHOW_EXTENSIONS = true

    /** Show folders first in sorted listings. Default: true. */
    const val KEY_FOLDERS_FIRST = "file_explorer_folders_first"
    const val DEFAULT_FOLDERS_FIRST = true
}

/**
 * Git preference keys and defaults.
 *
 * Based on AndroidIDE's Git settings and JGit configuration.
 */
object GitPrefs {

    /** Git user name for commits. Default: "". */
    const val KEY_USER_NAME = "git_user_name"
    const val DEFAULT_USER_NAME = ""

    /** Git user email for commits. Default: "". */
    const val KEY_USER_EMAIL = "git_user_email"
    const val DEFAULT_USER_EMAIL = ""

    /** Sign commits with GPG. Default: false. */
    const val KEY_SIGN_COMMITS = "git_sign_commits"
    const val DEFAULT_SIGN_COMMITS = false

    /** Auto-fetch from remote. Default: false. */
    const val KEY_AUTO_FETCH = "git_auto_fetch"
    const val DEFAULT_AUTO_FETCH = false

    /** Auto-fetch interval in minutes. Default: 15. */
    const val KEY_AUTO_FETCH_INTERVAL = "git_auto_fetch_interval_min"
    const val DEFAULT_AUTO_FETCH_INTERVAL = 15

    /** Git push default: "simple", "current", "matching", "upstream". Default: "simple". */
    const val KEY_PUSH_DEFAULT = "git_push_default"
    const val DEFAULT_PUSH_DEFAULT = "simple"

    /** Default branch name for new repos. Default: "main". */
    const val KEY_DEFAULT_BRANCH = "git_default_branch"
    const val DEFAULT_DEFAULT_BRANCH = "main"
}

/**
 * LSP (Language Server Protocol) preference keys and defaults.
 *
 * Based on AndroidIDE's LSP configuration, which allows configuring
 * the Java language server (jdt.ls) and other LSP servers.
 */
object LspPrefs {

    /** Enable LSP for Java. Default: true. */
    const val KEY_JAVA_LSP_ENABLED = "lsp_java_enabled"
    const val DEFAULT_JAVA_LSP_ENABLED = true

    /** Path to jdt.ls installation. Default: auto-detected. */
    const val KEY_JAVA_LSP_PATH = "lsp_java_path"
    const val DEFAULT_JAVA_LSP_PATH = ""

    /** JVM arguments for jdt.ls. */
    const val KEY_JAVA_LSP_JVM_ARGS = "lsp_java_jvm_args"
    const val DEFAULT_JAVA_LSP_JVM_ARGS = "-Xmx512m -XX:+UseG1GC"

    /** Workspace root for the Java LSP. Default: project root. */
    const val KEY_JAVA_LSP_WORKSPACE = "lsp_java_workspace"
    const val DEFAULT_JAVA_LSP_WORKSPACE = ""

    /** Enable auto-completion from LSP. Default: true. */
    const val KEY_AUTO_COMPLETION_ENABLED = "lsp_auto_completion"
    const val DEFAULT_AUTO_COMPLETION_ENABLED = true

    /** Enable LSP diagnostics. Default: true. */
    const val KEY_DIAGNOSTICS_ENABLED = "lsp_diagnostics"
    const val DEFAULT_DIAGNOSTICS_ENABLED = true

    /** Completion trigger delay in ms. Default: 150. */
    const val KEY_COMPLETION_DELAY = "lsp_completion_delay_ms"
    const val DEFAULT_COMPLETION_DELAY = 150

    /** Maximum completion items to show. Default: 50. */
    const val KEY_MAX_COMPLETION_ITEMS = "lsp_max_completion_items"
    const val DEFAULT_MAX_COMPLETION_ITEMS = 50
}

/**
 * AI copilot preference keys and defaults.
 */
object AiPrefs {

    /** AI provider: "openai", "claude", "gemini". Default: "openai". */
    const val KEY_PROVIDER = "ai_provider"
    const val DEFAULT_PROVIDER = "openai"

    /** API key for the AI provider. */
    const val KEY_API_KEY = "ai_api_key"
    const val DEFAULT_API_KEY = ""

    /** Model name (e.g. "gpt-4", "claude-3-opus"). Default: "gpt-4". */
    const val KEY_MODEL = "ai_model"
    const val DEFAULT_MODEL = "gpt-4"

    /** Base URL for the AI API. */
    const val KEY_BASE_URL = "ai_base_url"
    const val DEFAULT_BASE_URL = "https://api.openai.com/v1"

    /** Temperature (0.0-2.0). Default: 0.7. */
    const val KEY_TEMPERATURE = "ai_temperature"
    const val DEFAULT_TEMPERATURE = 0.7f

    /** Maximum tokens for responses. Default: 4096. */
    const val KEY_MAX_TOKENS = "ai_max_tokens"
    const val DEFAULT_MAX_TOKENS = 4096

    /** System prompt for the AI. */
    const val KEY_SYSTEM_PROMPT = "ai_system_prompt"
    const val DEFAULT_SYSTEM_PROMPT = "You are a helpful coding assistant inside the XCoder IDE."

    /** Stream responses (SSE). Default: true. */
    const val KEY_STREAM_RESPONSES = "ai_stream_responses"
    const val DEFAULT_STREAM_RESPONSES = true

    /** Enable inline AI suggestions. Default: true. */
    const val KEY_INLINE_SUGGESTIONS = "ai_inline_suggestions"
    const val DEFAULT_INLINE_SUGGESTIONS = true
}

/**
 * All preference categories for enumeration in settings screens.
 */
val ALL_PREFERENCE_CATEGORIES = listOf(
    EditorPrefs::class.simpleName to "Code Editor",
    TerminalPrefs::class.simpleName to "Terminal",
    BuildPrefs::class.simpleName to "Build",
    UIPrefs::class.simpleName to "Appearance",
    FileExplorerPrefs::class.simpleName to "File Explorer",
    GitPrefs::class.simpleName to "Git",
    LspPrefs::class.simpleName to "Language Servers",
    AiPrefs::class.simpleName to "AI Assistant",
)
