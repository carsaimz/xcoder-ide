package com.xcoder.plugin.loader

import android.content.Context
import android.util.Log
import com.xcoder.plugin.api.*
import com.xcoder.plugin.loader.PluginLoader.LoadResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PluginManager @Inject constructor(
    private val pluginLoader: PluginLoader,
    private val pluginRegistry: PluginRegistry,
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "XCoderPluginManager"
        private const val PLUGINS_DIR = "plugins"
        private const val PLUGIN_STATE_FILE = "plugin_states.json"
    }

    private val _plugins = MutableStateFlow<List<LoadedPlugin>>(emptyList())
    val plugins: StateFlow<List<LoadedPlugin>> = _plugins.asStateFlow()

    private val pluginDir: File by lazy {
        File(context.filesDir, PLUGINS_DIR).also { it.mkdirs() }
    }

    suspend fun installPlugin(pluginFile: File): Result<LoadedPlugin> = withContext(Dispatchers.IO) {
        try {
            val targetFile = File(pluginDir, pluginFile.name)
            if (targetFile.exists()) targetFile.delete()
            pluginFile.copyTo(targetFile)

            val loadResult = pluginLoader.loadFromFile(targetFile)
            val pluginContext = createPluginContext(loadResult)
            loadResult.plugin.onLoad(pluginContext)

            val loaded = LoadedPlugin(
                id = loadResult.pluginId,
                name = loadResult.plugin.getName(),
                version = loadResult.plugin.getVersion(),
                description = loadResult.plugin.getDescription(),
                plugin = loadResult.plugin,
                context = pluginContext,
                sourceFile = targetFile,
                isEnabled = true,
                isInstalled = true
            )

            val current = _plugins.value.toMutableList()
            current.add(loaded)
            _plugins.value = current
            pluginRegistry.register(loaded)
            savePluginStates()

            Log.i(TAG, "Installed plugin: ${loaded.name} v${loaded.version}")
            Result.success(loaded)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to install plugin", e)
            Result.failure(e)
        }
    }

    suspend fun uninstallPlugin(pluginId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val plugin = _plugins.value.find { it.id == pluginId }
        if (plugin == null) return@withContext Result.failure(IllegalArgumentException("Plugin not found: $pluginId"))

        try {
            plugin.plugin.onUnload()
            pluginRegistry.unregister(pluginId)
            plugin.sourceFile.delete()
            _plugins.value = _plugins.value.filter { it.id != pluginId }
            savePluginStates()
            Log.i(TAG, "Uninstalled plugin: $pluginId")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun enablePlugin(pluginId: String) {
        updatePluginState(pluginId) { it.copy(isEnabled = true) }
        _plugins.value.find { it.id == pluginId }?.let {
            pluginRegistry.register(it)
        }
    }

    fun disablePlugin(pluginId: String) {
        updatePluginState(pluginId) { it.copy(isEnabled = false) }
        pluginRegistry.unregister(pluginId)
    }

    suspend fun loadAllPlugins() = withContext(Dispatchers.IO) {
        val results = pluginLoader.loadFromDirectory(pluginDir)
        val loaded = mutableListOf<LoadedPlugin>()
        for (result in results) {
            try {
                val ctx = createPluginContext(result)
                result.plugin.onLoad(ctx)
                val lp = LoadedPlugin(
                    id = result.pluginId, name = result.plugin.getName(),
                    version = result.plugin.getVersion(), description = result.plugin.getDescription(),
                    plugin = result.plugin, context = ctx, sourceFile = result.sourceFile,
                    isEnabled = true, isInstalled = true
                )
                loaded.add(lp)
                pluginRegistry.register(lp)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load plugin from ${result.sourceFile}", e)
            }
        }
        _plugins.value = loaded
        Log.i(TAG, "Loaded ${loaded.size} plugins")
    }

    private fun createPluginContext(loadResult: LoadResult): PluginContext {
        // Return a real implementation; simplified here
        return object : PluginContext {
            override fun getApplicationContext(): Context = context
            override fun getPluginDataDir(): String =
                File(context.filesDir, "plugin_data/${loadResult.pluginId}").also { it.mkdirs() }.absolutePath
            override fun getProjectDir(): String? = null
            override fun readFile(path: String): String? = try { File(path).readText() } catch (_: Exception) { null }
            override fun writeFile(path: String, content: String): Boolean = try { File(path).writeText(content); true } catch (_: Exception) { false }
            override fun executeCommand(command: String, callback: CommandCallback?) {}
            override fun log(level: LogLevel, tag: String, message: String) {
                when (level) { LogLevel.ERROR -> Log.e(tag, message) else -> Log.i(tag, message) }
            }
            override fun showToast(message: String) { android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show() }
            override fun showNotification(title: String, message: String) {}
            override fun registerHook(hook: HookPoint, handler: HookHandler) {}
            override fun unregisterHook(hook: HookPoint, handler: HookHandler) {}
            override fun hasPermission(permission: PluginPermission): Boolean = true
            override fun requestPermission(permission: PluginPermission, callback: PermissionCallback) { callback.onGranted(permission) }
        }
    }

    private fun updatePluginState(pluginId: String, transform: (LoadedPlugin) -> LoadedPlugin) {
        _plugins.value = _plugins.value.map {
            if (it.id == pluginId) transform(it) else it
        }
        savePluginStates()
    }

    private fun savePluginStates() {
        val states = _plugins.value.map { mapOf("id" to it.id, "enabled" to it.isEnabled) }
        val file = File(pluginDir, PLUGIN_STATE_FILE)
        file.writeText(com.google.gson.Gson().toJson(states))
    }
}

data class LoadedPlugin(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val plugin: XCoderPlugin,
    val context: PluginContext,
    val sourceFile: File,
    val isEnabled: Boolean,
    val isInstalled: Boolean
)