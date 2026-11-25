package com.suarastudio.tts.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

enum class GeminiVoice(val voiceName: String, val characterNote: String) {
    PUCK("Puck", "Upbeat & conversational"),
    CHARON("Charon", "Calm & authoritative"),
    KORE("Kore", "Clear & expressive"),
    FENRIR("Fenrir", "Deep & resonant"),
    AOEDE("Aoede", "Warm & melodic"),
    ZEPHYR("Zephyr", "Bright & articulate"),
    TAVI("Tavi", "Natural & friendly")
}

sealed interface TtsMode {
    data class Single(val voice: GeminiVoice) : TtsMode
    data class Multi(
        val speaker1Name: String,
        val speaker1Voice: GeminiVoice,
        val speaker2Name: String,
        val speaker2Voice: GeminiVoice
    ) : TtsMode
}

data class TtsUsageMetadata(
    val promptTokens: Int = 0,
    val audioTokens: Int = 0,
    val totalTokens: Int = 0
)

data class TtsResult(
    val audioBytes: ByteArray,
    val mimeType: String,
    val usage: TtsUsageMetadata = TtsUsageMetadata()
)

data class DetectedModelsResult(
    val ttsModels: List<String>,
    val recommendedModel: String?,
    val allGeminiModelsCount: Int = 0,
    val geminiTypeSummary: String = ""
)

open class GeminiApiException(message: String) : Exception(message)

class GeminiQuotaException(
    message: String,
    val quotaLimitFromServer: Int? = null,
    val retryDelay: String? = null,
    val isFreeTierHint: Boolean = false
) : GeminiApiException(message)

class GeminiTtsClient {

    suspend fun generateSpeech(
        apiKey: String,
        apiBaseUrl: String,
        apiVersion: String,
        modelId: String,
        scriptText: String,
        styleInstruction: String,
        mode: TtsMode
    ): TtsResult = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isEmpty()) {
            throw GeminiApiException("API Key belum diatur. Buka ikon Settings di pojok kanan atas.")
        }

        val cleanScript = scriptText.trim()
        if (cleanScript.isEmpty()) {
            throw GeminiApiException("Teks naskah tidak boleh kosong.")
        }

        val cleanBase = apiBaseUrl.trim().trimEnd('/').ifEmpty { "https://generativelanguage.googleapis.com" }
        val cleanVersion = apiVersion.trim().trim('/').ifEmpty { "v1beta" }
        val rawModel = modelId.trim().ifEmpty { "gemini-3.8-flash-lite-tts" }
        val cleanModel = when {
            rawModel == "gemini-2.5-flash" -> "gemini-3.8-flash-lite-tts"
            mode is TtsMode.Multi && rawModel == "gemini-3.8-flash-lite-tts" -> "gemini-3.8-flash-tts"
            else -> rawModel
        }

        val isLegacy25Tts = cleanModel.contains("2.5-", ignoreCase = true)
        val cleanStyle = styleInstruction.trim()

        suspend fun executeAttempt(useLegacy25Format: Boolean): TtsResult {
            val payload = buildRequestPayload(
                scriptText = cleanScript,
                styleInstruction = cleanStyle,
                mode = mode,
                isLegacy25Tts = useLegacy25Format
            )
            val endpoint = "$cleanBase/$cleanVersion/models/${cleanModel}:generateContent"

            var connection: HttpURLConnection? = null
            try {
                val url = URL(endpoint)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 20_000
                    readTimeout = 60_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    setRequestProperty("x-goog-api-key", cleanKey)
                }

                OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(payload.toString())
                    writer.flush()
                }

                val statusCode = connection.responseCode
                val stream = if (statusCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

                val responseBody = stream?.let {
                    BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { reader ->
                        reader.readText()
                    }
                }.orEmpty()

                if (statusCode !in 200..299) {
                    val errText = parseErrorMessage(statusCode, responseBody, cleanModel)
                    throw GeminiApiException(errText)
                }

                return parseAudioResponse(responseBody)
            } catch (e: GeminiApiException) {
                throw e
            } catch (_: UnknownHostException) {
                throw GeminiApiException("Tidak ada koneksi internet. Periksa jaringan Anda.")
            } catch (_: SocketTimeoutException) {
                throw GeminiApiException("Permintaan ke Gemini API timeout. Coba naskah yang lebih singkat.")
            } catch (e: Exception) {
                throw GeminiApiException(e.localizedMessage ?: "Gagal terhubung ke Gemini API.")
            } finally {
                connection?.disconnect()
            }
        }

        try {
            executeAttempt(useLegacy25Format = isLegacy25Tts)
        } catch (e: GeminiApiException) {
            val msg = e.message.orEmpty()
            if (!isLegacy25Tts && msg.contains("Speech metadata", ignoreCase = true)) {
                executeAttempt(useLegacy25Format = true)
            } else {
                throw e
            }
        }
    }

    suspend fun detectAvailableTtsModels(
        apiKey: String,
        apiBaseUrl: String,
        apiVersion: String
    ): Result<DetectedModelsResult> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanKey = apiKey.trim()
            if (cleanKey.isEmpty()) {
                throw GeminiApiException("API Key belum diisi.")
            }
            val cleanBase = apiBaseUrl.trim().trimEnd('/').ifEmpty { "https://generativelanguage.googleapis.com" }
            val cleanVersion = apiVersion.trim().trim('/').ifEmpty { "v1beta" }
            val endpoint = "$cleanBase/$cleanVersion/models?pageSize=200"

            var connection: HttpURLConnection? = null
            val responseBody: String
            val statusCode: Int
            try {
                connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10_000
                    readTimeout = 15_000
                    setRequestProperty("x-goog-api-key", cleanKey)
                }
                statusCode = connection.responseCode
                val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
                responseBody = stream?.let {
                    BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { r -> r.readText() }
                }.orEmpty()
            } finally {
                connection?.disconnect()
            }

            if (statusCode !in 200..299) {
                throw GeminiApiException(parseErrorMessage(statusCode, responseBody, "models"))
            }

            val root = JSONObject(responseBody)
            val modelsArray = root.optJSONArray("models") ?: JSONArray()
            val candidates = mutableListOf<String>()
            val allGeminiIds = mutableListOf<String>()

            for (i in 0 until modelsArray.length()) {
                val obj = modelsArray.optJSONObject(i) ?: continue
                val fullName = obj.optString("name").orEmpty()
                val shortId = fullName.removePrefix("models/").trim()
                if (shortId.isEmpty()) continue
                val lower = shortId.lowercase()
                if (lower.contains("gemini")) {
                    allGeminiIds.add(shortId)
                }

                val methodsArray = obj.optJSONArray("supportedGenerationMethods")
                val supportsGenerate = if (methodsArray == null) {
                    true
                } else {
                    (0 until methodsArray.length()).any { idx ->
                        methodsArray.optString(idx) == "generateContent"
                    }
                }
                if (!supportsGenerate) continue

                val isDedicatedTts = lower.contains("tts") ||
                    (lower.contains("speech") && !lower.includesAudioOnlyExclusion(lower))
                if (isDedicatedTts) {
                    candidates.add(shortId)
                }
            }

            val priorityOrder = listOf(
                "gemini-3.8-flash-lite-tts",
                "gemini-3.8-flash-tts",
                "gemini-3.1-flash-tts-preview",
                "gemini-2.5-flash-tts",
                "gemini-2.5-flash-preview-tts",
                "gemini-2.5-pro-preview-tts"
            )

            val sorted = candidates.distinct().sortedWith(
                compareBy<String> { model ->
                    val idx = priorityOrder.indexOfFirst { model.equals(it, ignoreCase = true) }
                    if (idx >= 0) idx else priorityOrder.size
                }.thenBy { it }
            )

            val has38 = allGeminiIds.any { it.contains("3.8", ignoreCase = true) } ||
                sorted.any { it.contains("3.8", ignoreCase = true) }
            val has31 = allGeminiIds.any { it.contains("3.1", ignoreCase = true) } ||
                sorted.any { it.contains("3.1", ignoreCase = true) }
            val has25 = allGeminiIds.any { it.contains("2.5", ignoreCase = true) } ||
                sorted.any { it.contains("2.5", ignoreCase = true) }
            val hasProTts = sorted.any { it.contains("pro", ignoreCase = true) }
            val hasProAny = allGeminiIds.any { it.contains("pro", ignoreCase = true) }

            val familyParts = buildList {
                if (has38) add("Gemini 3.8 TTS")
                else if (has31) add("Gemini 3.1 TTS")
                if (has25) add("Gemini 2.5")
                if (hasProTts) add("Pro TTS")
                else if (hasProAny) add("Pro")
            }
            val typeSummary = if (familyParts.isNotEmpty()) {
                familyParts.joinToString(" · ")
            } else {
                sorted.firstOrNull() ?: "Gemini TTS"
            }

            DetectedModelsResult(
                ttsModels = sorted,
                recommendedModel = sorted.firstOrNull(),
                allGeminiModelsCount = allGeminiIds.distinct().size,
                geminiTypeSummary = typeSummary
            )
        }
    }

    private fun String.includesAudioOnlyExclusion(lower: String): Boolean =
        lower.contains("native-audio")

    suspend fun checkApiConnection(
        apiKey: String,
        apiBaseUrl: String,
        apiVersion: String,
        @Suppress("UNUSED_PARAMETER") modelId: String
    ): Boolean = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isEmpty()) return@withContext false

        val cleanBase = apiBaseUrl.trim().trimEnd('/').ifEmpty { "https://generativelanguage.googleapis.com" }
        val cleanVersion = apiVersion.trim().trim('/').ifEmpty { "v1beta" }
        val endpoint = "$cleanBase/$cleanVersion/models?pageSize=20"

        var connection: HttpURLConnection? = null
        try {
            connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("x-goog-api-key", cleanKey)
            }
            connection.responseCode in 200..299
        } catch (_: Exception) {
            false
        } finally {
            connection?.disconnect()
        }
    }

    private fun resolveVoiceForModel(voice: GeminiVoice, isLegacy25Tts: Boolean): String {
        // Legacy 2.5 preview TTS supports the core 30 voices; map extended voices cleanly so requests never fail
        return if (isLegacy25Tts && voice == GeminiVoice.TAVI) {
            GeminiVoice.AOEDE.voiceName
        } else {
            voice.voiceName
        }
    }

    private fun buildRequestPayload(
        scriptText: String,
        styleInstruction: String,
        mode: TtsMode,
        isLegacy25Tts: Boolean
    ): JSONObject {
        val partsArray = JSONArray()

        if (isLegacy25Tts) {
            val prompt25 = if (styleInstruction.isNotEmpty()) {
                "### DIRECTOR'S NOTES\nStyle: $styleInstruction\n\n#### TRANSCRIPT\n$scriptText"
            } else {
                scriptText
            }
            partsArray.put(JSONObject().put("text", prompt25))
        } else {
            when (mode) {
                is TtsMode.Single -> {
                    val partObj = JSONObject().put("text", scriptText)
                    if (styleInstruction.isNotEmpty()) {
                        partObj.put(
                            "speechMetadata",
                            JSONObject().put("style", styleInstruction)
                        )
                    }
                    partsArray.put(partObj)
                }
                is TtsMode.Multi -> {
                    val s1 = mode.speaker1Name.trim().ifEmpty { "Speaker 1" }
                    val s2 = mode.speaker2Name.trim().ifEmpty { "Speaker 2" }
                    val s1Regex = Regex("^${Regex.escape(s1)}\\s*:\\s*(.+)$", RegexOption.IGNORE_CASE)
                    val s2Regex = Regex("^${Regex.escape(s2)}\\s*:\\s*(.+)$", RegexOption.IGNORE_CASE)
                    val lines = scriptText.lines().map { it.trim() }.filter { it.isNotEmpty() }

                    var lastSpeaker = s1
                    for (line in lines) {
                        val m1 = s1Regex.find(line)
                        val m2 = s2Regex.find(line)
                        val (speakerName, spokenLine) = when {
                            m1 != null -> s1 to "$s1: ${m1.groupValues[1].trim()}"
                            m2 != null -> s2 to "$s2: ${m2.groupValues[1].trim()}"
                            else -> lastSpeaker to "$lastSpeaker: $line"
                        }
                        lastSpeaker = speakerName
                        val meta = JSONObject().put("speaker", speakerName)
                        if (styleInstruction.isNotEmpty()) {
                            meta.put("style", styleInstruction)
                        }
                        partsArray.put(
                            JSONObject()
                                .put("text", spokenLine)
                                .put("speechMetadata", meta)
                        )
                    }
                    if (partsArray.length() == 0) {
                        val meta = JSONObject().put("speaker", s1)
                        if (styleInstruction.isNotEmpty()) meta.put("style", styleInstruction)
                        partsArray.put(JSONObject().put("text", "$s1: $scriptText").put("speechMetadata", meta))
                    }
                }
            }
        }

        val contentObj = JSONObject()
            .put("role", "user")
            .put("parts", partsArray)

        val speechConfig = when (mode) {
            is TtsMode.Single -> {
                JSONObject().put(
                    "voiceConfig",
                    JSONObject().put(
                        "prebuiltVoiceConfig",
                        JSONObject().put("voiceName", resolveVoiceForModel(mode.voice, isLegacy25Tts))
                    )
                )
            }
            is TtsMode.Multi -> {
                val s1 = mode.speaker1Name.trim().ifEmpty { "Speaker 1" }
                val s2 = mode.speaker2Name.trim().ifEmpty { "Speaker 2" }
                val speakerConfigs = JSONArray()
                    .put(
                        JSONObject()
                            .put("speaker", s1)
                            .put(
                                "voiceConfig",
                                JSONObject().put(
                                    "prebuiltVoiceConfig",
                                    JSONObject().put("voiceName", resolveVoiceForModel(mode.speaker1Voice, isLegacy25Tts))
                                )
                            )
                    )
                    .put(
                        JSONObject()
                            .put("speaker", s2)
                            .put(
                                "voiceConfig",
                                JSONObject().put(
                                    "prebuiltVoiceConfig",
                                    JSONObject().put("voiceName", resolveVoiceForModel(mode.speaker2Voice, isLegacy25Tts))
                                )
                            )
                    )
                JSONObject().put(
                    "multiSpeakerVoiceConfig",
                    JSONObject().put("speakerVoiceConfigs", speakerConfigs)
                )
            }
        }

        val generationConfig = JSONObject()
            .put("responseModalities", JSONArray().put("AUDIO"))
            .put("speechConfig", speechConfig)

        return JSONObject()
            .put("contents", JSONArray().put(contentObj))
            .put("generationConfig", generationConfig)
    }

    private fun parseAudioResponse(responseBody: String): TtsResult {
        val root = JSONObject(responseBody)
        val usageObj = root.optJSONObject("usageMetadata")
        val usage = if (usageObj != null) {
            TtsUsageMetadata(
                promptTokens = usageObj.optInt("promptTokenCount", 0),
                audioTokens = usageObj.optInt("candidatesTokenCount", 0),
                totalTokens = usageObj.optInt("totalTokenCount", 0)
            )
        } else {
            TtsUsageMetadata()
        }

        val candidates = root.optJSONArray("candidates")
            ?: throw GeminiApiException("Respons API tidak mengandung audio.")
        if (candidates.length() == 0) {
            throw GeminiApiException("Gemini tidak mengembalikan kandidat audio.")
        }

        val parts = candidates.getJSONObject(0)
            .optJSONObject("content")
            ?.optJSONArray("parts")
            ?: throw GeminiApiException("Format respons audio tidak valid.")

        for (i in 0 until parts.length()) {
            val inlineData = parts.getJSONObject(i).optJSONObject("inlineData")
            val base64 = inlineData?.optString("data").orEmpty()
            if (base64.isNotEmpty()) {
                val mime = inlineData?.optString("mimeType").orEmpty().ifEmpty { "audio/L16" }
                val decoded = Base64.decode(base64, Base64.DEFAULT)
                return TtsResult(audioBytes = decoded, mimeType = mime, usage = usage)
            }
        }

        throw GeminiApiException("Data audio Base64 tidak ditemukan dalam respons Gemini.")
    }

    private fun parseErrorMessage(statusCode: Int, responseBody: String, modelUsed: String): String {
        val errorObj = runCatching { JSONObject(responseBody).optJSONObject("error") }.getOrNull()
        val apiMessage = errorObj?.optString("message").orEmpty()

        if (statusCode == 429) {
            var quotaVal: Int? = null
            var retryDelay: String? = null
            var isFreeTier = apiMessage.contains("free_tier", ignoreCase = true) ||
                apiMessage.contains("free tier", ignoreCase = true)
            val details = errorObj?.optJSONArray("details")
            if (details != null) {
                for (i in 0 until details.length()) {
                    val d = details.optJSONObject(i) ?: continue
                    val violations = d.optJSONArray("violations")
                    if (violations != null && violations.length() > 0) {
                        val v0 = violations.optJSONObject(0)
                        val vStr = v0?.optString("quotaValue").orEmpty()
                        val metric = v0?.optString("quotaMetric").orEmpty()
                        if (metric.contains("free_tier", ignoreCase = true)) {
                            isFreeTier = true
                        }
                        quotaVal = vStr.toIntOrNull() ?: quotaVal
                    }
                    val rd = d.optString("retryDelay").orEmpty()
                    if (rd.isNotEmpty()) retryDelay = rd
                }
            }
            val limitPart = quotaVal?.let { " Jatah limit server: $it." }.orEmpty()
            val tierTag = if (isFreeTier) " [Free Tier]" else ""
            val retryPart = retryDelay?.let { " Coba lagi dalam $it." }.orEmpty()
            throw GeminiQuotaException(
                message = "Kuota TTS tercapai (429)$tierTag.$limitPart$retryPart",
                quotaLimitFromServer = quotaVal,
                retryDelay = retryDelay,
                isFreeTierHint = isFreeTier
            )
        }

        return when (statusCode) {
            400 -> if (apiMessage.contains("API_KEY", ignoreCase = true) || apiMessage.contains("API key", ignoreCase = true)) {
                "API Key tidak valid. Pastikan salin Gemini API Key (AQ... atau AIza...) dari aistudio.google.com/api-keys."
            } else {
                "Permintaan ditolak (400): ${apiMessage.ifEmpty { "Periksa format naskah atau model." }}"
            }
            401, 403 -> "Akses ditolak ($statusCode): ${apiMessage.ifEmpty { "Pastikan Gemini API Key valid dan aktif." }}"
            404 -> "Model '$modelUsed' tidak ditemukan (404): ${apiMessage.ifEmpty { "Gunakan Deteksi Model TTS di Settings." }}"
            else -> "Kesalahan server ($statusCode): ${apiMessage.ifEmpty { "Silakan coba lagi." }}"
        }
    }
}
