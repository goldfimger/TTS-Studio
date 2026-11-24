package com.suarastudio.tts.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder

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

    fun extractPcmPayload(bytes: ByteArray): ByteArray {
        return if (hasWavHeader(bytes)) {
            bytes.copyOfRange(44, bytes.size)
        } else {
            bytes
        }
    }

    fun ensureWavBytes(bytes: ByteArray, sampleRate: Int = DEFAULT_SAMPLE_RATE): ByteArray {
        if (hasWavHeader(bytes)) return bytes
        val dataSize = bytes.size
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

        return header + bytes
    }

    fun parseSampleRate(mimeType: String): Int {
        val match = Regex("rate=(\\d+)").find(mimeType)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: DEFAULT_SAMPLE_RATE
    }
}
