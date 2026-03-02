package com.odogwudev.esdiacwebrtcdemo.ui

expect object CallProximityController {
    fun update(inCall: Boolean, useProximity: Boolean)
    fun reset()
}
