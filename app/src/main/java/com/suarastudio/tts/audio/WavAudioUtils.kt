package com.suarastudio.tts.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

object WavAudioUtils {

    const val DEFAULT_SAMPLE_RATE = 24_000
    const val CHANNELS = 1
    const val BITS_PER_SAMPLE = 16

    fun hasWavHeader(bytes: ByteArray): Boolean {
        if (bytes.size < 44) return false
        return bytes[0] == 'R'.code.toByte() &&
            bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() &&
            bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() &&
            bytes[9] == 'A'.code.toByte() &&
            bytes[10] == 'V'.code.toByte() &&
            bytes[11] == 'E'.code.toByte()
    }

    /**
     * Parses RIFF chunks properly and extracts strictly the "data" chunk PCM payload,
     * stripping any trailing metadata chunks (LIST, INFO, JUNK, PAD, etc.) that otherwise
     * cause audible static/noise at the end of playback.
     */
    fun extractPcmPayload(bytes: ByteArray): ByteArray {
        if (!hasWavHeader(bytes)) {
            val evenSize = bytes.size and -2
            return if (evenSize == bytes.size) bytes else bytes.copyOf(evenSize)
        }

        var offset = 12
        while (offset + 8 <= bytes.size) {
            val chunkId = String(bytes, offset, 4, Charsets.US_ASCII)
            val chunkSizeUnsigned = ByteBuffer
                .wrap(bytes, offset + 4, 4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .int
                .toLong() and 0xFFFFFFFFL

            val dataStart = offset + 8
            val available = (bytes.size - dataStart).coerceAtLeast(0).toLong()
            val clampedSize = min(chunkSizeUnsigned, available).toInt()

            if (chunkId == "data") {
                val evenSize = clampedSize and -2
                return bytes.copyOfRange(dataStart, dataStart + evenSize)
            }

            // RIFF chunks are word-aligned (padded to even byte boundary)
            val paddedSize = chunkSizeUnsigned + (chunkSizeUnsigned and 1L)
            val nextOffset = dataStart.toLong() + paddedSize
            if (nextOffset <= offset || nextOffset > bytes.size) break
            offset = nextOffset.toInt()
        }

        val fallbackStart = 44.coerceAtMost(bytes.size)
        val evenLen = (bytes.size - fallbackStart) and -2
        return bytes.copyOfRange(fallbackStart, fallbackStart + evenLen)
    }

    /**
     * Applies a gentle 18ms fade-out to the tail of 16-bit PCM audio so waveforms
     * return cleanly to zero without clicks, pops, or end-of-stream bursts.
     */
    fun sanitizeAndSmoothTail(pcmBytes: ByteArray, sampleRate: Int = DEFAULT_SAMPLE_RATE): ByteArray {
        val evenLen = pcmBytes.size and -2
        if (evenLen <= 2) return ByteArray(0)

        val out = pcmBytes.copyOf(evenLen)
        val totalSamples = evenLen / 2
        // 18ms micro-fade at the very end to avoid abrupt step-to-zero click
        val fadeSamples = min(totalSamples, ((sampleRate * 18L) / 1000L).toInt().coerceAtLeast(1))
        val fadeStartSample = totalSamples - fadeSamples

        for (i in 0 until fadeSamples) {
            val sampleIndex = fadeStartSample + i
            val byteIdx = sampleIndex * 2
            val low = out[byteIdx].toInt() and 0xFF
            val high = out[byteIdx + 1].toInt()
            val sample = (high shl 8) or low

            val remainingRatio = 1.0 - ((i + 1).toDouble() / fadeSamples.toDouble())
            // Smooth squared curve to bring tail seamlessly to zero
            val gain = remainingRatio * remainingRatio
            val attenuated = (sample * gain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())

            out[byteIdx] = (attenuated and 0xFF).toByte()
            out[byteIdx + 1] = ((attenuated ushr 8) and 0xFF).toByte()
        }

        return out
    }

    fun ensureWavBytes(bytes: ByteArray, sampleRate: Int = DEFAULT_SAMPLE_RATE): ByteArray {
        val cleanPcm = sanitizeAndSmoothTail(extractPcmPayload(bytes), sampleRate)
        val dataSize = cleanPcm.size
        val byteRate = sampleRate * CHANNELS * (BITS_PER_SAMPLE / 8)
        val blockAlign = (CHANNELS * (BITS_PER_SAMPLE / 8)).toShort()

        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(36 + dataSize)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16) // PCM subchunk size
            putShort(1.toShort()) // AudioFormat = 1 (PCM)
            putShort(CHANNELS.toShort())
            putInt(sampleRate)
            putInt(byteRate)
            putShort(blockAlign)
            putShort(BITS_PER_SAMPLE.toShort())
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(dataSize)
        }.array()

        return header + cleanPcm
    }

    fun parseSampleRate(mimeType: String): Int {
        val match = Regex("rate=(\\d+)").find(mimeType)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: DEFAULT_SAMPLE_RATE
    }
}
