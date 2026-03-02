package com.odogwudev.esdiacwebrtcdemo

import kotlinx.coroutines.flow.StateFlow

expect object AudioRouteController {
    val availableRoutes: StateFlow<List<AudioRoute>>
    val activeRoute: StateFlow<AudioRoute>
    fun selectRoute(route: AudioRoute)
    fun startMonitoring()
    fun toggleSpeaker()
    fun reset()
}
