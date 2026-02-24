package com.odogwudev.esdiacwebrtcdemo

import platform.AVFoundation.AVAudioSession
import platform.AVFoundation.AVAudioSessionCategoryPlayAndRecord
import platform.AVFoundation.AVAudioSessionModeVoiceChat
import platform.AVFoundation.AVAudioSessionPortOverrideNone
import platform.AVFoundation.AVAudioSessionPortOverrideSpeaker

actual object AudioRouteController {
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

    actual fun reset() {
        val session = AVAudioSession.sharedInstance()
        session.overrideOutputAudioPort(AVAudioSessionPortOverrideNone, error = null)
        session.setActive(false, error = null)
    }
}
