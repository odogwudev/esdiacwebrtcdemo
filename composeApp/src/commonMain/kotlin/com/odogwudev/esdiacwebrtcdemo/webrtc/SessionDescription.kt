package com.odogwudev.esdiacwebrtcdemo.webrtc

data class SessionDescription(
    val type: SdpType,
    val sdp: String
)

enum class SdpType { OFFER, ANSWER, PRANSWER }
