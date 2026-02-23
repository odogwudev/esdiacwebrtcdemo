@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlinx.cinterop.BetaInteropApi::class,
    kotlin.experimental.ExperimentalObjCRefinement::class
)

package com.odogwudev.esdiacwebrtcdemo.webrtc

import WebRTC.RTCAudioSession
import WebRTC.RTCAudioTrack
import WebRTC.RTCConfiguration
import WebRTC.RTCDataChannel
import WebRTC.RTCIceCandidate as NativeIceCandidate
import WebRTC.RTCIceConnectionState
import WebRTC.RTCIceGatheringState
import WebRTC.RTCIceServer
import WebRTC.RTCMediaConstraints
import WebRTC.RTCMediaStream
import WebRTC.RTCPeerConnection
import WebRTC.RTCPeerConnectionDelegateProtocol
import WebRTC.RTCPeerConnectionFactory
import WebRTC.RTCPeerConnectionState
import WebRTC.RTCSdpSemantics
import WebRTC.RTCSdpType
import WebRTC.RTCSessionDescription as NativeSessionDescription
import WebRTC.RTCSignalingState
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

actual class WebRtcClient actual constructor() {

    private var factory: RTCPeerConnectionFactory? = null
    private var peerConnection: RTCPeerConnection? = null
    private var localAudioTrack: RTCAudioTrack? = null
    private var listener: WebRtcListener? = null

    actual fun initialize(listener: WebRtcListener) {
        this.listener = listener

        factory = RTCPeerConnectionFactory()

        val iceServer = RTCIceServer(
            uRLStrings = listOf("stun:stun.l.google.com:19302")
        )
        val config = RTCConfiguration().apply {
            iceServers = listOf(iceServer)
            sdpSemantics = RTCSdpSemantics.RTCSdpSemanticsUnifiedPlan
        }

        val constraints = RTCMediaConstraints(
            mandatoryConstraints = null,
            optionalConstraints = null
        )

        val delegate = PeerConnectionDelegate(listener)
        peerConnection = factory!!.peerConnectionWithConfiguration(
            config, constraints, delegate
        )

        // Create audio track
        val audioSource = factory!!.audioSourceWithConstraints(null)
        localAudioTrack = factory!!.audioTrackWithSource(audioSource, trackId = "audio0")
        peerConnection!!.addTrack(localAudioTrack!!, listOf("stream0"))
    }

    actual suspend fun createOffer(): SessionDescription = suspendCancellableCoroutine { cont ->
        val constraints = RTCMediaConstraints(
            mandatoryConstraints = mapOf(
                "OfferToReceiveAudio" to "true",
                "OfferToReceiveVideo" to "false"
            ),
            optionalConstraints = null
        )
        peerConnection!!.offerForConstraints(constraints) { sdp, error ->
            if (error != null) {
                cont.resumeWithException(Exception(error.localizedDescription))
            } else if (sdp != null) {
                cont.resume(
                    SessionDescription(
                        type = SdpType.OFFER,
                        sdp = sdp.sdp
                    )
                )
            }
        }
    }

    actual suspend fun createAnswer(): SessionDescription = suspendCancellableCoroutine { cont ->
        val constraints = RTCMediaConstraints(
            mandatoryConstraints = mapOf(
                "OfferToReceiveAudio" to "true",
                "OfferToReceiveVideo" to "false"
            ),
            optionalConstraints = null
        )
        peerConnection!!.answerForConstraints(constraints) { sdp, error ->
            if (error != null) {
                cont.resumeWithException(Exception(error.localizedDescription))
            } else if (sdp != null) {
                cont.resume(
                    SessionDescription(
                        type = SdpType.ANSWER,
                        sdp = sdp.sdp
                    )
                )
            }
        }
    }

    actual suspend fun setLocalDescription(sdp: SessionDescription): Unit =
        suspendCancellableCoroutine { cont ->
            val nativeSdp = NativeSessionDescription(
                type = sdp.type.toNativeType(),
                sdp = sdp.sdp
            )
            peerConnection!!.setLocalDescription(nativeSdp) { error ->
                if (error != null) {
                    cont.resumeWithException(Exception(error.localizedDescription))
                } else {
                    cont.resume(Unit)
                }
            }
        }

    actual suspend fun setRemoteDescription(sdp: SessionDescription): Unit =
        suspendCancellableCoroutine { cont ->
            val nativeSdp = NativeSessionDescription(
                type = sdp.type.toNativeType(),
                sdp = sdp.sdp
            )
            peerConnection!!.setRemoteDescription(nativeSdp) { error ->
                if (error != null) {
                    cont.resumeWithException(Exception(error.localizedDescription))
                } else {
                    cont.resume(Unit)
                }
            }
        }

    actual fun addIceCandidate(candidate: IceCandidate) {
        val nativeCandidate = NativeIceCandidate(
            sdp = candidate.sdp,
            sdpMLineIndex = candidate.sdpMLineIndex,
            sdpMid = candidate.sdpMid
        )
        peerConnection?.addIceCandidate(nativeCandidate) { _ -> }
    }

    actual fun setAudioEnabled(enabled: Boolean) {
        localAudioTrack?.setIsEnabled(enabled)
    }

    actual fun dispose() {
        peerConnection?.close()
        peerConnection = null
        factory = null
        localAudioTrack = null
    }

    private fun SdpType.toNativeType(): RTCSdpType = when (this) {
        SdpType.OFFER -> RTCSdpType.RTCSdpTypeOffer
        SdpType.ANSWER -> RTCSdpType.RTCSdpTypeAnswer
        SdpType.PRANSWER -> RTCSdpType.RTCSdpTypePrAnswer
    }
}

private class PeerConnectionDelegate(
    private val listener: WebRtcListener
) : NSObject(), RTCPeerConnectionDelegateProtocol {

    override fun peerConnection(
        peerConnection: RTCPeerConnection,
        didGenerateIceCandidate: NativeIceCandidate
    ) {
        listener.onIceCandidateGenerated(
            IceCandidate(
                sdpMid = didGenerateIceCandidate.sdpMid,
                sdpMLineIndex = didGenerateIceCandidate.sdpMLineIndex,
                sdp = didGenerateIceCandidate.sdp
            )
        )
    }

    override fun peerConnection(
        peerConnection: RTCPeerConnection,
        didChangeConnectionState: RTCPeerConnectionState
    ) {
        val state = when (didChangeConnectionState) {
            RTCPeerConnectionState.RTCPeerConnectionStateNew -> PeerConnectionState.NEW
            RTCPeerConnectionState.RTCPeerConnectionStateConnecting -> PeerConnectionState.CONNECTING
            RTCPeerConnectionState.RTCPeerConnectionStateConnected -> PeerConnectionState.CONNECTED
            RTCPeerConnectionState.RTCPeerConnectionStateDisconnected -> PeerConnectionState.DISCONNECTED
            RTCPeerConnectionState.RTCPeerConnectionStateFailed -> PeerConnectionState.FAILED
            RTCPeerConnectionState.RTCPeerConnectionStateClosed -> PeerConnectionState.CLOSED
            else -> PeerConnectionState.NEW
        }
        listener.onConnectionStateChanged(state)
    }

    override fun peerConnection(
        peerConnection: RTCPeerConnection,
        didChangeSignalingState: RTCSignalingState
    ) {}

    @Suppress("CONFLICTING_OVERLOADS")
    override fun peerConnection(
        peerConnection: RTCPeerConnection,
        didAddStream: RTCMediaStream
    ) {}

    @Suppress("CONFLICTING_OVERLOADS")
    override fun peerConnection(
        peerConnection: RTCPeerConnection,
        didRemoveStream: RTCMediaStream
    ) {}

    override fun peerConnectionShouldNegotiate(peerConnection: RTCPeerConnection) {}

    override fun peerConnection(
        peerConnection: RTCPeerConnection,
        didChangeIceConnectionState: RTCIceConnectionState
    ) {}

    override fun peerConnection(
        peerConnection: RTCPeerConnection,
        didChangeIceGatheringState: RTCIceGatheringState
    ) {}

    override fun peerConnection(
        peerConnection: RTCPeerConnection,
        didRemoveIceCandidates: List<*>
    ) {}

    override fun peerConnection(
        peerConnection: RTCPeerConnection,
        didOpenDataChannel: RTCDataChannel
    ) {}
}
