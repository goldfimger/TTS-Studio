package com.suarastudio.tts.data

import android.content.Context
import android.os.Environment
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.suarastudio.tts.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "suara_settings")

data class UserSettings(
    val apiKey: String = "",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicMonet: Boolean = true,
    val modelId: String = "gemini-2.5-flash",
    val apiBaseUrl: String = "https://generativelanguage.googleapis.com",
    val apiVersion: String = "v1beta",
    val sampleRateHz: Int = 24_000,
    val downloadDirectory: String = "${Environment.DIRECTORY_DOWNLOADS}/GeminiVoiceStudio"
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val API_KEY = stringPreferencesKey("gemini_api_key")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_MONET = booleanPreferencesKey("use_dynamic_monet")
        val MODEL_ID = stringPreferencesKey("gemini_model_id")
        val API_BASE_URL = stringPreferencesKey("tts_api_base_url")
        val API_VERSION = stringPreferencesKey("tts_api_version")
        val SAMPLE_RATE_HZ = intPreferencesKey("tts_sample_rate_hz")
        val DOWNLOAD_DIRECTORY = stringPreferencesKey("download_directory")
    }

    val settingsFlow: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        val rawTheme = prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name
        val theme = runCatching { ThemeMode.valueOf(rawTheme) }.getOrDefault(ThemeMode.SYSTEM)
        UserSettings(
            apiKey = prefs[Keys.API_KEY].orEmpty(),
            themeMode = theme,
            useDynamicMonet = prefs[Keys.DYNAMIC_MONET] ?: true,
            modelId = prefs[Keys.MODEL_ID] ?: "gemini-2.5-flash",
            apiBaseUrl = prefs[Keys.API_BASE_URL] ?: "https://generativelanguage.googleapis.com",
            apiVersion = prefs[Keys.API_VERSION] ?: "v1beta",
            sampleRateHz = prefs[Keys.SAMPLE_RATE_HZ] ?: 24_000,
            downloadDirectory = prefs[Keys.DOWNLOAD_DIRECTORY]
                ?: "${Environment.DIRECTORY_DOWNLOADS}/GeminiVoiceStudio"
        )
    }

    suspend fun saveThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode.name
        }
    }

    suspend fun saveDownloadDirectory(directory: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DOWNLOAD_DIRECTORY] = directory.trim().ifEmpty {
                "${Environment.DIRECTORY_DOWNLOADS}/GeminiVoiceStudio"
            }
        }
    }

    suspend fun saveAll(
        apiKey: String,
        themeMode: ThemeMode,
        useDynamicMonet: Boolean,
        modelId: String,
        apiBaseUrl: String,
        apiVersion: String,
        sampleRateHz: Int
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.API_KEY] = apiKey.trim()
            prefs[Keys.THEME_MODE] = themeMode.name
            prefs[Keys.DYNAMIC_MONET] = useDynamicMonet
            prefs[Keys.MODEL_ID] = modelId.trim().ifEmpty { "gemini-2.5-flash" }
            prefs[Keys.API_BASE_URL] = apiBaseUrl.trim().trimEnd('/').ifEmpty {
                "https://generativelanguage.googleapis.com"
            }
            prefs[Keys.API_VERSION] = apiVersion.trim().ifEmpty { "v1beta" }
            prefs[Keys.SAMPLE_RATE_HZ] = sampleRateHz.coerceIn(16_000, 48_000)
        }
    }
}
