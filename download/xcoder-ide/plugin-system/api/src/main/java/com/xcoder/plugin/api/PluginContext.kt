package com.xcoder.plugin.api

import android.content.Context

/**
 * Provides a safe API surface for plugins to interact with XCoder IDE.
 * All operations go through permission checks before execution.
 */
interface PluginContext {

    /** Returns the Android Context for resource access. */
    fun getApplicationContext(): Context

    /** Returns the plugin's private data directory. */
    fun getPluginDataDir(): String

    /** Returns the current project directory path. */
    fun getProjectDir(): String?

    /** Read a file from the current project. Requires FILE_READ permission. */
    fun readFile(path: String): String?

    /** Write content to a file in the current project. Requires FILE_WRITE permission. */
    fun writeFile(path: String, content: String): Boolean

    /** Execute a shell command. Requires TERMINAL_EXECUTE permission. */
    fun executeCommand(command: String, callback: CommandCallback?)

    /** Log a message to the XCoder IDE log panel. */
    fun log(level: LogLevel, tag: String, message: String)

    /** Show a toast message to the user. */
    fun showToast(message: String)

    /** Show a notification. Requires NOTIFICATIONS permission. */
    fun showNotification(title: String, message: String)

    /** Register a hook for a specific event. */
    fun registerHook(hook: HookPoint, handler: HookHandler)

    /** Unregister a previously registered hook. */
    fun unregisterHook(hook: HookPoint, handler: HookHandler)

    /** Check if a specific permission is granted to this plugin. */
    fun hasPermission(permission: PluginPermission): Boolean

    /** Request a permission from the user. */
    fun requestPermission(permission: PluginPermission, callback: PermissionCallback)
}

enum class LogLevel { DEBUG, INFO, WARN, ERROR }

interface CommandCallback {
    fun onOutput(line: String)
    fun onError(line: String)
    fun onCompleted(exitCode: Int)
}

interface HookHandler {
    fun onHookFired(event: HookEvent)
}

interface PermissionCallback {
    fun onGranted(permission: PluginPermission)
    fun onDenied(permission: PluginPermission)
}

data class HookEvent(
    val hookPoint: HookPoint,
    val data: Map<String, Any> = emptyMap(),
    var consumed: Boolean = false,
    var result: Any? = null
)