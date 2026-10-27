package com.teatv.app.auth

import android.util.Log
import com.teatv.app.chat.ChatMessage
import com.teatv.app.chat.badges.BadgeRef
import com.teatv.app.chat.emotes.Emote
import com.teatv.app.chat.emotes.EmoteKind
import com.teatv.app.data.ChannelInfo
import com.teatv.app.data.FollowedChannel
import com.teatv.app.data.LiveStream
import com.teatv.app.data.TwitchUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import com.teatv.app.data.LiveStreamPage
import com.teatv.app.data.SearchCategory
import com.teatv.app.data.SearchChannel
import com.teatv.app.data.ChannelVideo
import com.teatv.app.data.MySubscription

class TwitchApi(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {

    /** Профиль канала с описанием (GET /helix/users). */
    suspend fun getUserProfile(userId: String, token: String): TwitchUser? =
        withContext(Dispatchers.IO) {
            val url = "https://api.twitch.tv/helix/users?id=$userId"
            val request = Request.Builder().url(url).withAuth(token).build()
            client.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) return@withContext null
                val arr = JSONObject(text).optJSONArray("data")
                    ?: return@withContext null
                val o = arr.optJSONObject(0) ?: return@withContext null
                TwitchUser(
                    id = o.optString("id", ""),
                    login = o.optString("login", ""),
                    displayName = o.optString("display_name", ""),
                    avatarUrl = o.optString("profile_image_url", ""),
                    description = o.optString("description", "")
                )
            }
        }

    /** Архивы канала (GET /helix/videos). */
    suspend fun getChannelVideos(
        userId: String,
        token: String,
        first: Int = 20
    ): List<ChannelVideo> = withContext(Dispatchers.IO) {
        val url = "https://api.twitch.tv/helix/videos?user_id=$userId&type=archive&first=$first"
        val request = Request.Builder().url(url).withAuth(token).build()
        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) return@withContext emptyList()
            val arr = JSONObject(text).optJSONArray("data")
                ?: return@withContext emptyList()
            val list = mutableListOf<ChannelVideo>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val viewable = o.optString("viewable", "public")
                if (viewable.equals("sub_only", ignoreCase = true)) continue
                list += ChannelVideo(
                    id = o.optString("id", ""),
                    title = o.optString("title", ""),
                    createdAt = o.optString("created_at", ""),
                    duration = o.optString("duration", ""),
                    viewCount = o.optInt("view_count", 0),
                    thumbnailUrl = o.optString("thumbnail_url", ""),
                    url = o.optString("url", "")
                )
            }
            list
        }
    }

    suspend fun getSubscriptionTo(
        broadcasterId: String,
        userId: String,
        token: String,
        clientId: String
    ): Pair<Int, MySubscription?> = withContext(Dispatchers.IO) {
        val url = "https://api.twitch.tv/helix/subscriptions/user" +
                "?broadcaster_id=$broadcasterId&user_id=$userId"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Client-ID", clientId)
            .build()

        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (resp.code == 404) return@withContext 404 to null
            if (!resp.isSuccessful) {
                Log.w("TeaTV-Api", "sub check ${resp.code}: ${text.take(200)}")
                return@withContext resp.code to null
            }
            val o = JSONObject(text).optJSONArray("data")?.optJSONObject(0)
                ?: return@withContext resp.code to null

            resp.code to MySubscription(
                broadcasterId = o.optString("broadcaster_id", ""),
                broadcasterLogin = o.optString("broadcaster_login", ""),
                broadcasterName = o.optString("broadcaster_name", ""),
                tier = o.optString("tier", "1000"),
                planName = o.optString("plan_name", ""),
                createdAt = o.optString("created_at", "").takeIf { it.isNotBlank() }
            )
        }
    }

    /** Подписан ли пользователь на канал (Helix). */
    suspend fun isFollowing(userId: String, broadcasterId: String, token: String): Boolean =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(
                    "https://api.twitch.tv/helix/channels/followed" +
                            "?user_id=$userId&broadcaster_id=$broadcasterId"
                )
                .withAuth(token)
                .build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext false
                JSONObject(resp.body?.string().orEmpty()).getJSONArray("data").length() > 0
            }
        }

    /** Ники в чате: только модератор/стример (scope moderator:read:chatters). */
    suspend fun getChatters(
        broadcasterId: String,
        moderatorId: String,
        token: String
    ): List<String> =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(
                    "https://api.twitch.tv/helix/chat/chatters" +
                            "?broadcaster_id=$broadcasterId&moderator_id=$moderatorId&first=1000"
                )
                .withAuth(token)
                .build()
            client.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("Chatters failed: ${resp.code} $text")
                val arr = JSONObject(text).getJSONArray("data")
                List(arr.length()) { i ->
                    arr.getJSONObject(i).optString("user_name", "")
                }.filter { it.isNotEmpty() }
            }
        }

    /** Client-ID текущей пары токен+клиент. */
    var clientId: String = TwitchAuth.ANDROID_CLIENT_ID

    private fun Request.Builder.withAuth(token: String): Request.Builder = apply {
        header("Client-ID", clientId)
        header("Authorization", "Bearer $token")
    }

    /** Профиль себя (GET /helix/users). */
    suspend fun getCurrentUser(accessToken: String): TwitchUser =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(TwitchAuth.HELIX_USERS)
                .withAuth(accessToken)
                .build()
            client.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("Helix users failed: ${resp.code} $text")
                parseUser(JSONObject(text).getJSONArray("data").getJSONObject(0))
            }
        }

    /** Подписки пользователя (GET /helix/channels/followed). */
    suspend fun getFollowedChannels(userId: String, token: String): List<FollowedChannel> =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("https://api.twitch.tv/helix/channels/followed?user_id=$userId&first=100")
                .withAuth(token)
                .build()
            client.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("Followed failed: ${resp.code} $text")
                val arr = JSONObject(text).getJSONArray("data")
                List(arr.length()) { i ->
                    val o = arr.getJSONObject(i)
                    FollowedChannel(
                        broadcasterId = o.getString("broadcaster_id"),
                        broadcasterLogin = o.getString("broadcaster_login"),
                        broadcasterName = o.getString("broadcaster_name"),
                        followedAt = o.optString("followed_at", "")
                    )
                }
            }
        }

    /** Живые стримы из подписок (GET /helix/streams/followed). */
    suspend fun getFollowedStreams(userId: String, token: String): List<LiveStream> =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("https://api.twitch.tv/helix/streams/followed?user_id=$userId&first=100")
                .withAuth(token)
                .build()
            client.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("Streams failed: ${resp.code} $text")
                val arr = JSONObject(text).getJSONArray("data")
                List(arr.length()) { i ->
                    val o = arr.getJSONObject(i)
                    LiveStream(
                        userId = o.getString("user_id"),
                        startedAt = o.optString("started_at", ""),
                        userName = o.getString("user_name"),
                        gameId = o.optString("game_id", ""),
                        gameName = o.optString("game_name", ""),
                        title = o.optString("title", ""),
                        viewerCount = o.optInt("viewer_count", 0),
                        thumbnailUrl = o.optString("thumbnail_url", "")
                    )
                }
            }
        }

    /** Пользователи по id (GET /helix/users). */
    suspend fun getUsersByIds(ids: List<String>, token: String): List<TwitchUser> =
        withContext(Dispatchers.IO) {
            if (ids.isEmpty()) return@withContext emptyList()
            val request = Request.Builder()
                .url("https://api.twitch.tv/helix/users?" + ids.joinToString("&") { "id=$it" })
                .withAuth(token)
                .build()
            client.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("Users failed: ${resp.code} $text")
                val arr = JSONObject(text).getJSONArray("data")
                List(arr.length()) { i -> parseUser(arr.getJSONObject(i)) }
            }
        }

    /** Инфо канала (GET /helix/channels). */
    suspend fun getChannelInfo(broadcasterId: String, token: String): ChannelInfo? =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("https://api.twitch.tv/helix/channels?broadcaster_id=$broadcasterId")
                .withAuth(token)
                .build()
            client.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) return@withContext null
                val arr = JSONObject(text).getJSONArray("data")
                if (arr.length() == 0) return@withContext null
                val o = arr.getJSONObject(0)
                ChannelInfo(
                    broadcasterId = o.getString("broadcaster_id"),
                    title = o.optString("title", ""),
                    gameName = o.optString("game_name", ""),
                    language = o.optString("language", "")
                )
            }
        }

    /** История чата (сервис recent-messages, до 100 сообщений). */
    suspend fun getRecentChat(login: String): List<ChatMessage> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(
                    "https://recent-messages.robotty.de/api/v2/recent-messages/$login" +
                            "?limit=100&hide_moderation_messages=true"
                )
                .header("Accept", "application/json")
                .build()
            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful || body.startsWith("<")) return@withContext emptyList()
                val arr = JSONObject(body).optJSONArray("messages")
                    ?: return@withContext emptyList()
                val out = mutableListOf<ChatMessage>()
                for (i in 0 until arr.length()) {
                    parseIrc(arr.optString(i))?.let { out += it }
                }
                out
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Значки канала (модер, вип, сабы). */
    suspend fun getChannelBadges(broadcasterId: String, token: String): List<BadgeRef> =
        helixBadges("chat/badges?broadcaster_id=$broadcasterId", token)

    /** Глобальные значки (турбо, прайм и т.д.). */
    suspend fun getGlobalBadges(token: String): List<BadgeRef> =
        helixBadges("chat/badges/global", token)

    private suspend fun helixBadges(path: String, token: String): List<BadgeRef> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("https://api.twitch.tv/helix/$path")
                    .withAuth(token)
                    .build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext emptyList()
                    val arr = JSONObject(resp.body?.string().orEmpty()).optJSONArray("data")
                        ?: return@withContext emptyList()
                    val out = mutableListOf<BadgeRef>()
                    for (i in 0 until arr.length()) {
                        val set = arr.getJSONObject(i)
                        val setId = set.optString("set_id")
                        val versions = set.optJSONArray("versions") ?: continue
                        for (j in 0 until versions.length()) {
                            val v = versions.getJSONObject(j)
                            out += BadgeRef(
                                setId = setId,
                                version = v.optString("id"),
                                url = v.optString("image_url_2x")
                            )
                        }
                    }
                    out
                }
            } catch (_: Exception) {
                emptyList()
            }
        }

    /** Разбор сырой IRC-строки из recent-messages. */
    private fun parseIrc(raw: String): ChatMessage? {
        return try {
            var tags = emptyMap<String, String>()
            var rest = raw
            if (rest.startsWith("@")) {
                val sp = rest.indexOf(' ')
                tags = rest.substring(1, sp).split(';').mapNotNull { kv ->
                    val eq = kv.indexOf('=')
                    if (eq < 0) null else kv.substring(0, eq) to kv.substring(eq + 1)
                }.toMap()
                rest = rest.substring(sp + 1)
            }
            val hash = rest.indexOf('#')
            val textStart = rest.indexOf(':', hash)
            if (rest.contains("PRIVMSG") && hash >= 0 && textStart >= 0) {
                val msgText = rest.substring(textStart + 1)
                val msgEmotes = mutableMapOf<String, String>()
                val emotesTag = tags["emotes"]
                if (!emotesTag.isNullOrBlank()) {
                    emotesTag.split('/').forEach { group ->
                        val parts = group.split(':')
                        if (parts.size == 2) {
                            val id = parts[0]
                            val firstRange = parts[1].split(',').firstOrNull()?.split('-')
                            if (firstRange != null && firstRange.size == 2) {
                                val start = firstRange[0].toIntOrNull()
                                val end = firstRange[1].toIntOrNull()
                                if (start != null && end != null && start >= 0 && end < msgText.length && start <= end) {
                                    val code = msgText.substring(start, end + 1)
                                    val url = "https://static-cdn.jtvnw.net/emoticons/v2/$id/default/dark/2.0"
                                    msgEmotes[code] = url
                                    com.teatv.app.chat.emotes.TwitchEmoteCache.put(code, url)
                                }
                            }
                        }
                    }
                }
                ChatMessage(
                    id = tags["id"] ?: "",
                    sender = tags["display-name"] ?: tags["login"] ?: "",
                    color = tags["color"] ?: "",
                    text = msgText,
                    badges = tags["badges"]?.split(',')?.filter { it.isNotBlank() } ?: emptyList(),
                    replyToId = tags["reply-parent-msg-id"]?.takeIf { it.isNotBlank() },
                    replyToName = tags["reply-parent-display-name"]?.takeIf { it.isNotBlank() },
                    replyToBody = tags["reply-parent-msg-body"]?.takeIf { it.isNotBlank() },
                    senderId = tags["user-id"]?.takeIf { it.isNotBlank() },
                    senderLogin = tags["login"]?.takeIf { it.isNotBlank() },
                    twitchEmotes = msgEmotes
                )
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Отправка сообщения в чат (scope user:write:chat). */
    suspend fun sendChatMessage(
        broadcasterId: String,
        senderId: String,
        message: String,
        token: String,
        replyParentMessageId: String? = null
    ): Boolean =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("broadcaster_id", broadcasterId)
                put("sender_id", senderId)
                put("message", message)
                if (replyParentMessageId != null) {
                    put("reply_parent_message_id", replyParentMessageId)
                }
            }.toString()
            val request = Request.Builder()
                .url("https://api.twitch.tv/helix/chat/messages")
                .post(body.toRequestBody("application/json".toMediaType()))
                .withAuth(token)
                .build()
            client.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("Send chat failed: ${resp.code} $text")
                true
            }
        }

    /** Смайлы канала. */
    suspend fun getChannelEmotes(broadcasterId: String, token: String): List<Emote> =
        helixEmotes("chat/emotes?broadcaster_id=$broadcasterId", token)

    /** Глобальные смайлы Twitch. */
    suspend fun getGlobalEmotes(token: String): List<Emote> =
        helixEmotes("chat/emotes/global", token)

    /** Смайлы пользователя (включая подписки), scope user:read:emotes. */
    suspend fun getUserEmotes(userId: String, token: String, subsOnly: Boolean = true): List<Emote> =
        helixEmotes("chat/emotes/user?user_id=$userId", token, subsOnly = subsOnly)

    private suspend fun helixEmotes(
        path: String,
        token: String,
        subsOnly: Boolean = false
    ): List<Emote> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("https://api.twitch.tv/helix/$path")
                    .withAuth(token)
                    .build()

                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext emptyList()

                    val arr = JSONObject(resp.body?.string().orEmpty()).optJSONArray("data")
                        ?: return@withContext emptyList()

                    val out = mutableListOf<Emote>()

                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)

                        val emoteType = o.optString("emote_type").ifBlank {
                            o.optJSONObject("emote_set")?.optString("type").orEmpty()
                        }
                        val ownerId = o.optString("owner_id").ifBlank {
                            if (path.contains("broadcaster_id=")) path.substringAfter("broadcaster_id=").substringBefore("&") else null
                        }

                        if (subsOnly) {
                            if (ownerId.isNullOrBlank() || ownerId == "0") {
                                continue
                            }
                            if (emoteType.isNotBlank() && emoteType in setOf("globals", "smilies")) {
                                continue
                            }
                            if (emoteType.isNotBlank() && emoteType !in setOf("subscriptions", "follower", "bitstier", "rewards", "hypetrain", "prime", "twofactor", "limitedtime")) {
                                continue
                            }
                        }

                        val id = o.optString("id")
                        val img = o.optJSONObject("images")
                        val code = o.optString("name")
                        val url = if (id.isNotBlank()) {
                            "https://static-cdn.jtvnw.net/emoticons/v2/$id/default/dark/2.0"
                        } else {
                            img?.optString("url_2x")?.takeIf { it.isNotBlank() } ?: img?.optString("url_1x").orEmpty()
                        }

                        if (code.isNotBlank() && url.isNotBlank()) {
                            val emote = Emote(
                                code = code,
                                url = url,
                                kind = EmoteKind.TWITCH,
                                ownerId = ownerId,
                                emoteType = emoteType.takeIf { it.isNotBlank() }
                            )
                            out += emote
                            com.teatv.app.chat.emotes.TwitchEmoteCache.put(code, url)
                        }
                    }

                    out
                }
            } catch (_: Exception) {
                emptyList()
            }
        }

    /** Стримы по игре с пагинацией (GET /helix/streams?game_id=...). */
    suspend fun getStreamsByGamePage(
        gameId: String,
        token: String,
        first: Int = 20,
        after: String? = null
    ): LiveStreamPage = withContext(Dispatchers.IO) {
        val url = buildString {
            append("https://api.twitch.tv/helix/streams?game_id=")
            append(gameId)
            append("&first=").append(first)
            if (!after.isNullOrBlank()) append("&after=").append(after)
        }
        val request = Request.Builder().url(url).withAuth(token).build()
        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("Streams by game failed: ${resp.code} $text")
            val root = JSONObject(text)
            val arr = root.optJSONArray("data") ?: org.json.JSONArray()
            val streams = List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                LiveStream(
                    userId = o.getString("user_id"),
                    userName = o.getString("user_name"),
                    gameId = o.optString("game_id", ""),
                    gameName = o.optString("game_name", ""),
                    title = o.optString("title", ""),
                    viewerCount = o.optInt("viewer_count", 0),
                    thumbnailUrl = o.optString("thumbnail_url", ""),
                    startedAt = o.optString("started_at", "")
                )
            }
            val cursor = root.optJSONObject("pagination")?.optString("cursor", "")
            LiveStreamPage(streams, cursor.takeIf { !it.isNullOrBlank() }, !cursor.isNullOrBlank())
        }
    }

    /** Аватары пользователей по id (для обогащения стримов игры). */
    suspend fun getUsersAvatars(ids: List<String>, token: String): Map<String, String> =
        withContext(Dispatchers.IO) {
            if (ids.isEmpty()) return@withContext emptyMap()
            // Helix позволяет до 100 id в одном запросе
            val request = Request.Builder()
                .url("https://api.twitch.tv/helix/users?" + ids.joinToString("&") { "id=$it" })
                .withAuth(token)
                .build()
            client.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) return@withContext emptyMap()
                val arr = JSONObject(text).optJSONArray("data") ?: return@withContext emptyMap()
                val out = mutableMapOf<String, String>()
                for (i in 0 until arr.length()) {
                    val u = arr.getJSONObject(i)
                    val id = u.optString("id", "")
                    val av = u.optString("profile_image_url", "")
                    if (id.isNotBlank()) out[id] = av
                }
                out
            }
        }

    /** Стримы по id пользователей (GET /helix/streams?user_id=...). */
    suspend fun getStreamsByUsers(
        userIds: List<String>,
        token: String
    ): List<LiveStream> = withContext(Dispatchers.IO) {
        if (userIds.isEmpty()) return@withContext emptyList()
        val url = "https://api.twitch.tv/helix/streams?" +
                userIds.take(100).joinToString("&") { "user_id=$it" }
        val request = Request.Builder().url(url).withAuth(token).build()
        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) return@withContext emptyList()
            val arr = JSONObject(text).optJSONArray("data") ?: return@withContext emptyList()
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                LiveStream(
                    userId = o.getString("user_id"),
                    userName = o.getString("user_name"),
                    gameId = o.optString("game_id", ""),
                    gameName = o.optString("game_name", ""),
                    title = o.optString("title", ""),
                    viewerCount = o.optInt("viewer_count", 0),
                    thumbnailUrl = o.optString("thumbnail_url", ""),
                    startedAt = o.optString("started_at", "")
                )
            }
        }
    }

    /** Поиск каналов (GET /helix/search/channels). */
    suspend fun searchChannels(
        query: String,
        token: String,
        first: Int = 10,
        liveOnly: Boolean = false
    ): List<SearchChannel> = withContext(Dispatchers.IO) {
        val url = "https://api.twitch.tv/helix/search/channels?query=" +
                android.net.Uri.encode(query) +
                "&first=$first&live_only=$liveOnly"
        val request = Request.Builder().url(url).withAuth(token).build()
        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) return@withContext emptyList()
            val arr = JSONObject(text).optJSONArray("data") ?: return@withContext emptyList()
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                SearchChannel(
                    id = o.optString("id", ""),
                    login = o.optString("broadcaster_login", o.optString("login", "")),
                    displayName = o.optString("display_name", ""),
                    gameName = o.optString("game_name", ""),
                    title = o.optString("title", ""),
                    isLive = o.optBoolean("is_live", false),
                    thumbnailUrl = o.optString("thumbnail_url", "")
                )
            }
        }
    }


    /** Поиск категорий (GET /helix/search/categories). */
    suspend fun searchCategories(
        query: String,
        token: String,
        first: Int = 10
    ): List<SearchCategory> = withContext(Dispatchers.IO) {
        val url = "https://api.twitch.tv/helix/search/categories?query=" +
                android.net.Uri.encode(query) + "&first=$first"
        val request = Request.Builder().url(url).withAuth(token).build()
        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) return@withContext emptyList()
            val arr = JSONObject(text).optJSONArray("data") ?: return@withContext emptyList()
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                SearchCategory(
                    id = o.optString("id", ""),
                    name = o.optString("name", ""),
                    boxArtUrl = o.optString("box_art_url", "")
                )
            }
        }
    }

    private fun parseUser(o: JSONObject): TwitchUser =
        TwitchUser(
            id = o.getString("id"),
            login = o.getString("login"),
            displayName = o.optString("display_name", o.getString("login")),
            avatarUrl = o.optString("profile_image_url", "")
        )
}