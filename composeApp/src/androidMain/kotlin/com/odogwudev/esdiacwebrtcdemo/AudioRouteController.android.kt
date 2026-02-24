package com.odogwudev.esdiacwebrtcdemo

import android.content.Context
import android.media.AudioManager

object AppContextHolder {
    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    fun audioManager(): AudioManager? {
        val context = appContext ?: return null
        return context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }
}

actual object AudioRouteController {
    actual fun setSpeakerEnabled(enabled: Boolean) {
        val audioManager = AppContextHolder.audioManager() ?: return
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = enabled
    }

    actual fun reset() {
        val audioManager = AppContextHolder.audioManager() ?: return
        audioManager.isSpeakerphoneOn = false
        audioManager.mode = AudioManager.MODE_NORMAL
    }
}
