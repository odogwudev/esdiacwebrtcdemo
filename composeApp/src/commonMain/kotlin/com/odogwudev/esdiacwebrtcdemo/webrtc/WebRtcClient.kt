package com.odogwudev.esdiacwebrtcdemo.webrtc

import com.odogwudev.esdiacwebrtcdemo.RuntimeEnvironment
import com.shepeliev.webrtckmp.IceCandidate
import com.shepeliev.webrtckmp.IceServer
import com.shepeliev.webrtckmp.MediaDevices
import com.shepeliev.webrtckmp.MediaStream
import com.shepeliev.webrtckmp.OfferAnswerOptions
import com.shepeliev.webrtckmp.PeerConnection
import com.shepeliev.webrtckmp.PeerConnectionState
import com.shepeliev.webrtckmp.RtcConfiguration
import com.shepeliev.webrtckmp.SessionDescription
import com.shepeliev.webrtckmp.SessionDescriptionType
import com.shepeliev.webrtckmp.audioTracks
import com.shepeliev.webrtckmp.IceGatheringState
import com.shepeliev.webrtckmp.onConnectionStateChange
import com.shepeliev.webrtckmp.onIceCandidate
import com.shepeliev.webrtckmp.onIceGatheringState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class WebRtcClient {

    private var peerConnection: PeerConnection? = null
    private var localStream: MediaStream? = null
    private var listener: WebRtcListener? = null
    private var scope: CoroutineScope? = null

    fun initialize(listener: WebRtcListener) {
        this.listener = listener
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        val config = RtcConfiguration(
            iceServers = listOf(
                IceServer(urls = listOf("stun:stun.l.google.com:19302"))
            )
        )

        peerConnection = PeerConnection(rtcConfiguration = config)

        // Collect ICE candidates
        peerConnection!!.onIceCandidate
            .onEach { candidate -> listener.onIceCandidateGenerated(candidate) }
            .launchIn(scope!!)

        // Collect connection state changes
        peerConnection!!.onConnectionStateChange
            .onEach { state -> listener.onConnectionStateChanged(state) }
            .launchIn(scope!!)

        // iOS Simulator audio input can fail at RemoteIO start; use receive-audio fallback.
        if (!RuntimeEnvironment.isSimulator()) {
            scope!!.launch {
                try {
                    localStream = MediaDevices.getUserMedia(audio = true, video = false)
                    localStream!!.audioTracks.forEach { track ->
                        peerConnection!!.addTrack(track)
                    }
                } catch (e: Exception) {
                    listener.onError("Failed to get audio: ${e.message}")
                }
            }
        }
    }

    suspend fun createOffer(): SessionDescription {
        val options = OfferAnswerOptions(
            offerToReceiveAudio = true,
            offerToReceiveVideo = false
        )
        return peerConnection!!.createOffer(options)
    }

    suspend fun createAnswer(): SessionDescription {
        val options = OfferAnswerOptions(
            offerToReceiveAudio = true,
            offerToReceiveVideo = false
        )
        return peerConnection!!.createAnswer(options)
    }

    suspend fun setLocalDescription(sdp: SessionDescription) {
        peerConnection!!.setLocalDescription(sdp)
    }

    suspend fun setRemoteDescription(sdp: SessionDescription) {
        peerConnection!!.setRemoteDescription(sdp)
    }

    /**
     * Creates an offer, sets it as local description, waits for ICE gathering
     * to complete, then returns the final SDP with all ICE candidates.
     * Required for Verto which does not use trickle ICE.
     */
    suspend fun createOfferAndGatherIce(): String {
        val offer = createOffer()
        setLocalDescription(offer)

        // Wait for ICE gathering to complete
        peerConnection!!.onIceGatheringState
            .first { it == IceGatheringState.Complete }

        // Local description now contains all ICE candidates
        return peerConnection!!.localDescription!!.sdp
    }

    suspend fun addIceCandidate(candidate: IceCandidate) {
        peerConnection?.addIceCandidate(candidate)
    }

    fun setAudioEnabled(enabled: Boolean) {
        localStream?.audioTracks?.forEach { track ->
            track.enabled = enabled
        }
    }

    fun dispose() {
        localStream?.release()
        localStream = null
        peerConnection?.close()
        peerConnection = null
        scope?.cancel()
        scope = null
        listener = null
    }
}
