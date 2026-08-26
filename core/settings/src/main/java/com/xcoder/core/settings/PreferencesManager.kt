package com.xcoder.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "xcoder_settings")

enum class ThemeMode(val key: String) {
    SYSTEM("system"), LIGHT("light"), DARK("dark");
    companion object {
        fun fromKey(key: String): ThemeMode = values().find { it.key == key } ?: SYSTEM
    }
}

enum class KeymapMode(val key: String) {
    DEFAULT("default"), VIM("vim"), EMACS("emacs"), SUBLIME("sublime");
    companion object {
        fun fromKey(key: String): KeymapMode = values().find { it.key == key } ?: DEFAULT
    }
}

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    // ========== Global Settings ==========
    private val APP_LANGUAGE = stringPreferencesKey("app_language")

    // ========== Theme Settings ==========
    private val THEME_MODE = stringPreferencesKey("theme_mode")
    private val FONT_SIZE = floatPreferencesKey("font_size")
    private val EDITOR_FONT = stringPreferencesKey("editor_font")
    private val TAB_SIZE = intPreferencesKey("tab_size")
    private val WORD_WRAP = booleanPreferencesKey("word_wrap")
    private val LINE_NUMBERS = booleanPreferencesKey("line_numbers")
    private val MINIMAP = booleanPreferencesKey("minimap")
    private val HIGHLIGHT_CURRENT_LINE = booleanPreferencesKey("highlight_current_line")
    private val AUTO_SAVE = booleanPreferencesKey("auto_save")
    private val AUTO_SAVE_INTERVAL = intPreferencesKey("auto_save_interval_ms")
    private val KEYMAP = stringPreferencesKey("keymap")
    private val SOFT_WRAP = booleanPreferencesKey("soft_wrap")
    private val SHOW_WHITESPACE = booleanPreferencesKey("show_whitespace")
    private val SHOW_INDENT_GUIDES = booleanPreferencesKey("show_indent_guides")
    private val BRACKET_MATCHING = booleanPreferencesKey("bracket_matching")
    private val CODE_COMPLETION = booleanPreferencesKey("code_completion")
    private val FONT_LIGATURES = booleanPreferencesKey("font_ligatures")

    // ========== AI Provider Settings ==========
    private val AI_PROVIDER = stringPreferencesKey("ai_provider")
    private val AI_API_KEY = stringPreferencesKey("ai_api_key")
    private val AI_MODEL = stringPreferencesKey("ai_model")
    private val AI_BASE_URL = stringPreferencesKey("ai_base_url")
    private val AI_TEMPERATURE = floatPreferencesKey("ai_temperature")
    private val AI_MAX_TOKENS = intPreferencesKey("ai_max_tokens")
    private val AI_SYSTEM_PROMPT = stringPreferencesKey("ai_system_prompt")
    private val AI_STREAM_RESPONSES = booleanPreferencesKey("ai_stream_responses")

    // ========== Terminal Settings ==========
    private val TERMINAL_SHELL_PATH = stringPreferencesKey("terminal_shell_path")
    private val TERMINAL_FONT_SIZE = floatPreferencesKey("terminal_font_size")
    private val TERMINAL_FONT_FAMILY = stringPreferencesKey("terminal_font_family")
    private val TERMINAL_CURSOR_STYLE = stringPreferencesKey("terminal_cursor_style")
    private val TERMINAL_SCROLLBACK_LINES = intPreferencesKey("terminal_scrollback_lines")
    private val TERMINAL_BELL = booleanPreferencesKey("terminal_bell")

    // ========== Build Settings ==========
    private val JDK_PATH = stringPreferencesKey("jdk_path")
    private val SDK_PATH = stringPreferencesKey("sdk_path")
    private val NDK_PATH = stringPreferencesKey("ndk_path")
    private val GRADLE_PATH = stringPreferencesKey("gradle_path")
    private val CMAKE_PATH = stringPreferencesKey("cmake_path")
    private val BUILD_TOOL = stringPreferencesKey("build_tool")

    // ========== File Explorer Settings ==========
    private val SHOW_HIDDEN_FILES = booleanPreferencesKey("show_hidden_files")
    private val SORT_BY = stringPreferencesKey("sort_by")
    private val SORT_ORDER = stringPreferencesKey("sort_order")
    private val FILE_TREE_WIDTH = intPreferencesKey("file_tree_width_dp")

    // ========== Git Settings ==========
    private val GIT_USER_NAME = stringPreferencesKey("git_user_name")
    private val GIT_USER_EMAIL = stringPreferencesKey("git_user_email")
    private val GIT_SIGN_COMMITS = booleanPreferencesKey("git_sign_commits")
    private val GIT_AUTO_FETCH = booleanPreferencesKey("git_auto_fetch")
    private val GIT_PUSH_DEFAULT = stringPreferencesKey("git_push_default")

    // ========== Global StateFlows ==========
    /** BCP-47 application language tag; pt-PT is the product default. */
    val appLanguage: Flow<String> = dataStore.data.map { prefs ->
        prefs[APP_LANGUAGE] ?: "pt-PT"
    }

    // ========== Theme StateFlows ==========
    val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs ->
        ThemeMode.fromKey(prefs[THEME_MODE] ?: ThemeMode.SYSTEM.key)
    }

    val fontSize: Flow<Float> = dataStore.data.map { prefs ->
        prefs[FONT_SIZE] ?: 14f
    }

    val editorFont: Flow<String> = dataStore.data.map { prefs ->
        prefs[EDITOR_FONT] ?: "JetBrains Mono"
    }

    val tabSize: Flow<Int> = dataStore.data.map { prefs ->
        prefs[TAB_SIZE] ?: 4
    }

    val wordWrap: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[WORD_WRAP] ?: false
    }

    val lineNumbers: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[LINE_NUMBERS] ?: true
    }

    val minimap: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[MINIMAP] ?: true
    }

    val highlightCurrentLine: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[HIGHLIGHT_CURRENT_LINE] ?: true
    }

    val autoSave: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[AUTO_SAVE] ?: true
    }

    val autoSaveInterval: Flow<Int> = dataStore.data.map { prefs ->
        prefs[AUTO_SAVE_INTERVAL] ?: 3000
    }

    val keymap: Flow<KeymapMode> = dataStore.data.map { prefs ->
        KeymapMode.fromKey(prefs[KEYMAP] ?: KeymapMode.DEFAULT.key)
    }

    val softWrap: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[SOFT_WRAP] ?: false
    }

    val showWhitespace: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[SHOW_WHITESPACE] ?: false
    }

    val showIndentGuides: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[SHOW_INDENT_GUIDES] ?: true
    }

    val bracketMatching: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[BRACKET_MATCHING] ?: true
    }

    val codeCompletion: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[CODE_COMPLETION] ?: true
    }

    val fontLigatures: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[FONT_LIGATURES] ?: true
    }

    // ========== AI StateFlows ==========
    val aiProvider: Flow<String> = dataStore.data.map { prefs ->
        prefs[AI_PROVIDER] ?: "openai"
    }

    val aiApiKey: Flow<String> = dataStore.data.map { prefs ->
        prefs[AI_API_KEY] ?: ""
    }

    val aiModel: Flow<String> = dataStore.data.map { prefs ->
        prefs[AI_MODEL] ?: "gpt-4"
    }

    val aiBaseUrl: Flow<String> = dataStore.data.map { prefs ->
        prefs[AI_BASE_URL] ?: "https://api.openai.com/v1"
    }

    val aiTemperature: Flow<Float> = dataStore.data.map { prefs ->
        prefs[AI_TEMPERATURE] ?: 0.7f
    }

    val aiMaxTokens: Flow<Int> = dataStore.data.map { prefs ->
        prefs[AI_MAX_TOKENS] ?: 4096
    }

    val aiSystemPrompt: Flow<String> = dataStore.data.map { prefs ->
        prefs[AI_SYSTEM_PROMPT] ?: "You are a helpful coding assistant inside the XCoder IDE."
    }

    val aiStreamResponses: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[AI_STREAM_RESPONSES] ?: true
    }

    // ========== Terminal StateFlows ==========
    val terminalShellPath: Flow<String> = dataStore.data.map { prefs ->
        prefs[TERMINAL_SHELL_PATH] ?: "/system/bin/sh"
    }

    val terminalFontSize: Flow<Float> = dataStore.data.map { prefs ->
        prefs[TERMINAL_FONT_SIZE] ?: 13f
    }

    val terminalFontFamily: Flow<String> = dataStore.data.map { prefs ->
        prefs[TERMINAL_FONT_FAMILY] ?: "Monospace"
    }

    val terminalCursorStyle: Flow<String> = dataStore.data.map { prefs ->
        prefs[TERMINAL_CURSOR_STYLE] ?: "block"
    }

    val terminalScrollbackLines: Flow<Int> = dataStore.data.map { prefs ->
        prefs[TERMINAL_SCROLLBACK_LINES] ?: 10000
    }

    val terminalBell: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[TERMINAL_BELL] ?: false
    }

    // ========== Build StateFlows ==========
    val jdkPath: Flow<String> = dataStore.data.map { prefs ->
        prefs[JDK_PATH] ?: "/data/data/com.xcoder.ide/files/jdk"
    }

    val sdkPath: Flow<String> = dataStore.data.map { prefs ->
        prefs[SDK_PATH] ?: "/data/data/com.xcoder.ide/files/android-sdk"
    }

    val ndkPath: Flow<String> = dataStore.data.map { prefs ->
        prefs[NDK_PATH] ?: ""
    }

    val gradlePath: Flow<String> = dataStore.data.map { prefs ->
        prefs[GRADLE_PATH] ?: ""
    }

    val cmakePath: Flow<String> = dataStore.data.map { prefs ->
        prefs[CMAKE_PATH] ?: ""
    }

    val buildTool: Flow<String> = dataStore.data.map { prefs ->
        prefs[BUILD_TOOL] ?: "gradle"
    }

    // ========== File Explorer StateFlows ==========
    val showHiddenFiles: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[SHOW_HIDDEN_FILES] ?: false
    }

    val sortBy: Flow<String> = dataStore.data.map { prefs ->
        prefs[SORT_BY] ?: "name"
    }

    val sortOrder: Flow<String> = dataStore.data.map { prefs ->
        prefs[SORT_ORDER] ?: "asc"
    }

    val fileTreeWidth: Flow<Int> = dataStore.data.map { prefs ->
        prefs[FILE_TREE_WIDTH] ?: 250
    }

    // ========== Git StateFlows ==========
    val gitUserName: Flow<String> = dataStore.data.map { prefs ->
        prefs[GIT_USER_NAME] ?: ""
    }

    val gitUserEmail: Flow<String> = dataStore.data.map { prefs ->
        prefs[GIT_USER_EMAIL] ?: ""
    }

    val gitSignCommits: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[GIT_SIGN_COMMITS] ?: false
    }

    val gitAutoFetch: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[GIT_AUTO_FETCH] ?: false
    }

    val gitPushDefault: Flow<String> = dataStore.data.map { prefs ->
        prefs[GIT_PUSH_DEFAULT] ?: "simple"
    }

    // ========== Setters (suspend functions) ==========
    suspend fun setAppLanguage(languageTag: String) {
        dataStore.edit { prefs -> prefs[APP_LANGUAGE] = languageTag }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { prefs -> prefs[THEME_MODE] = mode.key }
    }

    suspend fun setFontSize(size: Float) {
        dataStore.edit { prefs -> prefs[FONT_SIZE] = size.coerceIn(8f, 32f) }
    }

    suspend fun setEditorFont(font: String) {
        dataStore.edit { prefs -> prefs[EDITOR_FONT] = font }
    }

    suspend fun setTabSize(size: Int) {
        dataStore.edit { prefs -> prefs[TAB_SIZE] = size.coerceIn(1, 16) }
    }

    suspend fun setWordWrap(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[WORD_WRAP] = enabled }
    }

    suspend fun setLineNumbers(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[LINE_NUMBERS] = enabled }
    }

    suspend fun setMinimap(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[MINIMAP] = enabled }
    }

    suspend fun setHighlightCurrentLine(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[HIGHLIGHT_CURRENT_LINE] = enabled }
    }

    suspend fun setAutoSave(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[AUTO_SAVE] = enabled }
    }

    suspend fun setAutoSaveInterval(intervalMs: Int) {
        dataStore.edit { prefs -> prefs[AUTO_SAVE_INTERVAL] = intervalMs.coerceIn(500, 60000) }
    }

    suspend fun setKeymap(mode: KeymapMode) {
        dataStore.edit { prefs -> prefs[KEYMAP] = mode.key }
    }

    suspend fun setSoftWrap(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[SOFT_WRAP] = enabled }
    }

    suspend fun setShowWhitespace(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[SHOW_WHITESPACE] = enabled }
    }

    suspend fun setShowIndentGuides(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[SHOW_INDENT_GUIDES] = enabled }
    }

    suspend fun setBracketMatching(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[BRACKET_MATCHING] = enabled }
    }

    suspend fun setCodeCompletion(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[CODE_COMPLETION] = enabled }
    }

    suspend fun setFontLigatures(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[FONT_LIGATURES] = enabled }
    }

    // ========== AI Setters ==========
    suspend fun setAiProvider(provider: String) {
        dataStore.edit { prefs -> prefs[AI_PROVIDER] = provider }
    }

    suspend fun setAiApiKey(apiKey: String) {
        dataStore.edit { prefs -> prefs[AI_API_KEY] = apiKey }
    }

    suspend fun setAiModel(model: String) {
        dataStore.edit { prefs -> prefs[AI_MODEL] = model }
    }

    suspend fun setAiBaseUrl(url: String) {
        dataStore.edit { prefs -> prefs[AI_BASE_URL] = url }
    }

    suspend fun setAiTemperature(temperature: Float) {
        dataStore.edit { prefs -> prefs[AI_TEMPERATURE] = temperature.coerceIn(0f, 2f) }
    }

    suspend fun setAiMaxTokens(tokens: Int) {
        dataStore.edit { prefs -> prefs[AI_MAX_TOKENS] = tokens.coerceIn(1, 128000) }
    }

    suspend fun setAiSystemPrompt(prompt: String) {
        dataStore.edit { prefs -> prefs[AI_SYSTEM_PROMPT] = prompt }
    }

    suspend fun setAiStreamResponses(stream: Boolean) {
        dataStore.edit { prefs -> prefs[AI_STREAM_RESPONSES] = stream }
    }

    // ========== Terminal Setters ==========
    suspend fun setTerminalShellPath(path: String) {
        dataStore.edit { prefs -> prefs[TERMINAL_SHELL_PATH] = path }
    }

    suspend fun setTerminalFontSize(size: Float) {
        dataStore.edit { prefs -> prefs[TERMINAL_FONT_SIZE] = size.coerceIn(8f, 24f) }
    }

    suspend fun setTerminalFontFamily(family: String) {
        dataStore.edit { prefs -> prefs[TERMINAL_FONT_FAMILY] = family }
    }

    suspend fun setTerminalCursorStyle(style: String) {
        dataStore.edit { prefs -> prefs[TERMINAL_CURSOR_STYLE] = style }
    }

    suspend fun setTerminalScrollbackLines(lines: Int) {
        dataStore.edit { prefs -> prefs[TERMINAL_SCROLLBACK_LINES] = lines.coerceIn(100, 100000) }
    }

    suspend fun setTerminalBell(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[TERMINAL_BELL] = enabled }
    }

    // ========== Build Setters ==========
    suspend fun setJdkPath(path: String) {
        dataStore.edit { prefs -> prefs[JDK_PATH] = path }
    }

    suspend fun setSdkPath(path: String) {
        dataStore.edit { prefs -> prefs[SDK_PATH] = path }
    }

    suspend fun setNdkPath(path: String) {
        dataStore.edit { prefs -> prefs[NDK_PATH] = path }
    }

    suspend fun setGradlePath(path: String) {
        dataStore.edit { prefs -> prefs[GRADLE_PATH] = path }
    }

    suspend fun setCmakePath(path: String) {
        dataStore.edit { prefs -> prefs[CMAKE_PATH] = path }
    }

    suspend fun setBuildTool(tool: String) {
        dataStore.edit { prefs -> prefs[BUILD_TOOL] = tool }
    }

    // ========== File Explorer Setters ==========
    suspend fun setShowHiddenFiles(show: Boolean) {
        dataStore.edit { prefs -> prefs[SHOW_HIDDEN_FILES] = show }
    }

    suspend fun setSortBy(sort: String) {
        dataStore.edit { prefs -> prefs[SORT_BY] = sort }
    }

    suspend fun setSortOrder(order: String) {
        dataStore.edit { prefs -> prefs[SORT_ORDER] = order }
    }

    suspend fun setFileTreeWidth(widthDp: Int) {
        dataStore.edit { prefs -> prefs[FILE_TREE_WIDTH] = widthDp.coerceIn(150, 500) }
    }

    // ========== Git Setters ==========
    suspend fun setGitUserName(name: String) {
        dataStore.edit { prefs -> prefs[GIT_USER_NAME] = name }
    }

    suspend fun setGitUserEmail(email: String) {
        dataStore.edit { prefs -> prefs[GIT_USER_EMAIL] = email }
    }

    suspend fun setGitSignCommits(sign: Boolean) {
        dataStore.edit { prefs -> prefs[GIT_SIGN_COMMITS] = sign }
    }

    suspend fun setGitAutoFetch(autoFetch: Boolean) {
        dataStore.edit { prefs -> prefs[GIT_AUTO_FETCH] = autoFetch }
    }

    suspend fun setGitPushDefault(pushDefault: String) {
        dataStore.edit { prefs -> prefs[GIT_PUSH_DEFAULT] = pushDefault }
    }

    // ========== Synchronous getters (for non-coroutine contexts) ==========
    fun getThemeModeSync(): ThemeMode = runBlocking {
        themeMode.first()
    }

    fun getFontSizeSync(): Float = runBlocking { fontSize.first() }

    fun getTabSizeSync(): Int = runBlocking { tabSize.first() }

    fun getAiProviderSync(): String = runBlocking { aiProvider.first() }

    fun getAiApiKeySync(): String = runBlocking { aiApiKey.first() }

    fun getAiModelSync(): String = runBlocking { aiModel.first() }

    fun getTerminalShellPathSync(): String = runBlocking { terminalShellPath.first() }

    fun getJdkPathSync(): String = runBlocking { jdkPath.first() }

    fun getSdkPathSync(): String = runBlocking { sdkPath.first() }

    fun getGitUserNameSync(): String = runBlocking { gitUserName.first() }

    fun getGitUserEmailSync(): String = runBlocking { gitUserEmail.first() }

    // ========== Bulk operations ==========
    suspend fun resetToDefaults() {
        dataStore.edit { prefs -> prefs.clear() }
    }

    suspend fun getAllPreferences(): Map<String, Any> {
        return dataStore.data.first().asMap().mapKeys { (key, _) -> key.name }.mapValues { (_, value) ->
            when (value) {
                is Boolean -> value
                is Float -> value
                is Int -> value
                is Long -> value
                is Double -> value
                is String -> value
                else -> value.toString()
            }
        }
    }
}
