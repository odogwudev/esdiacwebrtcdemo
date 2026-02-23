package com.odogwudev.esdiacwebrtcdemo.webrtc

import kotlinx.coroutines.suspendCancellableCoroutine
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.IceCandidate as NativeIceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription as NativeSessionDescription
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

actual class WebRtcClient actual constructor() {

    private var factory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localAudioTrack: AudioTrack? = null
    private var audioSource: AudioSource? = null
    private var listener: WebRtcListener? = null

    actual fun initialize(listener: WebRtcListener) {
        this.listener = listener

        // Initialize the PeerConnectionFactory globally
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(
                AppContextHolder.appContext
            ).createInitializationOptions()
        )

        factory = PeerConnectionFactory.builder()
            .createPeerConnectionFactory()

        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302")
                .createIceServer()
        )

        val config = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }

        peerConnection = factory!!.createPeerConnection(config, createPeerConnectionObserver())

        // Create audio source and track
        audioSource = factory!!.createAudioSource(MediaConstraints())
        localAudioTrack = factory!!.createAudioTrack("audio0", audioSource)
        peerConnection!!.addTrack(localAudioTrack!!, listOf("stream0"))
    }

    actual suspend fun createOffer(): SessionDescription = suspendCancellableCoroutine { cont ->
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }
        peerConnection!!.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: NativeSessionDescription) {
                cont.resume(
                    SessionDescription(
                        type = SdpType.OFFER,
                        sdp = sdp.description
                    )
                )
            }
            override fun onCreateFailure(error: String?) {
                cont.resumeWithException(Exception(error ?: "Failed to create offer"))
            }
            override fun onSetSuccess() {}
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }

    actual suspend fun createAnswer(): SessionDescription = suspendCancellableCoroutine { cont ->
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }
        peerConnection!!.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(sdp: NativeSessionDescription) {
                cont.resume(
                    SessionDescription(
                        type = SdpType.ANSWER,
                        sdp = sdp.description
                    )
                )
            }
            override fun onCreateFailure(error: String?) {
                cont.resumeWithException(Exception(error ?: "Failed to create answer"))
            }
            override fun onSetSuccess() {}
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }

    actual suspend fun setLocalDescription(sdp: SessionDescription): Unit =
        suspendCancellableCoroutine { cont ->
            val nativeSdp = NativeSessionDescription(
                sdp.type.toNativeType(),
                sdp.sdp
            )
            peerConnection!!.setLocalDescription(object : SdpObserver {
                override fun onSetSuccess() { cont.resume(Unit) }
                override fun onSetFailure(error: String?) {
                    cont.resumeWithException(Exception(error ?: "Failed to set local description"))
                }
                override fun onCreateSuccess(p0: NativeSessionDescription?) {}
                override fun onCreateFailure(p0: String?) {}
            }, nativeSdp)
        }

    actual suspend fun setRemoteDescription(sdp: SessionDescription): Unit =
        suspendCancellableCoroutine { cont ->
            val nativeSdp = NativeSessionDescription(
                sdp.type.toNativeType(),
                sdp.sdp
            )
            peerConnection!!.setRemoteDescription(object : SdpObserver {
                override fun onSetSuccess() { cont.resume(Unit) }
                override fun onSetFailure(error: String?) {
                    cont.resumeWithException(Exception(error ?: "Failed to set remote description"))
                }
                override fun onCreateSuccess(p0: NativeSessionDescription?) {}
                override fun onCreateFailure(p0: String?) {}
            }, nativeSdp)
        }

    actual fun addIceCandidate(candidate: IceCandidate) {
        val nativeCandidate = NativeIceCandidate(
            candidate.sdpMid,
            candidate.sdpMLineIndex,
            candidate.sdp
        )
        peerConnection?.addIceCandidate(nativeCandidate)
    }

    actual fun setAudioEnabled(enabled: Boolean) {
        localAudioTrack?.setEnabled(enabled)
    }

    actual fun dispose() {
        localAudioTrack?.dispose()
        localAudioTrack = null
        audioSource?.dispose()
        audioSource = null
        peerConnection?.close()
        peerConnection?.dispose()
        peerConnection = null
        factory?.dispose()
        factory = null
    }

    private fun createPeerConnectionObserver() = object : PeerConnection.Observer {
        override fun onIceCandidate(candidate: NativeIceCandidate) {
            listener?.onIceCandidateGenerated(
                IceCandidate(
                    sdpMid = candidate.sdpMid,
                    sdpMLineIndex = candidate.sdpMLineIndex,
                    sdp = candidate.sdp
                )
            )
        }

        override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
            val state = when (newState) {
                PeerConnection.PeerConnectionState.NEW -> PeerConnectionState.NEW
                PeerConnection.PeerConnectionState.CONNECTING -> PeerConnectionState.CONNECTING
                PeerConnection.PeerConnectionState.CONNECTED -> PeerConnectionState.CONNECTED
                PeerConnection.PeerConnectionState.DISCONNECTED -> PeerConnectionState.DISCONNECTED
                PeerConnection.PeerConnectionState.FAILED -> PeerConnectionState.FAILED
                PeerConnection.PeerConnectionState.CLOSED -> PeerConnectionState.CLOSED
                else -> PeerConnectionState.NEW
            }
            listener?.onConnectionStateChanged(state)
        }

        override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {}
        override fun onIceConnectionReceivingChange(receiving: Boolean) {}
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
        override fun onIceCandidatesRemoved(candidates: Array<out NativeIceCandidate>?) {}
        override fun onAddStream(stream: MediaStream?) {}
        override fun onRemoveStream(stream: MediaStream?) {}
        override fun onDataChannel(channel: DataChannel?) {}
        override fun onRenegotiationNeeded() {}
        override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {}
        override fun onTrack(transceiver: RtpTransceiver?) {}
    }

    private fun SdpType.toNativeType(): NativeSessionDescription.Type = when (this) {
        SdpType.OFFER -> NativeSessionDescription.Type.OFFER
        SdpType.ANSWER -> NativeSessionDescription.Type.ANSWER
        SdpType.PRANSWER -> NativeSessionDescription.Type.PRANSWER
    }
}
