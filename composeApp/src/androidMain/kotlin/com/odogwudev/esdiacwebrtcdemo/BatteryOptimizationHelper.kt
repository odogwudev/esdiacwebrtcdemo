package com.odogwudev.esdiacwebrtcdemo

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * Helps ensure the app is excluded from battery optimization on devices
 * with aggressive OEM power management (Xiaomi, Samsung, Huawei, OnePlus, etc.).
 */
object BatteryOptimizationHelper {

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Opens the standard Android battery optimization settings for this app.
     * Returns true if the intent was launched successfully.
     */
    fun requestIgnoreBatteryOptimizations(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Attempts to open the OEM-specific battery/power management settings
     * for devices known to aggressively kill background services.
     * Falls back to the standard battery optimization request if the OEM intent isn't available.
     */
    fun openOemBatterySettings(context: Context) {
        val oemIntent = getOemBatteryIntent()
        if (oemIntent != null) {
            try {
                oemIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(oemIntent)
                return
            } catch (_: Exception) {
                // OEM intent not available on this build, fall through
            }
        }
        requestIgnoreBatteryOptimizations(context)
    }

    /**
     * Returns true if the current device is from an OEM known for aggressive
     * battery optimization that goes beyond stock Android Doze.
     */
    fun isAggressiveOemDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return manufacturer in AGGRESSIVE_OEMS
    }

    private fun getOemBatteryIntent(): Intent? {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") -> {
                Intent().apply {
                    component = ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity"
                    )
                }
            }
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> {
                Intent().apply {
                    component = ComponentName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                    )
                }
            }
            manufacturer.contains("samsung") -> {
                Intent().apply {
                    component = ComponentName(
                        "com.samsung.android.lool",
                        "com.samsung.android.sm.battery.ui.BatteryActivity"
                    )
                }
            }
            manufacturer.contains("oppo") || manufacturer.contains("realme") -> {
                Intent().apply {
                    component = ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                    )
                }
            }
            manufacturer.contains("vivo") -> {
                Intent().apply {
                    component = ComponentName(
                        "com.vivo.permissionmanager",
                        "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                    )
                }
            }
            manufacturer.contains("oneplus") -> {
                Intent().apply {
                    component = ComponentName(
                        "com.oneplus.security",
                        "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"
                    )
                }
            }
            manufacturer.contains("asus") -> {
                Intent().apply {
                    component = ComponentName(
                        "com.asus.mobilemanager",
                        "com.asus.mobilemanager.autostart.AutoStartActivity"
                    )
                }
            }
            manufacturer.contains("lenovo") || manufacturer.contains("motorola") -> {
                Intent().apply {
                    component = ComponentName(
                        "com.lenovo.powersetting",
                        "com.lenovo.powersetting.ui.Settings\$HighPowerApplicationsActivity"
                    )
                }
            }
            else -> null
        }
    }

    private val AGGRESSIVE_OEMS = setOf(
        "xiaomi", "redmi", "poco",
        "huawei", "honor",
        "samsung",
        "oppo", "realme",
        "vivo",
        "oneplus",
        "asus",
        "lenovo", "motorola",
        "meizu",
        "tecno", "infinix", "itel",
    )
}
