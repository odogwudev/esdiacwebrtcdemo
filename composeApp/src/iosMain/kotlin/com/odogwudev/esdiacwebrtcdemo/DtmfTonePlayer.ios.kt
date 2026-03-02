package com.odogwudev.esdiacwebrtcdemo

actual object DtmfTonePlayer {
    actual fun play(digit: String) {
        // No-op on iOS for now
    }

    actual fun release() {
        // No-op on iOS for now
    }
}
