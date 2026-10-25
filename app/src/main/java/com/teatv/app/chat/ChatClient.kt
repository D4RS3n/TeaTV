package com.teatv.app.chat

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Чат канала через EventSub WebSocket (только чтение). */
class ChatClient(
    private val token: String,
    private val clientId: String,
    private val userId: String,
    private val onMessage: (ChatMessage) -> Unit
) {

    private val http = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .build()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var ws: WebSocket? = null
    private var sessionId: String? = null
    private var broadcasterId: String? = null

    fun connect(broadcaster: String) {
        broadcasterId = broadcaster
        val req = Request.Builder()
            .url("wss://eventsub.wss.twitch.tv/ws")
            .build()
        ws = http.newWebSocket(req, listener)
    }

    fun disconnect() {
        runCatching { ws?.close(1000, "bye") }
        scope.cancel()
    }

    private val listener = object : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            scope.launch { handle(text) }
        }

    private fun handle(text: String) {
        val o = JSONObject(text)
        val type = o.getJSONObject("metadata").optString("message_type")
        when (type) {
            "session_welcome" -> {
                sessionId = o.getJSONObject("payload")
                    .getJSONObject("session").getString("id")
                subscribe()
            }
            "notification" -> {
                val sub = o.getJSONObject("payload").getJSONObject("subscription")
                if (sub.optString("type") == "channel.chat.message") {
                    val e = o.getJSONObject("payload").getJSONObject("event")
                    val reply = e.optJSONObject("reply")
                    val badgeArr = e.optJSONArray("badges")
                    val badgeKeys = mutableListOf<String>()
                    if (badgeArr != null) {
                        for (i in 0 until badgeArr.length()) {
                            val b = badgeArr.getJSONObject(i)
                            badgeKeys += "${b.optString("set_id")}/${b.optString("id")}"
                        }
                    }
                    val messageObj = e.optJSONObject("message")
                    val fragments = messageObj?.optJSONArray("fragments")
                    val msgEmotes = mutableMapOf<String, String>()
                    if (fragments != null) {
                        for (i in 0 until fragments.length()) {
                            val frag = fragments.optJSONObject(i) ?: continue
                            if (frag.optString("type") == "emote") {
                                val emoteCode = frag.optString("text")
                                val emoteObj = frag.optJSONObject("emote")
                                val emoteId = emoteObj?.optString("id")
                                if (!emoteCode.isNullOrBlank() && !emoteId.isNullOrBlank()) {
                                    val url = "https://static-cdn.jtvnw.net/emoticons/v2/$emoteId/default/dark/2.0"
                                    msgEmotes[emoteCode] = url
                                    com.teatv.app.chat.emotes.TwitchEmoteCache.put(emoteCode, url)
                                }
                            }
                        }
                    }
                    onMessage(
                        ChatMessage(
                            id = e.optString("message_id", ""),
                            sender = e.optString("chatter_user_name", ""),
                            color = e.optString("color", ""),
                            text = messageObj?.optString("text", "") ?: "",
                            badges = badgeKeys,
                            replyToId = reply?.optString("parent_message_id")
                                ?.takeIf { it.isNotBlank() },
                            replyToName = reply?.optString("parent_user_name")
                                ?.takeIf { it.isNotBlank() },
                            replyToBody = reply?.optString("parent_message_body")
                                ?.takeIf { it.isNotBlank() },
                            senderId = e.optString("chatter_user_id").takeIf { it.isNotBlank() },
                            senderLogin = e.optString("chatter_user_login").takeIf { it.isNotBlank() },
                            twitchEmotes = msgEmotes
                        )
                    )
                }
            }
        }
    }

    private fun subscribe() {
        val b = broadcasterId ?: return
        val s = sessionId ?: return
        val body = JSONObject().apply {
            put("type", "channel.chat.message")
            put("version", "1")
            put("condition", JSONObject().apply {
                put("broadcaster_user_id", b)
                put("user_id", userId)
            })
            put("transport", JSONObject().apply {
                put("method", "websocket")
                put("session_id", s)
            })
        }.toString()
        val req = Request.Builder()
            .url("https://api.twitch.tv/helix/eventsub/subscriptions")
            .post(body.toRequestBody("application/json".toMediaType()))
            .header("Client-ID", clientId)
            .header("Authorization", "Bearer $token")
            .build()
        try {
            http.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                Log.d("TeaTV-Chat", "eventsub subscribe: ${resp.code} $text")
            }
        } catch (e: Exception) {
            Log.e("TeaTV-Chat", "eventsub subscribe failed", e)
        }
        }
    }
}