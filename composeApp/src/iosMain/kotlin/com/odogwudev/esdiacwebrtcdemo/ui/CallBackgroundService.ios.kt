package com.odogwudev.esdiacwebrtcdemo.ui

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVAudioSessionModeVoiceChat
import platform.AVFAudio.setActive
import platform.Foundation.NSNotificationCenter
import platform.Foundation.setValue
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationState
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter

actual object CallBackgroundService {
    private const val CALL_STATE_CHANGED_NOTIFICATION = "EsdiacCallStateChanged"
    private const val CALLKIT_END_REQUEST_NOTIFICATION = "EsdiacCallKitEndRequested"
    private const val CALL_NOTIFICATION_ID = "active_call_notification"
    private var hasShownBackgroundNotification: Boolean = false
    private var hasRegisteredCallKitEndObserver: Boolean = false

    init {
        registerCallKitEndObserverIfNeeded()
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun startOrUpdate(
        destinationNumber: String,
        callPhase: CallPhase,
        isMuted: Boolean,
        isSpeakerOn: Boolean
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

        val isBackground = UIApplication.sharedApplication.applicationState == UIApplicationState.UIApplicationStateBackground
        if (isBackground && !hasShownBackgroundNotification) {
            showBackgroundCallNotification(destinationNumber)
            hasShownBackgroundNotification = true
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun stop() {
        val session = AVAudioSession.sharedInstance()
        session.setActive(false, error = null)
        notifyNativeCallState(
            destinationNumber = "",
            callPhase = CallPhase.Ended,
            isMuted = false,
            isSpeakerOn = false
        )
        hasShownBackgroundNotification = false
        val notificationCenter = UNUserNotificationCenter.currentNotificationCenter()
        val ids = listOf(CALL_NOTIFICATION_ID)
        notificationCenter.removePendingNotificationRequestsWithIdentifiers(ids)
        notificationCenter.removeDeliveredNotificationsWithIdentifiers(ids)
    }

    private fun showBackgroundCallNotification(destinationNumber: String) {
        val notificationCenter = UNUserNotificationCenter.currentNotificationCenter()
        val content = UNMutableNotificationContent()
        val bodyText = if (destinationNumber.isBlank()) {
            "Your call is active in background."
        } else {
            "Call with $destinationNumber is active in background."
        }
        content.setValue("Call in progress", forKey = "title")
        content.setValue(bodyText, forKey = "body")
        val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(
            timeInterval = 0.5,
            repeats = false
        )
        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = CALL_NOTIFICATION_ID,
            content = content,
            trigger = trigger
        )
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

    private fun registerCallKitEndObserverIfNeeded() {
        if (hasRegisteredCallKitEndObserver) return
        hasRegisteredCallKitEndObserver = true
        NSNotificationCenter.defaultCenter.addObserverForName(
            name = CALLKIT_END_REQUEST_NOTIFICATION,
            `object` = null,
            queue = null
        ) { _ ->
            CallControlActionBus.dispatch(CallControlAction.EndCall)
        }
    }
}
