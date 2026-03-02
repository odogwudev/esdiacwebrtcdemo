package com.odogwudev.esdiacwebrtcdemo.verto

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

object VertoMessages {
    private var requestId = 0

    private fun nextId(): Int = ++requestId

    fun login(login: String, password: String): String {
        val msg = buildJsonObject {
            put("jsonrpc", "2.0")
            put("method", "login")
            put("id", nextId())
            putJsonObject("params") {
                put("login", login)
                put("passwd", password)
            }
        }
        return msg.toString()
    }

    fun invite(
        callId: String,
        destinationNumber: String,
        callerIdName: String,
        callerIdNumber: String,
        sdp: String
    ): String {
        val msg = buildJsonObject {
            put("jsonrpc", "2.0")
            put("method", "verto.invite")
            put("id", nextId())
            putJsonObject("params") {
                putJsonObject("dialogParams") {
                    put("callID", callId)
                    put("destination_number", destinationNumber)
                    put("caller_id_name", callerIdName)
                    put("caller_id_number", callerIdNumber)
                    put("useVideo", false)
                    put("useStereo", false)
                    put("screenShare", false)
                }
                put("sdp", sdp)
            }
        }
        return msg.toString()
    }

    fun info(callId: String, dtmf: String): String {
        val msg = buildJsonObject {
            put("jsonrpc", "2.0")
            put("method", "verto.info")
            put("id", nextId())
            putJsonObject("params") {
                putJsonObject("dialogParams") {
                    put("callID", callId)
                }
                put("dtmf", dtmf)
            }
        }
        return msg.toString()
    }

    fun modify(callId: String, action: String, sdp: String): String {
        val msg = buildJsonObject {
            put("jsonrpc", "2.0")
            put("method", "verto.modify")
            put("id", nextId())
            putJsonObject("params") {
                putJsonObject("dialogParams") {
                    put("callID", callId)
                }
                put("action", action)
                put("sdp", sdp)
            }
        }
        return msg.toString()
    }

    fun bye(callId: String, cause: String = "NORMAL_CLEARING"): String {
        val msg = buildJsonObject {
            put("jsonrpc", "2.0")
            put("method", "verto.bye")
            put("id", nextId())
            putJsonObject("params") {
                putJsonObject("dialogParams") {
                    put("callID", callId)
                }
                put("cause", cause)
            }
        }
        return msg.toString()
    }
}

sealed class VertoEvent {
    data class LoginResult(val sessId: String) : VertoEvent()
    data class Media(val sdp: String) : VertoEvent()
    data class Answer(val sdp: String?) : VertoEvent()
    data class Bye(val cause: String?) : VertoEvent()
    data class Error(val code: Int?, val message: String) : VertoEvent()
    data class Unknown(val raw: String) : VertoEvent()
}

object VertoParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): VertoEvent {
        return try {
            val obj = json.parseToJsonElement(text).jsonObject

            when {
                obj.containsKey("result") -> parseResult(obj)
                obj.containsKey("error") -> parseError(obj)
                obj.containsKey("method") -> parseMethod(obj)
                else -> VertoEvent.Unknown(text)
            }
        } catch (e: Exception) {
            VertoEvent.Unknown(text)
        }
    }

    private fun parseResult(obj: JsonObject): VertoEvent {
        val result = obj["result"]?.jsonObject ?: return VertoEvent.Unknown(obj.toString())
        val message = result["message"]?.jsonPrimitive?.contentOrNull
        if (message == "logged in") {
            val sessId = result["sessid"]?.jsonPrimitive?.contentOrNull ?: ""
            return VertoEvent.LoginResult(sessId)
        }
        return VertoEvent.Unknown(obj.toString())
    }

    private fun parseError(obj: JsonObject): VertoEvent {
        val error = obj["error"]?.jsonObject
        return VertoEvent.Error(
            code = error?.get("code")?.jsonPrimitive?.intOrNull,
            message = error?.get("message")?.jsonPrimitive?.contentOrNull ?: "Unknown error"
        )
    }

    private fun parseMethod(obj: JsonObject): VertoEvent {
        val method = obj["method"]?.jsonPrimitive?.contentOrNull
        val params = obj["params"]?.jsonObject

        return when (method) {
            "verto.media" -> {
                val sdp = params?.get("sdp")?.jsonPrimitive?.contentOrNull
                    ?: return VertoEvent.Unknown(obj.toString())
                VertoEvent.Media(sdp)
            }
            "verto.answer" -> {
                VertoEvent.Answer(
                    params?.get("sdp")?.jsonPrimitive?.contentOrNull
                )
            }
            "verto.bye" -> {
                val cause = params?.get("cause")?.jsonPrimitive?.contentOrNull
                VertoEvent.Bye(cause)
            }
            else -> VertoEvent.Unknown(obj.toString())
        }
    }
}
