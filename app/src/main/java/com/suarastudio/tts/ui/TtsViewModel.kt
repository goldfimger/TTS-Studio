package com.suarastudio.tts.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.suarastudio.tts.audio.MediaStoreSaver
import com.suarastudio.tts.audio.PcmAudioPlayer
import com.suarastudio.tts.audio.PlaybackState
import com.suarastudio.tts.audio.SavedAudioFile
import com.suarastudio.tts.audio.WavAudioUtils
import com.suarastudio.tts.data.GeminiQuotaException
import com.suarastudio.tts.data.GeminiTtsClient
import com.suarastudio.tts.data.GeminiVoice
import com.suarastudio.tts.data.SettingsRepository
import com.suarastudio.tts.data.TtsMode
import com.suarastudio.tts.data.TtsResult
import com.suarastudio.tts.data.UserSettings
import com.suarastudio.tts.ui.theme.ThemeMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class ApiLogLevel {
    SUCCESS,
    INFO,
    WARNING,
    ERROR
}

@Immutable
data class ApiLogEntry(
    val id: String,
    val timestamp: String,
    val method: String,
    val endpoint: String,
    val statusCode: Int?,
    val level: ApiLogLevel,
    val actionLabel: String,
    val detail: String,
    val durationMs: Long? = null,
    val totalTokens: Int? = null
)

enum class AppTab(val label: String) {
    PROJEK("Projek"),
    ARTIFACTS("Artifacts"),
    TENTANG("Tentang")
}

@Immutable
data class AudioEditorState(
    val targetFile: SavedAudioFile,
    val currentPcm: ByteArray,
    val waveformBars: List<Float>,
    val sampleRate: Int = WavAudioUtils.DEFAULT_SAMPLE_RATE,
    val durationMs: Long = 0L,
    val undoCount: Int = 0,
    val redoCount: Int = 0,
    val isPreviewPlaying: Boolean = false,
    val isSaving: Boolean = false,
    val showSaveConfirm: Boolean = false
)

@Immutable
data class TtsUiState(
    val activeTab: AppTab = AppTab.PROJEK,
    val isMultiSpeaker: Boolean = false,
    val singleVoice: GeminiVoice = GeminiVoice.TAVI,
    val speaker1Name: String = "Speaker 1",
    val speaker1Voice: GeminiVoice = GeminiVoice.PUCK,
    val speaker2Name: String = "Speaker 2",
    val speaker2Voice: GeminiVoice = GeminiVoice.TAVI,
    val styleNotes: String = "",
    val scriptText: String = "Halo! Selamat datang di Gemini Voice Studio berbasis Gemini TTS API dan Android 15.",
    val isGenerating: Boolean = false,
    val isSavingWav: Boolean = false,
    val downloadProgress: Int? = null,
    val isDownloadDone: Boolean = false,
    val isSettingsOpen: Boolean = false,
    val isApiConnected: Boolean? = null,
    val detectedTtsModels: List<String> = emptyList(),
    val isDetectingModels: Boolean = false,
    val modelDetectionNote: String? = null,
    val previewingKey: String? = null,
    val isPreviewLoading: Boolean = false,
    val savedFiles: List<SavedAudioFile> = emptyList(),
    val activeSavedUri: Uri? = null,
    val isLogSheetOpen: Boolean = false,
    val apiLogs: List<ApiLogEntry> = emptyList(),
    val detectedGeminiSummary: String = "",
    val allGeminiModelsCount: Int = 0,
    val audioEditor: AudioEditorState? = null
)

class TtsViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepo = SettingsRepository(application.applicationContext)
    private val ttsClient = GeminiTtsClient()
    private val audioPlayer = PcmAudioPlayer()

    val settings: StateFlow<UserSettings> = settingsRepo.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UserSettings()
    )

    val playbackState: StateFlow<PlaybackState> = audioPlayer.state

    private val _uiState = MutableStateFlow(TtsUiState())
    val uiState: StateFlow<TtsUiState> = _uiState.asStateFlow()

    private val _snackbarMessage = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val snackbarMessage: SharedFlow<String> = _snackbarMessage.asSharedFlow()

    private var lastAudioResult: TtsResult? = null
    private var lastDetectedKey: String? = null
    private val previewCache = mutableMapOf<String, TtsResult>()
    private var previewJob: Job? = null
    private val editorUndoStack = ArrayDeque<ByteArray>()
    private val editorRedoStack = ArrayDeque<ByteArray>()

    init {
        viewModelScope.launch {
            settings.collectLatest { s ->
                refreshSavedFiles(s)
                checkApiConnectionAndModels(s)
            }
        }
        viewModelScope.launch {
            playbackState.collectLatest { pb ->
                if (!pb.isPlaying && !_uiState.value.isPreviewLoading && _uiState.value.previewingKey != null) {
                    _uiState.update { it.copy(previewingKey = null) }
                }
            }
        }
    }

    private fun appendApiLog(
        method: String,
        endpoint: String,
        statusCode: Int?,
        level: ApiLogLevel,
        actionLabel: String,
        detail: String,
        durationMs: Long? = null,
        totalTokens: Int? = null
    ) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        val entry = ApiLogEntry(
            id = UUID.randomUUID().toString(),
            timestamp = time,
            method = method,
            endpoint = endpoint,
            statusCode = statusCode,
            level = level,
            actionLabel = actionLabel,
            detail = detail,
            durationMs = durationMs,
            totalTokens = totalTokens
        )
        _uiState.update { current ->
            current.copy(apiLogs = (listOf(entry) + current.apiLogs).take(100))
        }
    }

    private suspend fun checkApiConnectionAndModels(currentSettings: UserSettings) {
        val cleanKey = currentSettings.apiKey.trim()
        if (cleanKey.isEmpty()) {
            lastDetectedKey = null
            _uiState.update {
                it.copy(
                    isApiConnected = false,
                    detectedTtsModels = emptyList(),
                    modelDetectionNote = null,
                    detectedGeminiSummary = "",
                    allGeminiModelsCount = 0
                )
            }
            appendApiLog(
                method = "INFO",
                endpoint = "/${currentSettings.apiVersion}/models",
                statusCode = null,
                level = ApiLogLevel.WARNING,
                actionLabel = "Cek Koneksi API",
                detail = "API Key belum diisi di Pengaturan."
            )
            return
        }

        val started = System.currentTimeMillis()
        val ok = ttsClient.checkApiConnection(
            apiKey = cleanKey,
            apiBaseUrl = currentSettings.apiBaseUrl,
            apiVersion = currentSettings.apiVersion,
            modelId = currentSettings.modelId
        )
        val elapsed = System.currentTimeMillis() - started
        _uiState.update { it.copy(isApiConnected = ok) }
        appendApiLog(
            method = "GET",
            endpoint = "/${currentSettings.apiVersion}/models?pageSize=20",
            statusCode = if (ok) 200 else 401,
            level = if (ok) ApiLogLevel.SUCCESS else ApiLogLevel.ERROR,
            actionLabel = "Cek Koneksi Server",
            detail = if (ok) "Terhubung ke server Gemini API." else "Gagal terhubung ke server Gemini API.",
            durationMs = elapsed
        )

        if (ok && lastDetectedKey != cleanKey) {
            lastDetectedKey = cleanKey
            detectModelsInternal(
                apiKey = cleanKey,
                apiBaseUrl = currentSettings.apiBaseUrl,
                apiVersion = currentSettings.apiVersion,
                autoApplyToSettings = true
            )
        }
    }

    fun detectModelsForKey(
        apiKey: String,
        apiBaseUrl: String,
        apiVersion: String,
        onRecommendedModel: ((String) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val recommended = detectModelsInternal(
                apiKey = apiKey,
                apiBaseUrl = apiBaseUrl,
                apiVersion = apiVersion,
                autoApplyToSettings = false
            )
            if (!recommended.isNullOrBlank()) {
                onRecommendedModel?.invoke(recommended)
            }
        }
    }

    private suspend fun detectModelsInternal(
        apiKey: String,
        apiBaseUrl: String,
        apiVersion: String,
        autoApplyToSettings: Boolean
    ): String? {
        if (apiKey.isBlank()) {
            _uiState.update {
                it.copy(
                    isDetectingModels = false,
                    detectedTtsModels = emptyList(),
                    modelDetectionNote = "Masukkan API Key terlebih dahulu."
                )
            }
            return null
        }

        _uiState.update {
            it.copy(
                isDetectingModels = true,
                modelDetectionNote = "Mendeteksi model TTS dari API Key..."
            )
        }

        val started = System.currentTimeMillis()
        val res = ttsClient.detectAvailableTtsModels(apiKey, apiBaseUrl, apiVersion)
        val elapsed = System.currentTimeMillis() - started
        return res.fold(
            onSuccess = { detected ->
                val list = detected.ttsModels
                val rec = detected.recommendedModel
                val currentModel = settings.value.modelId
                val chosen = if (rec != null && list.none { it.equals(currentModel, ignoreCase = true) }) {
                    rec
                } else {
                    currentModel
                }

                if (autoApplyToSettings && rec != null && list.none { it.equals(currentModel, ignoreCase = true) }) {
                    settingsRepo.saveModelId(rec)
                }

                val note = if (list.isNotEmpty()) {
                    "Terdeteksi ${list.size} model TTS aktif untuk API Key ini."
                } else {
                    "Tidak ada model TTS khusus tercantum; gunakan preset standar."
                }

                _uiState.update {
                    it.copy(
                        isApiConnected = true,
                        isDetectingModels = false,
                        detectedTtsModels = list,
                        modelDetectionNote = note,
                        detectedGeminiSummary = detected.geminiTypeSummary,
                        allGeminiModelsCount = detected.allGeminiModelsCount
                    )
                }
                appendApiLog(
                    method = "GET",
                    endpoint = "/${apiVersion.trim().ifEmpty { "v1beta" }}/models?pageSize=200",
                    statusCode = 200,
                    level = ApiLogLevel.SUCCESS,
                    actionLabel = "Deteksi Model TTS",
                    detail = if (list.isNotEmpty()) "Ditemukan: ${list.joinToString(", ")}" else note,
                    durationMs = elapsed
                )
                chosen
            },
            onFailure = { err ->
                _uiState.update {
                    it.copy(
                        isDetectingModels = false,
                        modelDetectionNote = err.message ?: "Gagal mendeteksi model."
                    )
                }
                appendApiLog(
                    method = "GET",
                    endpoint = "/${apiVersion.trim().ifEmpty { "v1beta" }}/models?pageSize=200",
                    statusCode = null,
                    level = ApiLogLevel.ERROR,
                    actionLabel = "Deteksi Model TTS",
                    detail = err.message ?: "Gagal mendeteksi model.",
                    durationMs = elapsed
                )
                null
            }
        )
    }

    fun toggleQuickTheme(isCurrentlyDark: Boolean) {
        viewModelScope.launch {
            val nextMode = if (isCurrentlyDark) ThemeMode.LIGHT else ThemeMode.DARK
            settingsRepo.saveThemeMode(nextMode)
        }
    }

    fun updateDownloadFolder(treeUri: Uri) {
        viewModelScope.launch {
            val label = MediaStoreSaver.extractFolderLabel(treeUri)
            settingsRepo.saveDownloadFolder(treeUri.toString(), label)
            _snackbarMessage.tryEmit("Folder penyimpanan diatur ke: $label")
        }
    }

    fun selectTab(tab: AppTab) {
        _uiState.update { it.copy(activeTab = tab) }
        if (tab == AppTab.ARTIFACTS) {
            refreshSavedFiles()
        }
    }

    fun setMode(isMultiSpeaker: Boolean) {
        _uiState.update { current ->
            val updatedScript = if (isMultiSpeaker && !current.scriptText.contains(":")) {
                "${current.speaker1Name}: Halo, bagaimana kabar proyek Android 15 kita hari ini?\n" +
                    "${current.speaker2Name}: Lancar sekali! Suara Tavi terdengar jernih dan ringan di Exynos 850."
            } else {
                current.scriptText
            }
            current.copy(isMultiSpeaker = isMultiSpeaker, scriptText = updatedScript)
        }
    }

    fun selectSingleVoice(voice: GeminiVoice) {
        _uiState.update { it.copy(singleVoice = voice) }
    }

    fun updateSpeaker1Name(name: String) {
        _uiState.update { it.copy(speaker1Name = name) }
    }

    fun updateSpeaker1Voice(voice: GeminiVoice) {
        _uiState.update { it.copy(speaker1Voice = voice) }
    }

    fun updateSpeaker2Name(name: String) {
        _uiState.update { it.copy(speaker2Name = name) }
    }

    fun updateSpeaker2Voice(voice: GeminiVoice) {
        _uiState.update { it.copy(speaker2Voice = voice) }
    }

    fun updateStyleNotes(notes: String) {
        _uiState.update { it.copy(styleNotes = notes) }
    }

    fun updateScriptText(text: String) {
        _uiState.update { it.copy(scriptText = text) }
    }

    fun openSettings() {
        _uiState.update { it.copy(isSettingsOpen = true) }
    }

    fun closeSettings() {
        _uiState.update { it.copy(isSettingsOpen = false) }
    }

    fun openLogs() {
        _uiState.update { it.copy(isLogSheetOpen = true) }
    }

    fun closeLogs() {
        _uiState.update { it.copy(isLogSheetOpen = false) }
    }

    fun clearLogs() {
        _uiState.update { it.copy(apiLogs = emptyList()) }
    }

    fun resetDailyQuotaUsage() {
        viewModelScope.launch {
            settingsRepo.resetDailyUsage()
            appendApiLog(
                method = "INFO",
                endpoint = "local://quota-tracker",
                statusCode = null,
                level = ApiLogLevel.INFO,
                actionLabel = "Reset Kuota Harian",
                detail = "Penghitung pemakaian request dan token harian direset ke 0."
            )
            _snackbarMessage.tryEmit("Hitungan pemakaian kuota hari ini direset.")
        }
    }

    fun saveSettings(
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
        viewModelScope.launch {
            lastDetectedKey = null
            settingsRepo.saveAll(
                apiKey = apiKey,
                themeMode = themeMode,
                useDynamicMonet = useDynamicMonet,
                modelId = modelId,
                apiBaseUrl = apiBaseUrl,
                apiVersion = apiVersion,
                sampleRateHz = sampleRateHz,
                dailyRequestLimit = dailyRequestLimit,
                dailyTokenLimit = dailyTokenLimit
            )
            _uiState.update { it.copy(isSettingsOpen = false) }
            _snackbarMessage.tryEmit("Pengaturan & API TTS disimpan.")
        }
    }

    fun generateAndPlay() {
        val state = _uiState.value
        val currentSettings = settings.value
        if (state.isGenerating) return

        if (currentSettings.apiKey.isBlank()) {
            _uiState.update { it.copy(isSettingsOpen = true) }
            _snackbarMessage.tryEmit("Masukkan Gemini API Key terlebih dahulu.")
            return
        }

        val mode = if (state.isMultiSpeaker) {
            TtsMode.Multi(
                speaker1Name = state.speaker1Name,
                speaker1Voice = state.speaker1Voice,
                speaker2Name = state.speaker2Name,
                speaker2Voice = state.speaker2Voice
            )
        } else {
            TtsMode.Single(state.singleVoice)
        }

        val modeLabel = if (state.isMultiSpeaker) {
            "Multi-Speaker (${state.speaker1Voice.voiceName} & ${state.speaker2Voice.voiceName})"
        } else {
            "Single Voice (${state.singleVoice.voiceName})"
        }
        val effectiveModel = if (state.isMultiSpeaker && currentSettings.modelId == "gemini-3.8-flash-lite-tts") {
            "gemini-3.8-flash-tts"
        } else {
            currentSettings.modelId
        }
        val endpointPath = "/${currentSettings.apiVersion}/models/$effectiveModel:generateContent"

        // Ensure any active preview or older audio clip never bleeds into the newly generated result
        previewJob?.cancel()
        previewJob = null
        audioPlayer.stop()
        lastAudioResult = null

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isGenerating = true,
                    activeSavedUri = null,
                    previewingKey = null,
                    isPreviewLoading = false,
                    isDownloadDone = false,
                    downloadProgress = null
                )
            }
            val started = System.currentTimeMillis()
            try {
                val result = ttsClient.generateSpeech(
                    apiKey = currentSettings.apiKey,
                    apiBaseUrl = currentSettings.apiBaseUrl,
                    apiVersion = currentSettings.apiVersion,
                    modelId = currentSettings.modelId,
                    scriptText = state.scriptText,
                    styleInstruction = state.styleNotes,
                    mode = mode
                )
                val elapsed = System.currentTimeMillis() - started
                lastAudioResult = result
                settingsRepo.recordTtsUsage(result.usage)
                _uiState.update { it.copy(isApiConnected = true) }
                appendApiLog(
                    method = "POST",
                    endpoint = endpointPath,
                    statusCode = 200,
                    level = ApiLogLevel.SUCCESS,
                    actionLabel = "Sintesis TTS · $modeLabel",
                    detail = "Berhasil membuat ${result.audioBytes.size / 1024} KB audio (Input ${result.usage.promptTokens} · Audio ${result.usage.audioTokens} token).",
                    durationMs = elapsed,
                    totalTokens = result.usage.totalTokens
                )
                audioPlayer.loadAndPlay(result.audioBytes, result.mimeType)
            } catch (e: GeminiQuotaException) {
                val elapsed = System.currentTimeMillis() - started
                settingsRepo.recordQuotaAlert(e.message ?: "Kuota TTS tercapai (429).", e.quotaLimitFromServer)
                appendApiLog(
                    method = "POST",
                    endpoint = endpointPath,
                    statusCode = 429,
                    level = ApiLogLevel.WARNING,
                    actionLabel = "Limit Kuota TTS",
                    detail = e.message ?: "Kuota TTS tercapai (429).",
                    durationMs = elapsed
                )
                _snackbarMessage.tryEmit(e.message ?: "Kuota TTS tercapai.")
            } catch (e: Exception) {
                val elapsed = System.currentTimeMillis() - started
                appendApiLog(
                    method = "POST",
                    endpoint = endpointPath,
                    statusCode = null,
                    level = ApiLogLevel.ERROR,
                    actionLabel = "Gagal Sintesis TTS",
                    detail = e.message ?: "Gagal menghasilkan audio.",
                    durationMs = elapsed
                )
                _snackbarMessage.tryEmit(e.message ?: "Gagal menghasilkan audio.")
            } finally {
                _uiState.update { it.copy(isGenerating = false) }
            }
        }
    }

    fun togglePlayPause() = audioPlayer.togglePlayPause()

    fun stopPlayback() {
        audioPlayer.stop()
        _uiState.update { it.copy(activeSavedUri = null, previewingKey = null, isPreviewLoading = false) }
    }

    fun stopPreview() {
        previewJob?.cancel()
        previewJob = null
        audioPlayer.stop()
        _uiState.update { it.copy(previewingKey = null, isPreviewLoading = false) }
    }

    fun previewVoice(voice: GeminiVoice) {
        val key = "voice:${voice.voiceName}"
        if (_uiState.value.previewingKey == key && (playbackState.value.isPlaying || _uiState.value.isPreviewLoading)) {
            stopPreview()
            return
        }
        val sample = "Halo, ini adalah contoh suara ${voice.voiceName} di Voice Studio."
        runPreview(
            previewKey = key,
            voice = voice,
            styleInstruction = "",
            sampleText = sample
        )
    }

    fun previewStyle(styleTitle: String, styleInstruction: String, sampleText: String) {
        val key = "style:$styleTitle"
        if (_uiState.value.previewingKey == key && (playbackState.value.isPlaying || _uiState.value.isPreviewLoading)) {
            stopPreview()
            return
        }
        val activeVoice = if (_uiState.value.isMultiSpeaker) {
            _uiState.value.speaker1Voice
        } else {
            _uiState.value.singleVoice
        }
        val textToSpeak = sampleText.ifBlank {
            "Halo! Ini adalah contoh intonasi untuk gaya $styleTitle."
        }
        runPreview(
            previewKey = key,
            voice = activeVoice,
            styleInstruction = styleInstruction,
            sampleText = textToSpeak
        )
    }

    private fun runPreview(
        previewKey: String,
        voice: GeminiVoice,
        styleInstruction: String,
        sampleText: String
    ) {
        if (_uiState.value.isGenerating) return
        val currentSettings = settings.value
        if (currentSettings.apiKey.isBlank()) {
            _snackbarMessage.tryEmit("Masukkan Gemini API Key di Pengaturan untuk preview suara.")
            return
        }

        val cacheKey = "${currentSettings.modelId}|$previewKey|${voice.voiceName}|$styleInstruction"
        val endpointPath = "/${currentSettings.apiVersion}/models/${currentSettings.modelId}:generateContent"
        previewCache[cacheKey]?.let { cached ->
            previewJob?.cancel()
            audioPlayer.stop()
            _uiState.update {
                it.copy(
                    previewingKey = previewKey,
                    isPreviewLoading = false,
                    activeSavedUri = null
                )
            }
            appendApiLog(
                method = "CACHE",
                endpoint = endpointPath,
                statusCode = 200,
                level = ApiLogLevel.INFO,
                actionLabel = "Preview Audio (Cache)",
                detail = "Memutar ulang preview $previewKey dengan suara ${voice.voiceName} tanpa kuota tambahan."
            )
            audioPlayer.loadAndPlay(cached.audioBytes, cached.mimeType)
            return
        }

        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            audioPlayer.stop()
            _uiState.update {
                it.copy(
                    previewingKey = previewKey,
                    isPreviewLoading = true,
                    activeSavedUri = null
                )
            }
            val started = System.currentTimeMillis()
            try {
                val res = ttsClient.generateSpeech(
                    apiKey = currentSettings.apiKey,
                    apiBaseUrl = currentSettings.apiBaseUrl,
                    apiVersion = currentSettings.apiVersion,
                    modelId = currentSettings.modelId,
                    scriptText = sampleText,
                    styleInstruction = styleInstruction,
                    mode = TtsMode.Single(voice)
                )
                val elapsed = System.currentTimeMillis() - started
                previewCache[cacheKey] = res
                settingsRepo.recordTtsUsage(res.usage)
                appendApiLog(
                    method = "POST",
                    endpoint = endpointPath,
                    statusCode = 200,
                    level = ApiLogLevel.SUCCESS,
                    actionLabel = "Preview · ${voice.voiceName}",
                    detail = "Preview $previewKey (${res.audioBytes.size / 1024} KB · Input ${res.usage.promptTokens} · Audio ${res.usage.audioTokens} token).",
                    durationMs = elapsed,
                    totalTokens = res.usage.totalTokens
                )
                // Ensure user didn't cancel or switch preview while loading
                if (_uiState.value.previewingKey == previewKey) {
                    _uiState.update { it.copy(isPreviewLoading = false, isApiConnected = true) }
                    audioPlayer.loadAndPlay(res.audioBytes, res.mimeType)
                }
            } catch (e: GeminiQuotaException) {
                val elapsed = System.currentTimeMillis() - started
                settingsRepo.recordQuotaAlert(e.message ?: "Kuota TTS tercapai (429).", e.quotaLimitFromServer)
                appendApiLog(
                    method = "POST",
                    endpoint = endpointPath,
                    statusCode = 429,
                    level = ApiLogLevel.WARNING,
                    actionLabel = "Limit Kuota Preview",
                    detail = e.message ?: "Kuota TTS tercapai (429).",
                    durationMs = elapsed
                )
                if (_uiState.value.previewingKey == previewKey) {
                    _uiState.update { it.copy(previewingKey = null, isPreviewLoading = false) }
                }
                _snackbarMessage.tryEmit(e.message ?: "Kuota TTS tercapai.")
            } catch (e: Exception) {
                val elapsed = System.currentTimeMillis() - started
                appendApiLog(
                    method = "POST",
                    endpoint = endpointPath,
                    statusCode = null,
                    level = ApiLogLevel.ERROR,
                    actionLabel = "Gagal Preview",
                    detail = e.message ?: "Gagal memutar preview.",
                    durationMs = elapsed
                )
                if (_uiState.value.previewingKey == previewKey) {
                    _uiState.update { it.copy(previewingKey = null, isPreviewLoading = false) }
                }
                _snackbarMessage.tryEmit(e.message ?: "Gagal memutar preview.")
            }
        }
    }

    fun seekTo(positionMs: Long) = audioPlayer.seekTo(positionMs)

    fun saveWavToDownloads() {
        val result = lastAudioResult
        if (result == null) {
            _snackbarMessage.tryEmit("Belum ada audio yang dihasilkan.")
            return
        }
        if (_uiState.value.isSavingWav) return

        val currentSettings = settings.value
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSavingWav = true,
                    downloadProgress = 0,
                    isDownloadDone = false
                )
            }
            val saveRes = MediaStoreSaver.saveWavToDirectory(
                context = getApplication(),
                rawOrWavBytes = result.audioBytes,
                mimeType = result.mimeType,
                targetDirectory = currentSettings.downloadDirectory,
                treeUriString = currentSettings.downloadTreeUri,
                folderLabel = currentSettings.downloadFolderLabel,
                sampleRateOverride = currentSettings.sampleRateHz,
                onProgress = { pct ->
                    _uiState.update { it.copy(downloadProgress = pct) }
                }
            )
            saveRes.onSuccess { savedPath ->
                _uiState.update {
                    it.copy(
                        isSavingWav = false,
                        downloadProgress = 100,
                        isDownloadDone = true
                    )
                }
                appendApiLog(
                    method = "EXPORT",
                    endpoint = savedPath,
                    statusCode = null,
                    level = ApiLogLevel.INFO,
                    actionLabel = "Simpan Audio WAV",
                    detail = "File audio disimpan ke $savedPath (${currentSettings.sampleRateHz} Hz)."
                )
                _snackbarMessage.tryEmit("Done · Tersimpan di $savedPath")
                refreshSavedFiles(currentSettings)
                delay(2_200)
                _uiState.update { it.copy(downloadProgress = null, isDownloadDone = false) }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isSavingWav = false,
                        downloadProgress = null,
                        isDownloadDone = false
                    )
                }
                _snackbarMessage.tryEmit("Gagal menyimpan file: ${err.message}")
            }
        }
    }

    fun refreshSavedFiles(currentSettings: UserSettings = settings.value) {
        viewModelScope.launch {
            val files = MediaStoreSaver.listSavedWavFiles(
                context = getApplication(),
                treeUriString = currentSettings.downloadTreeUri,
                folderLabel = currentSettings.downloadFolderLabel
            )
            _uiState.update { it.copy(savedFiles = files) }
        }
    }

    fun playSavedFile(file: SavedAudioFile) {
        if (_uiState.value.activeSavedUri == file.uri && playbackState.value.hasAudio) {
            audioPlayer.togglePlayPause()
            return
        }
        viewModelScope.launch {
            MediaStoreSaver.readAudioBytes(getApplication(), file.uri)
                .onSuccess { bytes ->
                    _uiState.update { it.copy(activeSavedUri = file.uri) }
                    audioPlayer.loadAndPlay(bytes, "audio/wav")
                }
                .onFailure { err ->
                    _snackbarMessage.tryEmit("Gagal memutar file: ${err.message}")
                }
        }
    }

    fun deleteSavedFile(file: SavedAudioFile) {
        viewModelScope.launch {
            if (_uiState.value.activeSavedUri == file.uri) {
                audioPlayer.stop()
                _uiState.update { it.copy(activeSavedUri = null) }
            }
            MediaStoreSaver.deleteSavedFile(getApplication(), file.uri)
                .onSuccess {
                    _snackbarMessage.tryEmit("File ${file.displayName} dihapus.")
                    refreshSavedFiles()
                }
                .onFailure { err ->
                    _snackbarMessage.tryEmit("Gagal menghapus: ${err.message}")
                }
        }
    }

    fun openAudioEditor(file: SavedAudioFile) {
        viewModelScope.launch {
            audioPlayer.stop()
            _uiState.update { it.copy(activeSavedUri = null, previewingKey = null, isPreviewLoading = false) }
            MediaStoreSaver.readAudioBytes(getApplication(), file.uri)
                .onSuccess { wavBytes ->
                    val pcm = WavAudioUtils.extractPcmPayload(wavBytes)
                    if (pcm.isEmpty()) {
                        _snackbarMessage.tryEmit("Audio kosong dan tidak dapat diedit.")
                        return@onSuccess
                    }
                    val sr = settings.value.sampleRateHz.takeIf { it > 0 } ?: WavAudioUtils.DEFAULT_SAMPLE_RATE
                    editorUndoStack.clear()
                    editorRedoStack.clear()
                    _uiState.update {
                        it.copy(
                            audioEditor = AudioEditorState(
                                targetFile = file,
                                currentPcm = pcm,
                                waveformBars = WavAudioUtils.computeWaveformBars(pcm),
                                sampleRate = sr,
                                durationMs = WavAudioUtils.durationMs(pcm, sr),
                                undoCount = 0,
                                redoCount = 0,
                                isPreviewPlaying = false,
                                isSaving = false,
                                showSaveConfirm = false
                            )
                        )
                    }
                }
                .onFailure { err ->
                    _snackbarMessage.tryEmit("Gagal membuka audio editor: ${err.message}")
                }
        }
    }

    fun trimEditorAudio(startFraction: Float, endFraction: Float) {
        val editor = _uiState.value.audioEditor ?: return
        val nextPcm = WavAudioUtils.trimPcm(editor.currentPcm, startFraction, endFraction, editor.sampleRate)
        if (nextPcm.size < 400) {
            _snackbarMessage.tryEmit("Bagian hasil trim terlalu pendek.")
            return
        }
        audioPlayer.stop()
        editorUndoStack.addLast(editor.currentPcm)
        editorRedoStack.clear()
        _uiState.update {
            it.copy(
                audioEditor = editor.copy(
                    currentPcm = nextPcm,
                    waveformBars = WavAudioUtils.computeWaveformBars(nextPcm),
                    durationMs = WavAudioUtils.durationMs(nextPcm, editor.sampleRate),
                    undoCount = editorUndoStack.size,
                    redoCount = 0,
                    isPreviewPlaying = false,
                    showSaveConfirm = false
                )
            )
        }
    }

    fun cutEditorAudio(startFraction: Float, endFraction: Float) {
        val editor = _uiState.value.audioEditor ?: return
        val nextPcm = WavAudioUtils.cutPcm(editor.currentPcm, startFraction, endFraction, editor.sampleRate)
        if (nextPcm.size < 400) {
            _snackbarMessage.tryEmit("Tidak dapat memotong seluruh audio.")
            return
        }
        audioPlayer.stop()
        editorUndoStack.addLast(editor.currentPcm)
        editorRedoStack.clear()
        _uiState.update {
            it.copy(
                audioEditor = editor.copy(
                    currentPcm = nextPcm,
                    waveformBars = WavAudioUtils.computeWaveformBars(nextPcm),
                    durationMs = WavAudioUtils.durationMs(nextPcm, editor.sampleRate),
                    undoCount = editorUndoStack.size,
                    redoCount = 0,
                    isPreviewPlaying = false,
                    showSaveConfirm = false
                )
            )
        }
    }

    fun undoEditorAudio() {
        val editor = _uiState.value.audioEditor ?: return
        if (editorUndoStack.isEmpty()) return
        audioPlayer.stop()
        val previous = editorUndoStack.removeLast()
        editorRedoStack.addLast(editor.currentPcm)
        _uiState.update {
            it.copy(
                audioEditor = editor.copy(
                    currentPcm = previous,
                    waveformBars = WavAudioUtils.computeWaveformBars(previous),
                    durationMs = WavAudioUtils.durationMs(previous, editor.sampleRate),
                    undoCount = editorUndoStack.size,
                    redoCount = editorRedoStack.size,
                    isPreviewPlaying = false,
                    showSaveConfirm = false
                )
            )
        }
    }

    fun redoEditorAudio() {
        val editor = _uiState.value.audioEditor ?: return
        if (editorRedoStack.isEmpty()) return
        audioPlayer.stop()
        val next = editorRedoStack.removeLast()
        editorUndoStack.addLast(editor.currentPcm)
        _uiState.update {
            it.copy(
                audioEditor = editor.copy(
                    currentPcm = next,
                    waveformBars = WavAudioUtils.computeWaveformBars(next),
                    durationMs = WavAudioUtils.durationMs(next, editor.sampleRate),
                    undoCount = editorUndoStack.size,
                    redoCount = editorRedoStack.size,
                    isPreviewPlaying = false,
                    showSaveConfirm = false
                )
            )
        }
    }

    fun toggleEditorPlayback() {
        val editor = _uiState.value.audioEditor ?: return
        if (playbackState.value.isPlaying) {
            audioPlayer.pause()
        } else {
            audioPlayer.loadAndPlay(editor.currentPcm, "audio/L16;rate=${editor.sampleRate}")
        }
    }

    fun requestSaveEditor() {
        val editor = _uiState.value.audioEditor ?: return
        audioPlayer.stop()
        _uiState.update {
            it.copy(audioEditor = editor.copy(showSaveConfirm = true, isPreviewPlaying = false))
        }
    }

    fun dismissSaveEditorConfirm() {
        val editor = _uiState.value.audioEditor ?: return
        _uiState.update {
            it.copy(audioEditor = editor.copy(showSaveConfirm = false))
        }
    }

    fun confirmSaveEditedAudio() {
        val editor = _uiState.value.audioEditor ?: return
        if (editor.isSaving) return
        val currentSettings = settings.value

        viewModelScope.launch {
            _uiState.update {
                it.copy(audioEditor = editor.copy(isSaving = true))
            }
            val res = MediaStoreSaver.saveEditedWav(
                context = getApplication(),
                pcmOrWavBytes = editor.currentPcm,
                originalFileName = editor.targetFile.displayName,
                targetDirectory = currentSettings.downloadDirectory,
                treeUriString = currentSettings.downloadTreeUri,
                folderLabel = currentSettings.downloadFolderLabel,
                sampleRate = editor.sampleRate
            )
            res.onSuccess { savedPath ->
                val editedName = MediaStoreSaver.buildEditedFileName(editor.targetFile.displayName)
                appendApiLog(
                    method = "EXPORT",
                    endpoint = savedPath,
                    statusCode = null,
                    level = ApiLogLevel.SUCCESS,
                    actionLabel = "Simpan Audio Editan",
                    detail = "Audio editan disimpan sebagai $editedName."
                )
                editorUndoStack.clear()
                editorRedoStack.clear()
                audioPlayer.stop()
                _uiState.update { it.copy(audioEditor = null) }
                _snackbarMessage.tryEmit("Disimpan sebagai $editedName")
                refreshSavedFiles(currentSettings)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(audioEditor = editor.copy(isSaving = false, showSaveConfirm = false))
                }
                _snackbarMessage.tryEmit("Gagal menyimpan audio editan: ${err.message}")
            }
        }
    }

    fun discardAndCloseEditor() {
        audioPlayer.stop()
        editorUndoStack.clear()
        editorRedoStack.clear()
        _uiState.update { it.copy(audioEditor = null) }
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayer.release()
    }
}
