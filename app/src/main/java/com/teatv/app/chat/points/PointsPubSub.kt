package com.teatv.app.chat.points

import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import com.teatv.app.mod.PinnedMessage

/** Живые события баллов и моментов через PubSub (жив для эмуляции веб-клиента). */
object PointsPubSub {

    private const val TAG = "TeaTV-PubSub"
    private const val URL = "wss://pubsub-edge.twitch.tv/v1"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun onMain(block: () -> Unit) {
        mainHandler.post(block)
    }
    private var socket: WebSocket? = null
    private var pingJob: Job? = null
    private var manualStop = false
    private var params: Params? = null
    private val recentRedeemIds = java.util.Collections.newSetFromMap(
        java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    )

    private data class Params(
        val client: OkHttpClient,
        val token: String,
        val channelId: String,
        val onMoment: (String) -> Unit,
        val onDelta: (Int, String) -> Unit,
        val onRedeem: (String, String, String, String?, String?) -> Unit,
        val onPinned: ((PinnedMessage?) -> Unit)? = null
    )

    fun start(
        client: OkHttpClient,
        token: String,
        channelId: String,
        onMoment: (String) -> Unit,
        onDelta: (Int, String) -> Unit,
        onRedeem: (String, String, String, String?, String?) -> Unit,
        onPinned: ((PinnedMessage?) -> Unit)? = null
    ) {
        stop()
        manualStop = false
        val p = Params(client, token, channelId, onMoment, onDelta, onRedeem, onPinned)
        params = p
        connect(p)
    }

    fun stop() {
        manualStop = true
        params = null
        recentRedeemIds.clear()
        pingJob?.cancel()
        pingJob = null
        socket?.close(1000, "bye")
        socket = null
    }

    private fun connect(p: Params) {
        val req = Request.Builder().url(URL).build()
        socket = p.client.newWebSocket(req, object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
                scope.launch {
                    val uid = fetchUserId(p.client, p.token)
                    if (uid == null) {
                        webSocket.close(1000, "no user")
                        return@launch
                    }
                    val listen = JSONObject().apply {
                        put("type", "LISTEN")
                        put("nonce", UUID.randomUUID().toString().take(8))
                        put(
                            "data",
                            JSONObject().apply {
                                put(
                                    "topics",
                                    JSONArray().apply {
                                        put("community-moments-channel-v1." + p.channelId)
                                        put("community-points-user-v1." + uid)
                                        put("community-points-channel-v1." + p.channelId)
                                        put("pinned-chat-updates-v1." + p.channelId)
                                    }
                                )
                                put("auth_token", p.token)
                            }
                        )
                    }.toString()
                    webSocket.send(listen)
                    startPing(webSocket)
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val root = JSONObject(text)
                    when (root.optString("type")) {
                        "MESSAGE" -> {
                            val data = root.optJSONObject("data")
                            val rawMsg = data?.optString("message").orEmpty()
                            handle(p, rawMsg)
                        }
                        "PONG" -> { }
                        "RECONNECT" -> {
                            webSocket.close(1000, "reconnect requested")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "parse error: ${e.message}")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "failure: ${t.message}")
                scheduleReconnect(p)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                scheduleReconnect(p)
            }
        })
    }

    private fun scheduleReconnect(p: Params) {
        pingJob?.cancel()
        if (manualStop || params == null) return
        scope.launch {
            delay(10_000)
            if (!manualStop && params != null) connect(p)
        }
    }

    private fun handle(p: Params, raw: String) {
        if (raw.isBlank()) return
        val msg = JSONObject(raw)
        when (msg.optString("type")) {
            "moment-start" -> {
                val id = msg.optJSONObject("data")?.optString("moment_id")
                if (!id.isNullOrBlank()) {
                    Log.i(TAG, "moment-start: $id")
                    onMain { p.onMoment(id) }
                }
            }
            "points-earned" -> {
                val data = msg.optJSONObject("data")
                val earn = data?.optJSONObject("earn")
                val amount = earn?.optInt("amount") ?: 0
                val reason = earn?.optString("reason").orEmpty()
                Log.d(TAG, "points-earned: +$amount ($reason)")
                onMain { p.onDelta(amount, reason) }
            }
            "reward-redeemed" -> {
                val redemption = msg.optJSONObject("data")?.optJSONObject("redemption")
                val redemptionId = redemption?.optString("id").orEmpty()
                if (redemptionId.isNotBlank() && !recentRedeemIds.add(redemptionId)) {
                    // Исключаем дубликаты событий (одинаковое событие приходит в user и channel топики)
                    return
                }
                val sender = redemption?.optJSONObject("user")?.optString("display_name").orEmpty()
                val reward = redemption?.optJSONObject("reward")
                val title = reward?.optString("title").orEmpty()
                val icon = reward?.optJSONObject("image")?.optString("url_1x")
                    ?: reward?.optJSONObject("default_image")?.optString("url_1x")
                val userInput = redemption?.optString("user_input").orEmpty().takeIf { it.isNotBlank() }
                Log.i(TAG, "reward-redeemed: $sender -> $title (id=$redemptionId)")
                onMain { p.onRedeem(redemptionId, sender, title, icon, userInput) }
            }
            "pin-message" -> {
                val data = msg.optJSONObject("data")
                val pinnedId = data?.optString("id").orEmpty()
                val pinnedBy = data?.optJSONObject("pinned_by")?.optString("display_name").orEmpty().ifBlank {
                    data?.optJSONObject("pinned_by")?.optString("login").orEmpty()
                }
                val innerMsg = data?.optJSONObject("pinned_message") ?: data?.optJSONObject("message")
                val sender = innerMsg?.optJSONObject("sender")?.optString("display_name").orEmpty().ifBlank {
                    innerMsg?.optJSONObject("sender")?.optString("login").orEmpty()
                }
                val senderColor = innerMsg?.optJSONObject("sender")?.optString("chat_color").orEmpty()
                val text = innerMsg?.optJSONObject("content")?.optString("text").orEmpty()
                val msgId = innerMsg?.optString("id").orEmpty()
                if (text.isNotBlank()) {
                    val pm = PinnedMessage(
                        id = pinnedId.ifBlank { msgId },
                        messageId = msgId,
                        sender = sender,
                        senderColor = senderColor,
                        text = text,
                        pinnedBy = pinnedBy.ifBlank { "Модератор" }
                    )
                    onMain { p.onPinned?.invoke(pm) }
                }
            }
            "unpin-message" -> {
                onMain { p.onPinned?.invoke(null) }
            }
        }
    }

    private fun startPing(ws: WebSocket) {
        pingJob?.cancel()
        pingJob = scope.launch {
            while (isActive) {
                delay(4 * 60 * 1000)
                val ping = JSONObject().put("type", "PING").toString()
                ws.send(ping)
            }
        }
    }

    private fun fetchUserId(client: OkHttpClient, token: String): String? {
        val body = """{"query":"query { currentUser { id } }"}"""
        val json = PointsRepository.postGqlPublic(body, token) ?: return null
        return json.optJSONObject("data")?.optJSONObject("currentUser")?.optString("id")
    }
}
