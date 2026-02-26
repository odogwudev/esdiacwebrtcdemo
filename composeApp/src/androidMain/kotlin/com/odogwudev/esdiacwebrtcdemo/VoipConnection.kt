package com.odogwudev.esdiacwebrtcdemo

import android.os.Build
import android.telecom.CallAudioState
import android.telecom.Connection
import android.telecom.DisconnectCause
import androidx.annotation.RequiresApi
import com.odogwudev.esdiacwebrtcdemo.ui.CallControlAction
import com.odogwudev.esdiacwebrtcdemo.ui.CallControlActionBus
import com.odogwudev.esdiacwebrtcdemo.ui.CallPhase

/**
 * A self-managed Telecom [Connection] that tells Android an active VoIP call
 * is in progress — the same mechanism WhatsApp, Telegram, etc. use.
 *
 * This gives the process the same protection as a cellular call:
 *   • OEM power managers (OPPO, Xiaomi, …) will never kill it.
 *   • No battery-optimization whitelist is needed.
 *   • Audio focus is managed by the system.
 */
@RequiresApi(Build.VERSION_CODES.O)
class VoipConnection : Connection() {

    init {
        connectionProperties = PROPERTY_SELF_MANAGED
        audioModeIsVoip = true
        connectionCapabilities = CAPABILITY_MUTE or
            CAPABILITY_SUPPORT_HOLD or
            CAPABILITY_HOLD
    }

    // ---------- system callbacks ----------

    override fun onShowIncomingCallUi() {
        // Self-managed: we show our own UI, nothing to do here.
    }

    override fun onCallAudioStateChanged(state: CallAudioState?) {
        // Audio routing is handled by our AudioRouteController; no bridging needed.
    }

    override fun onDisconnect() {
        // The user ended the call via a system surface (Android Auto, BT headset, etc.)
        CallControlActionBus.dispatch(CallControlAction.EndCall)
        markDisconnected(DisconnectCause.LOCAL)
    }

    override fun onAbort() {
        // The system is forcibly ending the call (e.g. incoming managed call takes priority).
        CallControlActionBus.dispatch(CallControlAction.EndCall)
        markDisconnected(DisconnectCause.CANCELED)
    }

    // ---------- state mirroring from CallSessionManager ----------

    fun updatePhase(phase: CallPhase) {
        when (phase) {
            CallPhase.Connecting,
            CallPhase.Calling -> setDialing()
            CallPhase.Ringing -> setDialing()      // outgoing: stays "dialing"
            CallPhase.Connected -> setActive()
            CallPhase.Ended -> markDisconnected(DisconnectCause.LOCAL)
            CallPhase.Error -> markDisconnected(DisconnectCause.ERROR)
            CallPhase.Idle -> Unit
        }
    }

    private fun markDisconnected(causeCode: Int) {
        setDisconnected(DisconnectCause(causeCode))
        destroy()
        TelecomBridge.onConnectionDestroyed()
    }
}
