package com.odogwudev.esdiacwebrtcdemo.webrtc

data class IceCandidate(
    val sdpMid: String?,
    val sdpMLineIndex: Int,
    val sdp: String
)
