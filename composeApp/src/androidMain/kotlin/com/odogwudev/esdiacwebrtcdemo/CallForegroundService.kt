package com.odogwudev.esdiacwebrtcdemo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.odogwudev.esdiacwebrtcdemo.ui.CallControlAction
import com.odogwudev.esdiacwebrtcdemo.ui.CallControlActionBus
import com.odogwudev.esdiacwebrtcdemo.ui.CallPhase

class CallForegroundService : Service() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
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
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun buildNotification(
        destinationNumber: String,
        callPhase: CallPhase,
        isMuted: Boolean,
        isSpeakerOn: Boolean
    ): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
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
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "active_call_channel"
        private const val NOTIFICATION_ID = 1001

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
