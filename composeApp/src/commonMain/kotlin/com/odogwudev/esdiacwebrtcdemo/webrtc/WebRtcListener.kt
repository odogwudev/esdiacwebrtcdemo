package com.odogwudev.esdiacwebrtcdemo.webrtc

import com.shepeliev.webrtckmp.IceCandidate
import com.shepeliev.webrtckmp.PeerConnectionState

interface WebRtcListener {
    fun onIceCandidateGenerated(candidate: IceCandidate)
    fun onConnectionStateChanged(state: PeerConnectionState)
    fun onError(message: String)
}
