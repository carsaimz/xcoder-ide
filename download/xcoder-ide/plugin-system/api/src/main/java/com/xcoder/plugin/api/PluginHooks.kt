package com.xcoder.plugin.api

/**
 * Defines all available hook points where plugins can intercept IDE behavior.
 * Plugins register handlers for specific hooks to react to IDE events.
 */
enum class HookPoint(val id: String, val description: String) {
    // File events
    ON_FILE_OPEN("on_file_open", "Fired when a file is opened in the editor"),
    ON_FILE_SAVE("on_file_save", "Fired before a file is saved (can modify content)"),
    ON_FILE_SAVED("on_file_saved", "Fired after a file is saved"),
    ON_FILE_CREATED("on_file_created", "Fired when a new file is created"),
    ON_FILE_DELETED("on_file_deleted", "Fired when a file is deleted"),
    ON_FILE_RENAMED("on_file_renamed", "Fired when a file is renamed"),

    // Editor events
    ON_EDITOR_CREATED("on_editor_created", "Fired when a new editor instance is created"),
    ON_EDITOR_FOCUS_CHANGED("on_editor_focus_changed", "Fired when editor focus changes"),
    ON_EDITOR_CONTENT_CHANGED("on_editor_content_changed", "Fired when editor content changes"),
    ON_EDITOR_CURSOR_CHANGED("on_editor_cursor_changed", "Fired when cursor position changes"),

    // Build events
    ON_BUILD_START("on_build_start", "Fired before a build begins"),
    ON_BUILD_END("on_build_end", "Fired after a build completes"),
    ON_BUILD_ERROR("on_build_error", "Fired when a build fails"),

    // Terminal events
    ON_TERMINAL_COMMAND("on_terminal_command", "Fired before a terminal command executes"),
    ON_TERMINAL_OUTPUT("on_terminal_output", "Fired on terminal output"),

    // App lifecycle
    ON_APP_START("on_app_start", "Fired when XCoder IDE starts"),
    ON_APP_RESUME("on_app_resume", "Fired when XCoder IDE resumes"),
    ON_APP_PAUSE("on_app_pause", "Fired when XCoder IDE pauses"),

    // Project events
    ON_PROJECT_OPEN("on_project_open", "Fired when a project is opened"),
    ON_PROJECT_CLOSE("on_project_close", "Fired when a project is closed"),

    // Git events
    ON_GIT_COMMIT("on_git_commit", "Fired when a commit is made"),
    ON_GIT_PUSH("on_git_push", "Fired before a push"),
    ON_GIT_PULL("on_git_pull", "Fired before a pull"),

    // UI events
    ON_SETTINGS_CHANGED("on_settings_changed", "Fired when user changes settings"),
    ON_MENU_CREATED("on_menu_created", "Fired when the main menu is created (can add items)"),

    // AI events
    ON_AI_MESSAGE_SENT("on_ai_message_sent", "Fired when user sends a message to AI"),
    ON_AI_RESPONSE_RECEIVED("on_ai_response_received", "Fired when AI responds");

    companion object {
        fun fromId(id: String): HookPoint? = entries.find { it.id == id }
    }
}
