package com.teatv.app.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/** Фоновой генератор Client-Integrity + GQL с ним (пара любая). */
class TwitchIntegrity(private val client: OkHttpClient = OkHttpClient()) {

    suspend fun raw(token: String?, clientId: String, deviceId: String): String =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("device_id", deviceId)
                put("platform", "android")
                put("workflow", "android_app")
            }.toString()
            val req = Request.Builder()
                .url("https://gql.twitch.tv/integrity")
                .post(body.toRequestBody("application/json".toMediaType()))
                .header("Client-ID", clientId)
                .apply { if (token != null) header("Authorization", "OAuth $token") }
                .header("X-Device-Id", deviceId)
                .header("User-Agent", TwitchAuth.ANDROID_USER_AGENT)
                .build()
            client.newCall(req).execute().use { it.body?.string().orEmpty() }
        }

    suspend fun gql(
        token: String,
        clientId: String,
        deviceId: String,
        integrity: String,
        jsonBody: String
    ): String =
        withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url("https://gql.twitch.tv/gql")
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .header("Client-ID", clientId)
                .header("Authorization", "OAuth $token")
                .header("X-Device-Id", deviceId)
                .header("Client-Integrity", integrity)
                .header("User-Agent", TwitchAuth.ANDROID_USER_AGENT)
                .build()
            client.newCall(req).execute().use { it.body?.string().orEmpty() }
        }
}