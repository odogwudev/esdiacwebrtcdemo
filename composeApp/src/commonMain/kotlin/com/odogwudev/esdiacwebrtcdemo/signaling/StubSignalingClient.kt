package com.odogwudev.esdiacwebrtcdemo.signaling

/**
 * Placeholder signaling client that logs messages.
 * Replace with a real WebSocket implementation (e.g., using Ktor WebSocket client)
 * that connects to your signaling server.
 */
class StubSignalingClient : SignalingClient {

    private var listener: SignalingListener? = null
    private var roomId: String? = null

    override fun connect(roomId: String) {
        this.roomId = roomId
        println("[Signaling] Connected to room: $roomId")
        // TODO: Connect to WebSocket server at wss://your-server/room/$roomId
        listener?.onConnected()
    }

    override fun send(message: SignalingMessage) {
        println("[Signaling] Sending: $message")
        // TODO: Serialize and send via WebSocket
        // For local loopback testing, you could echo back an answer:
        // if (message is SignalingMessage.Offer) { listener?.onMessageReceived(SignalingMessage.Answer(...)) }
    }

    override fun disconnect() {
        println("[Signaling] Disconnected from room: $roomId")
        roomId = null
        // TODO: Close WebSocket connection
        listener?.onDisconnected()
    }

    override fun setListener(listener: SignalingListener) {
        this.listener = listener
    }
}
