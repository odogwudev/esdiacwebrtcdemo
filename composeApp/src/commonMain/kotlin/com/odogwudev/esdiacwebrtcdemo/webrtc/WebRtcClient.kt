package com.odogwudev.esdiacwebrtcdemo.webrtc

expect class WebRtcClient() {
    fun initialize(listener: WebRtcListener)
    suspend fun createOffer(): SessionDescription
    suspend fun createAnswer(): SessionDescription
    suspend fun setLocalDescription(sdp: SessionDescription)
    suspend fun setRemoteDescription(sdp: SessionDescription)
    fun addIceCandidate(candidate: IceCandidate)
    fun setAudioEnabled(enabled: Boolean)
    fun dispose()
}
