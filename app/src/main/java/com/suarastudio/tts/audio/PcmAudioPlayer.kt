package com.suarastudio.tts.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.min

@Immutable
data class PlaybackState(
    val hasAudio: Boolean = false,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L
)

class PcmAudioPlayer {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var playbackJob: Job? = null
    private var audioTrack: AudioTrack? = null

    private var pcmData: ByteArray = ByteArray(0)
    private var sampleRate: Int = WavAudioUtils.DEFAULT_SAMPLE_RATE
    private var currentByteOffset: Int = 0

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    fun loadAndPlay(rawOrWavBytes: ByteArray, mimeType: String) {
        stopInternal(clearAudio = false)
        sampleRate = WavAudioUtils.parseSampleRate(mimeType)
        pcmData = WavAudioUtils.extractPcmPayload(rawOrWavBytes)
        currentByteOffset = 0

        val bytesPerSecond = sampleRate * 2L // 16-bit mono = 2 bytes per sample
        val totalDurationMs = if (bytesPerSecond > 0) (pcmData.size * 1000L) / bytesPerSecond else 0L

        _state.value = PlaybackState(
            hasAudio = pcmData.isNotEmpty(),
            isPlaying = false,
            positionMs = 0L,
            durationMs = totalDurationMs
        )

        if (pcmData.isNotEmpty()) {
            play()
        }
    }

    fun togglePlayPause() {
        if (pcmData.isEmpty()) return
        if (_state.value.isPlaying) {
            pause()
        } else {
            if (currentByteOffset >= pcmData.size) {
                currentByteOffset = 0
            }
            play()
        }
    }

    fun play() {
        if (pcmData.isEmpty() || _state.value.isPlaying) return
        playbackJob?.cancel()
        playbackJob = scope.launch {
            val minBuffer = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4096)

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuffer)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack = track
            track.play()
            _state.update { it.copy(isPlaying = true) }

            val chunkSize = 4096
            while (isActive && currentByteOffset < pcmData.size) {
                val remaining = pcmData.size - currentByteOffset
                val toWrite = min(chunkSize, remaining)
                val written = track.write(pcmData, currentByteOffset, toWrite)
                if (written <= 0) break
                currentByteOffset += written
                val posMs = (currentByteOffset * 1000L) / (sampleRate * 2L)
                _state.update { it.copy(positionMs = posMs.coerceAtMost(it.durationMs)) }
            }

            if (isActive && currentByteOffset >= pcmData.size) {
                // Allow final buffered frames to drain smoothly before resetting state
                delay(180)
                currentByteOffset = 0
                _state.update { it.copy(isPlaying = false, positionMs = 0L) }
            }

            runCatching {
                track.stop()
                track.release()
            }
            if (audioTrack === track) {
                audioTrack = null
            }
        }
    }

    fun pause() {
        playbackJob?.cancel()
        runCatching {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.release()
        }
        audioTrack = null
        _state.update { it.copy(isPlaying = false) }
    }

    fun stop() {
        stopInternal(clearAudio = false)
        currentByteOffset = 0
        _state.update { it.copy(isPlaying = false, positionMs = 0L) }
    }

    fun seekTo(positionMs: Long) {
        if (pcmData.isEmpty()) return
        val wasPlaying = _state.value.isPlaying
        pause()
        val targetByte = ((positionMs * sampleRate * 2L) / 1000L).toInt()
        // Align to 2-byte PCM sample boundary
        currentByteOffset = (targetByte and -2).coerceIn(0, pcmData.size)
        _state.update { it.copy(positionMs = positionMs.coerceIn(0L, it.durationMs)) }
        if (wasPlaying) {
            play()
        }
    }

    private fun stopInternal(clearAudio: Boolean) {
        playbackJob?.cancel()
        runCatching {
            audioTrack?.stop()
            audioTrack?.release()
        }
        audioTrack = null
        if (clearAudio) {
            pcmData = ByteArray(0)
            currentByteOffset = 0
            _state.value = PlaybackState()
        }
    }

    fun release() {
        stopInternal(clearAudio = true)
        scope.cancel()
    }
}
