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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "suara_settings")

data class UserSettings(
    val apiKey: String = "",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicMonet: Boolean = true,
    val modelId: String = "gemini-3.8-flash-lite-tts",
    val apiBaseUrl: String = "https://generativelanguage.googleapis.com",
    val apiVersion: String = "v1beta",
    val sampleRateHz: Int = 24_000,
    val downloadDirectory: String = "${Environment.DIRECTORY_DOWNLOADS}/GeminiVoiceStudio",
    val downloadTreeUri: String = "",
    val downloadFolderLabel: String = "Downloads/GeminiVoiceStudio",
    val dailyRequestLimit: Int = 50,
    val dailyTokenLimit: Int = 100_000,
    val usageDate: String = "",
    val requestsToday: Int = 0,
    val promptTokensToday: Int = 0,
    val audioTokensToday: Int = 0,
    val totalTokensToday: Int = 0,
    val lastTokensUsed: Int = 0,
    val lastQuotaAlert: String = ""
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
        val DOWNLOAD_TREE_URI = stringPreferencesKey("download_tree_uri")
        val DOWNLOAD_FOLDER_LABEL = stringPreferencesKey("download_folder_label")
        val DAILY_REQUEST_LIMIT = intPreferencesKey("daily_request_limit")
        val DAILY_TOKEN_LIMIT = intPreferencesKey("daily_token_limit")
        val USAGE_DATE = stringPreferencesKey("usage_date")
        val REQUESTS_TODAY = intPreferencesKey("requests_today")
        val PROMPT_TOKENS_TODAY = intPreferencesKey("prompt_tokens_today")
        val AUDIO_TOKENS_TODAY = intPreferencesKey("audio_tokens_today")
        val TOTAL_TOKENS_TODAY = intPreferencesKey("total_tokens_today")
        val LAST_TOKENS_USED = intPreferencesKey("last_tokens_used")
        val LAST_QUOTA_ALERT = stringPreferencesKey("last_quota_alert")
    }

    private fun todayKey(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    val settingsFlow: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        val rawTheme = prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name
        val theme = runCatching { ThemeMode.valueOf(rawTheme) }.getOrDefault(ThemeMode.SYSTEM)
        val savedModel = prefs[Keys.MODEL_ID]?.trim().orEmpty()
        val resolvedModel = if (savedModel.isEmpty() || savedModel == "gemini-2.5-flash") {
            "gemini-3.8-flash-lite-tts"
        } else {
            savedModel
        }

        val today = todayKey()
        val savedDate = prefs[Keys.USAGE_DATE].orEmpty()
        val isSameDay = savedDate == today

        UserSettings(
            apiKey = prefs[Keys.API_KEY].orEmpty(),
            themeMode = theme,
            useDynamicMonet = prefs[Keys.DYNAMIC_MONET] ?: true,
            modelId = resolvedModel,
            apiBaseUrl = prefs[Keys.API_BASE_URL] ?: "https://generativelanguage.googleapis.com",
            apiVersion = prefs[Keys.API_VERSION] ?: "v1beta",
            sampleRateHz = prefs[Keys.SAMPLE_RATE_HZ] ?: 24_000,
            downloadDirectory = prefs[Keys.DOWNLOAD_DIRECTORY]
                ?: "${Environment.DIRECTORY_DOWNLOADS}/GeminiVoiceStudio",
            downloadTreeUri = prefs[Keys.DOWNLOAD_TREE_URI].orEmpty(),
            downloadFolderLabel = prefs[Keys.DOWNLOAD_FOLDER_LABEL]
                ?: "Downloads/GeminiVoiceStudio",
            dailyRequestLimit = (prefs[Keys.DAILY_REQUEST_LIMIT] ?: 50).coerceAtLeast(1),
            dailyTokenLimit = (prefs[Keys.DAILY_TOKEN_LIMIT] ?: 100_000).coerceAtLeast(1_000),
            usageDate = today,
            requestsToday = if (isSameDay) (prefs[Keys.REQUESTS_TODAY] ?: 0) else 0,
            promptTokensToday = if (isSameDay) (prefs[Keys.PROMPT_TOKENS_TODAY] ?: 0) else 0,
            audioTokensToday = if (isSameDay) (prefs[Keys.AUDIO_TOKENS_TODAY] ?: 0) else 0,
            totalTokensToday = if (isSameDay) (prefs[Keys.TOTAL_TOKENS_TODAY] ?: 0) else 0,
            lastTokensUsed = if (isSameDay) (prefs[Keys.LAST_TOKENS_USED] ?: 0) else 0,
            lastQuotaAlert = if (isSameDay) prefs[Keys.LAST_QUOTA_ALERT].orEmpty() else ""
        )
    }

    suspend fun saveThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode.name
        }
    }

    suspend fun saveModelId(modelId: String) {
        val clean = modelId.trim()
        if (clean.isEmpty()) return
        context.dataStore.edit { prefs ->
            prefs[Keys.MODEL_ID] = clean
        }
    }

    suspend fun saveDownloadFolder(treeUri: String, label: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DOWNLOAD_TREE_URI] = treeUri.trim()
            prefs[Keys.DOWNLOAD_FOLDER_LABEL] = label.trim().ifEmpty { "Folder Terpilih" }
        }
    }

    suspend fun recordTtsUsage(usage: TtsUsageMetadata) {
        val today = todayKey()
        context.dataStore.edit { prefs ->
            val sameDay = prefs[Keys.USAGE_DATE] == today
            val prevReq = if (sameDay) (prefs[Keys.REQUESTS_TODAY] ?: 0) else 0
            val prevPrompt = if (sameDay) (prefs[Keys.PROMPT_TOKENS_TODAY] ?: 0) else 0
            val prevAudio = if (sameDay) (prefs[Keys.AUDIO_TOKENS_TODAY] ?: 0) else 0
            val prevTotal = if (sameDay) (prefs[Keys.TOTAL_TOKENS_TODAY] ?: 0) else 0

            val computedTotal = if (usage.totalTokens > 0) {
                usage.totalTokens
            } else {
                usage.promptTokens + usage.audioTokens
            }

            prefs[Keys.USAGE_DATE] = today
            prefs[Keys.REQUESTS_TODAY] = prevReq + 1
            prefs[Keys.PROMPT_TOKENS_TODAY] = prevPrompt + usage.promptTokens
            prefs[Keys.AUDIO_TOKENS_TODAY] = prevAudio + usage.audioTokens
            prefs[Keys.TOTAL_TOKENS_TODAY] = prevTotal + computedTotal
            prefs[Keys.LAST_TOKENS_USED] = computedTotal
            prefs[Keys.LAST_QUOTA_ALERT] = ""
        }
    }

    suspend fun recordQuotaAlert(alertMessage: String, serverLimit: Int?) {
        val today = todayKey()
        context.dataStore.edit { prefs ->
            prefs[Keys.USAGE_DATE] = today
            prefs[Keys.LAST_QUOTA_ALERT] = alertMessage
            if (serverLimit != null && serverLimit > 0) {
                prefs[Keys.DAILY_REQUEST_LIMIT] = serverLimit
            }
        }
    }

    suspend fun resetDailyUsage() {
        val today = todayKey()
        context.dataStore.edit { prefs ->
            prefs[Keys.USAGE_DATE] = today
            prefs[Keys.REQUESTS_TODAY] = 0
            prefs[Keys.PROMPT_TOKENS_TODAY] = 0
            prefs[Keys.AUDIO_TOKENS_TODAY] = 0
            prefs[Keys.TOTAL_TOKENS_TODAY] = 0
            prefs[Keys.LAST_TOKENS_USED] = 0
            prefs[Keys.LAST_QUOTA_ALERT] = ""
        }
    }

    suspend fun saveAll(
        apiKey: String,
        themeMode: ThemeMode,
        useDynamicMonet: Boolean,
        modelId: String,
        apiBaseUrl: String,
        apiVersion: String,
        sampleRateHz: Int,
        dailyRequestLimit: Int,
        dailyTokenLimit: Int
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.API_KEY] = apiKey.trim()
            prefs[Keys.THEME_MODE] = themeMode.name
            prefs[Keys.DYNAMIC_MONET] = useDynamicMonet
            val cleanModel = modelId.trim()
            prefs[Keys.MODEL_ID] = if (cleanModel.isEmpty() || cleanModel == "gemini-2.5-flash") {
                "gemini-3.8-flash-lite-tts"
            } else {
                cleanModel
            }
            prefs[Keys.API_BASE_URL] = apiBaseUrl.trim().trimEnd('/').ifEmpty {
                "https://generativelanguage.googleapis.com"
            }
            prefs[Keys.API_VERSION] = apiVersion.trim().ifEmpty { "v1beta" }
            prefs[Keys.SAMPLE_RATE_HZ] = sampleRateHz.coerceIn(16_000, 48_000)
            prefs[Keys.DAILY_REQUEST_LIMIT] = dailyRequestLimit.coerceIn(1, 100_000)
            prefs[Keys.DAILY_TOKEN_LIMIT] = dailyTokenLimit.coerceIn(1_000, 50_000_000)
        }
    }
}
