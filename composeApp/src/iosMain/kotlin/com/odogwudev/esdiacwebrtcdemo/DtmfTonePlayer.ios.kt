package com.odogwudev.esdiacwebrtcdemo

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.AVFAudio.AVAudioPlayer
import platform.Foundation.NSData
import platform.Foundation.create
import kotlin.math.PI
import kotlin.math.sin

actual object DtmfTonePlayer {

    private var player: AVAudioPlayer? = null

    // Standard DTMF dual-tone frequencies (row freq, col freq)
    private val dtmfFrequencies: Map<String, Pair<Double, Double>> = mapOf(
        "1" to (697.0 to 1209.0),
        "2" to (697.0 to 1336.0),
        "3" to (697.0 to 1477.0),
        "4" to (770.0 to 1209.0),
        "5" to (770.0 to 1336.0),
        "6" to (770.0 to 1477.0),
        "7" to (852.0 to 1209.0),
        "8" to (852.0 to 1336.0),
        "9" to (852.0 to 1477.0),
        "0" to (941.0 to 1336.0),
        "*" to (941.0 to 1209.0),
        "#" to (941.0 to 1477.0)
    )

    @OptIn(ExperimentalForeignApi::class)
    actual fun play(digit: String) {
        val (f1, f2) = dtmfFrequencies[digit] ?: return
        try {
            val sampleRate = 8000
            val durationMs = 150
            val numSamples = sampleRate * durationMs / 1000
            val wavBytes = generateWav(f1, f2, sampleRate, numSamples)

            val nsData = wavBytes.usePinned { pinned ->
                NSData.create(
                    bytes = pinned.addressOf(0),
                    length = wavBytes.size.toULong()
                )
            }

            player?.stop()
            player = AVAudioPlayer(data = nsData, error = null)
            player?.play()
        } catch (_: Exception) {
            // Best-effort tone playback
        }
    }

    actual fun release() {
        player?.stop()
        player = null
    }

    private fun generateWav(f1: Double, f2: Double, sampleRate: Int, numSamples: Int): ByteArray {
        val bitsPerSample = 16
        val numChannels = 1
        val dataSize = numSamples * numChannels * (bitsPerSample / 8)
        val headerSize = 44
        val wav = ByteArray(headerSize + dataSize)

        // RIFF header
        writeString(wav, 0, "RIFF")
        writeInt32LE(wav, 4, headerSize + dataSize - 8)
        writeString(wav, 8, "WAVE")

        // fmt chunk
        writeString(wav, 12, "fmt ")
        writeInt32LE(wav, 16, 16) // chunk size
        writeInt16LE(wav, 20, 1)  // PCM format
        writeInt16LE(wav, 22, numChannels.toShort())
        writeInt32LE(wav, 24, sampleRate)
        writeInt32LE(wav, 28, sampleRate * numChannels * bitsPerSample / 8) // byte rate
        writeInt16LE(wav, 32, (numChannels * bitsPerSample / 8).toShort()) // block align
        writeInt16LE(wav, 34, bitsPerSample.toShort())

        // data chunk
        writeString(wav, 36, "data")
        writeInt32LE(wav, 40, dataSize)

        // PCM samples — dual-tone at 40% amplitude each
        var offset = headerSize
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val sample = (sin(2.0 * PI * f1 * t) + sin(2.0 * PI * f2 * t)) * 0.4 * Short.MAX_VALUE
            val s = sample.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            wav[offset++] = (s.toInt() and 0xFF).toByte()
            wav[offset++] = ((s.toInt() shr 8) and 0xFF).toByte()
        }

        return wav
    }

    private fun writeString(buf: ByteArray, offset: Int, s: String) {
        for (i in s.indices) buf[offset + i] = s[i].code.toByte()
    }

    private fun writeInt32LE(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = (value and 0xFF).toByte()
        buf[offset + 1] = ((value shr 8) and 0xFF).toByte()
        buf[offset + 2] = ((value shr 16) and 0xFF).toByte()
        buf[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    private fun writeInt16LE(buf: ByteArray, offset: Int, value: Short) {
        buf[offset] = (value.toInt() and 0xFF).toByte()
        buf[offset + 1] = ((value.toInt() shr 8) and 0xFF).toByte()
    }
}
