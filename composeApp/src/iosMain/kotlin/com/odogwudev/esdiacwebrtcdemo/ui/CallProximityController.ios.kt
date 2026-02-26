package com.odogwudev.esdiacwebrtcdemo.ui

import platform.UIKit.UIDevice

actual object CallProximityController {
    actual fun update(inCall: Boolean, speakerOn: Boolean) {
        UIDevice.currentDevice.proximityMonitoringEnabled = inCall && !speakerOn
    }

    actual fun reset() {
        UIDevice.currentDevice.proximityMonitoringEnabled = false
    }
}
