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
import com.suarastudio.tts.data.GeminiTtsClient
import com.suarastudio.tts.data.GeminiVoice
import com.suarastudio.tts.data.SettingsRepository
import com.suarastudio.tts.data.TtsMode
import com.suarastudio.tts.data.TtsResult
import com.suarastudio.tts.data.UserSettings
import com.suarastudio.tts.ui.theme.ThemeMode
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

enum class AppTab(val label: String) {
    PROJEK("Projek"),
    ARTIFACTS("Artifacts"),
    TENTANG("Tentang")
}

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
    val scriptText: String = "Halo! Selamat datang di Gemini Voice Studio berbasis Gemini 2.5 Flash dan Android 15.",
    val isGenerating: Boolean = false,
    val isSavingWav: Boolean = false,
    val downloadProgress: Int? = null,
    val isDownloadDone: Boolean = false,
    val isSettingsOpen: Boolean = false,
    val isApiConnected: Boolean? = null,
    val savedFiles: List<SavedAudioFile> = emptyList(),
    val activeSavedUri: Uri? = null
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

    init {
        refreshSavedFiles()
        viewModelScope.launch {
            settings.collectLatest { s ->
                checkApiConnection(s)
            }
        }
    }

    fun checkApiConnection(currentSettings: UserSettings = settings.value) {
        viewModelScope.launch {
            val ok = ttsClient.checkApiConnection(
                apiKey = currentSettings.apiKey,
                apiBaseUrl = currentSettings.apiBaseUrl,
                apiVersion = currentSettings.apiVersion,
                modelId = currentSettings.modelId
            )
            _uiState.update { it.copy(isApiConnected = ok) }
        }
    }

    fun toggleQuickTheme(isCurrentlyDark: Boolean) {
        viewModelScope.launch {
            val nextMode = if (isCurrentlyDark) ThemeMode.LIGHT else ThemeMode.DARK
            settingsRepo.saveThemeMode(nextMode)
        }
    }

    fun updateDownloadDirectory(directory: String) {
        viewModelScope.launch {
            settingsRepo.saveDownloadDirectory(directory)
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

    fun saveSettings(
        apiKey: String,
        themeMode: ThemeMode,
        useDynamicMonet: Boolean,
        modelId: String,
        apiBaseUrl: String,
        apiVersion: String,
        sampleRateHz: Int
    ) {
        viewModelScope.launch {
            settingsRepo.saveAll(
                apiKey = apiKey,
                themeMode = themeMode,
                useDynamicMonet = useDynamicMonet,
                modelId = modelId,
                apiBaseUrl = apiBaseUrl,
                apiVersion = apiVersion,
                sampleRateHz = sampleRateHz
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

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isGenerating = true,
                    activeSavedUri = null,
                    isDownloadDone = false,
                    downloadProgress = null
                )
            }
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
                lastAudioResult = result
                _uiState.update { it.copy(isApiConnected = true) }
                audioPlayer.loadAndPlay(result.audioBytes, result.mimeType)
            } catch (e: Exception) {
                _snackbarMessage.tryEmit(e.message ?: "Gagal menghasilkan audio.")
            } finally {
                _uiState.update { it.copy(isGenerating = false) }
            }
        }
    }

    fun togglePlayPause() = audioPlayer.togglePlayPause()

    fun stopPlayback() {
        audioPlayer.stop()
        _uiState.update { it.copy(activeSavedUri = null) }
    }

    fun seekTo(positionMs: Long) = audioPlayer.seekTo(positionMs)

    fun saveWavToDownloads() {
        val result = lastAudioResult
        if (result == null) {
            _snackbarMessage.tryEmit("Belum ada audio yang dihasilkan.")
            return
        }
        if (_uiState.value.isSavingWav) return

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
                targetDirectory = settings.value.downloadDirectory,
                sampleRateOverride = settings.value.sampleRateHz,
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
                _snackbarMessage.tryEmit("Done · Tersimpan di $savedPath")
                refreshSavedFiles()
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

    fun refreshSavedFiles() {
        viewModelScope.launch {
            val files = MediaStoreSaver.listSavedWavFiles(getApplication())
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

    override fun onCleared() {
        super.onCleared()
        audioPlayer.release()
    }
}
