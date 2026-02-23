package com.odogwudev.esdiacwebrtcdemo.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.odogwudev.esdiacwebrtcdemo.signaling.SignalingClient
import com.odogwudev.esdiacwebrtcdemo.signaling.SignalingListener
import com.odogwudev.esdiacwebrtcdemo.signaling.SignalingMessage
import com.odogwudev.esdiacwebrtcdemo.signaling.StubSignalingClient
import com.odogwudev.esdiacwebrtcdemo.webrtc.IceCandidate
import com.odogwudev.esdiacwebrtcdemo.webrtc.PeerConnectionState
import com.odogwudev.esdiacwebrtcdemo.webrtc.WebRtcClient
import com.odogwudev.esdiacwebrtcdemo.webrtc.WebRtcListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Screen { HOME, IN_CALL }

data class CallUiState(
    val screen: Screen = Screen.HOME,
    val roomId: String = "",
    val isMuted: Boolean = false,
    val connectionState: PeerConnectionState = PeerConnectionState.NEW,
    val errorMessage: String? = null
)

class CallViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(CallUiState())
    val uiState: StateFlow<CallUiState> = _uiState.asStateFlow()

    private val signalingClient: SignalingClient = StubSignalingClient()
    private val webRtcClient = WebRtcClient()

    private val webRtcListener = object : WebRtcListener {
        override fun onIceCandidateGenerated(candidate: IceCandidate) {
            signalingClient.send(SignalingMessage.Ice(candidate))
        }

        override fun onConnectionStateChanged(state: PeerConnectionState) {
            _uiState.update { it.copy(connectionState = state) }
        }

        override fun onError(message: String) {
            _uiState.update { it.copy(errorMessage = message) }
        }
    }

    private val signalingListener = object : SignalingListener {
        override fun onMessageReceived(message: SignalingMessage) {
            viewModelScope.launch {
                when (message) {
                    is SignalingMessage.Offer -> {
                        webRtcClient.setRemoteDescription(message.sdp)
                        val answer = webRtcClient.createAnswer()
                        webRtcClient.setLocalDescription(answer)
                        signalingClient.send(SignalingMessage.Answer(answer))
                    }
                    is SignalingMessage.Answer -> {
                        webRtcClient.setRemoteDescription(message.sdp)
                    }
                    is SignalingMessage.Ice -> {
                        webRtcClient.addIceCandidate(message.candidate)
                    }
                    is SignalingMessage.Join -> { /* peer joined */ }
                    is SignalingMessage.Leave -> { endCall() }
                }
            }
        }

        override fun onConnected() {
            viewModelScope.launch {
                // As the initiator, create an offer when signaling connects
                val offer = webRtcClient.createOffer()
                webRtcClient.setLocalDescription(offer)
                signalingClient.send(SignalingMessage.Offer(offer))
            }
        }

        override fun onDisconnected() {
            _uiState.update { it.copy(connectionState = PeerConnectionState.DISCONNECTED) }
        }

        override fun onError(message: String) {
            _uiState.update { it.copy(errorMessage = message) }
        }
    }

    fun joinRoom(roomId: String) {
        _uiState.update {
            it.copy(
                screen = Screen.IN_CALL,
                roomId = roomId,
                isMuted = false,
                connectionState = PeerConnectionState.NEW,
                errorMessage = null
            )
        }

        webRtcClient.initialize(webRtcListener)
        signalingClient.setListener(signalingListener)
        signalingClient.connect(roomId)
    }

    fun toggleMute() {
        val newMuted = !_uiState.value.isMuted
        _uiState.update { it.copy(isMuted = newMuted) }
        webRtcClient.setAudioEnabled(!newMuted)
    }

    fun endCall() {
        webRtcClient.dispose()
        signalingClient.send(SignalingMessage.Leave)
        signalingClient.disconnect()
        _uiState.update {
            CallUiState() // reset to default
        }
    }

    override fun onCleared() {
        super.onCleared()
        webRtcClient.dispose()
        signalingClient.disconnect()
    }
}
