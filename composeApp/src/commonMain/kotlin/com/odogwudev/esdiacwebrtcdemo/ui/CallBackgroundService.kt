package com.odogwudev.esdiacwebrtcdemo.ui

expect object CallBackgroundService {
    fun startOrUpdate(
        destinationNumber: String,
        callPhase: CallPhase,
        isMuted: Boolean,
        isSpeakerOn: Boolean
    )
    fun stop()
}
