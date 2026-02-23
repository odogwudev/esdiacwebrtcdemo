package com.odogwudev.esdiacwebrtcdemo.webrtc

interface WebRtcListener {
    fun onIceCandidateGenerated(candidate: IceCandidate)
    fun onConnectionStateChanged(state: PeerConnectionState)
    fun onError(message: String)
}
