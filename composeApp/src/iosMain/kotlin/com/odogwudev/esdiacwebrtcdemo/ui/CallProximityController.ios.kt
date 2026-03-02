package com.odogwudev.esdiacwebrtcdemo.ui

import platform.UIKit.UIDevice

actual object CallProximityController {
    actual fun update(inCall: Boolean, useProximity: Boolean) {
        UIDevice.currentDevice.proximityMonitoringEnabled = inCall && useProximity
    }

    actual fun reset() {
        UIDevice.currentDevice.proximityMonitoringEnabled = false
    }
}
