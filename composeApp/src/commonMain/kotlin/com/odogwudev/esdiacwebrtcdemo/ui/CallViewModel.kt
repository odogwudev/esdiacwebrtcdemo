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
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
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
    private var endCallJob: Job? = null
    private var isRemoteDescriptionSet: Boolean = false
    private val foregroundPhases = setOf(
        CallPhase.Connecting,
        CallPhase.Calling,
        CallPhase.Ringing,
        CallPhase.Connected
    )

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

    init {
        viewModelScope.launch {
            CallControlActionBus.actions.collect { action ->
                when (action) {
                    CallControlAction.EndCall -> {
                        if (_uiState.value.screen == Screen.IN_CALL) {
                            endCall()
                        }
                    }
                    CallControlAction.ToggleSpeaker -> {
                        if (_uiState.value.screen == Screen.IN_CALL) {
                            toggleSpeaker()
                        }
                    }
                }
                CallControlActionBus.consume(action)
            }
        }

        viewModelScope.launch {
            uiState
                .map { state ->
                    CallBackgroundState(
                        destinationNumber = state.destinationNumber,
                        callPhase = state.callPhase,
                        isMuted = state.isMuted,
                        isSpeakerOn = state.isSpeakerOn,
                        shouldRunService = state.screen == Screen.IN_CALL && state.callPhase in foregroundPhases
                    )
                }
                .distinctUntilChanged()
                .collect { state ->
                    if (state.shouldRunService) {
                        CallBackgroundService.startOrUpdate(
                            destinationNumber = state.destinationNumber,
                            callPhase = state.callPhase,
                            isMuted = state.isMuted,
                            isSpeakerOn = state.isSpeakerOn
                        )
                    } else {
                        CallBackgroundService.stop()
                    }
                }
        }
    }

    fun makeCall(destinationNumber: String) {
        stopConnectedTimer()
        isRemoteDescriptionSet = false
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
                        applyRemoteAnswerSdpIfNeeded(event.sdp, "verto.media")
                    }
                    is VertoEvent.Answer -> {
                        applyRemoteAnswerSdpIfNeeded(event.sdp, "verto.answer")
                        markCallConnected()
                    }
                    is VertoEvent.Bye -> finalizeCall(sendBye = false)
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
        if (endCallJob?.isActive == true) return
        endCallJob = viewModelScope.launch {
            finalizeCall(sendBye = true)
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopConnectedTimer()
        isRemoteDescriptionSet = false
        webRtcClient.dispose()
        vertoClient?.disconnect()
        AudioRouteController.reset()
        CallBackgroundService.stop()
    }

    private suspend fun finalizeCall(sendBye: Boolean) {
        stopConnectedTimer()
        isRemoteDescriptionSet = false

        if (sendBye) {
            withTimeoutOrNull(1_500L) {
                try {
                    vertoClient?.sendBye()
                } catch (_: Exception) {
                    // Ignore signaling errors and continue teardown.
                }
            }
        }

        webRtcClient.dispose()
        vertoClient?.disconnect()
        vertoClient = null
        AudioRouteController.reset()
        _uiState.update { CallUiState() }
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

    private suspend fun applyRemoteAnswerSdpIfNeeded(sdp: String?, source: String) {
        if (sdp.isNullOrBlank()) return
        if (isRemoteDescriptionSet) {
            println("[WebRTC] Ignoring remote SDP from $source because remote description is already set")
            return
        }

        val normalizedSdp = normalizeAnswerSdpForUnifiedPlan(sdp)
        val remoteSdp = SessionDescription(
            type = SessionDescriptionType.Answer,
            sdp = normalizedSdp
        )

        try {
            webRtcClient.setRemoteDescription(remoteSdp)
            isRemoteDescriptionSet = true
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    errorMessage = "Failed to apply remote SDP ($source): ${e.message}",
                    callPhase = CallPhase.Error
                )
            }
        }
    }

    /**
     * Ensure  a=mid and a=group:BUNDLE lines. exist so Unified Plan/BUNDLE negotiation can complete.
     */
    private fun normalizeAnswerSdpForUnifiedPlan(rawSdp: String): String {
        val hadTrailingLineBreak = rawSdp.endsWith("\r\n") || rawSdp.endsWith("\n")
        val lineBreak = if (rawSdp.contains("\r\n")) "\r\n" else "\n"
        val lines = rawSdp
            .trimEnd('\r', '\n')
            .split(Regex("\\r?\\n"))
            .toMutableList()
        if (lines.isEmpty()) return rawSdp

        val mids = mutableListOf<String>()
        var mediaSectionIndex = 0
        var i = 0
        while (i < lines.size) {
            if (!lines[i].startsWith("m=")) {
                i++
                continue
            }

            val sectionStart = i
            var sectionEnd = sectionStart + 1
            while (sectionEnd < lines.size && !lines[sectionEnd].startsWith("m=")) {
                sectionEnd++
            }

            val existingMidIndex = (sectionStart + 1 until sectionEnd)
                .firstOrNull { index -> lines[index].startsWith("a=mid:") }

            if (existingMidIndex != null) {
                mids += lines[existingMidIndex].removePrefix("a=mid:")
            } else {
                val generatedMid = mediaSectionIndex.toString()
                lines.add(sectionStart + 1, "a=mid:$generatedMid")
                mids += generatedMid
                sectionEnd++
            }

            mediaSectionIndex++
            i = sectionEnd
        }

        val hasBundleGroup = lines.any { it.startsWith("a=group:BUNDLE") }
        if (!hasBundleGroup && mids.isNotEmpty()) {
            val firstMediaLineIndex = lines.indexOfFirst { it.startsWith("m=") }
            if (firstMediaLineIndex >= 0) {
                lines.add(firstMediaLineIndex, "a=group:BUNDLE ${mids.joinToString(" ")}")
            }
        }

        val normalized = lines.joinToString(lineBreak)
        return if (hadTrailingLineBreak) normalized + lineBreak else normalized
    }

    private data class CallBackgroundState(
        val destinationNumber: String,
        val callPhase: CallPhase,
        val isMuted: Boolean,
        val isSpeakerOn: Boolean,
        val shouldRunService: Boolean
    )
}
