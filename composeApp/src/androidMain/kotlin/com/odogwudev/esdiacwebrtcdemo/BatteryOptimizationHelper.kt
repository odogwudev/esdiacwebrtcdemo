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
 * with aggressive OEM power management (Xiaomi, Samsung, Huawei, OnePlus, OPPO, etc.).
 *
 * On Chinese-OEM devices a standard "ignore battery optimizations" whitelist is
 * often **not enough** — the OEM's proprietary power-management layer can still
 * kill the process.  This helper therefore:
 *   1. Requests the standard Android battery-optimization exemption.
 *   2. Attempts to open the OEM-specific autostart / background-activity page
 *      (trying multiple known component names per OEM).
 *   3. Falls back to the per-app Android battery settings page if all else fails.
 */
object BatteryOptimizationHelper {

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Shows the standard Android "Allow unrestricted battery usage" dialog.
     * Returns true if the dialog was shown.
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
     * Best-effort attempt to whitelist the app on the current device:
     *   1. Always request the standard Android battery-optimization exemption.
     *   2. On aggressive OEM devices, also open the OEM-specific settings.
     */
    fun ensureBatteryWhitelisted(context: Context) {
        // Always request the standard exemption first.
        requestIgnoreBatteryOptimizations(context)

        // On aggressive OEMs, also open the proprietary settings.
        if (isAggressiveOemDevice()) {
            openOemBatterySettings(context)
        }
    }

    /**
     * Tries multiple known OEM intents for the current manufacturer.
     * Falls back to the per-app battery settings page.
     */
    fun openOemBatterySettings(context: Context) {
        val intents = getOemBatteryIntents()
        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            } catch (_: Exception) {
                // This component doesn't exist on this build, try next
            }
        }
        // All OEM intents failed — open the per-app battery settings page.
        openAppBatterySettings(context)
    }

    fun isAggressiveOemDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return AGGRESSIVE_OEMS.any { manufacturer.contains(it) }
    }

    /**
     * Opens the standard per-app battery settings page as a last-resort fallback.
     */
    private fun openAppBatterySettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            // Nothing more we can do
        }
    }

    /**
     * Returns an ordered list of intents to try for the current OEM.
     * Multiple entries per manufacturer cover different OS/skin versions.
     */
    private fun getOemBatteryIntents(): List<Intent> {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return when {
            // ──── OPPO / Realme / OnePlus (ColorOS, realmeUI, OxygenOS) ────
            manufacturer.contains("oppo") ||
            manufacturer.contains("realme") ||
            manufacturer.contains("oneplus") -> listOf(
                // ColorOS 12+ / OxygenOS 13+ (Oplus rebranding)
                componentIntent(
                    "com.oplus.safecenter",
                    "com.oplus.safecenter.permission.startup.StartupAppListActivity"
                ),
                // ColorOS 7-11
                componentIntent(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                ),
                // Older ColorOS
                componentIntent(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.startupapp.StartupAppListActivity"
                ),
                // OPPO battery management
                componentIntent(
                    "com.coloros.oppoguardelf",
                    "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity"
                ),
                // OnePlus-specific
                componentIntent(
                    "com.oneplus.security",
                    "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"
                ),
                // Generic battery optimization page
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            )

            // ──── Xiaomi / Redmi / POCO (MIUI / HyperOS) ────
            manufacturer.contains("xiaomi") ||
            manufacturer.contains("redmi") ||
            manufacturer.contains("poco") -> listOf(
                componentIntent(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                ),
                componentIntent(
                    "com.miui.powerkeeper",
                    "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
                ),
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            )

            // ──── Huawei / Honor ────
            manufacturer.contains("huawei") ||
            manufacturer.contains("honor") -> listOf(
                componentIntent(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                ),
                componentIntent(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.optimize.process.ProtectActivity"
                ),
                componentIntent(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"
                ),
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            )

            // ──── Samsung ────
            manufacturer.contains("samsung") -> listOf(
                componentIntent(
                    "com.samsung.android.lool",
                    "com.samsung.android.sm.battery.ui.BatteryActivity"
                ),
                componentIntent(
                    "com.samsung.android.sm",
                    "com.samsung.android.sm.battery.ui.BatteryActivity"
                ),
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            )

            // ──── Vivo ────
            manufacturer.contains("vivo") -> listOf(
                componentIntent(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                ),
                componentIntent(
                    "com.iqoo.secure",
                    "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"
                ),
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            )

            // ──── ASUS ────
            manufacturer.contains("asus") -> listOf(
                componentIntent(
                    "com.asus.mobilemanager",
                    "com.asus.mobilemanager.autostart.AutoStartActivity"
                ),
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            )

            // ──── Lenovo / Motorola ────
            manufacturer.contains("lenovo") ||
            manufacturer.contains("motorola") -> listOf(
                componentIntent(
                    "com.lenovo.powersetting",
                    "com.lenovo.powersetting.ui.Settings\$HighPowerApplicationsActivity"
                ),
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            )

            // ──── Tecno / Infinix / Itel ────
            manufacturer.contains("tecno") ||
            manufacturer.contains("infinix") ||
            manufacturer.contains("itel") -> listOf(
                componentIntent(
                    "com.transsion.phonemanager",
                    "com.transsion.phonemanager.permission.autorun.AutoRunListActivity"
                ),
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            )

            else -> listOf(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            )
        }
    }

    private fun componentIntent(pkg: String, cls: String): Intent {
        return Intent().apply {
            component = ComponentName(pkg, cls)
        }
    }

    private val AGGRESSIVE_OEMS = setOf(
        "xiaomi", "redmi", "poco",
        "huawei", "honor",
        "samsung",
        "oppo", "realme", "oneplus",
        "vivo", "iqoo",
        "asus",
        "lenovo", "motorola",
        "meizu",
        "tecno", "infinix", "itel",
    )
}
