package com.teatv.app.auth

import android.util.Log
import com.teatv.app.chat.ChatMessage
import com.teatv.app.data.CategoryDetails
import com.teatv.app.data.ChannelClip
import com.teatv.app.data.FollowedCategory
import com.teatv.app.data.SocialLink
import com.teatv.app.data.TwitchUser
import org.json.JSONObject
import com.teatv.app.data.ChannelPanel
import org.json.JSONArray
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** GQL с автопродлением integrity + мутации + токен воспроизведения. */
class TwitchGql(
    private val tokenStore: TokenStore,
    private val appContext: android.content.Context
) {
    private val integrityWebView: TwitchIntegrityWebView by lazy {
        TwitchIntegrityWebView(appContext)
    }
    private val ic = TwitchIntegrity()
    private val http = OkHttpClient()
    private var integrityToken: String? = null
    private var integrityExp: Long = 0L
    private var webIntegrity: String? = null
    private var webExp: Long = 0L

    /** Запрос с веб client-id: persisted-хэши Twitch привязаны к клиенту. */
    suspend fun queryWeb(jsonBody: String): String {
        val token = tokenStore.accessToken ?: error("No access token")
        val cid = "kimne78kx3ncx6brgo4mv6wki5h1ko"
        val did = tokenStore.deviceId
        if (webIntegrity == null || System.currentTimeMillis() >= webExp) {
            val o = JSONObject(ic.raw(token, cid, did))
            webIntegrity = o.optString("token", "")
            val exp = o.optLong("expiration", 0L)
            webExp = if (exp > 0) exp - 60_000
            else System.currentTimeMillis() + 3 * 60 * 60 * 1000L
        }
        return ic.gql(token, cid, did, webIntegrity.orEmpty(), jsonBody)
    }

    /**
     * Выполняет мутацию через Persisted Query, мимикрируя под официальный веб-клиент Twitch.
     * Использует web-токен (accessToken) и внутренний Client-ID сайта.
     */
    suspend fun executePersistedMutation(operationName: String, variables: org.json.JSONObject): String =
        withContext(Dispatchers.IO) { // <-- ЯВНЫЙ ПЕРЕХОД В ФОНОВЫЙ ПОТОК

            val token = tokenStore.accessToken ?: throw IllegalStateException("No web authToken available")
            val cid = "kimne7vaqkx1s49fe5vaoicxus1cfq"
            val did = tokenStore.deviceId
            val hash = TwitchPersistedQueries.getHash(operationName)
                ?: throw IllegalArgumentException("No hash found for operation: $operationName")

            val payload = org.json.JSONArray().apply {
                put(org.json.JSONObject().apply {
                    put("operationName", operationName)
                    put("variables", variables)
                    put("extensions", org.json.JSONObject().apply {
                        put("persistedQuery", org.json.JSONObject().apply {
                            put("version", 1)
                            put("sha256Hash", hash)
                        })
                    })
                })
            }.toString()

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val request = okhttp3.Request.Builder()
                .url("https://gql.twitch.tv/gql")
                .post(payload.toRequestBody(mediaType))
                .header("Authorization", "OAuth $token")
                .header("Client-Id", cid)
                .header("X-Device-Id", did)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Mobile Safari/537.36")
                .build()

            // Выполнение запроса теперь безопасно в фоне
            http.newCall(request).execute().use { resp ->
                resp.body?.string() ?: throw Exception("Empty response from GQL")
            }
        }

    suspend fun query(jsonBody: String): String {
        val token = tokenStore.accessToken ?: error("No access token")
        val cid = TwitchAuth.ANDROID_CLIENT_ID
        val did = tokenStore.deviceId
        refreshIfStale(token, cid, did)
        Log.d("TeaTV-GQL", "query with token len=${token.length}, cid=$cid, integrity=${integrityToken?.take(20)}")
        return ic.gql(token, cid, did, integrityToken.orEmpty(), jsonBody)
    }



    /** Только настоящие зафоловленные категории пользователя. */
    suspend fun followedGames(userId: String): List<FollowedCategory> {
        val r = query(
            """{"query":"query { user(id: \"$userId\") { followedGames(first: 50) { nodes { id name boxArtURL(width: 188, height: 250) } } } }"}"""
        )
        if (r.contains("\"errors\"")) {
            Log.e("TeaTV-GQL", "followedGames errors: $r")
            return emptyList()
        }
        val nodes = JSONObject(r)
            .getJSONObject("data").getJSONObject("user")
            .getJSONObject("followedGames").getJSONArray("nodes")
        return List(nodes.length()) { i ->
            val n = nodes.getJSONObject(i)
            FollowedCategory(
                id = n.getString("id"),
                name = n.getString("name"),
                boxArtUrl = n.optString("boxArtURL", "")
            )
        }
    }

    /** Детали игры: имя, бокс-арт, зрители, фолловеры. Один точный запрос. */
    suspend fun getCategoryDetails(gameId: String): CategoryDetails? {
        return try {
            val r = query(
                """{"query":"query CategoryDetails(${'$'}id: ID!) { game(id: ${'$'}id) { id name boxArtURL(width: 188, height: 250) viewersCount followersCount } }","variables":{"id":"$gameId"}}"""
            )
            if (r.contains("\"errors\"")) {
                Log.w("TeaTV-GQL", "categoryDetails errors: $r")
                null
            } else {
                val node = JSONObject(r).optJSONObject("data")?.optJSONObject("game")
                if (node == null) {
                    null
                } else {
                    CategoryDetails(
                        id = node.optString("id", gameId),
                        name = node.optString("name", ""),
                        boxArtUrl = node.optString("boxArtURL", ""),
                        viewersCount = node.optInt("viewersCount", -1),
                        followersCount = node.optInt("followersCount", -1),
                        tags = emptyList(),
                        isFollowed = false
                    )
                }
            }
        } catch (e: Exception) {
            Log.w("TeaTV-GQL", "categoryDetails failed", e)
            null
        }
    }

    /** Фолоу игры. */
    suspend fun followGame(gameId: String): Boolean = try {
        val r = query(
            """{"query":"mutation FollowGame(${'$'}id: ID!) { followGame(input: {gameID: ${'$'}id}) { game { id } } }","variables":{"id":"$gameId"}}"""
        )
        r.contains("\"data\"") && !r.contains("\"errors\"")
    } catch (e: Exception) {
        Log.e("TeaTV-GQL", "followGame failed", e)
        false
    }

    /** Анфолоу игры. */
    suspend fun unfollowGame(gameId: String): Boolean = try {
        val r = query(
            """{"query":"mutation UnfollowGame(${'$'}id: ID!) { unfollowGame(input: {gameID: ${'$'}id}) { game { id } } }","variables":{"id":"$gameId"}}"""
        )
        r.contains("\"data\"") && !r.contains("\"errors\"")
    } catch (e: Exception) {
        Log.e("TeaTV-GQL", "unfollowGame failed", e)
        false
    }

    /** Текущий пользователь (работает и с веб-кукой, и с OAuth-токеном). */
    suspend fun currentUser(): TwitchUser? {
        return try {
            val r = query(
                """{"query":"query { currentUser { id login displayName profileImageURL(width: 300) } }"}"""
            )
            if (r.contains("\"errors\"")) {
                null
            } else {
                val u = JSONObject(r).optJSONObject("data")?.optJSONObject("currentUser")
                if (u == null) {
                    null
                } else {
                    TwitchUser(
                        id = u.getString("id"),
                        login = u.getString("login"),
                        displayName = u.optString("displayName", u.getString("login")),
                        avatarUrl = u.optString("profileImageURL", "")
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("TeaTV-GQL", "currentUser failed", e)
            null
        }
    }

    /** Токен воспроизведения (value+signature) для live. */
    suspend fun playbackAccessToken(login: String): Pair<String, String>? {
        for (playerType in listOf("android", "embed")) {
            val r = query(
                """{"query":"query PlaybackAccessToken(${'$'}login: String!, ${'$'}playerType: String!) { streamPlaybackAccessToken(channelName: ${'$'}login, params: {platform: \"android\", playerType: ${'$'}playerType}) { value signature } }","variables":{"login":"$login","playerType":"$playerType"}}"""
            )
            if (r.contains("\"errors\"")) {
                Log.w("TeaTV-GQL", "pat rejected playerType=$playerType: $r")
                continue
            }
            return try {
                val o = JSONObject(r)
                    .optJSONObject("data")?.optJSONObject("streamPlaybackAccessToken")
                    ?: return null.also { Log.w("TeaTV-GQL", "pat no data: $r") }
                o.getString("value") to o.getString("signature")
            } catch (e: Exception) {
                Log.e("TeaTV-GQL", "pat parse failed: $r", e)
                null
            }
        }
        return null
    }

    /** Счётчик фоловеров канала. Каскад: отказ → -1. */
    suspend fun getFollowersCount(userId: String): Int {
        val r = try {
            query("""{"query":"query { user(id: \"$userId\") { followers(first: 1) { totalCount } } }"}""")
        } catch (_: Exception) {
            ""
        }
        if (r.isBlank() || r.contains("\"errors\"")) {
            Log.w("TeaTV-GQL", "followers rejected: $r")
            return -1
        }
        return JSONObject(r).optJSONObject("data")?.optJSONObject("user")
            ?.optJSONObject("followers")?.optInt("totalCount", -1) ?: -1
    }

    /** Клипы канала по просмотрам. Каскад: отказ → пусто. */
    suspend fun getClips(userId: String): List<ChannelClip> {
        val r = try {
            query(
                """{"query":"query { user(id: \"$userId\") { clips(first: 20) { edges { node { id title viewCount thumbnailURL url durationSeconds createdAt } } } } }"}"""
            )
        } catch (_: Exception) {
            ""
        }
        if (r.isBlank() || r.contains("\"errors\"")) {
            Log.w("TeaTV-GQL", "clips rejected: $r")
            return emptyList()
        }
        val edges = JSONObject(r).optJSONObject("data")?.optJSONObject("user")
            ?.optJSONObject("clips")?.optJSONArray("edges") ?: return emptyList()
        val out = mutableListOf<ChannelClip>()
        for (i in 0 until edges.length()) {
            val n = edges.optJSONObject(i)?.optJSONObject("node") ?: continue
            out.add(
                ChannelClip(
                    id = n.optString("id", ""),
                    title = n.optString("title", ""),
                    viewCount = n.optInt("viewCount", 0),
                    thumbnailUrl = n.optString("thumbnailURL", ""),
                    url = n.optString("url", ""),
                    durationSeconds = n.optInt("durationSeconds", 0),
                    createdAt = n.optString("createdAt", "")
                )
            )
        }
        return out
    }

    /** Цвет бренда канала (primaryColorHex). Каскад: отказ → пусто. */
    suspend fun getBrandColor(userId: String): String {
        val r = try {
            query("""{"query":"query { user(id: \"$userId\") { primaryColorHex } }"}""")
        } catch (_: Exception) {
            ""
        }
        if (r.isBlank() || r.contains("\"errors\"")) {
            Log.w("TeaTV-GQL", "brandColor rejected: $r")
            return ""
        }
        return JSONObject(r).optJSONObject("data")?.optJSONObject("user")
            ?.optString("primaryColorHex", "") ?: ""
    }

    /** Ссылки канала: channel.socialMedias. Каскад: отказ → пусто. */
    suspend fun getSocialMedias(userId: String): List<SocialLink> {
        val r = try {
            query(
                """{"query":"query { user(id: \"$userId\") { channel { socialMedias { name title url } } } }"}"""
            )
        } catch (_: Exception) {
            ""
        }
        if (r.isBlank() || r.contains("\"errors\"")) {
            Log.w("TeaTV-GQL", "socialMedias rejected: $r")
            return emptyList()
        }
        val arr = JSONObject(r).optJSONObject("data")?.optJSONObject("user")
            ?.optJSONObject("channel")?.optJSONArray("socialMedias") ?: return emptyList()
        val out = mutableListOf<SocialLink>()
        for (i in 0 until arr.length()) {
            val n = arr.optJSONObject(i) ?: continue
            val url = n.optString("url", "")
            if (url.isNotBlank()) {
                out.add(SocialLink(n.optString("title", "").ifBlank { n.optString("name", "") }, url))
            }
        }
        return out
    }

    /** Плашки стримера: через queryWeb (web client-id kimne...). */
    suspend fun getChannelPanels(channelLogin: String): List<ChannelPanel> {
        val query = "query GetChannelPanels(\$login: String!) { user(login: \$login) { id login displayName panels { id ... on DefaultPanel { id title description linkURL imageURL } ... on ExtensionPanel { id slotID } } } }"
        val body = """[{"operationName":"GetChannelPanels","variables":{"login":"$channelLogin"},"query":"$query"}]"""
        val r = try {
            queryWeb(body)
        } catch (_: Exception) {
            ""
        }
        if (r.isBlank() || r.contains("\"errors\"")) {
            Log.w("TeaTV-GQL", "channelPanels rejected: $r")
            return emptyList()
        }
        val root = JSONArray(r).optJSONObject(0) ?: return emptyList()
        val arr = root.optJSONObject("data")?.optJSONObject("user")
            ?.optJSONArray("panels") ?: return emptyList()
        val out = mutableListOf<ChannelPanel>()
        for (i in 0 until arr.length()) {
            val node = arr.optJSONObject(i) ?: continue
            val desc = node.optString("description", "").takeIf { it != "null" }?.replace(Regex("<[^>]+>"), "")?.trim().orEmpty()
            val img = node.optString("imageURL", "").takeIf { it != "null" }.orEmpty()
            val link = node.optString("linkURL", "").takeIf { it != "null" }.orEmpty()
            val title = node.optString("title", "").takeIf { it != "null" }.orEmpty()
            if (img.isBlank() && desc.isBlank() && title.isBlank() && link.isBlank()) continue
            out.add(
                ChannelPanel(title = title, description = desc, imageUrl = img, linkUrl = link)
            )
        }
        return out
    }

    /** Баннер канала. socialMedia схемой отклонён — не запрашиваем. */
    suspend fun getChannelExtras(userId: String): Pair<String, List<SocialLink>> {
        val r = try {
            query("""{"query":"query { user(id: \"$userId\") { bannerImageURL } }"}""")
        } catch (_: Exception) {
            ""
        }
        if (r.isBlank() || r.contains("\"errors\"")) {
            Log.w("TeaTV-GQL", "channelExtras errors: $r")
            return "" to emptyList()
        }
        val banner = JSONObject(r).optJSONObject("data")?.optJSONObject("user")
            ?.optString("bannerImageURL", "") ?: ""
        return banner to emptyList()
    }

    /** Токен воспроизведения для VOD: перебор platform/playerType, как у лайва. */
    suspend fun getVodPlaybackAccessToken(videoId: String): Pair<String, String>? {
        for (platform in listOf("android", "web")) {
            for (playerType in listOf("embed", "site")) {
                val r = try {
                    query(
                        """{"query":"query VodPat(${'$'}id: ID!, ${'$'}p: String!, ${'$'}t: String!) { videoPlaybackAccessToken(id: ${'$'}id, params: {platform: ${'$'}p, playerType: ${'$'}t}) { value signature } }","variables":{"id":"$videoId","p":"$platform","t":"$playerType"}}"""
                    )
                } catch (e: Exception) {
                    Log.w("TeaTV-GQL", "vod pat exception $platform/$playerType: ${e.message}")
                    continue
                }
                if (r.contains("\"errors\"")) {
                    Log.w("TeaTV-GQL", "vod pat rejected $platform/$playerType")
                    continue
                }
                val o = JSONObject(r).optJSONObject("data")
                    ?.optJSONObject("videoPlaybackAccessToken") ?: continue
                val value = o.optString("value", "")
                val sig = o.optString("signature", "")
                if (value.isNotBlank() && sig.isNotBlank()) {
                    Log.i("TeaTV-GQL", "vod pat ok via $platform/$playerType")
                    return value to sig
                }
            }
        }
        Log.w("TeaTV-GQL", "vod pat: all combinations failed")
        return null
    }

    /** Ссылка на клип + подпись: sourceURL с путём /nauth/ требует sig+token. */
    suspend fun getClipPlaybackUrl(clipSlug: String): String? {
        for (platform in listOf("web", "android")) {
            for (playerType in listOf("site", "embed")) {
                val r = try {
                    query(
                        """{"query":"query ClipUrl(${'$'}slug: ID!, ${'$'}p: String!, ${'$'}t: String!) { clip(slug: ${'$'}slug) { playbackAccessToken(params: {platform: ${'$'}p, playerType: ${'$'}t}) { value signature } videoQualities { sourceURL quality } } }","variables":{"slug":"$clipSlug","p":"$platform","t":"$playerType"}}"""
                    )
                } catch (e: Exception) {
                    Log.w("TeaTV-GQL", "clip url exception $platform/$playerType: ${e.message}")
                    continue
                }
                if (r.contains("\"errors\"")) {
                    Log.w("TeaTV-GQL", "clip url rejected $platform/$playerType: $r")
                    continue
                }
                val clip = JSONObject(r).optJSONObject("data")?.optJSONObject("clip") ?: continue
                val pat = clip.optJSONObject("playbackAccessToken") ?: continue
                val sig = pat.optString("signature", "")
                val value = pat.optString("value", "")
                if (sig.isBlank() || value.isBlank()) {
                    Log.w("TeaTV-GQL", "clip pat empty $platform/$playerType")
                    continue
                }
                val arr = clip.optJSONArray("videoQualities") ?: continue
                var bestUrl: String? = null
                var bestQ = -1
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val url = o.optString("sourceURL", "")
                    if (url.isBlank()) continue
                    val q = Regex("(\\d+)p").find(o.optString("quality", ""))
                        ?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    if (q > bestQ) { bestQ = q; bestUrl = url }
                }
                val base = bestUrl ?: continue
                val sep = if (base.contains("?")) "&" else "?"
                Log.i("TeaTV-GQL", "clip url ok via $platform/$playerType")
                return base + sep + "sig=" + sig +
                        "&token=" + java.net.URLEncoder.encode(value, "UTF-8")
            }
        }
        Log.w("TeaTV-GQL", "clip url: all combinations failed")
        return null
    }

    /** Чат VOD по таймкоду: каскад GQL(web) → REST v5. */
    suspend fun getVodChatComments(
        videoId: String,
        contentOffsetSeconds: Int
    ): Pair<List<ChatMessage>, String?> {
        // Ветка 1: GQL через веб-ключ.
        try {
            val r = queryWeb(
                """{"query":"query VideoCommentsByOffset(${'$'}id: ID!, ${'$'}off: Int!) { video(id: ${'$'}id) { comments(contentOffsetSeconds: ${'$'}off, first: 30) { edges { node { id commenter { displayName } message { fragments { text } } } } } } }","variables":{"id":"$videoId","off":$contentOffsetSeconds}}"""
            )
            if (!r.contains("\"errors\"")) {
                val edges = JSONObject(r).optJSONObject("data")
                    ?.optJSONObject("video")
                    ?.optJSONObject("comments")
                    ?.optJSONArray("edges")
                if (edges != null && edges.length() > 0) {
                    val out = mutableListOf<ChatMessage>()
                    for (i in 0 until edges.length()) {
                        val node = edges.optJSONObject(i)?.optJSONObject("node") ?: continue
                        val frags = node.optJSONObject("message")?.optJSONArray("fragments")
                        val text = buildString {
                            if (frags != null) {
                                for (j in 0 until frags.length()) {
                                    append(frags.optJSONObject(j)?.optString("text").orEmpty())
                                }
                            }
                        }
                        out += ChatMessage(
                            id = node.optString("id"),
                            sender = node.optJSONObject("commenter")?.optString("displayName") ?: "Unknown",
                            color = "",
                            text = text,
                            badges = emptyList(),
                            replyToId = null,
                            replyToName = null,
                            replyToBody = null
                        )
                    }
                    if (out.isNotEmpty()) {
                        Log.i("TeaTV-GQL", "vod chat ok via gql")
                        return out to null
                    }
                }
            } else {
                Log.w("TeaTV-GQL", "vod chat gql rejected: ${r.take(300)}")
            }
        } catch (e: Exception) {
            Log.w("TeaTV-GQL", "vod chat gql failed: ${e.message}")
        }

        // Ветка 2: REST v5 comments (kraken-формат).
        return try {
            val req = Request.Builder()
                .url("https://api.twitch.tv/v5/videos/$videoId/comments?content_offset_seconds=$contentOffsetSeconds")
                .header("Client-ID", "kimne78kx3ncx6brgo4mv6wki5h1ko")
                .header("Accept", "application/json")
                .build()
            http.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    Log.w("TeaTV-GQL", "vod chat v5 http ${resp.code}")
                    return@use emptyList<ChatMessage>() to null
                }
                val arr = JSONObject(body).optJSONArray("comments")
                    ?: return@use emptyList<ChatMessage>() to null
                val out = mutableListOf<ChatMessage>()
                for (i in 0 until arr.length()) {
                    val c = arr.optJSONObject(i) ?: continue
                    val content = c.optJSONObject("content")
                    var text = content?.optString("text").orEmpty()
                    if (text.isBlank()) {
                        val frags = content?.optJSONArray("fragments")
                        text = buildString {
                            if (frags != null) {
                                for (j in 0 until frags.length()) {
                                    append(frags.optJSONObject(j)?.optString("text").orEmpty())
                                }
                            }
                        }
                    }
                    out += ChatMessage(
                        id = c.optString("_id"),
                        sender = c.optJSONObject("commenter")?.optString("display_name") ?: "Unknown",
                        color = "",
                        text = text,
                        badges = emptyList(),
                        replyToId = null,
                        replyToName = null,
                        replyToBody = null
                    )
                }
                Log.i("TeaTV-GQL", "vod chat ok via v5")
                out to null
            }
        } catch (e: Exception) {
            Log.e("TeaTV-GQL", "vod chat v5 failed", e)
            emptyList<ChatMessage>() to null
        }
    }

    private suspend fun refreshIfStale(token: String, cid: String, did: String) {
        if (integrityToken != null && System.currentTimeMillis() < integrityExp) return
        val o = JSONObject(ic.raw(token, cid, did))
        integrityToken = o.optString("token", "")
        val exp = o.optLong("expiration", 0L)
        integrityExp =
            if (exp > 0) exp - 60_000
            else System.currentTimeMillis() + 3 * 60 * 60 * 1000L
    }
}