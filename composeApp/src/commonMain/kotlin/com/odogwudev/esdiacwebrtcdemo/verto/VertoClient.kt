package com.odogwudev.esdiacwebrtcdemo.verto

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

enum class VertoState {
    Disconnected,
    Connecting,
    Connected,
    LoggingIn,
    LoggedIn,
    Calling,
    Ringing,
    InCall,
    HangingUp,
    Error
}

class VertoClient(private val config: VertoConfig) {

    private val _state = MutableStateFlow(VertoState.Disconnected)
    val state: StateFlow<VertoState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<VertoEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<VertoEvent> = _events.asSharedFlow()

    private var client: HttpClient? = null
    private var session: WebSocketSession? = null
    private var receiveJob: Job? = null
    private var scope: CoroutineScope? = null

    var sessionId: String? = null
        private set
    var currentCallId: String? = null
        private set

    fun connect(coroutineScope: CoroutineScope) {
        scope = coroutineScope
        _state.value = VertoState.Connecting

        client = HttpClient {
            install(WebSockets)
        }

        coroutineScope.launch {
            try {
                session = client!!.webSocketSession(config.wsUrl)
                _state.value = VertoState.Connected
                startReceiving()
                login()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = VertoState.Error
                _events.emit(VertoEvent.Error(null, "Connection failed: ${e.message}"))
            }
        }
    }

    private fun startReceiving() {
        receiveJob = scope?.launch {
            try {
                for (frame in session!!.incoming) {
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        println("[Verto] Received: $text")
                        val event = VertoParser.parse(text)
                        handleEvent(event)
                        _events.emit(event)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (_state.value != VertoState.Disconnected) {
                    _state.value = VertoState.Error
                    _events.emit(VertoEvent.Error(null, "WebSocket error: ${e.message}"))
                }
            }
        }
    }

    private fun handleEvent(event: VertoEvent) {
        when (event) {
            is VertoEvent.LoginResult -> {
                sessionId = event.sessId
                _state.value = VertoState.LoggedIn
            }
            is VertoEvent.Media -> {
                _state.value = VertoState.Ringing
            }
            is VertoEvent.Answer -> {
                _state.value = VertoState.InCall
            }
            is VertoEvent.Bye -> {
                _state.value = VertoState.Disconnected
            }
            is VertoEvent.Error -> {
                _state.value = VertoState.Error
            }
            is VertoEvent.Unknown -> { /* ignore */ }
        }
    }

    private suspend fun login() {
        _state.value = VertoState.LoggingIn
        val msg = VertoMessages.login(config.login, config.password)
        send(msg)
    }

    @OptIn(ExperimentalUuidApi::class)
    suspend fun sendInvite(destinationNumber: String, sdp: String): String {
        val callId = Uuid.random().toString()
        currentCallId = callId
        _state.value = VertoState.Calling
        val msg = VertoMessages.invite(
            callId = callId,
            destinationNumber = destinationNumber,
            callerIdName = config.callerIdName,
            callerIdNumber = config.callerIdNumber,
            sdp = sdp
        )
        send(msg)
        return callId
    }

    suspend fun sendBye() {
        val callId = currentCallId ?: return
        _state.value = VertoState.HangingUp
        val msg = VertoMessages.bye(callId)
        send(msg)
        currentCallId = null
    }

    private suspend fun send(text: String) {
        println("[Verto] Sending: $text")
        session?.send(Frame.Text(text))
    }

    fun disconnect() {
        scope?.launch {
            try {
                session?.close()
            } catch (_: Exception) { }
            client?.close()
            client = null
            session = null
            receiveJob?.cancel()
            receiveJob = null
            _state.value = VertoState.Disconnected
        }
    }
}
