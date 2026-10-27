package com.teatv.app.mod

import android.util.Log
import com.teatv.app.auth.TokenStore
import com.teatv.app.auth.TwitchApi
import com.teatv.app.auth.TwitchAuth
import com.teatv.app.auth.TwitchGql
import com.teatv.app.chat.points.PointsRepository
import com.teatv.app.screens.stream.mod.ModSliderAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap

class ModRepository(
    private val api: TwitchApi,
    private val gql: TwitchGql,
    private val tokenStore: TokenStore,
    private val client: OkHttpClient = PointsRepository.httpClient()
) {

    private val userIdCache = ConcurrentHashMap<String, String>()

    /** Выполнение HTTP запроса к Helix API с OWN_CLIENT_ID. */
    private suspend fun sendHelixRequest(
        url: String,
        method: String,
        jsonBody: String? = null
    ): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        val token = tokenStore.helixToken ?: tokenStore.accessToken ?: return@withContext false to null
        val cid = TwitchAuth.OWN_CLIENT_ID
        val reqBuilder = Request.Builder()
            .url(url)
            .header("Client-ID", cid)
            .header("Authorization", "Bearer $token")
        val req = when (method) {
            "GET" -> reqBuilder.get().build()
            "DELETE" -> reqBuilder.delete().build()
            "POST" -> {
                val body = (jsonBody ?: "{}").toRequestBody("application/json".toMediaType())
                reqBuilder.post(body).build()
            }
            "PATCH" -> {
                val body = (jsonBody ?: "{}").toRequestBody("application/json".toMediaType())
                reqBuilder.patch(body).build()
            }
            "PUT" -> {
                val body = (jsonBody ?: "{}").toRequestBody("application/json".toMediaType())
                reqBuilder.put(body).build()
            }
            else -> reqBuilder.build()
        }
        try {
            client.newCall(req).execute().use { resp ->
                val bodyStr = resp.body?.string().orEmpty()
                if (resp.isSuccessful) {
                    return@withContext true to bodyStr
                } else {
                    Log.d("TeaTV-ModRepo", "Helix $method $url: ${resp.code} $bodyStr")
                }
            }
        } catch (e: Exception) {
            Log.d("TeaTV-ModRepo", "Helix $method $url error: ${e.message}")
        }
        false to null
    }

    /** Проверка, является ли текущий пользователь модератором или владельцем канала. */
    suspend fun isModerator(channelId: String, channelLogin: String, myUserId: String?, myUserLogin: String?): Boolean = withContext(Dispatchers.IO) {
        if (!myUserId.isNullOrBlank() && (channelId == myUserId || channelLogin.equals(myUserLogin, ignoreCase = true))) {
            return@withContext true
        }

        // Проверка через GQL: user(login) { self { isModerator isEditor } }
        val gqlQuery = """
            query CheckModStatus(${"$"}login: String!) {
                user(login: ${"$"}login) {
                    id
                    self {
                        isModerator
                        isEditor
                    }
                }
            }
        """.trimIndent()

        try {
            val body = JSONObject().apply {
                put("query", gqlQuery)
                put("variables", JSONObject().apply { put("login", channelLogin) })
            }.toString()
            val raw = gql.queryWeb(body)
            val resp = JSONObject(raw)
            val isMod = resp.optJSONObject("data")
                ?.optJSONObject("user")
                ?.optJSONObject("self")
                ?.optBoolean("isModerator", false) ?: false

            val isEditor = resp.optJSONObject("data")
                ?.optJSONObject("user")
                ?.optJSONObject("self")
                ?.optBoolean("isEditor", false) ?: false

            if (isMod || isEditor) return@withContext true
        } catch (e: Exception) {
            Log.d("TeaTV-ModRepo", "GQL mod check: ${e.message}")
        }

        // Запасная проверка через Helix /helix/moderation/moderators
        if (!myUserId.isNullOrBlank()) {
            val url = "https://api.twitch.tv/helix/moderation/moderators?broadcaster_id=$channelId&user_id=$myUserId"
            val (ok, body) = sendHelixRequest(url, "GET")
            if (ok && !body.isNullOrBlank()) {
                val data = JSONObject(body).optJSONArray("data")
                if (data != null && data.length() > 0) return@withContext true
            }
        }
        false
    }

    /** Резолв логина пользователя в его численный ID с кэшированием. */
    suspend fun resolveUserId(userLogin: String): String? = withContext(Dispatchers.IO) {
        userIdCache[userLogin.lowercase()]?.let { return@withContext it }
        val url = "https://api.twitch.tv/helix/users?login=${userLogin.lowercase()}"
        val (ok, body) = sendHelixRequest(url, "GET")
        if (ok && !body.isNullOrBlank()) {
            val arr = JSONObject(body).optJSONArray("data")
            val id = arr?.optJSONObject(0)?.optString("id")
            if (!id.isNullOrBlank()) {
                userIdCache[userLogin.lowercase()] = id
                return@withContext id
            }
        }
        null
    }

    /** Выполнение модераторского действия (наказания) через Helix Moderation API. */
    suspend fun executeSliderAction(
        channelId: String,
        channelLogin: String,
        myUserId: String,
        targetUser: String,
        targetUserId: String?,
        messageId: String,
        action: ModSliderAction
    ): Boolean = withContext(Dispatchers.IO) {
        when (action) {
            ModSliderAction.NONE -> return@withContext false
            ModSliderAction.DELETE -> {
                val url = "https://api.twitch.tv/helix/moderation/chat?broadcaster_id=$channelId&moderator_id=$myUserId&message_id=$messageId"
                val (ok, _) = sendHelixRequest(url, "DELETE")
                if (ok) {
                    Log.i("TeaTV-ModRepo", "Message $messageId deleted on $channelLogin")
                }
                ok
            }
            else -> {
                val resolvedId = targetUserId?.takeIf { it.isNotBlank() } ?: resolveUserId(targetUser)
                if (resolvedId.isNullOrBlank()) {
                    Log.e("TeaTV-ModRepo", "Cannot resolve userId for $targetUser")
                    return@withContext false
                }
                val durationSec = action.durationSeconds
                val dataJson = JSONObject().apply {
                    put("user_id", resolvedId)
                    if (durationSec != null) put("duration", durationSec)
                    put("reason", "Moderation slider action")
                }
                val body = JSONObject().apply { put("data", dataJson) }.toString()
                val url = "https://api.twitch.tv/helix/moderation/bans?broadcaster_id=$channelId&moderator_id=$myUserId"
                val (ok, _) = sendHelixRequest(url, "POST", body)
                if (ok) {
                    Log.i("TeaTV-ModRepo", "Action $action applied to $targetUser ($resolvedId)")
                }
                ok
            }
        }
    }

    /** Разбан пользователя через Helix. */
    suspend fun unbanUser(
        channelId: String,
        myUserId: String,
        targetUser: String,
        targetUserId: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val resolvedId = targetUserId?.takeIf { it.isNotBlank() } ?: resolveUserId(targetUser)
        if (resolvedId.isNullOrBlank()) return@withContext false
        val url = "https://api.twitch.tv/helix/moderation/bans?broadcaster_id=$channelId&moderator_id=$myUserId&user_id=$resolvedId"
        val (ok, _) = sendHelixRequest(url, "DELETE")
        ok
    }

    /** Загрузка полной информации о чаттере (Twitch + 7TV). */
    suspend fun fetchChatterInfo(
        userId: String?,
        userLogin: String,
        channelId: String,
        channelLogin: String? = null,
        myUserId: String? = null,
        myUserLogin: String? = null,
        isChannelFollowed: Boolean? = null
    ): ChatterInfo = withContext(Dispatchers.IO) {
        var resolvedId = userId
        var avatarUrl: String? = null
        var createdAt: String? = null
        var followedAt: String? = null
        var isBanned = false
        var isTimedOut = false
        var actualLogin = userLogin
        var actualDisplayName = userLogin

        // 1. Профиль Twitch
        val uUrl = if (!resolvedId.isNullOrBlank()) {
            "https://api.twitch.tv/helix/users?id=$resolvedId"
        } else {
            "https://api.twitch.tv/helix/users?login=${userLogin.lowercase()}"
        }
        val (uOk, uBody) = sendHelixRequest(uUrl, "GET")
        if (uOk && !uBody.isNullOrBlank()) {
            val arr = JSONObject(uBody).optJSONArray("data")
            val obj = arr?.optJSONObject(0)
            if (obj != null) {
                resolvedId = obj.optString("id", resolvedId.orEmpty())
                actualLogin = obj.optString("login", actualLogin)
                actualDisplayName = obj.optString("display_name", actualDisplayName)
                avatarUrl = obj.optString("profile_image_url")
                createdAt = formatIsoDate(obj.optString("created_at"))
            }
        }

        // 2. Дата фоллоу на текущий канал стримера
        val isStreamer = (resolvedId != null && resolvedId == channelId) ||
                (channelLogin != null && actualLogin.equals(channelLogin, ignoreCase = true))
        val isSelf = (myUserId != null && resolvedId == myUserId) ||
                (myUserLogin != null && actualLogin.equals(myUserLogin, ignoreCase = true))

        if (isStreamer) {
            followedAt = "Автор канала"
        } else if (!resolvedId.isNullOrBlank()) {
            if (isSelf) {
                // Попытка 1: Helix channels/followed для себя
                val fUrl = "https://api.twitch.tv/helix/channels/followed?user_id=$resolvedId&broadcaster_id=$channelId"
                val (fOk, fBody) = sendHelixRequest(fUrl, "GET")
                if (fOk && !fBody.isNullOrBlank()) {
                    val arr = JSONObject(fBody).optJSONArray("data")
                    val obj = arr?.optJSONObject(0)
                    val rawDate = obj?.optString("followed_at")
                    if (!rawDate.isNullOrBlank()) {
                        followedAt = formatIsoDate(rawDate)
                    }
                }

                // Попытка 2: GQL relationship через Web API
                if (followedAt == null) {
                    try {
                        val q = """{"query":"query { user(id: \"$channelId\") { relationship { followedAt } } }"}"""
                        val resp = gql.queryWeb(q)
                        val rawDate = JSONObject(resp).optJSONObject("data")
                            ?.optJSONObject("user")
                            ?.optJSONObject("relationship")
                            ?.optString("followedAt")
                        if (!rawDate.isNullOrBlank()) {
                            followedAt = formatIsoDate(rawDate)
                        }
                    } catch (_: Exception) {}
                }

                // Попытка 3: если известно, что канал зафоловлен, но дата не получена
                if (followedAt == null) {
                    if (isChannelFollowed == true) {
                        followedAt = "Фолловит канал"
                    } else if (isChannelFollowed == false) {
                        followedAt = "Не фолловит"
                    }
                }
            } else {
                // Другой чаттер: проверка через Helix channels/followers (требует прав модератора)
                if (!myUserId.isNullOrBlank()) {
                    val fUrl = "https://api.twitch.tv/helix/channels/followers?broadcaster_id=$channelId&user_id=$resolvedId&moderator_id=$myUserId"
                    val (fOk, fBody) = sendHelixRequest(fUrl, "GET")
                    if (fOk && !fBody.isNullOrBlank()) {
                        val arr = JSONObject(fBody).optJSONArray("data")
                        val obj = arr?.optJSONObject(0)
                        val rawDate = obj?.optString("followed_at")
                        if (!rawDate.isNullOrBlank()) {
                            followedAt = formatIsoDate(rawDate)
                        }
                    }
                }
            }

            // 3. Проверка статуса блокировки
            val bUrl = if (!myUserId.isNullOrBlank()) {
                "https://api.twitch.tv/helix/moderation/banned?broadcaster_id=$channelId&user_id=$resolvedId"
            } else null
            if (bUrl != null) {
                val (bOk, bBody) = sendHelixRequest(bUrl, "GET")
                if (bOk && !bBody.isNullOrBlank()) {
                    val arr = JSONObject(bBody).optJSONArray("data")
                    val bItem = arr?.optJSONObject(0)
                    if (bItem != null) {
                        val exp = bItem.optString("expires_at")
                        if (exp.isNotBlank()) isTimedOut = true else isBanned = true
                    }
                }
            }
        }

        // 4. 7TV инфо: бейджи и краски
        val sevenBadges = mutableListOf<String>()
        var sevenPaint: String? = null
        if (!resolvedId.isNullOrBlank()) {
            try {
                val req = Request.Builder()
                    .url("https://7tv.io/v3/users/twitch/$resolvedId")
                    .build()
                client.newCall(req).execute().use { r ->
                    if (r.isSuccessful) {
                        val o = JSONObject(r.body?.string().orEmpty())
                        val badgeArr = o.optJSONArray("badges")
                        if (badgeArr != null) {
                            for (i in 0 until badgeArr.length()) {
                                val b = badgeArr.getJSONObject(i)
                                val bUrl = b.optJSONArray("urls")?.optJSONArray(0)?.optString(1)
                                if (!bUrl.isNullOrBlank()) sevenBadges += bUrl
                            }
                        }
                        sevenPaint = o.optJSONObject("style")?.optString("paint_id")
                    }
                }
            } catch (_: Exception) { }
        }

        ChatterInfo(
            userId = resolvedId ?: "",
            userLogin = userLogin,
            displayName = userLogin,
            avatarUrl = avatarUrl,
            createdAt = createdAt ?: "—",
            followedAt = followedAt ?: if (isStreamer) "Автор канала" else if (isSelf) "Не фолловит" else "— (приватно)",
            isSubscribed = false,
            subTier = null,
            isBanned = isBanned,
            isTimedOut = isTimedOut,
            sevenTvBadges = sevenBadges,
            sevenTvPaint = sevenPaint
        )
    }

    /** Загрузка списка наград и очереди запросов (Helix для стримера / GQL для модераторов). */
    suspend fun fetchRewardRequests(channelId: String, myUserId: String? = null): List<RewardRequestItem> = withContext(Dispatchers.IO) {
        val out = mutableListOf<RewardRequestItem>()

        // 1. Если текущий пользователь — владелец канала, опрашиваем Helix очередь
        if (!myUserId.isNullOrBlank() && channelId == myUserId) {
            val rewardsUrl = "https://api.twitch.tv/helix/channel_points/custom_rewards?broadcaster_id=$channelId&only_manageable_rewards=false"
            val (ok, body) = sendHelixRequest(rewardsUrl, "GET")
            if (ok && !body.isNullOrBlank()) {
                val dataArr = JSONObject(body).optJSONArray("data")
                if (dataArr != null) {
                    for (i in 0 until dataArr.length()) {
                        val rObj = dataArr.optJSONObject(i) ?: continue
                        val rewardId = rObj.optString("id")
                        val rewardTitle = rObj.optString("title")
                        val rewardCost = rObj.optInt("cost", 0)

                        val redemptionsUrl = "https://api.twitch.tv/helix/channel_points/custom_rewards/redemptions?broadcaster_id=$channelId&reward_id=$rewardId&status=UNFULFILLED&first=20"
                        val (redOk, redBody) = sendHelixRequest(redemptionsUrl, "GET")
                        if (redOk && !redBody.isNullOrBlank()) {
                            val redArr = JSONObject(redBody).optJSONArray("data")
                            if (redArr != null) {
                                for (j in 0 until redArr.length()) {
                                    val redemption = redArr.optJSONObject(j) ?: continue
                                    out += RewardRequestItem(
                                        id = redemption.optString("id"),
                                        rewardId = rewardId,
                                        rewardTitle = rewardTitle,
                                        rewardCost = rewardCost,
                                        userName = redemption.optString("user_name").ifBlank { redemption.optString("user_login") },
                                        userInput = redemption.optString("user_input").takeIf { it.isNotBlank() },
                                        timestamp = parseIsoToMillis(redemption.optString("redeemed_at"))
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Для модераторов загружаем кастомные награды канала через GQL (работает для любых каналов без 401)
        if (out.isEmpty()) {
            val gqlQuery = """
                query GetChannelRewards(${"$"}channelID: ID!) {
                    user(id: ${"$"}channelID) {
                        channel {
                            communityPointsSettings {
                                customRewards {
                                    id
                                    title
                                    cost
                                    prompt
                                    isUserInputRequired
                                }
                            }
                        }
                    }
                }
            """.trimIndent()
            try {
                val body = JSONObject().apply {
                    put("query", gqlQuery)
                    put("variables", JSONObject().apply { put("channelID", channelId) })
                }.toString()
                val raw = gql.queryWeb(body)
                val resp = JSONObject(raw)
                val rewards = resp.optJSONObject("data")
                    ?.optJSONObject("user")
                    ?.optJSONObject("channel")
                    ?.optJSONObject("communityPointsSettings")
                    ?.optJSONArray("customRewards")
                if (rewards != null) {
                    for (i in 0 until rewards.length()) {
                        val r = rewards.optJSONObject(i) ?: continue
                        if (r.optBoolean("isUserInputRequired", false)) {
                            out += RewardRequestItem(
                                id = r.optString("id"),
                                rewardId = r.optString("id"),
                                rewardTitle = r.optString("title"),
                                rewardCost = r.optInt("cost", 0),
                                userName = "Чаттер",
                                userInput = r.optString("prompt").takeIf { it.isNotBlank() },
                                timestamp = System.currentTimeMillis()
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d("TeaTV-ModRepo", "GQL reward fetch: ${e.message}")
            }
        }
        out
    }

    /** Выполнение запроса на награду. */
    suspend fun fulfillReward(channelId: String, rewardId: String, redemptionId: String): Boolean = withContext(Dispatchers.IO) {
        val url = "https://api.twitch.tv/helix/channel_points/custom_rewards/redemptions?broadcaster_id=$channelId&reward_id=$rewardId&id=$redemptionId"
        val body = JSONObject().apply { put("status", "FULFILLED") }.toString()
        val (ok, _) = sendHelixRequest(url, "PATCH", body)
        ok
    }

    /** Отклонение запроса на награду (возврат баллов). */
    suspend fun rejectReward(channelId: String, rewardId: String, redemptionId: String): Boolean = withContext(Dispatchers.IO) {
        val url = "https://api.twitch.tv/helix/channel_points/custom_rewards/redemptions?broadcaster_id=$channelId&reward_id=$rewardId&id=$redemptionId"
        val body = JSONObject().apply { put("status", "CANCELED") }.toString()
        val (ok, _) = sendHelixRequest(url, "PATCH", body)
        ok
    }

    /** Загрузка последних действий модерации (журнал действий через /helix/moderation/banned). */
    suspend fun fetchModLogs(channelId: String, myUserId: String? = null): List<ModLogItem> = withContext(Dispatchers.IO) {
        val modParam = if (!myUserId.isNullOrBlank()) "&moderator_id=$myUserId" else ""
        val url = "https://api.twitch.tv/helix/moderation/banned?broadcaster_id=$channelId$modParam&first=30"
        val (ok, body) = sendHelixRequest(url, "GET")
        if (!ok || body.isNullOrBlank()) return@withContext emptyList()

        val out = mutableListOf<ModLogItem>()
        val arr = JSONObject(body).optJSONArray("data") ?: return@withContext emptyList()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val expiresAt = o.optString("expires_at")
            val isTimeout = expiresAt.isNotBlank()
            val actionName = if (isTimeout) "Таймаут" else "Бан"
            val target = o.optString("user_name").ifBlank { o.optString("user_login") }
            val mod = o.optString("moderator_name").ifBlank { o.optString("moderator_login") }.ifBlank { "Модератор" }
            val reason = o.optString("reason").takeIf { it.isNotBlank() }

            out += ModLogItem(
                id = o.optString("user_id") + "-${o.optString("created_at")}",
                timestamp = parseIsoToMillis(o.optString("created_at")),
                modName = mod,
                targetName = target,
                action = actionName,
                details = reason
            )
        }
        out
    }

    /** Загрузка апелляций на разбан (запросов на снятие блокировок). */
    suspend fun fetchUnbanRequests(channelId: String, myUserId: String): List<UnbanRequestItem> = withContext(Dispatchers.IO) {
        val url = "https://api.twitch.tv/helix/moderation/unban_requests?broadcaster_id=$channelId&moderator_id=$myUserId&status=pending"
        val (ok, body) = sendHelixRequest(url, "GET")
        if (!ok || body.isNullOrBlank()) return@withContext emptyList()

        val out = mutableListOf<UnbanRequestItem>()
        val arr = JSONObject(body).optJSONArray("data") ?: return@withContext emptyList()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out += UnbanRequestItem(
                id = o.optString("id"),
                userId = o.optString("user_id"),
                userLogin = o.optString("user_login"),
                userName = o.optString("user_name").ifBlank { o.optString("user_login") },
                appealMessage = o.optString("text"),
                createdAt = formatIsoDate(o.optString("created_at"))
            )
        }
        out
    }

    /** Решение по апелляции на разбан. */
    suspend fun resolveUnbanRequest(
        channelId: String,
        myUserId: String,
        requestId: String,
        approved: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        val status = if (approved) "approved" else "denied"
        val url = "https://api.twitch.tv/helix/moderation/unban_requests?broadcaster_id=$channelId&moderator_id=$myUserId&unban_request_id=$requestId&status=$status"
        val (ok, _) = sendHelixRequest(url, "PATCH")
        ok
    }

    /** Загрузка активного прогноза на канале. */
    suspend fun fetchActivePrediction(channelId: String, myUserId: String? = null): PredictionInfo? = withContext(Dispatchers.IO) {
        // Helix predictions в Twitch API разрешён только владельцу канала
        if (!myUserId.isNullOrBlank() && channelId != myUserId) {
            return@withContext null
        }
        val url = "https://api.twitch.tv/helix/predictions?broadcaster_id=$channelId&first=1"
        val (ok, body) = sendHelixRequest(url, "GET")
        if (!ok || body.isNullOrBlank()) return@withContext null

        val arr = JSONObject(body).optJSONArray("data")
        val p = arr?.optJSONObject(0) ?: return@withContext null
        val outcomes = p.optJSONArray("outcomes")
        val opts = mutableListOf<PredictionOption>()
        if (outcomes != null) {
            for (i in 0 until outcomes.length()) {
                val o = outcomes.optJSONObject(i) ?: continue
                opts += PredictionOption(
                    id = o.optString("id"),
                    title = o.optString("title"),
                    points = o.optInt("channel_points", 0),
                    userCount = o.optInt("users", 0)
                )
            }
        }
        PredictionInfo(
            id = p.optString("id"),
            title = p.optString("title"),
            status = p.optString("status", "ACTIVE"),
            options = opts,
            winningOptionId = p.optString("winning_outcome_id").takeIf { it.isNotBlank() },
            durationSeconds = p.optInt("prediction_window", 120),
            createdAt = parseIsoToMillis(p.optString("created_at"))
        )
    }

    /** Загрузка текущих режимов канала (Helix Chat Settings + Shield Mode). */
    suspend fun fetchChannelModes(channelId: String, myUserId: String): ChannelModesState = withContext(Dispatchers.IO) {
        var state = ChannelModesState()
        val url = "https://api.twitch.tv/helix/chat/settings?broadcaster_id=$channelId&moderator_id=$myUserId"
        val (ok, body) = sendHelixRequest(url, "GET")
        if (ok && !body.isNullOrBlank()) {
            val item = JSONObject(body).optJSONArray("data")?.optJSONObject(0)
            if (item != null) {
                val followerMode = item.optBoolean("follower_mode", false)
                val followerDur = if (followerMode) item.optInt("follower_mode_duration", 0) else -1
                val slowMode = item.optBoolean("slow_mode", false)
                val slowWait = if (slowMode) item.optInt("slow_mode_wait_time", 3) else 0
                state = state.copy(
                    subOnly = item.optBoolean("subscriber_mode", false),
                    emoteOnly = item.optBoolean("emote_mode", false),
                    followersDurationMinutes = followerDur,
                    slowModeSeconds = slowWait
                )
            }
        }
        val shieldUrl = "https://api.twitch.tv/helix/moderation/shield_mode?broadcaster_id=$channelId&moderator_id=$myUserId"
        val (shOk, shBody) = sendHelixRequest(shieldUrl, "GET")
        if (shOk && !shBody.isNullOrBlank()) {
            val shItem = JSONObject(shBody).optJSONArray("data")?.optJSONObject(0)
            if (shItem != null) {
                state = state.copy(shieldMode = shItem.optBoolean("is_active", false))
            }
        }
        state
    }

    /** Применение настроек чата через PATCH /helix/chat/settings. */
    suspend fun updateChatSettings(
        channelId: String,
        myUserId: String,
        jsonPatch: JSONObject
    ): Boolean = withContext(Dispatchers.IO) {
        val url = "https://api.twitch.tv/helix/chat/settings?broadcaster_id=$channelId&moderator_id=$myUserId"
        val (ok, _) = sendHelixRequest(url, "PATCH", jsonPatch.toString())
        ok
    }

    /** Переключение режима щита (Shield Mode). */
    suspend fun setShieldMode(channelId: String, myUserId: String, enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        val url = "https://api.twitch.tv/helix/moderation/shield_mode?broadcaster_id=$channelId&moderator_id=$myUserId"
        val body = JSONObject().apply { put("is_active", enabled) }.toString()
        val (ok, _) = sendHelixRequest(url, "PUT", body)
        ok
    }

    /** Отправка цветного оповещения /announce через Helix. */
    suspend fun sendAnnouncement(
        channelId: String,
        myUserId: String,
        text: String,
        color: String = "primary"
    ): Boolean = withContext(Dispatchers.IO) {
        val url = "https://api.twitch.tv/helix/chat/announcements?broadcaster_id=$channelId&moderator_id=$myUserId"
        val body = JSONObject().apply {
            put("message", text)
            put("color", color)
        }.toString()
        val (ok, _) = sendHelixRequest(url, "POST", body)
        ok
    }

    private fun formatIsoDate(iso: String?): String {
        if (iso.isNullOrBlank()) return "—"
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val date = parser.parse(iso.take(19)) ?: return iso
            val formatter = SimpleDateFormat("d MMM yyyy", Locale.forLanguageTag("ru"))
            formatter.format(date)
        } catch (_: Exception) {
            iso.take(10)
        }
    }

    private fun parseIsoToMillis(iso: String?): Long {
        if (iso.isNullOrBlank()) return System.currentTimeMillis()
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            parser.parse(iso.take(19))?.time ?: System.currentTimeMillis()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }

    /** Загрузка текущего закреплённого сообщения на канале через Twitch GQL. */
    suspend fun fetchPinnedChatMessage(channelLogin: String): PinnedMessage? = withContext(Dispatchers.IO) {
        val query = """
            query GetChannelPinnedChatMessages(${'$'}channelLogin: String!) {
              user(login: ${'$'}channelLogin) {
                channel {
                  pinnedChatMessages {
                    edges {
                      node {
                        id
                        type
                        endsAt
                        updatedAt
                        pinnedBy {
                          id
                          login
                          displayName
                        }
                        pinnedMessage {
                          id
                          sentAt
                          sender {
                            id
                            login
                            displayName
                            chatColor
                          }
                          content {
                            text
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
        """.trimIndent()
        try {
            val body = JSONObject().apply {
                put("query", query)
                put("variables", JSONObject().put("channelLogin", channelLogin))
            }.toString()
            val raw = gql.queryWeb(body)
            val json = JSONObject(raw)
            val edges = json.optJSONObject("data")
                ?.optJSONObject("user")
                ?.optJSONObject("channel")
                ?.optJSONObject("pinnedChatMessages")
                ?.optJSONArray("edges")
            if (edges != null && edges.length() > 0) {
                val node = edges.getJSONObject(0).optJSONObject("node") ?: return@withContext null
                val pinnedId = node.optString("id")
                val pinnedBy = node.optJSONObject("pinnedBy")?.optString("displayName").orEmpty().ifBlank {
                    node.optJSONObject("pinnedBy")?.optString("login").orEmpty()
                }
                val msgObj = node.optJSONObject("pinnedMessage") ?: return@withContext null
                val msgId = msgObj.optString("id")
                val senderObj = msgObj.optJSONObject("sender")
                val senderName = senderObj?.optString("displayName").orEmpty().ifBlank {
                    senderObj?.optString("login").orEmpty()
                }
                val senderColor = senderObj?.optString("chatColor").orEmpty()
                val text = msgObj.optJSONObject("content")?.optString("text").orEmpty()
                if (text.isNotBlank()) {
                    return@withContext PinnedMessage(
                        id = pinnedId,
                        messageId = msgId,
                        sender = senderName,
                        senderColor = senderColor,
                        text = text,
                        pinnedBy = pinnedBy.ifBlank { "Модератор" }
                    )
                }
            }
        } catch (e: Exception) {
            Log.d("TeaTV-ModRepo", "fetchPinnedChatMessage error: ${e.message}")
        }
        null
    }

    /** Закрепление сообщения на канале через Twitch GQL (мутация pinChatMessage). */
    suspend fun pinChatMessage(channelId: String, messageId: String): PinnedMessage? = withContext(Dispatchers.IO) {
        val query = """
            mutation PinChatMessage(${'$'}channelID: ID!, ${'$'}messageID: ID!) {
              pinChatMessage(input: {
                channelID: ${'$'}channelID,
                messageID: ${'$'}messageID,
                type: MOD
              }) {
                pinnedChatMessage {
                  id
                  pinnedBy {
                    displayName
                    login
                  }
                  pinnedMessage {
                    id
                    sender {
                      displayName
                      login
                      chatColor
                    }
                    content {
                      text
                    }
                  }
                }
                error {
                  code
                }
              }
            }
        """.trimIndent()
        try {
            val body = JSONObject().apply {
                put("query", query)
                put("variables", JSONObject().apply {
                    put("channelID", channelId)
                    put("messageID", messageId)
                })
            }.toString()
            val raw = gql.queryWeb(body)
            val json = JSONObject(raw)
            val payload = json.optJSONObject("data")?.optJSONObject("pinChatMessage")
            val pcm = payload?.optJSONObject("pinnedChatMessage")
            if (pcm != null) {
                val pinnedId = pcm.optString("id")
                val pinnedBy = pcm.optJSONObject("pinnedBy")?.optString("displayName").orEmpty().ifBlank {
                    pcm.optJSONObject("pinnedBy")?.optString("login").orEmpty()
                }
                val msg = pcm.optJSONObject("pinnedMessage")
                val senderObj = msg?.optJSONObject("sender")
                val senderName = senderObj?.optString("displayName").orEmpty().ifBlank {
                    senderObj?.optString("login").orEmpty()
                }
                val senderColor = senderObj?.optString("chatColor").orEmpty()
                val text = msg?.optJSONObject("content")?.optString("text").orEmpty()
                return@withContext PinnedMessage(
                    id = pinnedId,
                    messageId = messageId,
                    sender = senderName,
                    senderColor = senderColor,
                    text = text,
                    pinnedBy = pinnedBy.ifBlank { "Модератор" }
                )
            }
            val errCode = payload?.optJSONObject("error")?.optString("code")
            Log.d("TeaTV-ModRepo", "pinChatMessage err: $errCode, raw: $raw")
        } catch (e: Exception) {
            Log.d("TeaTV-ModRepo", "pinChatMessage error: ${e.message}")
        }
        null
    }

    /** Открепление сообщения на канале через Twitch GQL (мутация unpinChatMessage). */
    suspend fun unpinChatMessage(pinnedId: String): Boolean = withContext(Dispatchers.IO) {
        val query = """
            mutation UnpinChatMessage(${'$'}id: ID!) {
              unpinChatMessage(input: {
                id: ${'$'}id,
                reason: UNPIN
              }) {
                unpinnedChatMessage {
                  id
                }
                error {
                  code
                }
              }
            }
        """.trimIndent()
        try {
            val body = JSONObject().apply {
                put("query", query)
                put("variables", JSONObject().apply {
                    put("id", pinnedId)
                })
            }.toString()
            val raw = gql.queryWeb(body)
            val json = JSONObject(raw)
            val unpinned = json.optJSONObject("data")
                ?.optJSONObject("unpinChatMessage")
                ?.optJSONObject("unpinnedChatMessage")
            if (unpinned != null) {
                return@withContext true
            }
            val err = json.optJSONObject("data")
                ?.optJSONObject("unpinChatMessage")
                ?.optJSONObject("error")
                ?.optString("code")
            Log.d("TeaTV-ModRepo", "unpinChatMessage result error: $err, raw: $raw")
        } catch (e: Exception) {
            Log.d("TeaTV-ModRepo", "unpinChatMessage error: ${e.message}")
        }
        false
    }
}
