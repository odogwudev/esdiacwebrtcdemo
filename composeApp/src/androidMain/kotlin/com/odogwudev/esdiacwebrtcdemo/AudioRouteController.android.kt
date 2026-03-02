package com.odogwudev.esdiacwebrtcdemo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AppContextHolder {
    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    fun audioManager(): AudioManager? {
        val context = appContext ?: return null
        return context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    fun applicationContext(): Context? = appContext
}

actual object AudioRouteController {

    private val defaultEarpiece = AudioRoute(AudioRouteType.Earpiece, name = "Earpiece")
    private val defaultSpeaker = AudioRoute(AudioRouteType.Speaker, name = "Speaker")

    private val _availableRoutes = MutableStateFlow(listOf(defaultEarpiece, defaultSpeaker))
    actual val availableRoutes: StateFlow<List<AudioRoute>> = _availableRoutes.asStateFlow()

    private val _activeRoute = MutableStateFlow(defaultEarpiece)
    actual val activeRoute: StateFlow<AudioRoute> = _activeRoute.asStateFlow()

    private var lastNonSpeakerRoute: AudioRoute = defaultEarpiece
    private var isMonitoring = false

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            refreshAvailableDevices()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            refreshAvailableDevices()
            val current = _activeRoute.value
            if (current.type != AudioRouteType.Earpiece &&
                current.type != AudioRouteType.Speaker &&
                _availableRoutes.value.none { it.id == current.id }
            ) {
                selectRoute(defaultEarpiece)
            }
        }
    }

    private var scoReceiver: BroadcastReceiver? = null

    actual fun startMonitoring() {
        if (isMonitoring) return
        isMonitoring = true
        val audioManager = AppContextHolder.audioManager() ?: return
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

        audioManager.registerAudioDeviceCallback(
            deviceCallback,
            Handler(Looper.getMainLooper())
        )

        if (Build.VERSION.SDK_INT < 31) {
            val context = AppContextHolder.applicationContext() ?: return
            scoReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    refreshAvailableDevices()
                }
            }
            val filter = IntentFilter().apply {
                addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
                addAction(AudioManager.ACTION_HEADSET_PLUG)
            }
            context.registerReceiver(scoReceiver, filter)
        }

        refreshAvailableDevices()
        selectRoute(defaultEarpiece)
    }

    actual fun selectRoute(route: AudioRoute) {
        val audioManager = AppContextHolder.audioManager() ?: return
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

        if (route.type != AudioRouteType.Speaker) {
            lastNonSpeakerRoute = route
        }

        if (Build.VERSION.SDK_INT >= 31) {
            selectRouteApi31(audioManager, route)
        } else {
            selectRouteLegacy(audioManager, route)
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

    actual fun reset() {
        val audioManager = AppContextHolder.audioManager() ?: return
        if (isMonitoring) {
            audioManager.unregisterAudioDeviceCallback(deviceCallback)
            val context = AppContextHolder.applicationContext()
            scoReceiver?.let { receiver ->
                try { context?.unregisterReceiver(receiver) } catch (_: Exception) {}
            }
            scoReceiver = null
            isMonitoring = false
        }
        if (Build.VERSION.SDK_INT >= 31) {
            audioManager.clearCommunicationDevice()
        } else {
            audioManager.isSpeakerphoneOn = false
            @Suppress("DEPRECATION")
            audioManager.stopBluetoothSco()
            @Suppress("DEPRECATION")
            audioManager.setBluetoothScoOn(false)
        }
        audioManager.mode = AudioManager.MODE_NORMAL
        _availableRoutes.value = listOf(defaultEarpiece, defaultSpeaker)
        _activeRoute.value = defaultEarpiece
        lastNonSpeakerRoute = defaultEarpiece
    }

    private fun refreshAvailableDevices() {
        val audioManager = AppContextHolder.audioManager() ?: return
        val routes = mutableListOf(defaultEarpiece, defaultSpeaker)

        if (Build.VERSION.SDK_INT >= 31) {
            val devices = audioManager.availableCommunicationDevices
            for (device in devices) {
                when (device.type) {
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                    AudioDeviceInfo.TYPE_BLE_HEADSET,
                    AudioDeviceInfo.TYPE_BLE_SPEAKER -> {
                        val name = device.productName?.toString()?.takeIf { it.isNotBlank() }
                            ?: "Bluetooth"
                        routes.add(
                            AudioRoute(
                                type = AudioRouteType.Bluetooth,
                                id = device.id.toString(),
                                name = name
                            )
                        )
                    }
                    AudioDeviceInfo.TYPE_WIRED_HEADSET,
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                    AudioDeviceInfo.TYPE_USB_HEADSET -> {
                        val name = device.productName?.toString()?.takeIf { it.isNotBlank() }
                            ?: "Wired Headset"
                        routes.add(
                            AudioRoute(
                                type = AudioRouteType.WiredHeadset,
                                id = device.id.toString(),
                                name = name
                            )
                        )
                    }
                }
            }
        } else {
            val outputDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            for (device in outputDevices) {
                when (device.type) {
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> {
                        val name = device.productName?.toString()?.takeIf { it.isNotBlank() }
                            ?: "Bluetooth"
                        if (routes.none { it.type == AudioRouteType.Bluetooth && it.id == device.id.toString() }) {
                            routes.add(
                                AudioRoute(
                                    type = AudioRouteType.Bluetooth,
                                    id = device.id.toString(),
                                    name = name
                                )
                            )
                        }
                    }
                    AudioDeviceInfo.TYPE_WIRED_HEADSET,
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                    AudioDeviceInfo.TYPE_USB_HEADSET -> {
                        if (routes.none { it.type == AudioRouteType.WiredHeadset }) {
                            routes.add(
                                AudioRoute(
                                    type = AudioRouteType.WiredHeadset,
                                    id = device.id.toString(),
                                    name = "Wired Headset"
                                )
                            )
                        }
                    }
                }
            }
        }

        _availableRoutes.value = routes.distinctBy { "${it.type}_${it.id}" }
    }

    @androidx.annotation.RequiresApi(31)
    private fun selectRouteApi31(audioManager: AudioManager, route: AudioRoute) {
        when (route.type) {
            AudioRouteType.Earpiece -> {
                val device = audioManager.availableCommunicationDevices
                    .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
                if (device != null) {
                    audioManager.setCommunicationDevice(device)
                } else {
                    audioManager.clearCommunicationDevice()
                }
            }
            AudioRouteType.Speaker -> {
                val device = audioManager.availableCommunicationDevices
                    .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                if (device != null) {
                    audioManager.setCommunicationDevice(device)
                } else {
                    audioManager.clearCommunicationDevice()
                    @Suppress("DEPRECATION")
                    audioManager.isSpeakerphoneOn = true
                }
            }
            AudioRouteType.Bluetooth -> {
                val deviceId = route.id.toIntOrNull()
                val device = audioManager.availableCommunicationDevices
                    .firstOrNull { it.id == deviceId }
                if (device != null) {
                    audioManager.setCommunicationDevice(device)
                }
            }
            AudioRouteType.WiredHeadset -> {
                val deviceId = route.id.toIntOrNull()
                val device = audioManager.availableCommunicationDevices
                    .firstOrNull { it.id == deviceId }
                if (device != null) {
                    audioManager.setCommunicationDevice(device)
                } else {
                    audioManager.clearCommunicationDevice()
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun selectRouteLegacy(audioManager: AudioManager, route: AudioRoute) {
        when (route.type) {
            AudioRouteType.Earpiece -> {
                audioManager.isSpeakerphoneOn = false
                audioManager.stopBluetoothSco()
                audioManager.setBluetoothScoOn(false)
            }
            AudioRouteType.Speaker -> {
                audioManager.stopBluetoothSco()
                audioManager.setBluetoothScoOn(false)
                audioManager.isSpeakerphoneOn = true
            }
            AudioRouteType.Bluetooth -> {
                audioManager.isSpeakerphoneOn = false
                audioManager.startBluetoothSco()
                audioManager.setBluetoothScoOn(true)
            }
            AudioRouteType.WiredHeadset -> {
                audioManager.isSpeakerphoneOn = false
                audioManager.stopBluetoothSco()
                audioManager.setBluetoothScoOn(false)
            }
        }
    }
}
