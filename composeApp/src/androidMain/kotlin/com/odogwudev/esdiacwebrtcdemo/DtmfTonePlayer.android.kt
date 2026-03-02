package com.odogwudev.esdiacwebrtcdemo

import android.media.AudioManager
import android.media.ToneGenerator

actual object DtmfTonePlayer {

    private var toneGenerator: ToneGenerator? = null

    private fun generator(): ToneGenerator {
        return toneGenerator ?: ToneGenerator(AudioManager.STREAM_DTMF, 80).also {
            toneGenerator = it
        }
    }

    actual fun play(digit: String) {
        val toneType = when (digit) {
            "0" -> ToneGenerator.TONE_DTMF_0
            "1" -> ToneGenerator.TONE_DTMF_1
            "2" -> ToneGenerator.TONE_DTMF_2
            "3" -> ToneGenerator.TONE_DTMF_3
            "4" -> ToneGenerator.TONE_DTMF_4
            "5" -> ToneGenerator.TONE_DTMF_5
            "6" -> ToneGenerator.TONE_DTMF_6
            "7" -> ToneGenerator.TONE_DTMF_7
            "8" -> ToneGenerator.TONE_DTMF_8
            "9" -> ToneGenerator.TONE_DTMF_9
            "*" -> ToneGenerator.TONE_DTMF_S
            "#" -> ToneGenerator.TONE_DTMF_P
            else -> return
        }
        try {
            generator().startTone(toneType, 150)
        } catch (_: Exception) {
            // ToneGenerator can throw if audio system is busy
        }
    }

    actual fun release() {
        toneGenerator?.release()
        toneGenerator = null
    }
}
