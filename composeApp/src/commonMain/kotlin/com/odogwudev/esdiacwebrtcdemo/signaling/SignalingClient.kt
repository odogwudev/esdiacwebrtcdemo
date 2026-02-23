package com.odogwudev.esdiacwebrtcdemo.signaling

import com.odogwudev.esdiacwebrtcdemo.webrtc.IceCandidate
import com.odogwudev.esdiacwebrtcdemo.webrtc.SessionDescription

sealed class SignalingMessage {
    data class Offer(val sdp: SessionDescription) : SignalingMessage()
    data class Answer(val sdp: SessionDescription) : SignalingMessage()
    data class Ice(val candidate: IceCandidate) : SignalingMessage()
    data class Join(val roomId: String) : SignalingMessage()
    data object Leave : SignalingMessage()
}

interface SignalingClient {
    fun connect(roomId: String)
    fun send(message: SignalingMessage)
    fun disconnect()
    fun setListener(listener: SignalingListener)
}

interface SignalingListener {
    fun onMessageReceived(message: SignalingMessage)
    fun onConnected()
    fun onDisconnected()
    fun onError(message: String)
}
