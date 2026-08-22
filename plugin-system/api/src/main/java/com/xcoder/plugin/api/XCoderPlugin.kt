package com.xcoder.plugin.api

/**
 * Base interface that all XCoder IDE plugins must implement.
 * The plugin class must have a no-argument constructor and be registered
 * in the plugin's manifest file.
 */
interface XCoderPlugin {

    /** Called when the plugin is loaded by XCoder IDE. */
    fun onLoad(context: PluginContext)

    /** Called when the plugin is unloaded or XCoder IDE is shutting down. */
    fun onUnload()

    /** Returns the display name of the plugin. */
    fun getName(): String

    /** Returns the version string (e.g., "1.0.0"). */
    fun getVersion(): String

    /** Returns a short description of the plugin's functionality. */
    fun getDescription(): String

    /** Returns the author's name. */
    fun getAuthor(): String = "Unknown"

    /** Returns the minimum XCoder IDE version required. */
    fun getMinAppVersion(): String = "1.0.0"

    /** Returns the plugin's unique identifier (reverse domain name). */
    fun getPluginId(): String

    /** Returns the permissions the plugin needs. */
    fun getRequiredPermissions(): List<PluginPermission> = emptyList()
}

enum class PluginPermission {
    FILE_READ, FILE_WRITE, TERMINAL_EXECUTE, NETWORK_ACCESS,
    CLIPBOARD_READ, CLIPBOARD_WRITE, NOTIFICATIONS
}

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class PluginMetadata(
    val id: String,
    val name: String,
    val version: String = "1.0.0",
    val description: String = "",
    val author: String = "Unknown",
    val minAppVersion: String = "1.0.0"
)