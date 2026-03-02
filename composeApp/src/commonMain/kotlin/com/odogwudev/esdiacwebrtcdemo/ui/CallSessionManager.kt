package com.odogwudev.esdiacwebrtcdemo.ui

import com.odogwudev.esdiacwebrtcdemo.AudioRouteController
import com.odogwudev.esdiacwebrtcdemo.DtmfTonePlayer
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

object CallSessionManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _uiState = MutableStateFlow(CallUiState())
    val uiState: StateFlow<CallUiState> = _uiState.asStateFlow()

    private val vertoConfig = VertoConfig()
    private var vertoClient: VertoClient? = null
    private val webRtcClient = WebRtcClient()
    private var callTimerJob: Job? = null
    private var endCallJob: Job? = null
    private var vertoStateJob: Job? = null
    private var vertoEventsJob: Job? = null
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
        scope.launch {
            CallControlActionBus.actions.collect { action ->
                when (action) {
                    CallControlAction.EndCall -> {
                        if (_uiState.value.screen == Screen.IN_CALL) endCall()
                    }
                    CallControlAction.ToggleSpeaker -> {
                        if (_uiState.value.screen == Screen.IN_CALL) toggleSpeaker()
                    }
                    CallControlAction.ToggleHold -> {
                        if (_uiState.value.screen == Screen.IN_CALL) toggleHold()
                    }
                }
                CallControlActionBus.consume(action)
            }
        }

        scope.launch {
            uiState
                .map { state ->
                    NotificationState(
                        destinationNumber = state.destinationNumber,
                        callPhase = state.callPhase,
                        isMuted = state.isMuted,
                        isSpeakerOn = state.isSpeakerOn,
                        isOnHold = state.isOnHold,
                        shouldRunService = state.screen == Screen.IN_CALL && state.callPhase in foregroundPhases
                    )
                }
                .distinctUntilChanged()
                .collect { state ->
                    CallProximityController.update(
                        inCall = state.shouldRunService,
                        speakerOn = state.isSpeakerOn
                    )
                    if (state.shouldRunService) {
                        CallBackgroundService.startOrUpdate(
                            destinationNumber = state.destinationNumber,
                            callPhase = state.callPhase,
                            isMuted = state.isMuted,
                            isSpeakerOn = state.isSpeakerOn,
                            isOnHold = state.isOnHold
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
        endCallJob = null
        AudioRouteController.setSpeakerEnabled(false)
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

        webRtcClient.initialize(webRtcListener)

        val client = VertoClient(vertoConfig)
        vertoClient = client
        client.connect(scope)

        vertoStateJob?.cancel()
        vertoStateJob = scope.launch {
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
                    else -> Unit
                }
            }
        }

        vertoEventsJob?.cancel()
        vertoEventsJob = scope.launch {
            client.events.collect { event ->
                when (event) {
                    is VertoEvent.Media -> applyRemoteAnswerSdpIfNeeded(event.sdp, "verto.media")
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
                    else -> Unit
                }
            }
        }
    }

    fun toggleMute() {
        val newMuted = !_uiState.value.isMuted
        _uiState.update { it.copy(isMuted = newMuted) }
        webRtcClient.setAudioEnabled(!newMuted)
    }

    fun sendDtmf(digit: String) {
        DtmfTonePlayer.play(digit)
        scope.launch {
            try {
                vertoClient?.sendDtmf(digit)
            } catch (_: Exception) {
                // Best-effort — don't fail the call for a missed DTMF
            }
        }
    }

    fun toggleDialpad() {
        _uiState.update { it.copy(isDialpadVisible = !it.isDialpadVisible) }
    }

    fun toggleHold() {
        val currentlyHeld = _uiState.value.isOnHold
        scope.launch {
            try {
                val direction = if (currentlyHeld) "sendrecv" else "sendonly"
                val action = if (currentlyHeld) "unhold" else "hold"
                val sdp = webRtcClient.createOfferAndGatherIceWithDirection(direction)
                // Allow the incoming verto.answer to re-apply remote SDP
                isRemoteDescriptionSet = false
                vertoClient?.sendModify(action, sdp)
                _uiState.update { it.copy(isOnHold = !currentlyHeld) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = "Hold failed: ${e.message}")
                }
            }
        }
    }

    fun toggleSpeaker() {
        val newSpeakerState = !_uiState.value.isSpeakerOn
        _uiState.update { it.copy(isSpeakerOn = newSpeakerState) }
        AudioRouteController.setSpeakerEnabled(newSpeakerState)
    }

    fun endCall() {
        if (endCallJob?.isActive == true) return
        endCallJob = scope.launch {
            finalizeCall(sendBye = true)
        }
    }

    private fun sendInvite(client: VertoClient, destinationNumber: String) {
        scope.launch {
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

        vertoStateJob?.cancel()
        vertoStateJob = null
        vertoEventsJob?.cancel()
        vertoEventsJob = null

        webRtcClient.dispose()
        vertoClient?.disconnect()
        vertoClient = null
        DtmfTonePlayer.release()
        CallProximityController.reset()
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
        callTimerJob = scope.launch {
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

        val offerSdp = webRtcClient.getLocalSdp()
        val normalizedSdp = normalizeAnswerSdpForUnifiedPlan(sdp, offerSdp)
        val remoteSdp = SessionDescription(
            type = SessionDescriptionType.Answer,
            sdp = normalizedSdp
        )

        try {
            webRtcClient.setRemoteDescription(remoteSdp)
            isRemoteDescriptionSet = true
        } catch (e: Throwable) {
            _uiState.update {
                it.copy(
                    errorMessage = "Failed to apply remote SDP ($source): ${e.message}",
                    callPhase = CallPhase.Error
                )
            }
        }
    }

    /**
     * Aligns the answer SDP with the offer so
     */
    private fun normalizeAnswerSdpForUnifiedPlan(rawSdp: String, offerSdp: String?): String {
        val hadTrailingLineBreak = rawSdp.endsWith("\r\n") || rawSdp.endsWith("\n")
        val lineBreak = if (rawSdp.contains("\r\n")) "\r\n" else "\n"
        val lines = rawSdp
            .trimEnd('\r', '\n')
            .split(Regex("\\r?\\n"))
            .toMutableList()
        if (lines.isEmpty()) return rawSdp

        // Extract the ordered mids from the offer so we can force-align the answer.
        val offerMids = offerSdp?.let { extractOrderedMids(it) } ?: emptyList()

        val answerMids = mutableListOf<String>()
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

            // Determine the correct mid: prefer the offer's mid for this position.
            val targetMid = offerMids.getOrNull(mediaSectionIndex)
                ?: mediaSectionIndex.toString()

            val existingMidIndex = (sectionStart + 1 until sectionEnd)
                .firstOrNull { idx -> lines[idx].startsWith("a=mid:") }

            if (existingMidIndex != null) {
                // Replace with the offer's mid so they match.
                lines[existingMidIndex] = "a=mid:$targetMid"
            } else {
                lines.add(sectionStart + 1, "a=mid:$targetMid")
                sectionEnd++
            }
            answerMids += targetMid

            mediaSectionIndex++
            i = sectionEnd
        }

        // Ensure a=group:BUNDLE line exists and lists the correct mids.
        val bundleLineIndex = lines.indexOfFirst { it.startsWith("a=group:BUNDLE") }
        val bundleLine = "a=group:BUNDLE ${answerMids.joinToString(" ")}"
        if (bundleLineIndex >= 0) {
            lines[bundleLineIndex] = bundleLine
        } else if (answerMids.isNotEmpty()) {
            val firstMediaLineIndex = lines.indexOfFirst { it.startsWith("m=") }
            if (firstMediaLineIndex >= 0) {
                lines.add(firstMediaLineIndex, bundleLine)
            }
        }

        val normalized = lines.joinToString(lineBreak)
        return if (hadTrailingLineBreak) normalized + lineBreak else normalized
    }

    /** Returns the ordered list of mid values from the given SDP. */
    private fun extractOrderedMids(sdp: String): List<String> {
        val mids = mutableListOf<String>()
        val sdpLines = sdp.split(Regex("\\r?\\n"))
        var inMediaSection = false
        for (line in sdpLines) {
            if (line.startsWith("m=")) {
                inMediaSection = true
            } else if (inMediaSection && line.startsWith("a=mid:")) {
                mids += line.removePrefix("a=mid:")
                inMediaSection = false
            }
        }
        return mids
    }

    private data class NotificationState(
        val destinationNumber: String,
        val callPhase: CallPhase,
        val isMuted: Boolean,
        val isSpeakerOn: Boolean,
        val isOnHold: Boolean,
        val shouldRunService: Boolean
    )
}
