package com.odogwudev.esdiacwebrtcdemo.verto

data class VertoConfig(
    val wsUrl: String = "wss://webrtc.esdiac.com:8082",
    val login: String = "2348074563047@webrtc.esdiac.com",
    val password: String = "Password@234",
    val callerIdName: String = "Test",
    val callerIdNumber: String = "2348074563047"
)
