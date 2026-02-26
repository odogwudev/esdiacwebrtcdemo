package com.odogwudev.esdiacwebrtcdemo.ui

expect object CallProximityController {
    fun update(inCall: Boolean, speakerOn: Boolean)
    fun reset()
}
