package com.odogwudev.esdiacwebrtcdemo.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.odogwudev.esdiacwebrtcdemo.AudioRouteController
import com.odogwudev.esdiacwebrtcdemo.verto.VertoClient
import com.odogwudev.esdiacwebrtcdemo.verto.VertoConfig
import com.odogwudev.esdiacwebrtcdemo.verto.VertoEvent
import com.odogwudev.esdiacwebrtcdemo.verto.VertoState
import com.odogwudev.esdiacwebrtcdemo.webrtc.WebRtcClient
import com.odogwudev.esdiacwebrtcdemo.webrtc.WebRtcListener
import com.shepeliev.webrtckmp.IceCandidate
import com.shepeliev.webrtckmp.PeerConnectionState
import com.shepeliev.webrtckmp.SessionDescription
import com.shepeliev.webrtckmp.SessionDescriptionType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Screen { HOME, IN_CALL }

enum class CallPhase {
    Idle,
    Connecting,
    Calling,
    Ringing,
    Connected,
    Ended,
    Error
}

data class CallUiState(
    val screen: Screen = Screen.HOME,
    val destinationNumber: String = "",
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val callPhase: CallPhase = CallPhase.Idle,
    val connectedDurationSeconds: Long = 0L,
    val connectionState: PeerConnectionState = PeerConnectionState.New,
    val errorMessage: String? = null
)

class CallViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(CallUiState())
    val uiState: StateFlow<CallUiState> = _uiState.asStateFlow()

    private val vertoConfig = VertoConfig()
    private var vertoClient: VertoClient? = null
    private val webRtcClient = WebRtcClient()
    private var callTimerJob: Job? = null

    private val webRtcListener = object : WebRtcListener {
        override fun onIceCandidateGenerated(candidate: IceCandidate) {
            // Verto does not use trickle ICE — candidates are gathered into the SDP
        }

        override fun onConnectionStateChanged(state: PeerConnectionState) {
            _uiState.update { it.copy(connectionState = state) }
        }

        override fun onError(message: String) {
            _uiState.update { it.copy(errorMessage = message, callPhase = CallPhase.Error) }
        }
    }

    fun makeCall(destinationNumber: String) {
        stopConnectedTimer()
        AudioRouteController.reset()
        _uiState.update {
            it.copy(
                screen = Screen.IN_CALL,
                destinationNumber = destinationNumber,
                isMuted = false,
                isSpeakerOn = false,
                callPhase = CallPhase.Connecting,
                connectedDurationSeconds = 0L,
                connectionState = PeerConnectionState.New,
                errorMessage = null
            )
        }

        // Initialize WebRTC (audio capture + peer connection)
        webRtcClient.initialize(webRtcListener)

        // Connect Verto (WebSocket + auto-login)
        val client = VertoClient(vertoConfig)
        vertoClient = client
        client.connect(viewModelScope)

        // Observe Verto state transitions
        viewModelScope.launch {
            client.state.collect { state ->
                when (state) {
                    VertoState.LoggedIn -> sendInvite(client, destinationNumber)
                    VertoState.Calling -> _uiState.update { current ->
                        if (current.callPhase == CallPhase.Connected) current
                        else current.copy(callPhase = CallPhase.Calling)
                    }
                    VertoState.Ringing -> _uiState.update { current ->
                        if (current.callPhase == CallPhase.Connected) current
                        else current.copy(callPhase = CallPhase.Ringing)
                    }
                    VertoState.InCall -> markCallConnected()
                    VertoState.Error -> {
                        stopConnectedTimer()
                        _uiState.update { it.copy(callPhase = CallPhase.Error) }
                    }
                    VertoState.Disconnected -> {
                        stopConnectedTimer()
                        if (_uiState.value.callPhase != CallPhase.Idle) {
                            _uiState.update { it.copy(callPhase = CallPhase.Ended) }
                        }
                    }
                    else -> { /* transitional states */ }
                }
            }
        }

        // Observe Verto events for SDP handling
        viewModelScope.launch {
            client.events.collect { event ->
                when (event) {
                    is VertoEvent.Media -> {
                        val remoteSdp = SessionDescription(
                            type = SessionDescriptionType.Answer,
                            sdp = event.sdp
                        )
                        webRtcClient.setRemoteDescription(remoteSdp)
                    }
                    is VertoEvent.Answer -> {
                        event.sdp?.let { sdp ->
                            val remoteSdp = SessionDescription(
                                type = SessionDescriptionType.Answer,
                                sdp = sdp
                            )
                            webRtcClient.setRemoteDescription(remoteSdp)
                        }
                        markCallConnected()
                    }
                    is VertoEvent.Bye -> endCall()
                    is VertoEvent.Error -> {
                        stopConnectedTimer()
                        _uiState.update {
                            it.copy(
                                errorMessage = event.message,
                                callPhase = CallPhase.Error
                            )
                        }
                    }
                    else -> { /* LoginResult handled via state, Unknown ignored */ }
                }
            }
        }
    }

    private fun sendInvite(client: VertoClient, destinationNumber: String) {
        viewModelScope.launch {
            try {
                val sdp = webRtcClient.createOfferAndGatherIce()
                client.sendInvite(destinationNumber, sdp)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        errorMessage = "Failed to create offer: ${e.message}",
                        callPhase = CallPhase.Error
                    )
                }
            }
        }
    }

    fun toggleMute() {
        val newMuted = !_uiState.value.isMuted
        _uiState.update { it.copy(isMuted = newMuted) }
        webRtcClient.setAudioEnabled(!newMuted)
    }

    fun toggleSpeaker() {
        val newSpeakerState = !_uiState.value.isSpeakerOn
        _uiState.update { it.copy(isSpeakerOn = newSpeakerState) }
        AudioRouteController.setSpeakerEnabled(newSpeakerState)
    }

    fun endCall() {
        stopConnectedTimer()
        viewModelScope.launch {
            try {
                vertoClient?.sendBye()
            } catch (_: Exception) { }
        }
        webRtcClient.dispose()
        vertoClient?.disconnect()
        vertoClient = null
        AudioRouteController.reset()
        _uiState.update { CallUiState() }
    }

    override fun onCleared() {
        super.onCleared()
        stopConnectedTimer()
        webRtcClient.dispose()
        vertoClient?.disconnect()
        AudioRouteController.reset()
    }

    private fun markCallConnected() {
        if (_uiState.value.callPhase == CallPhase.Connected) return
        _uiState.update {
            it.copy(
                callPhase = CallPhase.Connected,
                connectedDurationSeconds = 0L
            )
        }
        startConnectedTimer()
    }

    private fun startConnectedTimer() {
        stopConnectedTimer()
        callTimerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                _uiState.update { current ->
                    if (current.callPhase != CallPhase.Connected) current
                    else current.copy(connectedDurationSeconds = current.connectedDurationSeconds + 1L)
                }
            }
        }
    }

    private fun stopConnectedTimer() {
        callTimerJob?.cancel()
        callTimerJob = null
    }
}
