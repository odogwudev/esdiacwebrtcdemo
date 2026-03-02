package com.odogwudev.esdiacwebrtcdemo.ui

import androidx.lifecycle.ViewModel
import com.odogwudev.esdiacwebrtcdemo.AudioRoute
import com.odogwudev.esdiacwebrtcdemo.AudioRouteType
import com.shepeliev.webrtckmp.PeerConnectionState
import kotlinx.coroutines.flow.StateFlow

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
    val activeAudioRoute: AudioRoute = AudioRoute(AudioRouteType.Earpiece, name = "Earpiece"),
    val availableAudioRoutes: List<AudioRoute> = listOf(
        AudioRoute(AudioRouteType.Earpiece, name = "Earpiece"),
        AudioRoute(AudioRouteType.Speaker, name = "Speaker")
    ),
    val isAudioRouteSheetVisible: Boolean = false,
    val isDialpadVisible: Boolean = false,
    val isOnHold: Boolean = false,
    val callPhase: CallPhase = CallPhase.Idle,
    val connectedDurationSeconds: Long = 0L,
    val connectionState: PeerConnectionState = PeerConnectionState.New,
    val errorMessage: String? = null
) {
    val isSpeakerOn: Boolean get() = activeAudioRoute.type == AudioRouteType.Speaker
}

class CallViewModel : ViewModel() {
    val uiState: StateFlow<CallUiState> = CallSessionManager.uiState

    fun makeCall(destinationNumber: String) = CallSessionManager.makeCall(destinationNumber)

    fun toggleMute() = CallSessionManager.toggleMute()

    fun toggleSpeaker() = CallSessionManager.toggleSpeaker()

    fun selectAudioRoute(route: AudioRoute) = CallSessionManager.selectAudioRoute(route)

    fun showAudioRouteSheet() = CallSessionManager.showAudioRouteSheet()

    fun hideAudioRouteSheet() = CallSessionManager.hideAudioRouteSheet()

    fun sendDtmf(digit: String) = CallSessionManager.sendDtmf(digit)

    fun toggleDialpad() = CallSessionManager.toggleDialpad()

    fun toggleHold() = CallSessionManager.toggleHold()

    fun endCall() = CallSessionManager.endCall()
}
