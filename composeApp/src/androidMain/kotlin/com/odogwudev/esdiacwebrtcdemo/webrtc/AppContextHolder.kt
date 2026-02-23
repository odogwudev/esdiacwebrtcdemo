package com.odogwudev.esdiacwebrtcdemo.webrtc

import android.content.Context

object AppContextHolder {
    lateinit var appContext: Context
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
    }
}
