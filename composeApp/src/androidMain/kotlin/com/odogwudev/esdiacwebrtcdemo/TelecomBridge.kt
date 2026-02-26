package com.odogwudev.esdiacwebrtcdemo

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import androidx.annotation.RequiresApi
import com.odogwudev.esdiacwebrtcdemo.ui.CallPhase

/**
 * Bridges between our call logic and the Android Telecom framework.
 *
 * On API 26+ we register a **self-managed** [PhoneAccount] so the system
 * treats our VoIP calls exactly like cellular calls — OEM power managers
 * will never kill the process while a call is active.
 *
 * If registration or [placeCall] fails for any reason the caller should
 * fall back to the foreground-service approach.
 */
object TelecomBridge {

    private var phoneAccountHandle: PhoneAccountHandle? = null

    @Volatile
    var activeConnection: VoipConnection? = null
        private set

    val isActive: Boolean get() = activeConnection != null

    // ---------- registration ----------

    fun register(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val telecomManager =
            context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager ?: return

        val handle = PhoneAccountHandle(
            ComponentName(context, CallConnectionService::class.java),
            "esdiac_voip"
        )

        val account = PhoneAccount.builder(handle, "Esdiac VoIP")
            .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)
            .build()

        try {
            telecomManager.registerPhoneAccount(account)
            phoneAccountHandle = handle
        } catch (e: Exception) {
            println("[TelecomBridge] Failed to register phone account: ${e.message}")
        }
    }

    // ---------- call lifecycle ----------

    /**
     * Asks the Telecom framework to place an outgoing self-managed call.
     * Returns `true` if the request was accepted (the system will call
     * [CallConnectionService.onCreateOutgoingConnection] shortly after).
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun placeCall(context: Context, destinationNumber: String): Boolean {
        val handle = phoneAccountHandle ?: return false

        val telecomManager =
            context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager ?: return false

        val uri = Uri.fromParts("tel", destinationNumber, null)
        val extras = Bundle().apply {
            putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
        }

        return try {
            telecomManager.placeCall(uri, extras)
            true
        } catch (e: Exception) {
            println("[TelecomBridge] placeCall failed: ${e.message}")
            false
        }
    }

    /** Mirror a [CallPhase] change to the active Telecom [VoipConnection]. */
    fun updateCallPhase(phase: CallPhase) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        activeConnection?.updatePhase(phase)
    }

    /** Tear down the Telecom connection (e.g. when CallSessionManager ends the call). */
    fun endCall() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        activeConnection?.updatePhase(CallPhase.Ended)
        activeConnection = null
    }

    // ---------- callbacks from ConnectionService ----------

    fun onConnectionCreated(connection: VoipConnection) {
        activeConnection = connection
    }

    fun onConnectionFailed() {
        activeConnection = null
    }

    fun onConnectionDestroyed() {
        activeConnection = null
    }
}
