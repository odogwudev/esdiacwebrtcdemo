package com.odogwudev.esdiacwebrtcdemo.ui

import android.content.Intent
import androidx.core.content.ContextCompat
import com.odogwudev.esdiacwebrtcdemo.AppContextHolder
import com.odogwudev.esdiacwebrtcdemo.CallForegroundService

actual object CallBackgroundService {
    actual fun startOrUpdate(
        destinationNumber: String,
        callPhase: CallPhase,
        isMuted: Boolean,
        isSpeakerOn: Boolean
    ) {
        val context = AppContextHolder.applicationContext() ?: return
        val intent = CallForegroundService.startOrUpdateIntent(
            context = context,
            destinationNumber = destinationNumber,
            callPhase = callPhase,
            isMuted = isMuted,
            isSpeakerOn = isSpeakerOn
        )
        ContextCompat.startForegroundService(context, intent)
    }

    actual fun stop() {
        val context = AppContextHolder.applicationContext() ?: return
        context.stopService(Intent(context, CallForegroundService::class.java))
    }
}
