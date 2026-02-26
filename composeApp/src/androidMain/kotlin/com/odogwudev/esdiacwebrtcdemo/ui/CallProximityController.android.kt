package com.odogwudev.esdiacwebrtcdemo.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.PowerManager
import com.odogwudev.esdiacwebrtcdemo.AppContextHolder

actual object CallProximityController {
    private const val WAKE_LOCK_TAG = "esdiacwebrtcdemo:proximity"
    private var wakeLock: PowerManager.WakeLock? = null
    private var sensorManager: SensorManager? = null
    private var proximitySensor: Sensor? = null
    private var sensorRegistered: Boolean = false
    private var shouldEnableProximity: Boolean = false
    private var isNear: Boolean = false
    private var hasProximityReading: Boolean = false

    private val proximityListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            val sensor = proximitySensor ?: return
            val value = event?.values?.firstOrNull() ?: return
            hasProximityReading = true
            isNear = value < sensor.maximumRange
            applyWakeLockState()
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    actual fun update(inCall: Boolean, speakerOn: Boolean) {
        val shouldEnable = inCall && !speakerOn
        shouldEnableProximity = shouldEnable
        if (!shouldEnable) {
            isNear = false
            hasProximityReading = false
            unregisterProximityListener()
            release()
            return
        }

        val hasProximitySensor = registerProximityListener()
        if (!hasProximitySensor) {
            // Fallback: keep wake lock active and let the platform handle proximity if possible.
            acquire()
            return
        }
        applyWakeLockState()
    }

    actual fun reset() {
        shouldEnableProximity = false
        isNear = false
        hasProximityReading = false
        unregisterProximityListener()
        release()
    }

    private fun registerProximityListener(): Boolean {
        val context = AppContextHolder.applicationContext() ?: return false
        val manager = sensorManager
            ?: (context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager)
                ?.also { sensorManager = it }
            ?: return false
        val sensor = proximitySensor
            ?: manager.getDefaultSensor(Sensor.TYPE_PROXIMITY)?.also { proximitySensor = it }
            ?: return false

        if (sensorRegistered) return true
        sensorRegistered = manager.registerListener(
            proximityListener,
            sensor,
            SensorManager.SENSOR_DELAY_NORMAL
        )
        return sensorRegistered
    }

    private fun unregisterProximityListener() {
        if (!sensorRegistered) return
        runCatching { sensorManager?.unregisterListener(proximityListener) }
        sensorRegistered = false
    }

    private fun applyWakeLockState() {
        if (!shouldEnableProximity) {
            release()
            return
        }
        if (!sensorRegistered || !hasProximityReading || isNear) acquire() else release()
    }

    private fun acquire() {
        val context = AppContextHolder.applicationContext() ?: return
        val powerManager =
            context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return

        val lock = wakeLock ?: runCatching {
            powerManager.newWakeLock(
                PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                WAKE_LOCK_TAG
            ).also { it.setReferenceCounted(false) }
        }.getOrNull()?.also { wakeLock = it } ?: return

        if (lock.isHeld) return
        runCatching { lock.acquire() }
    }

    private fun release() {
        val lock = wakeLock ?: return
        if (!lock.isHeld) return
        val released = runCatching {
            lock.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY)
        }.isSuccess
        if (!released) {
            runCatching { lock.release() }
        }
    }
}
