package com.odogwudev.esdiacwebrtcdemo.ui

import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.odogwudev.esdiacwebrtcdemo.AppContextHolder
import com.odogwudev.esdiacwebrtcdemo.CallForegroundService
import com.odogwudev.esdiacwebrtcdemo.TelecomBridge

actual object CallBackgroundService {

    /**
     * Whether we successfully registered a self-managed Telecom connection
     * for the current call.  Reset on [stop].
     */
    private var telecomPlaced = false

    actual fun startOrUpdate(
        destinationNumber: String,
        callPhase: CallPhase,
        isMuted: Boolean,
        isSpeakerOn: Boolean
    ) {
        val context = AppContextHolder.applicationContext() ?: return

        // ── 1.  Telecom framework (primary — same as WhatsApp) ──
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (callPhase == CallPhase.Connecting && !telecomPlaced) {
                telecomPlaced = TelecomBridge.placeCall(context, destinationNumber)
            }
            if (telecomPlaced) {
                TelecomBridge.updateCallPhase(callPhase)
            }
        }

        // ── 2.  Foreground service (fallback / notification) ──
        val intent = CallForegroundService.startOrUpdateIntent(
            context = context,
            destinationNumber = destinationNumber,
            callPhase = callPhase,
            isMuted = isMuted,
            isSpeakerOn = isSpeakerOn
        )
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (e: Exception) {
            println("[CallBackgroundService] Failed to start foreground service: ${e.message}")
        }
    }

    actual fun stop() {
        val context = AppContextHolder.applicationContext() ?: return

        // Tear down Telecom connection
        if (telecomPlaced) {
            TelecomBridge.endCall()
            telecomPlaced = false
        }

        // Stop foreground service
        try {
            context.stopService(Intent(context, CallForegroundService::class.java))
        } catch (e: Exception) {
            println("[CallBackgroundService] Failed to stop service: ${e.message}")
        }
    }
}
