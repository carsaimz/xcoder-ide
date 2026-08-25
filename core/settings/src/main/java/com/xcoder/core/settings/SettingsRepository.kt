package com.xcoder.core.settings

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private val Context.exportDataStore: DataStore<Preferences> by preferencesDataStore(name = "xcoder_settings")

interface SettingsRepository {
    suspend fun <T> get(key: String, default: T): T
    suspend fun <T> set(key: String, value: T)
    suspend fun remove(key: String)
    suspend fun contains(key: String): Boolean
    suspend fun getKeys(): Set<String>
    fun <T> observe(key: String, default: T): Flow<T>
    suspend fun getAll(): Map<String, Any?>
    suspend fun clear()
    suspend fun exportToJson(): String
    suspend fun exportToFile(uri: Uri): Result<Unit>
    suspend fun importFromJson(json: String): Result<Unit>
    suspend fun importFromFile(uri: Uri): Result<Unit>
}

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) : SettingsRepository {

    private val dataStore = context.exportDataStore

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T> get(key: String, default: T): T = withContext(Dispatchers.IO) {
        val prefs = dataStore.data.first()
        val prefKey = stringPreferencesKey(key)
        val rawValue = prefs[prefKey]
        if (rawValue == null) {
            return@withContext default
        }
        val result: Any = when (default) {
            is String -> rawValue
            is Boolean -> rawValue.toBooleanStrictOrNull() ?: default
            is Int -> rawValue.toIntOrNull() ?: default
            is Long -> rawValue.toLongOrNull() ?: default
            is Float -> rawValue.toFloatOrNull() ?: default
            is Double -> rawValue.toDoubleOrNull() ?: default
            else -> rawValue
        }
        result as T
    }

    override suspend fun <T> set(key: String, value: T) {
        withContext(Dispatchers.IO) {
            val prefKey = stringPreferencesKey(key)
            dataStore.edit { prefs ->
                prefs[prefKey] = value.toString()
            }
        }
    }

    override suspend fun remove(key: String) {
        withContext(Dispatchers.IO) {
            val prefKey = stringPreferencesKey(key)
            dataStore.edit { prefs ->
                prefs.remove(prefKey)
            }
        }
    }

    override suspend fun contains(key: String): Boolean = withContext(Dispatchers.IO) {
        val prefs = dataStore.data.first()
        val prefKey = stringPreferencesKey(key)
        prefs.contains(prefKey)
    }

    override suspend fun getKeys(): Set<String> = withContext(Dispatchers.IO) {
        dataStore.data.first().asMap().keys.map { it.name }.toSet()
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> observe(key: String, default: T): Flow<T> {
        val prefKey = stringPreferencesKey(key)
        return dataStore.data.map { prefs ->
            val rawValue: String? = prefs[prefKey]
            val result: Any = if (rawValue == null) {
                default as Any
            } else {
                when (default) {
                    is String -> rawValue
                    is Boolean -> rawValue.toBooleanStrictOrNull() ?: default
                    is Int -> rawValue.toIntOrNull() ?: default
                    is Long -> rawValue.toLongOrNull() ?: default
                    is Float -> rawValue.toFloatOrNull() ?: default
                    is Double -> rawValue.toDoubleOrNull() ?: default
                    else -> rawValue
                }
            }
            result as T
        }
    }

    override suspend fun getAll(): Map<String, Any?> = withContext(Dispatchers.IO) {
        dataStore.data.first().asMap().mapKeys { it.key.name }.mapValues { it.value }
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) {
            dataStore.edit { prefs -> prefs.clear() }
        }
    }

    override suspend fun exportToJson(): String = withContext(Dispatchers.IO) {
        val allPrefs = dataStore.data.first().asMap().mapKeys { it.key.name }
        val exportData = mapOf(
            "version" to 1,
            "exported_at" to SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(Date()),
            "app" to "xcoder-ide",
            "settings" to allPrefs
        )
        gson.toJson(exportData)
    }

    override suspend fun exportToFile(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val json = exportToJson()
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(json)
                    writer.flush()
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun importFromJson(json: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val type = object : TypeToken<Map<String, Any>>() {}.type
            val root = gson.fromJson<Map<String, Any>>(json, type)
            val settings = root["settings"] as? Map<String, String>
                ?: return@withContext Result.failure(
                    IllegalArgumentException("Invalid settings export format: missing 'settings' key")
                )
            dataStore.edit { prefs ->
                for ((key, value) in settings) {
                    prefs[stringPreferencesKey(key)] = value
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun importFromFile(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val json = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).readText()
            } ?: return@withContext Result.failure(
                IllegalArgumentException("Cannot read file: $uri")
            )
            importFromJson(json)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
