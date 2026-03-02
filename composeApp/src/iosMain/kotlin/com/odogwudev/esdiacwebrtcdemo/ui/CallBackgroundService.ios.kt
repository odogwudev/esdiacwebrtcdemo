package com.odogwudev.esdiacwebrtcdemo.ui

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVAudioSessionModeVoiceChat
import platform.AVFAudio.setActive
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationState
import platform.UIKit.UIDevice
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter

actual object CallBackgroundService {
    private const val CALL_STATE_CHANGED_NOTIFICATION = "EsdiacCallStateChanged"
    private const val CALLKIT_END_REQUEST_NOTIFICATION = "EsdiacCallKitEndRequested"
    private const val CALLKIT_SPEAKER_TOGGLE_REQUEST_NOTIFICATION = "EsdiacCallKitSpeakerToggleRequested"
    private const val CALL_NOTIFICATION_ID = "active_call_notification"
    private const val CALL_NOTIFICATION_CATEGORY = "ACTIVE_CALL_CATEGORY"
    private var hasRegisteredCallKitEndObserver: Boolean = false
    private var hasReportedActiveCallInProcess: Boolean = false
    private var lastBackgroundNotificationSnapshot: BackgroundNotificationSnapshot? = null

    init {
        registerCallKitObserversIfNeeded()
        registerForegroundObserver()
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun startOrUpdate(
        destinationNumber: String,
        callPhase: CallPhase,
        isMuted: Boolean,
        isSpeakerOn: Boolean,
        isOnHold: Boolean
    ) {
        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryPlayAndRecord, error = null)
        session.setMode(AVAudioSessionModeVoiceChat, error = null)
        session.setActive(true, error = null)
        notifyNativeCallState(
            destinationNumber = destinationNumber,
            callPhase = callPhase,
            isMuted = isMuted,
            isSpeakerOn = isSpeakerOn
        )
        hasReportedActiveCallInProcess = true

        val isBackground = UIApplication.sharedApplication.applicationState ==
            UIApplicationState.UIApplicationStateBackground
        if (isBackground) {
            if (supportsLiveActivityOnLockScreen()) {
                clearBackgroundCallNotification()
                return
            }
            val snapshot = BackgroundNotificationSnapshot(
                destinationNumber = destinationNumber,
                callPhase = callPhase,
                isSpeakerOn = isSpeakerOn
            )
            if (snapshot != lastBackgroundNotificationSnapshot) {
                showBackgroundCallNotification(destinationNumber, callPhase, isSpeakerOn)
                lastBackgroundNotificationSnapshot = snapshot
            }
        } else {
            lastBackgroundNotificationSnapshot = null
        }
    }

    actual fun stop() {
        // Do NOT deactivate the audio session here.
        // CallKit manages the audio session lifecycle — deactivating it here
        // could kill audio during app suspension or swipe-off.
        if (hasReportedActiveCallInProcess) {
            notifyNativeCallState(
                destinationNumber = "",
                callPhase = CallPhase.Ended,
                isMuted = false,
                isSpeakerOn = false
            )
            hasReportedActiveCallInProcess = false
        }
        clearBackgroundCallNotification()
        lastBackgroundNotificationSnapshot = null
        UIApplication.sharedApplication.applicationIconBadgeNumber = 0
    }

    private fun showBackgroundCallNotification(
        destinationNumber: String,
        callPhase: CallPhase,
        isSpeakerOn: Boolean
    ) {
        val phaseText = when (callPhase) {
            CallPhase.Connecting -> "Connecting..."
            CallPhase.Calling -> "Calling..."
            CallPhase.Ringing -> "Ringing..."
            CallPhase.Connected -> "Connected"
            else -> "Active"
        }
        val bodyText = if (destinationNumber.isBlank()) {
            "Call $phaseText — Tap to return"
        } else {
            "$destinationNumber — $phaseText — Tap to return"
        }
        val content = UNMutableNotificationContent()
        content.setTitle("Call in progress")
        content.setBody(bodyText)
        content.setCategoryIdentifier(CALL_NOTIFICATION_CATEGORY)
        content.setSubtitle(if (isSpeakerOn) "Speaker On" else "Speaker Off")
        content.setBadge(NSNumber(int = 1))
        content.setSound(null)
        UIApplication.sharedApplication.applicationIconBadgeNumber = 1

        val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(
            timeInterval = 0.1,
            repeats = false
        )
        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = CALL_NOTIFICATION_ID,
            content = content,
            trigger = trigger
        )
        val notificationCenter = UNUserNotificationCenter.currentNotificationCenter()
        clearBackgroundCallNotification()
        notificationCenter.addNotificationRequest(request, withCompletionHandler = null)
    }

    private fun notifyNativeCallState(
        destinationNumber: String,
        callPhase: CallPhase,
        isMuted: Boolean,
        isSpeakerOn: Boolean
    ) {
        val userInfo = mapOf<Any?, Any?>(
            "destinationNumber" to destinationNumber,
            "callPhase" to callPhase.name,
            "isMuted" to isMuted,
            "isSpeakerOn" to isSpeakerOn
        )
        NSNotificationCenter.defaultCenter.postNotificationName(
            CALL_STATE_CHANGED_NOTIFICATION,
            null,
            userInfo
        )
    }

    private fun registerCallKitObserversIfNeeded() {
        if (hasRegisteredCallKitEndObserver) return
        hasRegisteredCallKitEndObserver = true
        NSNotificationCenter.defaultCenter.addObserverForName(
            name = CALLKIT_END_REQUEST_NOTIFICATION,
            `object` = null,
            queue = null
        ) { _ ->
            CallControlActionBus.dispatch(CallControlAction.EndCall)
        }
        NSNotificationCenter.defaultCenter.addObserverForName(
            name = CALLKIT_SPEAKER_TOGGLE_REQUEST_NOTIFICATION,
            `object` = null,
            queue = null
        ) { _ ->
            CallControlActionBus.dispatch(CallControlAction.ToggleSpeaker)
        }
    }

    private fun registerForegroundObserver() {
        NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIApplicationDidBecomeActiveNotification,
            `object` = null,
            queue = null
        ) { _ ->
            lastBackgroundNotificationSnapshot = null
            clearBackgroundCallNotification()
            UIApplication.sharedApplication.applicationIconBadgeNumber = 0
        }
    }

    private fun supportsLiveActivityOnLockScreen(): Boolean {
        val parts = UIDevice.currentDevice.systemVersion.split(".")
        val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return major > 16 || (major == 16 && minor >= 1)
    }

    private fun clearBackgroundCallNotification() {
        val notificationCenter = UNUserNotificationCenter.currentNotificationCenter()
        val ids = listOf(CALL_NOTIFICATION_ID)
        notificationCenter.removePendingNotificationRequestsWithIdentifiers(ids)
        notificationCenter.removeDeliveredNotificationsWithIdentifiers(ids)
    }

    private data class BackgroundNotificationSnapshot(
        val destinationNumber: String,
        val callPhase: CallPhase,
        val isSpeakerOn: Boolean
    )
}
