package com.teatv.app.chat.points

import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Живые сессии просмотра (foreground) + примитивы для догонки SleepFarm. */
object PointsFarmer {

    private const val TAG = "TeaTV-Farmer"
    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/108.0.0.0 Safari/537.36"
    private const val CLAIM_HASH =
        "46aaeebe02c99afdf4fc97c7c0cba964124bf6b0af229395f1f6d1feed05b3d0"
    private const val RAID_HASH =
        "c6a332a86d1087fbbb1a8623aa01bd1313d2386e7c63be60fdb2d1901f01a4ae"
    private const val MOMENT_HASH =
        "e2d67415aead910f7f9ceb45a77b750a1e1d9622c936d832328a0689e054db62"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun onMain(block: () -> Unit) {
        mainHandler.post(block)
    }
    private val jobs = mutableMapOf<String, Job>()

    private class Callbacks(
        val onClaimed: () -> Unit,
        val onBalance: (Int) -> Unit,
        val onRaided: () -> Unit,
        val onOffline: () -> Unit
    )

    private val callbacks = mutableMapOf<String, Callbacks>()
    private val joinedRaids = mutableMapOf<String, String>()
    private var spadeUrl: String? = null
    private var userIdCache: String? = null

    fun isRunning(channelId: String): Boolean = jobs[channelId]?.isActive == true

    fun runningIds(): List<String> = jobs.keys.filter { jobs[it]?.isActive == true }

    fun broadcastIdFor(channelId: String, token: String): String? =
        fetchWithRetry(attempts = 1) {
            fetchBroadcastId(PointsRepository.httpClient(), channelId, token)
        }

    fun startSession(
        channelId: String,
        channelLogin: String,
        token: String,
        onClaimed: () -> Unit,
        onBalance: (Int) -> Unit,
        onRaided: () -> Unit,
        onOffline: () -> Unit
    ) {
        Log.i(TAG, "start session: $channelLogin")
        callbacks[channelId] = Callbacks(onClaimed, onBalance, onRaided, onOffline)
        if (isRunning(channelId)) return
        jobs[channelId] = scope.launch {
            delay(2.seconds) // 2 секунды вместо ошибочных 2000 секунд
            val client = PointsRepository.httpClient()
            val spade = fetchWithRetry { spadeUrlFor(client, channelLogin) }
            if (spade == null) {
                Log.w(TAG, "no spade_url, session offline: $channelLogin")
                jobs.remove(channelId)
                callbacks.remove(channelId)?.onOffline?.invoke()
                return@launch
            }
            val userId = userIdCache
                ?: fetchWithRetry { fetchUserId(client, token) }
                    ?.also { userIdCache = it }
            val broadcastId = fetchWithRetry { fetchBroadcastId(client, channelId, token) }
            if (broadcastId == null || userId == null) {
                Log.w(TAG, "no broadcast/user id, session offline: $channelLogin")
                jobs.remove(channelId)
                callbacks.remove(channelId)?.onOffline?.invoke()
                return@launch
            }
            Log.i(TAG, "loop begin: $channelLogin broadcast=$broadcastId user=$userId")
            var nextTick = System.currentTimeMillis()
            var tick = 0
            while (isActive) {
                val cb = callbacks[channelId]
                sendMinuteWatched(client, spade, channelId, channelLogin, broadcastId, userId)
                if (cb != null) {
                    tryClaim(client, channelId, token, cb.onClaimed, cb.onBalance)
                    tryJoinRaid(client, channelId, token, cb.onRaided)
                }
                tick++
                if (tick % 5 == 0) {
                    val live = fetchBroadcastId(client, channelId, token)
                    if (live == null) {
                        Log.i(TAG, "stream offline, stop session: $channelLogin")
                        jobs.remove(channelId)
                        val cb = callbacks.remove(channelId)
                        cb?.onOffline?.invoke()
                        return@launch
                    }
                }
                nextTick += 60_000
                val now = System.currentTimeMillis()
                if (nextTick < now) {
                    Log.d(TAG, "skipped ticks: ${((now - nextTick) / 60_000) + 1}")
                    nextTick = now + 60_000
                }
                delay((nextTick - now).milliseconds)
            }
        }
    }

    fun stopSession(channelId: String) {
        jobs.remove(channelId)?.cancel()
        joinedRaids.remove(channelId)
        callbacks.remove(channelId)
    }

    fun stopAll() {
        jobs.keys.toList().forEach { stopSession(it) }
    }

    /** spade_url для догонки (кеш общий). */
    private fun spadeUrlFor(client: OkHttpClient, login: String): String? =
        spadeUrl ?: fetchSpadeUrl(client, login)?.also { spadeUrl = it }

    /** Собрать момент по id (приходит из PubSub). */
    fun claimMoment(momentId: String, token: String, onClaimed: () -> Unit) {
        scope.launch {
            try {
                val body = JSONObject().apply {
                    put("operationName", "CommunityMomentCallout_Claim")
                    put(
                        "variables",
                        JSONObject().put(
                            "input",
                            JSONObject().put("momentID", momentId)
                        )
                    )
                    put(
                        "extensions",
                        JSONObject().put(
                            "persistedQuery",
                            JSONObject()
                                .put("version", 1)
                                .put("sha256Hash", MOMENT_HASH)
                        )
                    )
                }.toString()
                val json = postRaw(PointsRepository.httpClient(), body, token)
                if (json != null && !json.has("errors")) {
                    Log.i(TAG, "moment claimed: $momentId")
                    onMain { onClaimed() }
                } else {
                    Log.w(TAG, "moment claim resp: $json")
                }
            } catch (e: Exception) {
                Log.w(TAG, "claimMoment failed", e)
            }
        }
    }

    private fun fetchWithRetry(attempts: Int = 3, block: () -> String?): String? {
        for (i in 0 until attempts) {
            val v = try {
                block()
            } catch (_: Exception) {
                null
            }
            if (!v.isNullOrBlank()) return v
            try {
                Thread.sleep(1500L * (i + 1))
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun sendMinuteWatched(
        client: OkHttpClient,
        spade: String,
        channelId: String,
        channelLogin: String,
        broadcastId: String,
        userId: String
    ) {
        try {
            val payload = JSONArray().put(
                JSONObject()
                    .put("event", "minute-watched")
                    .put(
                        "properties",
                        JSONObject().apply {
                            put("channel_id", channelId)
                            put("broadcast_id", broadcastId)
                            put("player", "site")
                            put("user_id", userId)
                            put("live", true)
                            put("channel", channelLogin)
                        }
                    )
            )
            val encoded = Base64.encodeToString(
                payload.toString().toByteArray(),
                Base64.NO_WRAP
            )
            val req = Request.Builder()
                .url(spade)
                .post(encoded.toRequestBody("text/plain".toMediaType()))
                .header("User-Agent", UA)
                .build()
            client.newCall(req).execute().use { resp ->
                Log.d(TAG, "minute-watched: ${resp.code} ($channelLogin)")
            }
        } catch (e: Exception) {
            Log.w(TAG, "minute-watched failed", e)
        }
    }

    private fun tryClaim(
        client: OkHttpClient,
        channelId: String,
        token: String,
        onClaimed: () -> Unit,
        onBalance: (Int) -> Unit
    ) {
        try {
            val query = "query TeaClaim { channel(id: \"" + channelId +
                    "\") { self { communityPoints { balance availableClaim { id } } } } }"
            val body = JSONObject().put("query", query).toString()
            val json = PointsRepository.postGqlPublic(body, token) ?: return
            val pointsNode = json.optJSONObject("data")?.optJSONObject("channel")
                ?.optJSONObject("self")?.optJSONObject("communityPoints") ?: return
            if (pointsNode.has("balance")) {
                onMain { onBalance(pointsNode.getInt("balance")) }
            }
            val claimId = pointsNode.optJSONObject("availableClaim")
                ?.optString("id")?.takeIf { it.isNotBlank() } ?: return
            if (claimChest(client, channelId, claimId, token)) onMain { onClaimed() }
        } catch (e: Exception) {
            Log.w(TAG, "tryClaim failed", e)
        }
    }

    private fun tryJoinRaid(
        client: OkHttpClient,
        channelId: String,
        token: String,
        onRaided: () -> Unit
    ) {
        try {
            val query = "query TeaRaid { user(id: \"" + channelId +
                    "\") { raid { id } } }"
            val body = JSONObject().put("query", query).toString()
            val json = PointsRepository.postGqlPublic(body, token) ?: return
            val raidId = json.optJSONObject("data")?.optJSONObject("user")
                ?.optJSONObject("raid")?.optString("id")
                ?.takeIf { it.isNotBlank() } ?: return
            if (raidId == joinedRaids[channelId]) return
            val persisted = JSONObject().apply {
                put("operationName", "JoinRaid")
                put(
                    "variables",
                    JSONObject().put(
                        "input",
                        JSONObject().put("raidID", raidId)
                    )
                )
                put(
                    "extensions",
                    JSONObject().put(
                        "persistedQuery",
                        JSONObject()
                            .put("version", 1)
                            .put("sha256Hash", RAID_HASH)
                    )
                )
            }.toString()
            var resp = postRaw(client, persisted, token)
            if (resp?.has("errors") == true &&
                resp.optJSONArray("errors")?.toString().orEmpty()
                    .contains("PersistedQueryNotFound")
            ) {
                val raw = "mutation TeaRaid { joinRaid(input: {raidID: \"" + raidId +
                        "\"}) { __typename }\n}"
                resp = postRaw(client, JSONObject().put("query", raw).toString(), token)
            }
            val ok = resp?.optJSONObject("data")?.optJSONObject("joinRaid") != null
            if (ok) {
                joinedRaids[channelId] = raidId
                Log.i(TAG, "raid joined: $raidId")
                onMain { onRaided() }
            }
        } catch (e: Exception) {
            Log.w(TAG, "tryJoinRaid failed", e)
        }
    }

    private fun claimChest(
        client: OkHttpClient,
        channelId: String,
        claimId: String,
        token: String
    ): Boolean {
        val persisted = JSONObject().apply {
            put("operationName", "ClaimCommunityPoints")
            put(
                "variables",
                JSONObject().put(
                    "input",
                    JSONObject()
                        .put("channelID", channelId)
                        .put("claimID", claimId)
                )
            )
            put(
                "extensions",
                JSONObject().put(
                    "persistedQuery",
                    JSONObject()
                        .put("version", 1)
                        .put("sha256Hash", CLAIM_HASH)
                )
            )
        }.toString()
        var json = postRaw(client, persisted, token)
        if (json?.has("errors") == true &&
            json.optJSONArray("errors")?.toString().orEmpty()
                .contains("PersistedQueryNotFound")
        ) {
            val raw = "mutation TeaClaim { claimCommunityPoints(input: {channelID: \"" +
                    channelId + "\", claimID: \"" + claimId + "\"}) { __typename }\n}"
            json = postRaw(client, JSONObject().put("query", raw).toString(), token)
        }
        return json?.optJSONObject("data")?.optJSONObject("claimCommunityPoints") != null
    }

    private fun postRaw(client: OkHttpClient, body: String, token: String): JSONObject? =
        try {
            val req = Request.Builder()
                .url("https://gql.twitch.tv/gql")
                .post(body.toRequestBody("application/json".toMediaType()))
                .header("Client-ID", PointsRepository.webClientId())
                .header("Authorization", "OAuth $token")
                .build()
            client.newCall(req).execute().use { resp ->
                JSONObject(resp.body?.string().orEmpty())
            }
        } catch (e: Exception) {
            Log.w(TAG, "gql post failed", e)
            null
        }

    private fun fetchSpadeUrl(client: OkHttpClient, channelLogin: String): String? =
        try {
            val page = Request.Builder()
                .url("https://www.twitch.tv/$channelLogin")
                .header("User-Agent", UA)
                .build()
            val settingsUrl = client.newCall(page).execute().use { resp ->
                Regex("(https://static\\.twitchcdn\\.net/config/settings.*?js|https://assets\\.twitch\\.tv/config/settings.*?.js)")
                    .find(resp.body?.string().orEmpty())?.groupValues?.get(1)
            } ?: return null
            val settings = Request.Builder()
                .url(settingsUrl)
                .header("User-Agent", UA)
                .build()
            client.newCall(settings).execute().use { resp ->
                Regex("\"spade_url\":\"(.*?)\"")
                    .find(resp.body?.string().orEmpty())?.groupValues?.get(1)
            }
        } catch (e: Exception) {
            Log.w(TAG, "spade fetch failed", e)
            null
        }

    private fun fetchBroadcastId(
        client: OkHttpClient,
        channelId: String,
        token: String
    ): String? {
        val q = "query TeaStream { user(id: \"" + channelId + "\") { stream { id } } }"
        val body = JSONObject().put("query", q).toString()
        val json = PointsRepository.postGqlPublic(body, token) ?: return null
        return json.optJSONObject("data")?.optJSONObject("user")
            ?.optJSONObject("stream")?.optString("id")
            ?.takeIf { it.isNotBlank() }
    }

    private fun fetchUserId(client: OkHttpClient, token: String): String? {
        val body = JSONObject()
            .put("query", "query TeaMe { currentUser { id } }")
            .toString()
        val json = PointsRepository.postGqlPublic(body, token) ?: return null
        return json.optJSONObject("data")?.optJSONObject("currentUser")
            ?.optString("id")
            ?.takeIf { it.isNotBlank() }
    }
}