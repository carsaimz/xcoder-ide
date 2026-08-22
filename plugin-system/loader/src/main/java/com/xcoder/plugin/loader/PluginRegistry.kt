package com.xcoder.plugin.loader

import com.xcoder.plugin.api.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registry of all active plugins and their registered hooks.
 * Provides hook dispatch to registered plugins.
 */
@Singleton
class PluginRegistry @Inject constructor() {

    private val _activePlugins = MutableStateFlow<Map<String, LoadedPlugin>>(emptyMap())
    val activePlugins: StateFlow<Map<String, LoadedPlugin>> = _activePlugins.asStateFlow()

    private val hookHandlers = mutableMapOf<HookPoint, MutableList<Pair<String, HookHandler>>>()

    fun register(plugin: LoadedPlugin) {
        _activePlugins.value = _activePlugins.value + (plugin.id to plugin)
    }

    fun unregister(pluginId: String) {
        _activePlugins.value = _activePlugins.value - pluginId
        hookHandlers.values.forEach { handlers ->
            handlers.removeAll { it.first == pluginId }
        }
    }

    fun addHook(pluginId: String, hook: HookPoint, handler: HookHandler) {
        hookHandlers.getOrPut(hook) { mutableListOf() }.add(pluginId to handler)
    }

    fun removeHook(pluginId: String, hook: HookPoint, handler: HookHandler) {
        hookHandlers[hook]?.removeAll { it.first == pluginId && it.second === handler }
    }

    fun dispatchHook(hook: HookPoint, event: HookEvent): HookEvent {
        val handlers = hookHandlers[hook] ?: return event
        for ((_, handler) in handlers) {
            if (event.consumed) break
            handler.onHookFired(event)
        }
        return event
    }

    fun getPluginsForHook(hook: HookPoint): List<String> {
        return hookHandlers[hook]?.map { it.first } ?: emptyList()
    }

    fun clear() {
        hookHandlers.clear()
        _activePlugins.value = emptyMap()
    }
}
