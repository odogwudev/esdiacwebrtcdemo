package com.odogwudev.esdiacwebrtcdemo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.odogwudev.esdiacwebrtcdemo.ui.CallControlAction
import com.odogwudev.esdiacwebrtcdemo.ui.CallControlActionBus
import com.odogwudev.esdiacwebrtcdemo.ui.CallPhase
import com.odogwudev.esdiacwebrtcdemo.ui.CallSessionManager
import com.odogwudev.esdiacwebrtcdemo.ui.Screen

class CallForegroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onCreate() {
        super.onCreate()
        AppContextHolder.initialize(applicationContext)
        CallSessionManager
        createNotificationChannel()
        acquireWakeLock()
        acquireWifiLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_OR_UPDATE -> {
                val destinationNumber = intent.getStringExtra(EXTRA_DESTINATION_NUMBER).orEmpty()
                val callPhase = intent.getStringExtra(EXTRA_CALL_PHASE)
                    ?.let { phase -> runCatching { CallPhase.valueOf(phase) }.getOrNull() }
                    ?: CallPhase.Connecting
                val isMuted = intent.getBooleanExtra(EXTRA_IS_MUTED, false)
                val isSpeakerOn = intent.getBooleanExtra(EXTRA_IS_SPEAKER_ON, false)
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification(
                        destinationNumber = destinationNumber,
                        callPhase = callPhase,
                        isMuted = isMuted,
                        isSpeakerOn = isSpeakerOn
                    )
                )
            }
            ACTION_HANGUP -> {
                CallControlActionBus.dispatch(CallControlAction.EndCall)
            }
            ACTION_TOGGLE_SPEAKER -> {
                CallControlActionBus.dispatch(CallControlAction.ToggleSpeaker)
            }
            else -> Unit
        }
        return START_REDELIVER_INTENT
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        restartIfCallStillActive()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        releaseWifiLock()
        releaseWakeLock()
        restartIfCallStillActive()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        if (wakeLock != null) return
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            WAKE_LOCK_TAG
        ).apply {
            acquire()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { lock ->
            if (lock.isHeld) lock.release()
        }
        wakeLock = null
    }

    private fun acquireWifiLock() {
        if (wifiLock != null) return
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return
        wifiLock = wifiManager.createWifiLock(
            WifiManager.WIFI_MODE_FULL_HIGH_PERF,
            WIFI_LOCK_TAG
        ).apply {
            acquire()
        }
    }

    private fun releaseWifiLock() {
        wifiLock?.let { lock ->
            if (lock.isHeld) lock.release()
        }
        wifiLock = null
    }

    private fun buildNotification(
        destinationNumber: String,
        callPhase: CallPhase,
        isMuted: Boolean,
        isSpeakerOn: Boolean
    ): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val hangupIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, CallForegroundService::class.java).apply { action = ACTION_HANGUP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val speakerIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, CallForegroundService::class.java).apply { action = ACTION_TOGGLE_SPEAKER },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusText = when (callPhase) {
            CallPhase.Connecting -> getString(R.string.call_status_connecting)
            CallPhase.Calling -> getString(R.string.call_status_calling)
            CallPhase.Ringing -> getString(R.string.call_status_ringing)
            CallPhase.Connected -> getString(R.string.call_status_connected)
            CallPhase.Ended -> getString(R.string.call_status_ended)
            CallPhase.Error -> getString(R.string.call_status_error)
            CallPhase.Idle -> getString(R.string.call_status_connecting)
        }
        val mutedText = if (isMuted) getString(R.string.call_status_muted) else ""
        val speakerText = if (isSpeakerOn) getString(R.string.call_status_speaker_on) else ""
        val destinationText = destinationNumber.ifBlank { getString(R.string.call_status_destination_unknown) }
        val contentText = listOf(statusText, destinationText, mutedText, speakerText)
            .filter { it.isNotBlank() }
            .joinToString(separator = " • ")
        val speakerActionLabel = if (isSpeakerOn) {
            getString(R.string.call_action_speaker_off)
        } else {
            getString(R.string.call_action_speaker_on)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_phone_call)
            .setContentTitle(getString(R.string.call_notification_title))
            .setContentText(contentText)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
            .setNumber(1)
            .addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    getString(R.string.call_action_hangup),
                    hangupIntent
                ).build()
            )
            .addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_lock_silent_mode_off,
                    speakerActionLabel,
                    speakerIntent
                ).build()
            )
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.call_notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.call_notification_channel_description)
            setShowBadge(true)
        }
        manager.createNotificationChannel(channel)
    }

    private fun restartIfCallStillActive() {
        val state = CallSessionManager.uiState.value
        val inForegroundCall = state.screen == Screen.IN_CALL && state.callPhase in FOREGROUND_PHASES
        if (!inForegroundCall) return

        val restartIntent = startOrUpdateIntent(
            context = applicationContext,
            destinationNumber = state.destinationNumber,
            callPhase = state.callPhase,
            isMuted = state.isMuted,
            isSpeakerOn = state.isSpeakerOn
        )
        ContextCompat.startForegroundService(applicationContext, restartIntent)
    }

    companion object {
        private const val CHANNEL_ID = "active_call_channel"
        private const val NOTIFICATION_ID = 1001
        private const val WAKE_LOCK_TAG = "esdiacwebrtc:call_wake_lock"
        private const val WIFI_LOCK_TAG = "esdiacwebrtc:call_wifi_lock"
        private val FOREGROUND_PHASES = setOf(
            CallPhase.Connecting,
            CallPhase.Calling,
            CallPhase.Ringing,
            CallPhase.Connected
        )

        const val ACTION_START_OR_UPDATE = "com.odogwudev.esdiacwebrtcdemo.action.START_OR_UPDATE_CALL"
        const val ACTION_HANGUP = "com.odogwudev.esdiacwebrtcdemo.action.HANGUP_CALL"
        const val ACTION_TOGGLE_SPEAKER = "com.odogwudev.esdiacwebrtcdemo.action.TOGGLE_SPEAKER"

        private const val EXTRA_DESTINATION_NUMBER = "destination_number"
        private const val EXTRA_CALL_PHASE = "call_phase"
        private const val EXTRA_IS_MUTED = "is_muted"
        private const val EXTRA_IS_SPEAKER_ON = "is_speaker_on"

        fun startOrUpdateIntent(
            context: Context,
            destinationNumber: String,
            callPhase: CallPhase,
            isMuted: Boolean,
            isSpeakerOn: Boolean
        ): Intent = Intent(context, CallForegroundService::class.java).apply {
            action = ACTION_START_OR_UPDATE
            putExtra(EXTRA_DESTINATION_NUMBER, destinationNumber)
            putExtra(EXTRA_CALL_PHASE, callPhase.name)
            putExtra(EXTRA_IS_MUTED, isMuted)
            putExtra(EXTRA_IS_SPEAKER_ON, isSpeakerOn)
        }
    }
}
