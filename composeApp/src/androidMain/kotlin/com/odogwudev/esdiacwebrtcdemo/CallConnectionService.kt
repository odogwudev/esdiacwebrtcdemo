package com.odogwudev.esdiacwebrtcdemo

import android.os.Build
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.PhoneAccountHandle
import androidx.annotation.RequiresApi

/**
 * System-facing [ConnectionService] that the Telecom framework binds to
 * when we place a self-managed outgoing call via [TelecomBridge.placeCall].
 *
 * The system calls [onCreateOutgoingConnection] and we return a
 * [VoipConnection] that mirrors the call's lifecycle.
 */
@RequiresApi(Build.VERSION_CODES.O)
class CallConnectionService : ConnectionService() {

    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        val connection = VoipConnection()
        connection.setDialing()
        TelecomBridge.onConnectionCreated(connection)
        return connection
    }

    override fun onCreateOutgoingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ) {
        println("[CallConnectionService] System rejected outgoing call — falling back to foreground service")
        TelecomBridge.onConnectionFailed()
    }
}
