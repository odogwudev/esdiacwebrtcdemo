package com.odogwudev.esdiacwebrtcdemo


import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVAudioSessionModeVoiceChat
import platform.AVFAudio.setActive
import platform.AVFAudio.AVAudioSessionPortOverrideSpeaker
import platform.AVFAudio.AVAudioSessionPortOverrideNone

actual object AudioRouteController {
    @OptIn(ExperimentalForeignApi::class)
    actual fun setSpeakerEnabled(enabled: Boolean) {
        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryPlayAndRecord, error = null)
        session.setMode(AVAudioSessionModeVoiceChat, error = null)
        session.setActive(true, error = null)
        session.overrideOutputAudioPort(
            if (enabled) AVAudioSessionPortOverrideSpeaker else AVAudioSessionPortOverrideNone,
            error = null
        )
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun reset() {
        val session = AVAudioSession.sharedInstance()
        session.overrideOutputAudioPort(AVAudioSessionPortOverrideNone, error = null)
        session.setActive(false, error = null)
    }
}
