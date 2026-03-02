package com.odogwudev.esdiacwebrtcdemo

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVAudioSessionModeVoiceChat
import platform.AVFAudio.AVAudioSessionPortBluetoothA2DP
import platform.AVFAudio.AVAudioSessionPortBluetoothHFP
import platform.AVFAudio.AVAudioSessionPortBluetoothLE
import platform.AVFAudio.AVAudioSessionPortBuiltInReceiver
import platform.AVFAudio.AVAudioSessionPortBuiltInSpeaker
import platform.AVFAudio.AVAudioSessionPortDescription
import platform.AVFAudio.AVAudioSessionPortHeadphones
import platform.AVFAudio.AVAudioSessionPortHeadsetMic
import platform.AVFAudio.AVAudioSessionPortOverrideNone
import platform.AVFAudio.AVAudioSessionPortOverrideSpeaker
import platform.AVFAudio.AVAudioSessionPortUSBAudio
import platform.AVFAudio.AVAudioSessionRouteChangeNotification
import platform.AVFAudio.AVAudioSessionRouteDescription
import platform.AVFAudio.setActive
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.valueForKey
import platform.darwin.NSObjectProtocol

actual object AudioRouteController {

    private val defaultEarpiece = AudioRoute(AudioRouteType.Earpiece, name = "iPhone")
    private val defaultSpeaker = AudioRoute(AudioRouteType.Speaker, name = "Speaker")

    private val _availableRoutes = MutableStateFlow(listOf(defaultEarpiece, defaultSpeaker))
    actual val availableRoutes: StateFlow<List<AudioRoute>> = _availableRoutes.asStateFlow()

    private val _activeRoute = MutableStateFlow(defaultEarpiece)
    actual val activeRoute: StateFlow<AudioRoute> = _activeRoute.asStateFlow()

    private var lastNonSpeakerRoute: AudioRoute = defaultEarpiece
    private var routeChangeObserver: NSObjectProtocol? = null

    @OptIn(ExperimentalForeignApi::class)
    actual fun startMonitoring() {
        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryPlayAndRecord, error = null)
        session.setMode(AVAudioSessionModeVoiceChat, error = null)
        session.setActive(true, error = null)

        routeChangeObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            name = AVAudioSessionRouteChangeNotification,
            `object` = session,
            queue = NSOperationQueue.mainQueue
        ) { _ ->
            refreshAvailableDevices()
            detectActiveRoute()
        }

        refreshAvailableDevices()
        selectRoute(defaultEarpiece)
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun selectRoute(route: AudioRoute) {
        val session = AVAudioSession.sharedInstance()
        if (route.type != AudioRouteType.Speaker) {
            lastNonSpeakerRoute = route
        }

        when (route.type) {
            AudioRouteType.Speaker -> {
                session.overrideOutputAudioPort(AVAudioSessionPortOverrideSpeaker, error = null)
            }
            AudioRouteType.Earpiece -> {
                session.overrideOutputAudioPort(AVAudioSessionPortOverrideNone, error = null)
                session.setPreferredInput(null, error = null)
            }
            AudioRouteType.Bluetooth, AudioRouteType.WiredHeadset -> {
                session.overrideOutputAudioPort(AVAudioSessionPortOverrideNone, error = null)
                @Suppress("UNCHECKED_CAST")
                val inputs = session.valueForKey("availableInputs") as? List<AVAudioSessionPortDescription>
                val matchingPort = inputs?.firstOrNull { port ->
                    port.UID == route.id
                }
                if (matchingPort != null) {
                    session.setPreferredInput(matchingPort, error = null)
                }
            }
        }
        _activeRoute.value = route
    }

    actual fun toggleSpeaker() {
        val current = _activeRoute.value
        if (current.type == AudioRouteType.Speaker) {
            val target = if (_availableRoutes.value.any { it.id == lastNonSpeakerRoute.id && it.type == lastNonSpeakerRoute.type }) {
                lastNonSpeakerRoute
            } else {
                defaultEarpiece
            }
            selectRoute(target)
        } else {
            selectRoute(defaultSpeaker)
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun reset() {
        val session = AVAudioSession.sharedInstance()
        session.overrideOutputAudioPort(AVAudioSessionPortOverrideNone, error = null)
        session.setPreferredInput(null, error = null)
        session.setActive(false, error = null)

        routeChangeObserver?.let { observer ->
            NSNotificationCenter.defaultCenter.removeObserver(observer)
        }
        routeChangeObserver = null

        _availableRoutes.value = listOf(defaultEarpiece, defaultSpeaker)
        _activeRoute.value = defaultEarpiece
        lastNonSpeakerRoute = defaultEarpiece
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun refreshAvailableDevices() {
        val session = AVAudioSession.sharedInstance()
        val routes = mutableListOf(defaultEarpiece, defaultSpeaker)

        @Suppress("UNCHECKED_CAST")
        val inputs = session.valueForKey("availableInputs") as? List<AVAudioSessionPortDescription> ?: emptyList()
        for (port in inputs) {
            val portType = port.portType
            when (portType) {
                AVAudioSessionPortBluetoothHFP,
                AVAudioSessionPortBluetoothA2DP,
                AVAudioSessionPortBluetoothLE -> {
                    routes.add(
                        AudioRoute(
                            type = AudioRouteType.Bluetooth,
                            id = port.UID,
                            name = port.portName
                        )
                    )
                }
                AVAudioSessionPortHeadsetMic,
                AVAudioSessionPortHeadphones,
                AVAudioSessionPortUSBAudio -> {
                    routes.add(
                        AudioRoute(
                            type = AudioRouteType.WiredHeadset,
                            id = port.UID,
                            name = port.portName
                        )
                    )
                }
            }
        }

        _availableRoutes.value = routes
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun detectActiveRoute() {
        val session = AVAudioSession.sharedInstance()
        val currentRoute = session.valueForKey("currentRoute") as? AVAudioSessionRouteDescription ?: return
        @Suppress("UNCHECKED_CAST")
        val outputs = currentRoute.valueForKey("outputs") as? List<AVAudioSessionPortDescription> ?: return
        val output = outputs.firstOrNull() ?: return

        val detectedType = when (output.portType) {
            AVAudioSessionPortBuiltInSpeaker -> AudioRouteType.Speaker
            AVAudioSessionPortBuiltInReceiver -> AudioRouteType.Earpiece
            AVAudioSessionPortBluetoothHFP,
            AVAudioSessionPortBluetoothA2DP,
            AVAudioSessionPortBluetoothLE -> AudioRouteType.Bluetooth
            AVAudioSessionPortHeadphones,
            AVAudioSessionPortUSBAudio -> AudioRouteType.WiredHeadset
            else -> AudioRouteType.Earpiece
        }

        val matchingRoute = _availableRoutes.value.firstOrNull {
            it.type == detectedType && (it.id == output.UID || it.id.isEmpty())
        } ?: AudioRoute(detectedType, id = output.UID, name = output.portName)

        _activeRoute.value = matchingRoute
    }
}
