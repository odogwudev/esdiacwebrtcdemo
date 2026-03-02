package com.odogwudev.esdiacwebrtcdemo

expect object DtmfTonePlayer {
    fun play(digit: String)
    fun release()
}
