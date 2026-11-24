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

data class TtsResult(
    val audioBytes: ByteArray,
    val mimeType: String
)

class GeminiApiException(message: String) : Exception(message)

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

        val finalPrompt = buildString {
            val cleanStyle = styleInstruction.trim()
            if (cleanStyle.isNotEmpty()) {
                append("Style / Director's Notes: ")
                append(cleanStyle)
                append("\n\n")
            }
            append(cleanScript)
        }

        val payload = buildRequestPayload(finalPrompt, mode)
        val cleanBase = apiBaseUrl.trim().trimEnd('/').ifEmpty { "https://generativelanguage.googleapis.com" }
        val cleanVersion = apiVersion.trim().trim('/').ifEmpty { "v1beta" }
        val cleanModel = modelId.trim().ifEmpty { "gemini-2.5-flash" }
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
                throw GeminiApiException(parseErrorMessage(statusCode, responseBody))
            }

            parseAudioResponse(responseBody)
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

    suspend fun checkApiConnection(
        apiKey: String,
        apiBaseUrl: String,
        apiVersion: String,
        modelId: String
    ): Boolean = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isEmpty()) return@withContext false

        val cleanBase = apiBaseUrl.trim().trimEnd('/').ifEmpty { "https://generativelanguage.googleapis.com" }
        val cleanVersion = apiVersion.trim().trim('/').ifEmpty { "v1beta" }
        val cleanModel = modelId.trim().ifEmpty { "gemini-2.5-flash" }
        val endpoint = "$cleanBase/$cleanVersion/models/$cleanModel"

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

    private fun buildRequestPayload(promptText: String, mode: TtsMode): JSONObject {
        val partObj = JSONObject().put("text", promptText)
        val contentObj = JSONObject()
            .put("role", "user")
            .put("parts", JSONArray().put(partObj))

        val speechConfig = when (mode) {
            is TtsMode.Single -> {
                JSONObject().put(
                    "voiceConfig",
                    JSONObject().put(
                        "prebuiltVoiceConfig",
                        JSONObject().put("voiceName", mode.voice.voiceName)
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
                                    JSONObject().put("voiceName", mode.speaker1Voice.voiceName)
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
                                    JSONObject().put("voiceName", mode.speaker2Voice.voiceName)
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
                return TtsResult(audioBytes = decoded, mimeType = mime)
            }
        }

        throw GeminiApiException("Data audio Base64 tidak ditemukan dalam respons Gemini.")
    }

    private fun parseErrorMessage(statusCode: Int, responseBody: String): String {
        val apiMessage = runCatching {
            JSONObject(responseBody).optJSONObject("error")?.optString("message")
        }.getOrNull().orEmpty()

        return when (statusCode) {
            400 -> if (apiMessage.contains("API_KEY", ignoreCase = true)) {
                "API Key tidak valid. Periksa kembali Gemini API Key di Settings."
            } else {
                "Permintaan ditolak (400): ${apiMessage.ifEmpty { "Periksa format naskah atau model." }}"
            }
            401, 403 -> "Akses ditolak ($statusCode). Pastikan Gemini API Key valid dan aktif."
            404 -> "Model atau endpoint tidak ditemukan (404). Periksa pengaturan API TTS di Settings."
            429 -> "Batas kuota tercapai (429). Tunggu sebentar sebelum mencoba lagi."
            else -> "Kesalahan server ($statusCode): ${apiMessage.ifEmpty { "Silakan coba lagi." }}"
        }
    }
}
