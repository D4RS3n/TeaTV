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

    /** Ручное сохранение ID пользователя в кэш. */
    fun cacheUserId(userLoginOrName: String, userId: String) {
        if (userLoginOrName.isNotBlank() && userId.isNotBlank()) {
            userIdCache[userLoginOrName.trim().removePrefix("@").lowercase()] = userId
        }
    }

    /** Выполнение сырого HTTP запроса с заданными заголовками авторизации. */
    private suspend fun executeRawHttp(
        url: String,
        method: String,
        jsonBody: String?,
        clientId: String,
        authHeader: String
    ): Pair<Int, String?> {
        val reqBuilder = Request.Builder()
            .url(url)
            .header("Client-Id", clientId)
            .header("Authorization", authHeader)

        val req = when (method) {
            "GET" -> reqBuilder.get().build()
            "DELETE" -> reqBuilder.delete().build()
            "POST" -> {
                val body = if (jsonBody != null) jsonBody.toRequestBody("application/json".toMediaType())
                else ByteArray(0).toRequestBody(null)
                reqBuilder.post(body).build()
            }
            "PATCH" -> {
                val body = (jsonBody ?: "{}").toRequestBody("application/json".toMediaType())
                reqBuilder.patch(body).build()
            }
            "PUT" -> {
                val body = if (jsonBody != null) jsonBody.toRequestBody("application/json".toMediaType())
                else ByteArray(0).toRequestBody(null)
                reqBuilder.put(body).build()
            }
            else -> reqBuilder.build()
        }
        return try {
            client.newCall(req).execute().use { resp ->
                resp.code to resp.body?.string().orEmpty()
            }
        } catch (e: Exception) {
            Log.d("TeaTV-ModRepo", "HTTP $method $url error: ${e.message}")
            -1 to null
        }
    }

    /**
     * Выполнение HTTP запроса к Helix API с интеллектуальным двухфазным токеном:
     * Фаза 1: Официальный OAuth токен заявки с OWN_CLIENT_ID (Bearer $helixToken).
     * Фаза 2: Фоллбэк на сессионный веб-токен (auth-token) с WEB_CLIENT_ID (OAuth $accessToken),
     *         если helixToken отсутствует, просрочен (401) или не имеет необходимых прав (403).
     */
    private suspend fun sendHelixRequest(
        url: String,
        method: String,
        jsonBody: String? = null
    ): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        val helixToken = tokenStore.helixToken
        val accessToken = tokenStore.accessToken

        var lastCode = -1
        var lastBody: String? = null

        // Фаза 1: Пробуем официальный Helix OAuth токен своей заявки
        if (!helixToken.isNullOrBlank()) {
            val (code, body) = executeRawHttp(
                url = url,
                method = method,
                jsonBody = jsonBody,
                clientId = TwitchAuth.OWN_CLIENT_ID,
                authHeader = "Bearer $helixToken"
            )
            if (code in 200..299) {
                return@withContext true to body
            }
            lastCode = code
            lastBody = body
            Log.d("TeaTV-ModRepo", "Helix Phase 1 ($method $url) code $code: $body")
        }

        // Фаза 2: Фоллбэк на сессионный веб-токен с WEB_CLIENT_ID
        if (!accessToken.isNullOrBlank() && (lastCode == 401 || lastCode == 403 || lastCode == -1 || helixToken.isNullOrBlank())) {
            // Сначала пробуем стандартный для Helix заголовок Bearer
            val (webCode, webBody) = executeRawHttp(
                url = url,
                method = method,
                jsonBody = jsonBody,
                clientId = TwitchAuth.WEB_CLIENT_ID,
                authHeader = "Bearer $accessToken"
            )
            if (webCode in 200..299) {
                Log.i("TeaTV-ModRepo", "Helix Phase 2 (Bearer Web token) succeeded for $method $url")
                return@withContext true to webBody
            }
            lastCode = webCode
            lastBody = webBody

            // Если Bearer не принят (401), пробуем префикс OAuth
            if (webCode == 401) {
                val (oauthCode, oauthBody) = executeRawHttp(
                    url = url,
                    method = method,
                    jsonBody = jsonBody,
                    clientId = TwitchAuth.WEB_CLIENT_ID,
                    authHeader = "OAuth $accessToken"
                )
                if (oauthCode in 200..299) {
                    Log.i("TeaTV-ModRepo", "Helix Phase 2 (OAuth Web token) succeeded for $method $url")
                    return@withContext true to oauthBody
                }
                lastCode = oauthCode
                lastBody = oauthBody
            }
            Log.d("TeaTV-ModRepo", "Helix Phase 2 ($method $url) code $lastCode: $lastBody")
        }

        false to lastBody
    }

    /** Проверка, является ли текущий пользователь модератором или владельцем канала. */
    suspend fun isModerator(channelId: String, channelLogin: String, myUserId: String?, myUserLogin: String?): Boolean = withContext(Dispatchers.IO) {
        val uid = myUserId ?: tokenStore.userId
        val ulogin = myUserLogin ?: tokenStore.userLogin
        if ((!uid.isNullOrBlank() && channelId == uid) ||
            (!ulogin.isNullOrBlank() && channelLogin.equals(ulogin, ignoreCase = true))) {
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

    /** Резолв логина или отображаемого имени пользователя в его численный ID с кэшированием. */
    suspend fun resolveUserId(userLogin: String): String? = withContext(Dispatchers.IO) {
        val clean = userLogin.trim().removePrefix("@")
        if (clean.isBlank()) return@withContext null
        val lower = clean.lowercase()
        userIdCache[lower]?.let { return@withContext it }

        // Если передан уже численный ID
        if (clean.all { it.isDigit() }) {
            userIdCache[lower] = clean
            return@withContext clean
        }

        // 1. Быстрая проверка текущего пользователя
        if (clean.equals(tokenStore.userLogin, ignoreCase = true) && !tokenStore.userId.isNullOrBlank()) {
            userIdCache[lower] = tokenStore.userId!!
            return@withContext tokenStore.userId
        }

        // 2. Через GQL (быстро, надежно, работает для любых пользователей)
        try {
            val q = """{"query":"query { user(login: \"$lower\") { id login displayName } }"}"""
            val resp = gql.queryWeb(q)
            val uObj = JSONObject(resp).optJSONObject("data")?.optJSONObject("user")
            val id = uObj?.optString("id")
            if (!id.isNullOrBlank()) {
                userIdCache[lower] = id
                uObj.optString("displayName").takeIf { it.isNotBlank() }?.let {
                    userIdCache[it.lowercase()] = id
                }
                return@withContext id
            }
        } catch (_: Exception) {}

        // 3. Если строка содержит допустимые ASCII символы логина Twitch [a-zA-Z0-9_]
        val isAsciiLogin = clean.matches(Regex("^[a-zA-Z0-9_]+$"))
        if (isAsciiLogin) {
            val url = "https://api.twitch.tv/helix/users?login=$lower"
            val (ok, body) = sendHelixRequest(url, "GET")
            if (ok && !body.isNullOrBlank()) {
                val arr = JSONObject(body).optJSONArray("data")
                val id = arr?.optJSONObject(0)?.optString("id")
                if (!id.isNullOrBlank()) {
                    userIdCache[lower] = id
                    return@withContext id
                }
            }
        }

        // 4. Если логин содержит не-ASCII символы (русский displayName) или не найден по прямому логину,
        // ищем через Helix поиск каналов
        try {
            val token = tokenStore.helixToken ?: tokenStore.accessToken
            if (!token.isNullOrBlank()) {
                val searchResults = api.searchChannels(clean, token, first = 5)
                val matched = searchResults.firstOrNull {
                    it.login.equals(clean, ignoreCase = true) ||
                            it.displayName.equals(clean, ignoreCase = true)
                } ?: searchResults.firstOrNull()
                if (matched != null && matched.id.isNotBlank()) {
                    userIdCache[lower] = matched.id
                    userIdCache[matched.login.lowercase()] = matched.id
                    return@withContext matched.id
                }
            }
        } catch (_: Exception) {}

        null
    }

    /** Выполнение модераторского действия (наказания) через Helix Moderation API с чат-фоллбэком. */
    suspend fun executeSliderAction(
        channelId: String,
        channelLogin: String,
        myUserId: String,
        targetUser: String,
        targetUserId: String?,
        messageId: String,
        action: ModSliderAction,
        targetUserLogin: String? = null,
        reason: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        when (action) {
            ModSliderAction.NONE -> return@withContext false
            ModSliderAction.DELETE -> {
                if (messageId.isNotBlank()) {
                    val url = "https://api.twitch.tv/helix/moderation/chat?broadcaster_id=$channelId&moderator_id=$myUserId&message_id=$messageId"
                    val (ok, _) = sendHelixRequest(url, "DELETE")
                    if (ok) {
                        Log.i("TeaTV-ModRepo", "Message $messageId deleted on $channelLogin")
                        return@withContext true
                    }
                }
                // Фоллбэк: если прямое удаление сообщения недоступно, выполняем purge сообщений пользователя через Helix ban API с таймаутом на 1 секунду.
                // Никаких сообщений в чат (через sendChatMessage) НЕ отправляем!
                val resolvedId = targetUserId?.takeIf { it.isNotBlank() }
                    ?: targetUserLogin?.takeIf { it.isNotBlank() }?.let { resolveUserId(it) }
                    ?: resolveUserId(targetUser)
                if (!resolvedId.isNullOrBlank()) {
                    val dataJson = JSONObject().apply {
                        put("user_id", resolvedId)
                        put("duration", 1)
                        put("reason", reason ?: "Message delete (purge)")
                    }
                    val body = JSONObject().apply { put("data", dataJson) }.toString()
                    val url = "https://api.twitch.tv/helix/moderation/bans?broadcaster_id=$channelId&moderator_id=$myUserId"
                    val (ok, _) = sendHelixRequest(url, "POST", body)
                    if (ok) {
                        Log.i("TeaTV-ModRepo", "Message purged via 1s timeout for $targetUser ($resolvedId)")
                        return@withContext true
                    }
                }
                false
            }
            else -> {
                // Приоритет резолва ID: targetUserId -> targetUserLogin -> targetUser
                val resolvedId = targetUserId?.takeIf { it.isNotBlank() }
                    ?: targetUserLogin?.takeIf { it.isNotBlank() }?.let { resolveUserId(it) }
                    ?: resolveUserId(targetUser)

                val durationSec = action.durationSeconds
                var helixSuccess = false

                if (!resolvedId.isNullOrBlank()) {
                    val dataJson = JSONObject().apply {
                        put("user_id", resolvedId)
                        if (durationSec != null) put("duration", durationSec)
                        put("reason", reason ?: "Moderation action")
                    }
                    val body = JSONObject().apply { put("data", dataJson) }.toString()
                    val url = "https://api.twitch.tv/helix/moderation/bans?broadcaster_id=$channelId&moderator_id=$myUserId"
                    val (ok, _) = sendHelixRequest(url, "POST", body)
                    if (ok) {
                        Log.i("TeaTV-ModRepo", "Action $action applied to $targetUser ($resolvedId)")
                        helixSuccess = true
                    }
                } else {
                    Log.w("TeaTV-ModRepo", "Could not resolve numeric ID for $targetUser ($targetUserLogin)")
                }

                if (helixSuccess) return@withContext true
                false
            }
        }
    }

    /** Выдача таймаута на произвольное количество секунд. */
    suspend fun timeoutUser(
        channelId: String,
        myUserId: String,
        targetUser: String,
        durationSeconds: Int = 600,
        reason: String = "Timeout by command"
    ): Boolean = withContext(Dispatchers.IO) {
        val resolvedId = resolveUserId(targetUser) ?: return@withContext false
        val validDuration = durationSeconds.coerceIn(1, 1209600)
        val dataJson = JSONObject().apply {
            put("user_id", resolvedId)
            put("duration", validDuration)
            put("reason", reason)
        }
        val body = JSONObject().apply { put("data", dataJson) }.toString()
        val url = "https://api.twitch.tv/helix/moderation/bans?broadcaster_id=$channelId&moderator_id=$myUserId"
        val (ok, _) = sendHelixRequest(url, "POST", body)
        ok
    }

    /** Разбан пользователя через Helix. */
    suspend fun unbanUser(
        channelId: String,
        myUserId: String,
        targetUser: String,
        targetUserId: String? = null,
        targetUserLogin: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val resolvedId = targetUserId?.takeIf { it.isNotBlank() }
            ?: targetUserLogin?.takeIf { it.isNotBlank() }?.let { resolveUserId(it) }
            ?: resolveUserId(targetUser)

        if (!resolvedId.isNullOrBlank()) {
            val url = "https://api.twitch.tv/helix/moderation/bans?broadcaster_id=$channelId&moderator_id=$myUserId&user_id=$resolvedId"
            val (ok, _) = sendHelixRequest(url, "DELETE")
            if (ok) return@withContext true
        }
        false
    }

    /** Добавить модератора канала (POST /helix/moderation/moderators). */
    suspend fun addModerator(channelId: String, myUserId: String, targetUser: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val userId = resolveUserId(targetUser)
        if (userId.isNullOrBlank()) {
            return@withContext false to "Пользователь @$targetUser не найден"
        }
        val bId = channelId.ifBlank { tokenStore.userId.orEmpty() }.ifBlank { myUserId }
        val url = "https://api.twitch.tv/helix/moderation/moderators?broadcaster_id=$bId&user_id=$userId"
        val (ok, body) = sendHelixRequest(url, "POST")
        if (ok) return@withContext true to "Пользователь @$targetUser назначен модератором"
        false to (parseHelixErrorMessage(body, "Не удалось назначить модератора (проверьте права)") ?: "Ошибка назначения модератора")
    }

    /** Снять модератора канала (DELETE /helix/moderation/moderators). */
    suspend fun removeModerator(channelId: String, myUserId: String, targetUser: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val userId = resolveUserId(targetUser)
        if (userId.isNullOrBlank()) {
            return@withContext false to "Пользователь @$targetUser не найден"
        }
        val bId = channelId.ifBlank { tokenStore.userId.orEmpty() }.ifBlank { myUserId }
        val url = "https://api.twitch.tv/helix/moderation/moderators?broadcaster_id=$bId&user_id=$userId"
        val (ok, body) = sendHelixRequest(url, "DELETE")
        if (ok) return@withContext true to "Пользователь @$targetUser снят с должности модератора"
        false to (parseHelixErrorMessage(body, "Не удалось снять модератора (проверьте права)") ?: "Ошибка снятия модератора")
    }

    /** Назначить VIP канала (POST /helix/channels/vips). */
    suspend fun addVip(channelId: String, myUserId: String, targetUser: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val userId = resolveUserId(targetUser)
        if (userId.isNullOrBlank()) {
            return@withContext false to "Пользователь @$targetUser не найден"
        }
        val bId = channelId.ifBlank { tokenStore.userId.orEmpty() }.ifBlank { myUserId }
        val url = "https://api.twitch.tv/helix/channels/vips?broadcaster_id=$bId&user_id=$userId"
        val (ok, body) = sendHelixRequest(url, "POST")
        if (ok) return@withContext true to "Пользователь @$targetUser добавлен в список VIP"
        false to (parseHelixErrorMessage(body, "Не удалось выдать VIP (проверьте лимит VIP или права)") ?: "Ошибка выдачи VIP")
    }

    /** Снять VIP канала (DELETE /helix/channels/vips). */
    suspend fun removeVip(channelId: String, myUserId: String, targetUser: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val userId = resolveUserId(targetUser)
        if (userId.isNullOrBlank()) {
            return@withContext false to "Пользователь @$targetUser не найден"
        }
        val bId = channelId.ifBlank { tokenStore.userId.orEmpty() }.ifBlank { myUserId }
        val url = "https://api.twitch.tv/helix/channels/vips?broadcaster_id=$bId&user_id=$userId"
        val (ok, body) = sendHelixRequest(url, "DELETE")
        if (ok) return@withContext true to "Пользователь @$targetUser удален из списка VIP"
        false to (parseHelixErrorMessage(body, "Не удалось снять VIP (проверьте права)") ?: "Ошибка снятия VIP")
    }

    /** Очистить весь чат канала (/clear). */
    suspend fun clearChat(channelId: String, myUserId: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val mId = myUserId.ifBlank { tokenStore.userId.orEmpty() }
        val url = "https://api.twitch.tv/helix/moderation/chat?broadcaster_id=$channelId&moderator_id=$mId"
        val (ok, body) = sendHelixRequest(url, "DELETE")
        if (ok) return@withContext true to "Чат канала очищен"
        false to (parseHelixErrorMessage(body) ?: "Не удалось очистить чат (проверьте права)")
    }

    suspend fun setSlowMode(channelId: String, myUserId: String, seconds: Int): Boolean = withContext(Dispatchers.IO) {
        val patch = JSONObject().apply {
            put("slow_mode", seconds > 0)
            if (seconds > 0) put("slow_mode_wait_time", seconds)
        }
        updateChatSettings(channelId, myUserId, patch)
    }

    suspend fun setFollowersMode(channelId: String, myUserId: String, minutes: Int): Boolean = withContext(Dispatchers.IO) {
        val patch = JSONObject().apply {
            put("follower_mode", minutes >= 0)
            if (minutes >= 0) put("follower_mode_duration", minutes)
        }
        updateChatSettings(channelId, myUserId, patch)
    }

    suspend fun setSubOnlyMode(channelId: String, myUserId: String, enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        val patch = JSONObject().apply {
            put("subscriber_mode", enabled)
        }
        updateChatSettings(channelId, myUserId, patch)
    }

    suspend fun setEmoteOnlyMode(channelId: String, myUserId: String, enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        val patch = JSONObject().apply {
            put("emote_mode", enabled)
        }
        updateChatSettings(channelId, myUserId, patch)
    }

    /** Запуск рекламы на канале (/commercial [30, 60, 90, 120, 150, 180]). */
    suspend fun startCommercial(channelId: String, myUserId: String, lengthSeconds: Int = 30): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val validLength = when (lengthSeconds) {
            30, 60, 90, 120, 150, 180 -> lengthSeconds
            else -> 30
        }
        val url = "https://api.twitch.tv/helix/channels/commercial"
        val body = JSONObject().apply {
            put("broadcaster_id", channelId)
            put("length", validLength)
        }.toString()
        val (ok, respBody) = sendHelixRequest(url, "POST", body)
        if (ok) return@withContext true to "Реклама на $validLength сек запущена"
        false to (parseHelixErrorMessage(respBody) ?: "Не удалось запустить рекламу (кулдаун или нет прав компаньона/партнера)")
    }

    /** Смена цвета ника пользователя в чате (/color). */
    suspend fun setChatColor(myUserId: String, color: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val cleanColor = color.trim().lowercase()
        val url = "https://api.twitch.tv/helix/chat/color?user_id=$myUserId&color=${android.net.Uri.encode(cleanColor)}"
        val (ok, body) = sendHelixRequest(url, "PUT")
        if (ok) return@withContext true to "Цвет ника изменен на $cleanColor"
        false to (parseHelixErrorMessage(body) ?: "Не удалось изменить цвет ника")
    }

    private fun parseHelixErrorMessage(body: String?, defaultAction: String? = null): String? {
        if (body.isNullOrBlank()) return defaultAction
        return try {
            val json = JSONObject(body)
            val msg = json.optString("message")
            if (msg.isNotBlank()) {
                val lower = msg.lowercase()
                when {
                    lower.contains("missing scope") -> "Требуется перелогиниться в аккаунт для обновления прав Twitch"
                    lower.contains("already a moderator") -> "Пользователь уже является модератором"
                    lower.contains("not a moderator") -> "Пользователь не является модератором"
                    lower.contains("already a vip") -> "Пользователь уже является VIP"
                    lower.contains("not a vip") -> "Пользователь не является VIP"
                    lower.contains("max vips") || lower.contains("vip limit") -> "Достигнут лимит VIP на канале"
                    lower.contains("broadcaster cannot") -> "Владелец канала не может быть модератором или VIP"
                    lower.contains("not found") -> "Пользователь или канал не найден"
                    lower.contains("forbidden") || lower.contains("not authorized") -> (defaultAction ?: "Нет прав доступа к этому действию")
                    else -> msg
                }
            } else {
                val err = json.optString("error")
                if (err.isNotBlank()) "${defaultAction ?: "Ошибка"}: $err" else defaultAction
            }
        } catch (_: Exception) {
            defaultAction
        }
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
    suspend fun fetchChannelModes(channelId: String, myUserId: String? = null): ChannelModesState = withContext(Dispatchers.IO) {
        var state = ChannelModesState()
        val modParam = if (!myUserId.isNullOrBlank()) "&moderator_id=$myUserId" else ""
        val url = "https://api.twitch.tv/helix/chat/settings?broadcaster_id=$channelId$modParam"
        var (ok, body) = sendHelixRequest(url, "GET")
        if (!ok && !myUserId.isNullOrBlank()) {
            // Фолбэк без moderator_id для обычных зрителей (chat/settings доступен всем)
            val publicUrl = "https://api.twitch.tv/helix/chat/settings?broadcaster_id=$channelId"
            val res = sendHelixRequest(publicUrl, "GET")
            ok = res.first
            body = res.second
        }
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
        if (!myUserId.isNullOrBlank()) {
            val shieldUrl = "https://api.twitch.tv/helix/moderation/shield_mode?broadcaster_id=$channelId&moderator_id=$myUserId"
            val (shOk, shBody) = sendHelixRequest(shieldUrl, "GET")
            if (shOk && !shBody.isNullOrBlank()) {
                val shItem = JSONObject(shBody).optJSONArray("data")?.optJSONObject(0)
                if (shItem != null) {
                    state = state.copy(shieldMode = shItem.optBoolean("is_active", false))
                }
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
