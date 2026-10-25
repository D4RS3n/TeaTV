package com.teatv.app.data

import android.app.Application
import com.teatv.app.auth.TokenStore
import com.teatv.app.auth.TwitchApi
import com.teatv.app.auth.TwitchGql
import kotlin.collections.emptyList
import kotlin.to
import com.teatv.app.auth.TwitchAuth
import com.teatv.app.player.TwitchHls
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class TwitchRepository(
    private val api: TwitchApi,
    private val tokenStore: TokenStore,
    private val gql: TwitchGql,
    @Suppress("unused") private val app: Application
) {

    private var profileCache: TwitchUser? = null
    private var channelCache: ChannelInfo? = null
    private var streamersCache: List<StreamerItem>? = null
    private var categoriesCache: List<FollowedCategory>? = null
    private val categoryDetailsCache = mutableMapOf<String, CategoryDetails>()

    /** Страница канала: профиль + баннер/соцссылки + онлайн-статус (параллельная загрузка). */
    suspend fun getChannelPage(userId: String): Triple<TwitchUser, ChannelPage, LiveStream?> = coroutineScope {
        val profileDef = async { api.getUserProfile(userId, helix()) ?: error("no profile") }
        val extrasDef = async {
            try { gql.getChannelExtras(userId) } catch (_: Exception) { "" to emptyList() }
        }
        val liveDef = async {
            try { api.getStreamsByUsers(listOf(userId), helix()).firstOrNull() } catch (_: Exception) { null }
        }
        val socialsDef = async {
            try { gql.getSocialMedias(userId) } catch (_: Exception) { emptyList() }
        }
        val followersDef = async {
            try { gql.getFollowersCount(userId) } catch (_: Exception) { -1 }
        }
        val brandDef = async {
            try { gql.getBrandColor(userId) } catch (_: Exception) { "" }
        }

        val profile = profileDef.await()
        val panels = try {
            gql.getChannelPanels(profile.login.lowercase())
        } catch (_: Exception) {
            emptyList()
        }
        val extras = extrasDef.await()
        val live = liveDef.await()
        val socials = socialsDef.await()
        val followers = followersDef.await()
        val brand = brandDef.await()

        Triple(
            profile,
            ChannelPage(
                bannerUrl = extras.first,
                description = profile.description,
                socials = socials,
                panels = panels,
                followersCount = followers,
                brandColor = brand
            ),
            live
        )
    }

    /** Клипы канала для объединённой вкладки. */
    suspend fun getChannelClips(userId: String): List<ChannelClip> = try {
        gql.getClips(userId)
    } catch (_: Exception) {
        emptyList()
    }

    /** Архивы канала для вкладки «Видео». */
    suspend fun getChannelVideos(userId: String): List<ChannelVideo> = try {
        api.getChannelVideos(userId, helix())
    } catch (_: Exception) {
        emptyList()
    }

    /** URL потока VOD — через usher, как лайв, но путь /vod/. */
    suspend fun getVodStreamUrl(videoId: String): String? {
        val token = gql.getVodPlaybackAccessToken(videoId) ?: return null
        val (value, signature) = token
        return TwitchHls.vodMasterUrl(videoId, value, signature)
    }

    /** URL потока клипа — прямая CDN-ссылка, usher не нужен. */
    suspend fun getClipStreamUrl(clipSlug: String): String? =
        gql.getClipPlaybackUrl(clipSlug)

    /** Мои активные подписки: параллельный обход фолоу. */
    suspend fun getMySubscriptions(): List<MySubscription> {
        val me = getProfile()
        val token = helix()
        val clientId = TwitchAuth.OWN_CLIENT_ID

        val follows = try {
            api.getFollowedChannels(me.id, token)
        } catch (_: Exception) {
            return emptyList()
        }

        if (follows.isEmpty()) return emptyList()

        // Параллельная проверка всех каналов
        return coroutineScope {
            follows.map { f ->
                async {
                    try {
                        api.getSubscriptionTo(f.broadcasterId, me.id, token, clientId)
                    } catch (_: Exception) {
                        0 to null
                    }
                }
            }.awaitAll().filter { (code, sub) ->
                if (code == 401) return@coroutineScope emptyList()
                code == 200 && sub != null
            }.map { it.second!! }
        }
    }

    /** Подписан ли я на канал (звёздочка). */
    suspend fun isSubscribedTo(broadcasterId: String): Boolean {
        val me = getProfile()
        val token = helix()
        val clientId = TwitchAuth.OWN_CLIENT_ID
        val (code, sub) = try {
            api.getSubscriptionTo(broadcasterId, me.id, token, clientId)
        } catch (_: Exception) {
            0 to null
        }
        return code == 200 && sub != null
    }

    /** Helix-токен своей заявки. */
    private fun helix(): String = tokenStore.helixToken ?: ("No Helix token")

    suspend fun getProfile(): TwitchUser {
        profileCache?.let { return it }
        return api.getCurrentUser(helix()).also { profileCache = it }
    }

    suspend fun getMyChannel(): ChannelInfo? {
        channelCache?.let { return it }
        val c = api.getChannelInfo(getProfile().id, helix())
        channelCache = c
        return c
    }

    suspend fun getFollowedStreamers(force: Boolean = false): List<StreamerItem> {
        if (!force) streamersCache?.let { return it }
        val me = getProfile()
        val follows = api.getFollowedChannels(me.id, helix())
        val users = api.getUsersByIds(follows.map { it.broadcasterId }, helix())
            .associateBy { it.id }
        val streams = try {
            api.getFollowedStreams(me.id, helix()).associateBy { it.userId }
        } catch (_: Exception) {
            emptyMap()
        }
        val items = follows.mapNotNull { f ->
            val u = users[f.broadcasterId] ?: return@mapNotNull null
            StreamerItem(
                id = u.id,
                login = u.login,
                name = u.displayName,
                avatarUrl = u.avatarUrl,
                isLive = streams.containsKey(u.id),
                stream = streams[u.id]
            )
        }
        streamersCache = items
        return items
    }

    /** Живые категории из GQL (веб-токен внутри query()). */
    suspend fun getFollowedCategories(force: Boolean = false): List<FollowedCategory> {
        if (!force) categoriesCache?.let { return it }
        return gql.followedGames(getProfile().id).also { categoriesCache = it }
    }

    /** Детали категории с кэшем. Фолоу берём из списка зафоловленных категорий. */
    suspend fun getCategoryDetails(gameId: String, force: Boolean = false): CategoryDetails? {
        if (!force) categoryDetailsCache[gameId]?.let { return it }
        val base = gql.getCategoryDetails(gameId) ?: return null
        val followed = try {
            getFollowedCategories().any { it.id == gameId }
        } catch (_: Exception) {
            false
        }
        val d = base.copy(isFollowed = followed)
        categoryDetailsCache[gameId] = d
        return d
    }

    /** Страница стримов по игре. Обогащается аватарами через /users. */
    suspend fun getStreamsPage(
        gameId: String,
        cursor: String? = null,
        first: Int = 20
    ): StreamsPage {
        val page = api.getStreamsByGamePage(gameId, helix(), first, cursor)
        val ids = page.streams.map { it.userId }
        val avatars = try {
            api.getUsersAvatars(ids, helix())
        } catch (_: Exception) {
            emptyMap()
        }
        val items = page.streams.map { s ->
            StreamerItem(
                id = s.userId,
                login = s.userName.lowercase(),
                name = s.userName,
                avatarUrl = avatars[s.userId] ?: "",
                isLive = true,
                stream = s
            )
        }
        return StreamsPage(items, page.cursor, page.hasMore)
    }

    /** Результаты поиска: каналы, категории, стримы. Досортировка по сходству. */
    suspend fun searchAll(term: String): SearchBundle {
        val t = term.trim()
        if (t.length < 2) return SearchBundle.EMPTY
        val channels = try {
            api.searchChannels(t, helix(), first = 10)
        } catch (_: Exception) {
            emptyList()
        }
        val categories = try {
            api.searchCategories(t, helix(), first = 10)
        } catch (_: Exception) {
            emptyList()
        }
        val liveChannels = try {
            api.searchChannels(t, helix(), first = 10, liveOnly = true)
        } catch (_: Exception) {
            emptyList()
        }
        val liveIds = liveChannels.map { it.id }
        val streams = if (liveIds.isEmpty()) emptyList() else try {
            api.getStreamsByUsers(liveIds, helix())
        } catch (_: Exception) {
            emptyList()
        }
        return SearchBundle(
            channels = channels.sortedByDescending { score(it.name, t) },
            categories = categories.sortedByDescending { score(it.name, t) },
            streams = streams.sortedByDescending { score(it.userName, t) }
        )
    }

    /** Скоринг сходства: точное > префикс > подстрока > токены > Левенштейн. */
    private fun score(name: String, term: String): Double {
        val n = name.lowercase()
        val q = term.lowercase()
        return when {
            n == q -> 1.0
            n.startsWith(q) -> 0.85
            n.contains(q) -> 0.7
            q.split(' ').any { it.isNotBlank() && n.contains(it) } -> 0.55
            else -> 1.0 - (levenshtein(n, q).toDouble() / maxOf(n.length, q.length, 1))
        }
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
            }
        }
        return dp[a.length][b.length]
    }

    suspend fun followGame(gameId: String): Boolean {
        val ok = gql.followGame(gameId)
        if (ok) categoryDetailsCache.remove(gameId)
        return ok
    }

    suspend fun unfollowGame(gameId: String): Boolean {
        val ok = gql.unfollowGame(gameId)
        if (ok) categoryDetailsCache.remove(gameId)
        return ok
    }

    suspend fun isFollowing(broadcasterId: String): Boolean =
        api.isFollowing(getProfile().id, broadcasterId, helix())

    fun invalidateStreamers() {
        streamersCache = null
    }

    fun invalidateAll() {
        profileCache = null
        channelCache = null
        streamersCache = null
        categoriesCache = null
        categoryDetailsCache.clear()
    }
}