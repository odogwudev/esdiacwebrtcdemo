package com.odogwudev.esdiacwebrtcdemo

import platform.Foundation.NSProcessInfo

actual object RuntimeEnvironment {
    actual fun isSimulator(): Boolean {
        return NSProcessInfo.processInfo.environment["SIMULATOR_DEVICE_NAME"] != null
    }
}
