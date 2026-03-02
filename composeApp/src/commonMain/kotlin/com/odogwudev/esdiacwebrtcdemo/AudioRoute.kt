package com.odogwudev.esdiacwebrtcdemo

enum class AudioRouteType {
    Earpiece,
    Speaker,
    Bluetooth,
    WiredHeadset
}

data class AudioRoute(
    val type: AudioRouteType,
    val id: String = "",
    val name: String = type.name
)
