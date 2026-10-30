package com.teatv.app

import android.app.Application
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.teatv.app.auth.FavoritesStore
import com.teatv.app.auth.NotifyStore
import com.teatv.app.auth.TokenStore
import com.teatv.app.auth.TwitchApi
import com.teatv.app.auth.TwitchAuth
import com.teatv.app.auth.TwitchGql
import com.teatv.app.auth.WebLoginActivity
import com.teatv.app.chat.ChatClient
import com.teatv.app.chat.MessageSplitter
import com.teatv.app.chat.ChatMessage
import com.teatv.app.chat.badges.toMap
import com.teatv.app.chat.emotes.Emote
import com.teatv.app.chat.emotes.EmoteBundles
import com.teatv.app.chat.emotes.EmoteRepository
import com.teatv.app.chat.points.FarmManager
import com.teatv.app.chat.points.PointReward
import com.teatv.app.chat.points.PointsFarmer
import com.teatv.app.chat.points.PointsPubSub
import com.teatv.app.chat.points.PointsRepository
import com.teatv.app.data.ChannelInfo
import com.teatv.app.data.FollowedCategory
import com.teatv.app.data.SettingsStore
import com.teatv.app.data.StreamerItem
import com.teatv.app.data.TwitchUser
import com.teatv.app.player.QualityOption
import com.teatv.app.player.TwitchHls
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.teatv.app.data.CategoryDetails
import com.teatv.app.data.SearchBundle
import com.teatv.app.chat.points.ChannelPointsState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import com.teatv.app.data.TwitchRepository
import com.teatv.app.data.ChannelPage
import com.teatv.app.data.ChannelVideo
import com.teatv.app.data.LiveStream
import com.teatv.app.data.MySubscription
import com.teatv.app.data.ChannelClip
import com.teatv.app.player.PlayerHolder
import kotlinx.coroutines.Dispatchers

import com.teatv.app.settings.ClientSettingsStore
import com.teatv.app.settings.DefaultReplySettings
import com.teatv.app.settings.MentionNotificationHelper
import com.teatv.app.settings.ReplySettings
import com.teatv.app.settings.ReplySettingsStore
import com.teatv.app.ui.theme.TeaColors

import com.teatv.app.mod.ChannelModesState
import com.teatv.app.mod.ChatterInfo
import com.teatv.app.mod.ModLogItem
import com.teatv.app.mod.ModRepository
import com.teatv.app.mod.PinnedMessage
import com.teatv.app.mod.PredictionInfo
import com.teatv.app.mod.PredictionOption
import com.teatv.app.mod.RewardRequestItem
import com.teatv.app.mod.UnbanRequestItem
import com.teatv.app.screens.stream.mod.ModSliderAction

enum class Screen { Auth, Home, Stream, Category, Account, Subscriptions, Channel, Vod, Settings, AccountSettings, ClientSettings, EmoteOrderSettings }

enum class MainTab { View, Search, Tools }

enum class ViewMode { Normal, AudioOnly }

enum class AudioPolicy { Headphones, Always, Never }

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val clientSettingsStore = ClientSettingsStore(app)
    val replySettingsStore = ReplySettingsStore(app)
    var replySettings by mutableStateOf(replySettingsStore.load())
    var isStreamMinimized by mutableStateOf(false)
        private set

    var modSwipeRequiresSelection by mutableStateOf(clientSettingsStore.modSwipeRequiresSelection)
        private set
    var favoriteEmotes by mutableStateOf<List<Emote>>(emptyList())
        private set
    var emoteCategoryOrder by mutableStateOf<List<String>>(emptyList())
        private set

    private val api = TwitchApi(client = PointsRepository.httpClient())
    private val tokenStore = TokenStore(app)
    val gql = TwitchGql(tokenStore, app)
    val modRepository = ModRepository(api, gql, tokenStore)
    private val repository = TwitchRepository(api, tokenStore, gql, app)

    var isCurrentStreamModerator by mutableStateOf(false)
        private set

    val isCurrentStreamOwner: Boolean
        get() {
            val t = currentStream ?: return false
            val u = user
            if (u != null) {
                if (t.id == u.id || t.login.equals(u.login, ignoreCase = true)) return true
            }
            val storedUid = tokenStore.userId
            val storedLogin = tokenStore.userLogin
            if (!storedUid.isNullOrBlank() && t.id == storedUid) return true
            if (!storedLogin.isNullOrBlank() && t.login.equals(storedLogin, ignoreCase = true)) return true
            return false
        }
    var selectedChatterInfo by mutableStateOf<ChatterInfo?>(null)
        private set
    var selectedChatMessage by mutableStateOf<ChatMessage?>(null)
        private set
    var pinnedMessage by mutableStateOf<PinnedMessage?>(null)
        private set
    var channelModes by mutableStateOf(ChannelModesState())
        private set
    val modLogs = mutableStateListOf<ModLogItem>()
    val rewardRequests = mutableStateListOf<RewardRequestItem>()
    var activePrediction by mutableStateOf<PredictionInfo?>(null)
        private set
    val unbanRequests = mutableStateListOf<UnbanRequestItem>()
    private val settingsStore = SettingsStore(app)
    private val notifyStore = NotifyStore(app)
    var favorites by mutableStateOf<List<StreamerItem>>(emptyList())
        private set

    var viewMode by mutableStateOf(ViewMode.Normal)
        private set
    var audioPolicy by mutableStateOf(
        runCatching { AudioPolicy.valueOf(settingsStore.audioPolicy()) }
            .getOrDefault(AudioPolicy.Headphones)
    )
        private set

    var videoBufferSizeSeconds by mutableStateOf(clientSettingsStore.videoBufferSizeSeconds)
        private set

    var isModModeActive by mutableStateOf(clientSettingsStore.modModeEnabled)
        private set

    var isSplitMessagesActive by mutableStateOf(clientSettingsStore.splitLongMessagesEnabled)
        private set

    fun setVideoBufferSize(seconds: Int) {
        videoBufferSizeSeconds = seconds
        clientSettingsStore.videoBufferSizeSeconds = seconds
    }

    fun setModModeEnabled(enabled: Boolean) {
        isModModeActive = enabled
        clientSettingsStore.modModeEnabled = enabled
    }

    fun setSplitMessagesEnabled(enabled: Boolean) {
        isSplitMessagesActive = enabled
        clientSettingsStore.splitLongMessagesEnabled = enabled
    }

    var isKillBackgroundProcessesActive by mutableStateOf(clientSettingsStore.killBackgroundProcesses)
        private set

    fun setKillBackgroundProcesses(enabled: Boolean) {
        isKillBackgroundProcessesActive = enabled
        clientSettingsStore.killBackgroundProcesses = enabled
    }

    val chatMessages = mutableStateListOf<ChatMessage>()
    private val seenIds = hashSetOf<String>()
    private val chatWindowLimit = 200
    private var chatClient: ChatClient? = null
    private val streamChatCache = mutableMapOf<String, MutableList<ChatMessage>>()

    var selectedViewerInfo by mutableStateOf<ChatterInfo?>(null)
    var isLoadingViewerInfo by mutableStateOf(false)

    fun openViewerProfile(userLogin: String, userId: String? = null) {
        val ch = currentStream ?: return
        viewModelScope.launch {
            isLoadingViewerInfo = true
            selectedViewerInfo = ChatterInfo(
                userId = userId.orEmpty(),
                userLogin = userLogin,
                displayName = userLogin
            )
            val info = modRepository.fetchChatterInfo(
                channelId = ch.id,
                channelLogin = ch.login,
                userLogin = userLogin,
                userId = userId,
                myUserId = user?.id,
                myUserLogin = user?.login
            )
            selectedViewerInfo = info
            isLoadingViewerInfo = false
        }
    }

    fun closeViewerProfile() {
        selectedViewerInfo = null
        isLoadingViewerInfo = false
    }

    var emotes by mutableStateOf(EmoteBundles())
        private set
    private var globalTwitchEmotesCache: List<Emote>? = null
    private var globalBadgesCache: List<com.teatv.app.chat.badges.BadgeRef>? = null
    var isHomeRefreshing by mutableStateOf(false)
        private set
    var isChannelRefreshing by mutableStateOf(false)
        private set
    var badgeMap by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    var viewers by mutableStateOf<List<String>?>(null)
        private set
    var targetNotified by mutableStateOf(false)
        private set
    var qualities by mutableStateOf<List<QualityOption>>(emptyList())
        private set
    var audioOnlyUrl by mutableStateOf<String?>(null)
        private set
    var sourceUrl by mutableStateOf<String?>(null)
        private set
    var playbackUrl by mutableStateOf<String?>(null)
        private set
    var selectedQualityLabel by mutableStateOf<String?>(null)
        private set
    var playbackRefreshTrigger by mutableStateOf(0L)
        private set
    private val favoritesStore = FavoritesStore(app)
    var screen by mutableStateOf(Screen.Auth)
        private set
    var user by mutableStateOf<TwitchUser?>(null)
        private set
    var streamers by mutableStateOf<List<StreamerItem>>(emptyList())
        private set
    var categories by mutableStateOf<List<FollowedCategory>>(emptyList())
        private set
    var currentCategory by mutableStateOf<FollowedCategory?>(null)
        private set
    var currentVodVideo by mutableStateOf<ChannelVideo?>(null)
        private set
    var currentVodClip by mutableStateOf<ChannelClip?>(null)
        private set
    var vodPlaybackUrl by mutableStateOf<String?>(null)
        private set
    var vodChatMessages by mutableStateOf<List<ChatMessage>>(emptyList())
        private set
    private var lastVodChatOffset by mutableStateOf(-1)
        private set
    var vodTitle by mutableStateOf<String>("")
        private set
    var categoryDetails by mutableStateOf<CategoryDetails?>(null)
        private set
    var categoryStreams by mutableStateOf<List<StreamerItem>>(emptyList())
        private set
    var categoryLoading by mutableStateOf(false)
        private set
    var categoryLoadingMore by mutableStateOf(false)
        private set
    var categoryCursor by mutableStateOf<String?>(null)
        private set
    var categoryHasMore by mutableStateOf(true)
        private set
    private var categoryLoadJob: Job? = null
    var points by mutableStateOf<ChannelPointsState?>(null)
        private set
    var channel by mutableStateOf<ChannelInfo?>(null)
        private set
    var channelPageUser by mutableStateOf<TwitchUser?>(null)
        private set
    var channelPageInfo by mutableStateOf<ChannelPage?>(null)
        private set
    var channelPageLive by mutableStateOf<LiveStream?>(null)
        private set
    var channelVideos by mutableStateOf<List<ChannelVideo>>(emptyList())
        private set
    var channelIsFollowed by mutableStateOf<Boolean?>(null)
        private set
    var channelIsSubscribed by mutableStateOf<Boolean?>(null)
        private set
    var channelClips by mutableStateOf<List<ChannelClip>>(emptyList())
        private set
    var mySubscriptions by mutableStateOf<List<MySubscription>>(emptyList())
        private set
    var currentChannelId by mutableStateOf<String?>(null)
        private set
    var channelNotifyEnabled by mutableStateOf(false)
        private set
    var channelIsOwn by mutableStateOf(false)
        private set
    var tab by mutableStateOf(MainTab.View)
        private set
    var currentStream by mutableStateOf<StreamerItem?>(null)
        private set
    var targetFollowed by mutableStateOf<Boolean?>(null)
        private set
    private var lastFollowActionTime: Long = 0
    private val followDebounceMs = 3000L // 3 секунды
    var botActive by mutableStateOf(false)
        private set
    var searchQuery by mutableStateOf("")
        private set
    var searchResults by mutableStateOf(SearchBundle.EMPTY)
        private set
    var searchLoading by mutableStateOf(false)
        private set
    var searchJob: Job? = null
        private set

    init {
        TeaColors.applyFromStore(clientSettingsStore)
        api.clientId = TwitchAuth.OWN_CLIENT_ID
        favoriteEmotes = clientSettingsStore.getFavoriteEmotes()
        emoteCategoryOrder = clientSettingsStore.getEmoteCategoryOrder()
        emotes = emotes.copy(favorite = favoriteEmotes)
        if (tokenStore.isLoggedIn) {
            val savedId = tokenStore.userId
            if (!savedId.isNullOrBlank()) {
                user = TwitchUser(
                    id = savedId,
                    login = tokenStore.userLogin.orEmpty(),
                    displayName = tokenStore.userDisplayName ?: tokenStore.userLogin.orEmpty(),
                    avatarUrl = ""
                )
            }
            screen = Screen.Home
            loadHomeData()
            viewModelScope.launch {
                val token = tokenStore.accessToken ?: return@launch
                FarmManager.restore(getApplication(), token)
                // Временная проверка scopes токена
            }
        }
    }

    fun selectTab(t: MainTab) {
        tab = t
    }

    fun onSearchQuery(q: String) {
        searchQuery = q
        searchJob?.cancel()
        if (q.trim().length < 2) {
            searchResults = SearchBundle.EMPTY
            searchLoading = false
            return
        }
        searchLoading = true
        searchJob = viewModelScope.launch {
            delay(400) // debounce
            try {
                searchResults = repository.searchAll(q)
            } catch (_: Exception) {
                searchResults = SearchBundle.EMPTY
            } finally {
                searchLoading = false
            }
        }
    }

    /** Возврат из фона: подобрать веб-вход или освежить баланс. */
    fun onForeground() {
        replySettings = replySettingsStore.load()
        if (screen == Screen.Auth && tokenStore.isLoggedIn) {
            screen = Screen.Home
            loadHomeData()
            return
        }
        if (currentStream != null) refreshPoints()
    }

    /** Вкл/выкл бот-режим текущего канала. */
    fun setBotMode(enabled: Boolean) {
        val t = currentStream ?: return
        viewModelScope.launch {
            val token = tokenStore.accessToken ?: return@launch
            if (enabled) FarmManager.requestIgnoreOptimizations(getApplication())
            val watching = currentStream?.id == t.id
            FarmManager.setBotEnabled(
                context = getApplication(),
                channelId = t.id,
                login = t.login,
                token = token,
                enabled = enabled,
                startSessionNow = !watching
            )
            botActive = enabled
        }
    }

    fun minimizeStream() {
        isStreamMinimized = true
        screen = Screen.Home
    }

    fun maximizeStream() {
        isStreamMinimized = false
        screen = Screen.Stream
    }

    fun openStream(item: StreamerItem) {
        isStreamMinimized = false
        currentStream = item
        targetFollowed = null
        selectedChatterInfo = null
        selectedChatMessage = null
        pinnedMessage = null

        val isOwner = (user != null && (item.id == user?.id || item.login.equals(user?.login, ignoreCase = true))) ||
                (!tokenStore.userId.isNullOrBlank() && item.id == tokenStore.userId) ||
                (!tokenStore.userLogin.isNullOrBlank() && item.login.equals(tokenStore.userLogin, ignoreCase = true))

        isCurrentStreamModerator = isOwner

        viewModelScope.launch {
            if (!isOwner) {
                val myId = user?.id ?: tokenStore.userId ?: try { repository.getProfile().id } catch (_: Exception) { null }
                val myLogin = user?.login ?: tokenStore.userLogin ?: try { repository.getProfile().login } catch (_: Exception) { null }
                isCurrentStreamModerator = modRepository.isModerator(
                    channelId = item.id,
                    channelLogin = item.login,
                    myUserId = myId,
                    myUserLogin = myLogin
                )
            }
            if (isCurrentStreamModerator) {
                loadModData(item.id, item.login)
            } else {
                try {
                    channelModes = modRepository.fetchChannelModes(item.id, user?.id ?: tokenStore.userId)
                } catch (_: Exception) {}
            }
        }
        targetNotified = notifyStore.isNotified(item.id)
        screen = Screen.Stream
        botActive = FarmManager.isBot(item.id)
        FarmManager.setViewing(item.id)

        viewModelScope.launch {
            targetFollowed = try {
                repository.isFollowing(item.id)
            } catch (_: Exception) {
                null
            }
        }

        viewModelScope.launch {
            val token = tokenStore.accessToken ?: return@launch
            PointsFarmer.startSession(
                channelId = item.id,
                channelLogin = item.login,
                token = token,
                onClaimed = {
                    Toast.makeText(getApplication(), "Бонус канала собран", Toast.LENGTH_SHORT).show()
                },
                onBalance = { b ->
                    if (!isCurrentStreamOwner) {
                        points = points?.copy(balance = b)
                    }
                },
                onRaided = {
                    Toast.makeText(getApplication(), "Рейд принят: +баллы", Toast.LENGTH_SHORT).show()
                },
                onOffline = { FarmManager.sessionDied() }
            )
            PointsPubSub.start(
                client = PointsRepository.httpClient(),
                token = token,
                channelId = item.id,
                onMoment = { id ->
                    PointsFarmer.claimMoment(id, token) {
                        Toast.makeText(getApplication(), "Момент собран", Toast.LENGTH_SHORT).show()
                    }
                },
                onDelta = { amount, reason ->
                    if (amount > 0) {
                        Toast.makeText(getApplication(), "+$amount баллов: $reason", Toast.LENGTH_SHORT).show()
                    }
                },
                onRedeem = { id, sender, title, icon, userInput ->
                    val msgId = if (id.isNotBlank()) "redeem-$id" else "redeem-${sender.hashCode()}-${title.hashCode()}-${System.currentTimeMillis() / 2000}"
                    val msg = ChatMessage(
                        id = msgId,
                        sender = sender,
                        color = "",
                        text = userInput.orEmpty(),
                        badges = emptyList(),
                        replyToId = null,
                        replyToName = null,
                        replyToBody = null,
                        redemptionTitle = title,
                        redemptionIcon = icon
                    )
                    insertMessage(msg)
                    refreshPoints(force = true)
                },
                onPinned = { pin ->
                    pinnedMessage = pin
                }
            )
        }

        loadPlayback(item)
        loadEmotes(item)
        startChat(item)
        loadBadges(item)
        refreshPoints()
        viewModelScope.launch {
            try {
                val initialPin = modRepository.fetchPinnedChatMessage(item.login)
                if (initialPin != null) {
                    pinnedMessage = initialPin
                }
            } catch (_: Exception) {}
        }
    }

    fun openVod(video: ChannelVideo) {
        if (currentStream != null) {
            getApplication<Application>().sendBroadcast(
                Intent(TeaAudioService.ACTION_DISMISS).setPackage(getApplication<Application>().packageName)
            )
            closeStream()
        }
        currentVodVideo = video
        currentVodClip = null
        vodTitle = video.title
        vodChatMessages = emptyList()
        lastVodChatOffset = -1
        screen = Screen.Vod
        viewModelScope.launch {
            vodPlaybackUrl = repository.getVodStreamUrl(video.id)
        }
    }

    fun loadVodChatAt(videoId: String, offsetSeconds: Int) {
        if (lastVodChatOffset >= 0 && kotlin.math.abs(offsetSeconds - lastVodChatOffset) < 15) return

        val isJump = lastVodChatOffset < 0 || kotlin.math.abs(offsetSeconds - lastVodChatOffset) > 45
        lastVodChatOffset = offsetSeconds
        viewModelScope.launch {
            try {
                val (messages, _) = gql.getVodChatComments(videoId, offsetSeconds)
                if (isJump) {
                    vodChatMessages = messages.sortedBy { it.offsetSeconds }
                } else {
                    val existingIds = vodChatMessages.map { it.id }.toSet()
                    val newUnique = messages.filter { it.id !in existingIds }
                    vodChatMessages = (vodChatMessages + newUnique).sortedBy { it.offsetSeconds }.takeLast(250)
                }
            } catch (e: Exception) {
                Log.e("TeaTV-VM", "vod chat load failed", e)
            }
        }
    }

    fun clearVodChat() {
        vodChatMessages = emptyList()
        lastVodChatOffset = -1
    }

    /** Принудительное обновление главной страницы */
    fun refreshHome() {
        if (isHomeRefreshing) return
        isHomeRefreshing = true
        viewModelScope.launch {
            try {
                streamers = repository.getFollowedStreamers(force = true)
                // Если есть другие данные для обновления — добавь их здесь:
                // categories = repository.getFollowedCategories(force = true)
                // favorites = ...
            } catch (e: Exception) {
                Log.e("TeaTV-VM", "Home refresh failed", e)
            } finally {
                isHomeRefreshing = false
            }
        }
    }

    fun refreshChannel() {
        val id = currentChannelId ?: return
        if (isChannelRefreshing) return
        isChannelRefreshing = true
        viewModelScope.launch {
            try {
                // Вызываем твой существующий метод открытия/загрузки канала
                openChannel(id)
            } catch (e: Exception) {
                android.util.Log.e("TeaTV-VM", "Channel refresh failed", e)
            } finally {
                isChannelRefreshing = false
            }
        }
    }

    fun openClip(clip: ChannelClip) {
        if (currentStream != null) {
            getApplication<Application>().sendBroadcast(
                Intent(TeaAudioService.ACTION_DISMISS).setPackage(getApplication<Application>().packageName)
            )
            closeStream()
        }
        currentVodClip = clip
        currentVodVideo = null
        vodTitle = clip.title
        vodChatMessages = emptyList()
        lastVodChatOffset = -1
        screen = Screen.Vod

        val slug = runCatching {
            android.net.Uri.parse(clip.url).lastPathSegment
        }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: clip.id

        viewModelScope.launch {
            vodPlaybackUrl = repository.getClipStreamUrl(slug)
        }
    }

    fun closeVod() {
        currentVodVideo = null
        currentVodClip = null
        vodPlaybackUrl = null
        vodTitle = ""
        clearVodChat()
        PlayerHolder.player?.stop()
        PlayerHolder.player?.clearMediaItems()
        screen = if (currentChannelId != null) Screen.Channel else Screen.Home
    }

    fun openCategory(c: FollowedCategory) {
        currentCategory = c
        categoryDetails = null
        categoryStreams = emptyList()
        categoryCursor = null
        categoryHasMore = true
        categoryLoading = true
        screen = Screen.Category

        categoryLoadJob?.cancel()
        categoryLoadJob = viewModelScope.launch {
            // Параллельно: детали + первая страница стримов
            val detailsDeferred = async { repository.getCategoryDetails(c.id) }
            val pageDeferred = async { repository.getStreamsPage(c.id, cursor = null, first = 20) }
            categoryDetails = detailsDeferred.await()
            val page = runCatching { pageDeferred.await() }.getOrNull()
            if (page != null) {
                categoryStreams = page.streams
                categoryCursor = page.cursor
                categoryHasMore = page.hasMore
            }
            categoryLoading = false
        }
    }

    fun closeCategory() {
        categoryLoadJob?.cancel()
        categoryLoadJob = null
        currentCategory = null
        categoryDetails = null
        categoryStreams = emptyList()
        categoryCursor = null
        categoryHasMore = true
        categoryLoading = false
        categoryLoadingMore = false
        screen = Screen.Home
    }

    fun openAccount() {
        screen = Screen.Account
    }

    fun backToHome() {
        screen = Screen.Home
    }

    fun backToAccount() {
        screen = Screen.Account
    }

    fun openSettings() {
        screen = Screen.Settings
    }

    fun openAccountSettings() {
        screen = Screen.AccountSettings
    }

    fun openClientSettings() {
        screen = Screen.ClientSettings
    }

    fun backToSettings() {
        screen = Screen.Settings
    }



    /** Выделение/снятие выделения сообщения и чаттера в чате. */
    fun toggleSelectChatMessage(m: ChatMessage) {
        if (selectedChatMessage?.id == m.id) {
            selectedChatMessage = null
            selectedChatterInfo = null
        } else {
            selectedChatMessage = m
            val ch = currentStream ?: return
            viewModelScope.launch {

                val isFollowed = targetFollowed ?: try { repository.isFollowing(ch.id) } catch (_: Exception) { null }

                selectedChatterInfo = modRepository.fetchChatterInfo(

                    userId = m.senderId,

                    userLogin = m.senderLogin ?: m.sender,

                    channelId = ch.id,

                    channelLogin = ch.login,

                    myUserId = user?.id,

                    myUserLogin = user?.login,

                    isChannelFollowed = isFollowed

                )

            }
        }
    }

    fun clearSelectedChatter() {
        selectedChatMessage = null
        selectedChatterInfo = null
    }

    /** Фоновая загрузка модераторских данных канала (награды, логи, режимы, апелляции, прогноз). */
    fun loadModData(channelId: String, channelLogin: String) {
        val me = user ?: return
        viewModelScope.launch {
            try {
                channelModes = modRepository.fetchChannelModes(channelId, me.id)
            } catch (_: Exception) {}
        }
        viewModelScope.launch {
            try {
                val logs = modRepository.fetchModLogs(channelId, me.id)
                modLogs.clear()
                modLogs.addAll(logs)
            } catch (_: Exception) {}
        }
        viewModelScope.launch {
            try {
                val reqs = modRepository.fetchRewardRequests(channelId, me.id)
                rewardRequests.clear()
                rewardRequests.addAll(reqs)
            } catch (_: Exception) {}
        }
        viewModelScope.launch {
            try {
                val unbans = modRepository.fetchUnbanRequests(channelId, me.id)
                unbanRequests.clear()
                unbanRequests.addAll(unbans)
            } catch (_: Exception) {}
        }
        viewModelScope.launch {
            try {
                activePrediction = modRepository.fetchActivePrediction(channelId, me.id)
            } catch (_: Exception) {}
        }
    }

    /** Исполнение модераторского свайп-действия (7TV) через Helix. */
    fun executeModSliderAction(m: ChatMessage, action: ModSliderAction) {
        val ch = currentStream ?: return
        val me = user ?: return
        viewModelScope.launch {
            val ok = modRepository.executeSliderAction(
                channelId = ch.id,
                channelLogin = ch.login,
                myUserId = me.id,
                targetUser = m.sender,
                targetUserId = m.senderId,
                messageId = m.id,
                action = action,
                targetUserLogin = m.senderLogin
            )
            if (ok) {
                // Мгновенно удаляем сообщения чаттера локально
                if (action == ModSliderAction.DELETE) {
                    chatMessages.removeAll { it.id == m.id }
                } else {
                    chatMessages.removeAll { it.sender.equals(m.sender, ignoreCase = true) }
                }
                modLogs.add(
                    0,
                    ModLogItem(
                        id = "log-${System.currentTimeMillis()}",
                        timestamp = System.currentTimeMillis(),
                        modName = me.displayName,
                        targetName = m.sender,
                        action = action.title,
                        details = m.text.take(60)
                    )
                )
                Toast.makeText(getApplication(), "${action.iconEmoji} ${action.title}: ${m.sender}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(getApplication(), "Ошибка применения: ${action.title}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** Разбан пользователя через Helix. */
    fun unbanChatter(targetUser: String, targetUserId: String? = null, targetUserLogin: String? = null) {
        val ch = currentStream ?: return
        val me = user ?: return
        viewModelScope.launch {
            val ok = modRepository.unbanUser(
                channelId = ch.id,
                myUserId = me.id,
                targetUser = targetUser,
                targetUserId = targetUserId ?: selectedChatterInfo?.userId,
                targetUserLogin = targetUserLogin ?: selectedChatterInfo?.userLogin
            )
            if (ok) {
                modLogs.add(
                    0,
                    ModLogItem(
                        id = "log-${System.currentTimeMillis()}",
                        timestamp = System.currentTimeMillis(),
                        modName = me.displayName,
                        targetName = targetUser,
                        action = "Разбан"
                    )
                )
                Toast.makeText(getApplication(), "Пользователь $targetUser разбанен", Toast.LENGTH_SHORT).show()
                selectedChatterInfo = selectedChatterInfo?.copy(isBanned = false, isTimedOut = false)
            } else {
                Toast.makeText(getApplication(), "Ошибка разбана $targetUser", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun toggleShieldMode() {
        val ch = currentStream ?: return
        val me = user ?: return
        val newShield = !channelModes.shieldMode
        channelModes = channelModes.copy(shieldMode = newShield)
        viewModelScope.launch {
            val ok = modRepository.setShieldMode(ch.id, me.id, newShield)
            if (!ok) channelModes = channelModes.copy(shieldMode = !newShield)
        }
    }

    fun setSubOnlyMode(enabled: Boolean) {
        val ch = currentStream ?: return
        val me = user ?: return
        channelModes = channelModes.copy(subOnly = enabled)
        viewModelScope.launch {
            val patch = org.json.JSONObject().apply { put("subscriber_mode", enabled) }
            val ok = modRepository.updateChatSettings(ch.id, me.id, patch)
            if (!ok) channelModes = channelModes.copy(subOnly = !enabled)
        }
    }

    fun setEmoteOnlyMode(enabled: Boolean) {
        val ch = currentStream ?: return
        val me = user ?: return
        channelModes = channelModes.copy(emoteOnly = enabled)
        viewModelScope.launch {
            val patch = org.json.JSONObject().apply { put("emote_mode", enabled) }
            val ok = modRepository.updateChatSettings(ch.id, me.id, patch)
            if (!ok) channelModes = channelModes.copy(emoteOnly = !enabled)
        }
    }

    fun setFollowersDuration(minutes: Int) {
        val ch = currentStream ?: return
        val me = user ?: return
        val old = channelModes.followersDurationMinutes
        channelModes = channelModes.copy(followersDurationMinutes = minutes)
        viewModelScope.launch {
            val patch = org.json.JSONObject().apply {
                put("follower_mode", minutes >= 0)
                if (minutes >= 0) put("follower_mode_duration", minutes)
            }
            val ok = modRepository.updateChatSettings(ch.id, me.id, patch)
            if (!ok) channelModes = channelModes.copy(followersDurationMinutes = old)
        }
    }

    fun setSlowMode(seconds: Int) {
        val ch = currentStream ?: return
        val me = user ?: return
        val old = channelModes.slowModeSeconds
        channelModes = channelModes.copy(slowModeSeconds = seconds)
        viewModelScope.launch {
            val patch = org.json.JSONObject().apply {
                put("slow_mode", seconds > 0)
                if (seconds > 0) put("slow_mode_wait_time", seconds)
            }
            val ok = modRepository.updateChatSettings(ch.id, me.id, patch)
            if (!ok) channelModes = channelModes.copy(slowModeSeconds = old)
        }
    }

    fun sendAnnouncement(text: String) {
        val ch = currentStream ?: return
        val me = user ?: return
        viewModelScope.launch {
            val ok = modRepository.sendAnnouncement(ch.id, me.id, text)
            if (ok) {
                Toast.makeText(getApplication(), "Оповещение отправлено", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(getApplication(), "Ошибка отправки оповещения", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun pinChatMessage(m: ChatMessage) {
        val ch = currentStream ?: return
        viewModelScope.launch {
            val pinned = modRepository.pinChatMessage(ch.id, m.id)
            if (pinned != null) {
                pinnedMessage = pinned
                Toast.makeText(getApplication(), "Сообщение закреплено на канале", Toast.LENGTH_SHORT).show()
            } else {
                pinnedMessage = PinnedMessage(
                    id = m.id,
                    messageId = m.id,
                    sender = m.sender,
                    senderColor = m.color,
                    text = m.text,
                    pinnedBy = user?.displayName ?: "Модератор"
                )
                Toast.makeText(getApplication(), "Сообщение закреплено", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun unpinChatMessage() {
        val pin = pinnedMessage ?: return
        viewModelScope.launch {
            val ok = modRepository.unpinChatMessage(pin.id)
            pinnedMessage = null
            if (ok) {
                Toast.makeText(getApplication(), "Сообщение откреплено на канале", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(getApplication(), "Сообщение откреплено", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun hidePinnedMessageForMe() {
        // Локальное скрытие закреплённого сообщения для себя
    }

    fun fulfillReward(item: RewardRequestItem) {
        val ch = currentStream ?: return
        rewardRequests.remove(item)
        viewModelScope.launch {
            modRepository.fulfillReward(ch.id, item.rewardId, item.id)
        }
        Toast.makeText(getApplication(), "Награда выполнена: ${item.userName}", Toast.LENGTH_SHORT).show()
    }

    fun rejectReward(item: RewardRequestItem) {
        val ch = currentStream ?: return
        rewardRequests.remove(item)
        viewModelScope.launch {
            modRepository.rejectReward(ch.id, item.rewardId, item.id)
        }
        Toast.makeText(getApplication(), "Награда отклонена: ${item.userName}", Toast.LENGTH_SHORT).show()
    }

    fun fulfillAllRewards() {
        val ch = currentStream ?: return
        val items = rewardRequests.toList()
        rewardRequests.clear()
        viewModelScope.launch {
            for (item in items) {
                modRepository.fulfillReward(ch.id, item.rewardId, item.id)
            }
        }
        Toast.makeText(getApplication(), "Выполнено наград: ${items.size}", Toast.LENGTH_SHORT).show()
    }

    fun rejectAllRewards() {
        val ch = currentStream ?: return
        val items = rewardRequests.toList()
        rewardRequests.clear()
        viewModelScope.launch {
            for (item in items) {
                modRepository.rejectReward(ch.id, item.rewardId, item.id)
            }
        }
        Toast.makeText(getApplication(), "Отклонено наград: ${items.size}", Toast.LENGTH_SHORT).show()
    }

    fun createPrediction(title: String, options: List<String>, duration: Int) {
        activePrediction = PredictionInfo(
            id = "pred-${System.currentTimeMillis()}",
            title = title,
            status = "ACTIVE",
            options = options.mapIndexed { idx, optTitle ->
                PredictionOption(id = "opt-$idx", title = optTitle)
            },
            durationSeconds = duration
        )
        Toast.makeText(getApplication(), "Прогноз запущен: $title", Toast.LENGTH_SHORT).show()
    }

    fun resolvePrediction(winningOptionId: String) {
        activePrediction = activePrediction?.copy(
            status = "RESOLVED",
            winningOptionId = winningOptionId
        )
        Toast.makeText(getApplication(), "Прогноз завершён!", Toast.LENGTH_SHORT).show()
    }

    fun cancelPrediction() {
        activePrediction = activePrediction?.copy(status = "CANCELED")
        Toast.makeText(getApplication(), "Прогноз отменён (баллы возвращены)", Toast.LENGTH_SHORT).show()
    }

    fun approveUnban(item: UnbanRequestItem) {
        val ch = currentStream ?: return
        val me = user ?: return
        unbanRequests.remove(item)
        viewModelScope.launch {
            modRepository.resolveUnbanRequest(ch.id, me.id, item.id, approved = true)
        }
        Toast.makeText(getApplication(), "Разбан одобрен: ${item.userName}", Toast.LENGTH_SHORT).show()
    }

    fun denyUnban(item: UnbanRequestItem) {
        val ch = currentStream ?: return
        val me = user ?: return
        unbanRequests.remove(item)
        viewModelScope.launch {
            modRepository.resolveUnbanRequest(ch.id, me.id, item.id, approved = false)
        }
        Toast.makeText(getApplication(), "Разбан отклонён: ${item.userName}", Toast.LENGTH_SHORT).show()
    }

    fun openSubscriptions() {
        screen = Screen.Subscriptions
        viewModelScope.launch {
            mySubscriptions = repository.getMySubscriptions()
        }
    }

    fun openChannel(userId: String) {
        currentChannelId = userId
        channelPageUser = null
        channelPageInfo = null
        channelPageLive = null
        channelVideos = emptyList()
        channelClips = emptyList()
        channelIsFollowed = null
        channelIsSubscribed = null
        screen = Screen.Channel
        viewModelScope.launch {
            coroutineScope {
                val meDeferred = async { repository.getProfile() }
                val pageDeferred = async { repository.getChannelPage(userId) }
                val videosDeferred = async { repository.getChannelVideos(userId) }
                val clipsDeferred = async { repository.getChannelClips(userId) }
                val followDeferred = async { repository.isFollowing(userId) }
                val subDeferred = async { repository.isSubscribedTo(userId) }

                val (profile, page, live) = pageDeferred.await()
                val me = meDeferred.await()
                val isOwn = profile.id == me.id

                channelPageUser = profile
                channelPageInfo = page
                channelPageLive = live
                channelIsOwn = isOwn
                channelVideos = videosDeferred.await()
                channelClips = clipsDeferred.await()
                channelIsFollowed = if (isOwn) null else followDeferred.await()
                channelIsSubscribed = if (isOwn) null else subDeferred.await()
                channelNotifyEnabled = NotifyStore(getApplication()).isNotified(userId)
            }
        }
    }

    /** Фолоу/анфолоу через Persisted Query (для страницы канала). */
    fun toggleChannelFollow() {
        val id = currentChannelId ?: return

        // Debounce
        val now = System.currentTimeMillis()
        if (now - lastFollowActionTime < followDebounceMs) {
            Log.d("TeaTV-VM", "Channel follow action debounced")
            return
        }
        lastFollowActionTime = now

        val current = channelIsFollowed ?: return
        viewModelScope.launch {
            try {
                val variables = org.json.JSONObject().apply {
                    put("input", org.json.JSONObject().apply {
                        put("targetID", id)
                    })
                }

                val opName = if (current) "FollowButton_UnfollowUser" else "FollowButton_FollowUser"
                val response = gql.executePersistedMutation(opName, variables)

                if (response.contains("\"errors\"")) {
                    Log.w("TeaTV-GQL", "Channel mutation rejected: ${response.take(300)}")
                    channelIsFollowed = current // Откатываем состояние
                    Toast.makeText(getApplication(), "Twitch отклонил действие", Toast.LENGTH_SHORT).show()
                } else {
                    channelIsFollowed = !current
                }
            } catch (e: Exception) {
                Log.e("TeaTV-GQL", "Channel mutation failed", e)
                channelIsFollowed = current // Откатываем состояние
                Toast.makeText(getApplication(), "Ошибка сети", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun toggleChannelNotify() {
        val id = currentChannelId ?: return
        val next = !channelNotifyEnabled
        NotifyStore(getApplication()).setNotified(id, next)
        channelNotifyEnabled = next
    }

    fun toggleChannelSubscription() {
        // Заглушка: подписка требует платёжной системы, пока просто логируем.
        Log.i("TeaTV-VM", "subscription toggle not implemented")
    }

    fun loadMoreCategoryStreams() {
        val gameId = currentCategory?.id ?: return
        if (categoryLoadingMore || !categoryHasMore) return
        categoryLoadingMore = true
        viewModelScope.launch {
            try {
                val page = repository.getStreamsPage(gameId, cursor = categoryCursor, first = 20)
                // Исключаем дубликаты по id
                val existingIds = categoryStreams.map { it.id }.toSet()
                val newItems = page.streams.filter { it.id !in existingIds }
                categoryStreams = categoryStreams + newItems
                categoryCursor = page.cursor
                categoryHasMore = page.hasMore
            } catch (_: Exception) {
            } finally {
                categoryLoadingMore = false
            }
        }
    }

    fun toggleCategoryFollow() {
        val gameId = currentCategory?.id ?: return
        val current = categoryDetails ?: return
        val next = !current.isFollowed
        // Оптимистичное обновление UI
        categoryDetails = current.copy(isFollowed = next)
        viewModelScope.launch {
            val ok = if (next) repository.followGame(gameId) else repository.unfollowGame(gameId)
            if (!ok) {
                // Откат
                categoryDetails = current
            } else {
                // Освежить список категорий на главной
                repository.invalidateAll()
                launch {
                    categories = try {
                        repository.getFollowedCategories(force = true)
                    } catch (_: Exception) {
                        categories
                    }
                }
            }
        }
    }

    fun closeStream(resetScreen: Boolean = true) {
        isStreamMinimized = false
        val t = currentStream
        currentStream = null
        isCurrentStreamModerator = false
        selectedChatterInfo = null
        selectedChatMessage = null
        pinnedMessage = null
        rewardRequests.clear()
        modLogs.clear()
        activePrediction = null
        unbanRequests.clear()
        targetFollowed = null
        qualities = emptyList()
        playbackUrl = null
        targetNotified = false
        viewers = null
        badgeMap = emptyMap()
        emotes = EmoteBundles(favorite = favoriteEmotes)
        points = null
        if (resetScreen || screen == Screen.Stream) {
            screen = Screen.Home
        }
        botActive = false

        stopChat()

        // Ушли со стрима: зафлаженный канал продолжает фармить той же сессией,
        // но с тихими колбэками; незафлаженный гасится.
        if (t != null) {
            if (FarmManager.isBot(t.id)) {
                viewModelScope.launch {
                    val token = tokenStore.accessToken ?: return@launch
                    PointsFarmer.startSession(
                        channelId = t.id,
                        channelLogin = t.login,
                        token = token,
                        onClaimed = { },
                        onBalance = { },
                        onRaided = { },
                        onOffline = { FarmManager.sessionDied() }
                    )
                }
            } else {
                PointsFarmer.stopSession(t.id)
            }
            FarmManager.setViewing(null)
            FarmManager.wake(getApplication())
        }

        PointsPubSub.stop()
    }

    /** Закрытие мини-плеера: останавливает плеер и фоновый аудиорежим, не сбрасывая текущий экран. */
    fun closeMiniPlayer() {
        runCatching {
            val ctx = getApplication<Application>()
            ctx.sendBroadcast(Intent(TeaAudioService.ACTION_DISMISS).setPackage(ctx.packageName))
        }
        PlayerHolder.player?.let { p ->
            p.playWhenReady = false
            p.stop()
            p.clearMediaItems()
        }
        closeStream(resetScreen = false)
    }

    /** Фолоу/анфолоу через Persisted Query (веб-клиент Twitch). */
    fun toggleFollow() {
        val t = currentStream ?: return

        // Debounce: проверяем, не слишком ли часто нажимают
        val now = System.currentTimeMillis()
        if (now - lastFollowActionTime < followDebounceMs) {
            Log.d("TeaTV-VM", "Follow action debounced")
            return
        }
        lastFollowActionTime = now

        viewModelScope.launch {
            val isCurrentlyFollowing = targetFollowed ?: try {
                repository.isFollowing(t.id)
            } catch (_: Exception) {
                Toast.makeText(getApplication(), "Не удалось проверить статус", Toast.LENGTH_SHORT).show()
                return@launch
            }

            try {
                val variables = org.json.JSONObject().apply {
                    put("input", org.json.JSONObject().apply {
                        put("targetID", t.id)
                    })
                }

                val opName = if (isCurrentlyFollowing) "FollowButton_UnfollowUser" else "FollowButton_FollowUser"
                val response = gql.executePersistedMutation(opName, variables)

                if (response.contains("\"errors\"")) {
                    Log.w("TeaTV-GQL", "Mutation rejected: ${response.take(300)}")

                    // Проверяем, не ошибка ли это PersistedQueryNotFound (нужен новый хэш)
                    if (response.contains("PersistedQueryNotFound")) {
                        val hashMatch = Regex("\"sha256Hash\":\"([a-f0-9]+)\"").find(response)
                        if (hashMatch != null) {
                            Log.e("TeaTV-GQL", "Need new hash for $opName: ${hashMatch.groupValues[1]}")
                            Toast.makeText(getApplication(), "Обнови хэш в TwitchPersistedQueries", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(getApplication(), "Twitch отклонил действие", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // Успех: инвертируем состояние и обновляем кэш
                    targetFollowed = !isCurrentlyFollowing
                    repository.invalidateStreamers()
                    launch {
                        streamers = try {
                            repository.getFollowedStreamers(force = true)
                        } catch (_: Exception) {
                            streamers
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("TeaTV-GQL", "Mutation failed", e)
                Toast.makeText(getApplication(), "Ошибка сети", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** Колокольчик: локальный тумблер live-уведомлений. */
    fun toggleNotify() {
        val t = currentStream ?: return
        val next = !targetNotified
        notifyStore.setNotified(t.id, next)
        targetNotified = next
    }

    /** Добавить/удалить канал из избранного. */
    fun toggleFavorite(item: StreamerItem) {
        val next = !favoritesStore.isFavorite(item.id)
        favoritesStore.setFavorite(item.id, next)
        favorites = if (next) {
            (favorites + item).distinctBy { it.id }
        } else {
            favorites.filter { it.id != item.id }
        }
    }

    /** Вкл/выкл бота для канала без захода на стрим. */
    fun toggleBotForChannel(item: StreamerItem) {
        viewModelScope.launch {
            val token = tokenStore.accessToken ?: return@launch
            val current = FarmManager.isBot(item.id)
            if (!current) FarmManager.requestIgnoreOptimizations(getApplication())
            FarmManager.setBotEnabled(
                context = getApplication(),
                channelId = item.id,
                login = item.login,
                token = token,
                enabled = !current,
                startSessionNow = !current
            )
        }
    }

    /** Обновить состояние бота для канала (для UI). */
    fun isBotForChannel(channelId: String): Boolean = FarmManager.isBot(channelId)

    /** Ники зрителей — если ты модератор/стример канала. */
    fun loadViewers() {
        val t = currentStream ?: return
        viewers = null
        viewModelScope.launch {
            viewers = try {
                val me = repository.getProfile()
                api.getChatters(t.id, me.id, tokenStore.helixToken.orEmpty())
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    /** Загрузить все пачки смайлов для канала. */
    private fun loadEmotes(t: StreamerItem) {
        viewModelScope.launch {
            val me = try {
                repository.getProfile()
            } catch (_: Exception) {
                null
            }
            emotes = coroutineScope {
                val token = tokenStore.helixToken.orEmpty()
                val user = async {
                    val subs = me?.let { api.getUserEmotes(it.id, token, subsOnly = true) } ?: emptyList()
                    val mine = me?.let { api.getChannelEmotes(it.id, token) } ?: emptyList()

                    val ownerIds = subs.mapNotNull { it.ownerId }.filter { it.isNotBlank() }.distinct()
                    val knownNames = mutableMapOf<String, String>()
                    mySubscriptions.forEach { knownNames[it.broadcasterId] = it.broadcasterName }
                    streamers.forEach { knownNames[it.id] = it.name }
                    if (me != null) knownNames[me.id] = me.displayName
                    if (t.id.isNotBlank()) knownNames[t.id] = t.name

                    val missingIds = ownerIds.filter { it !in knownNames }
                    if (missingIds.isNotEmpty()) {
                        try {
                            missingIds.chunked(100).forEach { chunk ->
                                val users = api.getUsersByIds(chunk, token)
                                users.forEach { knownNames[it.id] = it.displayName }
                            }
                        } catch (_: Exception) {}
                    }

                    val enrichedSubs = subs
                        .filter { !it.ownerId.isNullOrBlank() && it.ownerId != "0" }
                        .mapNotNull { e ->
                            val chName = e.ownerId?.let { knownNames[it] } ?: e.channelName?.takeIf { !it.equals("Подписки", ignoreCase = true) }
                            if (chName.isNullOrBlank()) null
                            else e.copy(channelName = chName)
                        }
                    val enrichedMine = mine.map { e ->
                        e.copy(channelName = me?.displayName ?: "Мой канал")
                    }

                    (enrichedSubs + enrichedMine).distinctBy { it.code }
                }
                val chan = async {
                    val list = api.getChannelEmotes(t.id, token)
                    list.map { it.copy(channelName = t.name) }
                }
                val glob = async {
                    globalTwitchEmotesCache ?: api.getGlobalEmotes(token).also { globalTwitchEmotesCache = it }
                }
                val s7c = async { EmoteRepository.sevenTvChannel(t.id) }
                val s7g = async { EmoteRepository.sevenTvGlobal() }
                val btc = async { EmoteRepository.bttvChannel(t.id) }
                val btg = async { EmoteRepository.bttvGlobal() }
                val ffzc = async { EmoteRepository.ffzChannel(t.login) }
                val ffzg = async { EmoteRepository.ffzGlobal() }
                EmoteBundles(
                    favorite = clientSettingsStore.getFavoriteEmotes(),
                    user = user.await(),
                    channel = chan.await(),
                    seven = s7c.await() + s7g.await(),
                    bttv = (btc.await() + btg.await() + ffzc.await() + ffzg.await()).distinctBy { it.code },
                    global = glob.await()
                )
            }
        }
    }

    /** Обновить баллы и награды канала. */
    fun refreshPoints(force: Boolean = false) {
        val t = currentStream ?: return
        val isOwner = isCurrentStreamOwner
        viewModelScope.launch {
            val token = tokenStore.accessToken ?: return@launch
            val loaded = PointsRepository.load(t.id, token, force)
            points = if (isOwner && loaded != null) {
                loaded.copy(balance = loaded.balance?.takeIf { it > 0 })
            } else {
                loaded
            }
        }
    }

    /** Использовать награду за баллы. */
    fun redeemReward(reward: PointReward, userInput: String? = null) {
        val t = currentStream ?: return
        val isOwner = isCurrentStreamOwner
        viewModelScope.launch {
            val token = tokenStore.accessToken ?: return@launch
            val success = PointsRepository.redeem(getApplication(), t.id, reward, userInput, token)
            if (success) {
                refreshPoints(force = true)
            } else if (isOwner) {
                val myName = user?.displayName ?: user?.login ?: tokenStore.userDisplayName ?: tokenStore.userLogin ?: "Стример"
                val msgId = "redeem-owner-${System.currentTimeMillis()}"
                val msg = ChatMessage(
                    id = msgId,
                    sender = myName,
                    color = "#9146FF",
                    text = userInput.orEmpty(),
                    badges = listOf("broadcaster/1"),
                    replyToId = null,
                    replyToName = null,
                    replyToBody = null,
                    redemptionTitle = reward.title,
                    redemptionIcon = reward.imageUrl
                )
                insertMessage(msg)
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "Награда активирована: ${reward.title}", Toast.LENGTH_SHORT).show()
                }
                if (!userInput.isNullOrBlank()) {
                    val hToken = tokenStore.helixToken ?: tokenStore.accessToken
                    val myId = user?.id ?: tokenStore.userId
                    if (hToken != null && myId != null) {
                        try {
                            api.sendChatMessage(t.id, myId, "[Награда: ${reward.title}] $userInput", hToken)
                        } catch (_: Exception) {}
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "Не удалось активировать награду", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /** Смайл выбран из панели для отправки. */
    fun pickEmote(e: Emote) {
        // Выбор смайла
    }

    /** Переключение смайла в избранных (зажатие в панели). Возвращает true, если добавлен. */
    fun toggleFavoriteEmote(e: Emote): Boolean {
        val current = clientSettingsStore.getFavoriteEmotes().toMutableList()
        val exists = current.any { it.code == e.code }
        if (exists) {
            current.removeAll { it.code == e.code }
        } else {
            current.add(0, e)
        }
        clientSettingsStore.saveFavoriteEmotes(current)
        favoriteEmotes = current
        emotes = emotes.copy(favorite = current)
        return !exists
    }

    /** Список доступных категорий смайлов без огромной категории "Подписки". */
    fun getAvailableEmoteCategories(): List<String> {
        val subChannels = (mySubscriptions.map { it.broadcasterName } + emotes.user.mapNotNull { it.channelName })
            .filter { it.isNotBlank() && !it.equals("Подписки", ignoreCase = true) }
            .distinct()

        val all = mutableListOf<String>()
        all.addAll(subChannels)
        all.add("Смайлы текущего канала")
        all.add("7TV")
        all.add("BTTV")
        all.add("Twitch")

        val saved = clientSettingsStore.getEmoteCategoryOrder()
        if (saved.isEmpty()) return all

        val ordered = saved.filter { it in all && !it.equals("Подписки", ignoreCase = true) }.toMutableList()
        all.forEach { if (it !in ordered) ordered.add(it) }
        return ordered
    }

    fun saveEmoteCategoryOrder(order: List<String>) {
        val filtered = order.filter { !it.equals("Подписки", ignoreCase = true) }
        clientSettingsStore.saveEmoteCategoryOrder(filtered)
        emoteCategoryOrder = filtered
    }

    fun resetEmoteCategoryOrder() {
        clientSettingsStore.saveEmoteCategoryOrder(emptyList())
        emoteCategoryOrder = emptyList()
    }

    fun openEmoteOrderSettings() {
        screen = Screen.EmoteOrderSettings
    }

    fun backFromEmoteOrderSettings() {
        screen = Screen.Home
        tab = MainTab.Tools
    }

    fun updateModSwipeRequiresSelection(enabled: Boolean) {
        modSwipeRequiresSelection = enabled
        clientSettingsStore.modSwipeRequiresSelection = enabled
    }

    fun startLogin() {
        val app = getApplication<Application>()
        app.startActivity(
            Intent(app, WebLoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** Токен + плейлист + автозапуск лучшего качества. */
    private fun loadPlayback(t: StreamerItem) {
        if (!t.isLive) {
            playbackUrl = null
            qualities = emptyList()
            audioOnlyUrl = null
            sourceUrl = null
            PlayerHolder.player?.stop()
            PlayerHolder.player?.clearMediaItems()
            return
        }

        viewModelScope.launch {
            try {
                val pat = gql.playbackAccessToken(t.login)
                if (pat == null) {
                    playbackUrl = null
                    qualities = emptyList()
                    audioOnlyUrl = null
                    sourceUrl = null
                    PlayerHolder.player?.stop()
                    PlayerHolder.player?.clearMediaItems()
                    return@launch
                }
                val qs = TwitchHls.fetchQualities(
                    TwitchHls.masterUrl(t.login, pat.first, pat.second)
                )
                qualities = qs.filter { !it.audioOnly && !it.isSource }
                sourceUrl = qs.firstOrNull { it.isSource }?.url
                audioOnlyUrl = qs.firstOrNull { it.audioOnly }?.url

                // Сохраняем выбранное пользователем качество при обновлении (или берем лучшее по умолчанию)
                val matchedQuality = if (!selectedQualityLabel.isNullOrBlank()) {
                    qualities.firstOrNull { it.label.equals(selectedQualityLabel, ignoreCase = true) }
                        ?: if (selectedQualityLabel.equals("source", ignoreCase = true) || selectedQualityLabel.equals("исходное", ignoreCase = true)) {
                            qs.firstOrNull { it.isSource }
                        } else null
                } else null

                val targetUrl = when {
                    viewMode == ViewMode.AudioOnly && audioOnlyUrl != null -> audioOnlyUrl
                    matchedQuality != null -> matchedQuality.url
                    else -> qualities.firstOrNull()?.url ?: sourceUrl
                }

                if (selectedQualityLabel.isNullOrBlank()) {
                    selectedQualityLabel = matchedQuality?.label ?: qualities.firstOrNull()?.label ?: "Source"
                }

                playbackUrl = targetUrl
                playbackRefreshTrigger++
                if (playbackUrl.isNullOrBlank()) {
                    PlayerHolder.player?.stop()
                    PlayerHolder.player?.clearMediaItems()
                }
            } catch (_: Exception) {
                playbackUrl = null
                qualities = emptyList()
                audioOnlyUrl = null
                sourceUrl = null
                PlayerHolder.player?.stop()
                PlayerHolder.player?.clearMediaItems()
            }
        }
    }

    /** Обновить плеер (F5-эффект с сохранением качества и мгновенным прыжком на живую кромку). */
    fun refreshPlayback() {
        val p = PlayerHolder.player
        if (p != null && p.playbackState != androidx.media3.common.Player.STATE_IDLE) {
            p.seekToDefaultPosition()
        }
        playbackRefreshTrigger++
        currentStream?.let { loadPlayback(it) }
    }

    /** Смена качества = явный выбор качества пользователем с сохранением метки. */
    fun selectQuality(q: QualityOption) {
        selectedQualityLabel = q.label
        playbackUrl = q.url
        playbackRefreshTrigger++
    }

    fun selectViewMode(m: ViewMode) {
        viewMode = m
        when (m) {
            ViewMode.AudioOnly -> audioOnlyUrl?.let { playbackUrl = it }
            ViewMode.Normal -> {
                val matchedQuality = qualities.firstOrNull { it.label.equals(selectedQualityLabel, ignoreCase = true) }
                val target = matchedQuality?.url ?: qualities.firstOrNull()?.url ?: sourceUrl
                target?.let { playbackUrl = it }
            }
        }
        playbackRefreshTrigger++
    }

    /** Политика внешнего (фонового) воспроизведения. */
    fun selectAudioPolicy(p: AudioPolicy) {
        audioPolicy = p
        settingsStore.setAudioPolicy(p.name)
    }

    /** Единая точка вставки: дедуп по id + усечение головы, всё на main. */
    private fun insertMessage(m: ChatMessage) {
        viewModelScope.launch(Dispatchers.Main.immediate) {
            if (seenIds.contains(m.id)) return@launch
            chatMessages.add(m)
            seenIds.add(m.id)
            currentStream?.id?.let { streamId ->
                val cache = streamChatCache.getOrPut(streamId) { mutableListOf() }
                cache.add(m)
                while (cache.size > chatWindowLimit) {
                    cache.removeAt(0)
                }
            }
            while (chatMessages.size > chatWindowLimit) {
                val removed = chatMessages.removeAt(0)
                seenIds.remove(removed.id)
            }

            // Уведомление в шторку при упоминании, когда стрим свернут
            if (replySettings.notifyOnMention && (isStreamMinimized || screen != Screen.Stream)) {
                val triggers = replySettings.triggerWords
                val userName = user?.displayName ?: user?.login
                val matchesWord = triggers.any { word -> m.text.contains(word, ignoreCase = true) }
                val matchesUser = !userName.isNullOrBlank() && m.text.contains(userName, ignoreCase = true)
                if (matchesWord || matchesUser) {
                    MentionNotificationHelper.notifyMention(
                        context = getApplication(),
                        sender = m.sender,
                        messageText = m.text,
                        streamName = currentStream?.name
                    )
                }
            }
        }
    }

    /** Сброс окна и фильтра вместе (при открытии канала, синхронно на main). */
    private fun resetChat() {
        chatMessages.clear()
        seenIds.clear()
    }

    /** Заполнить окно историей: сброс + дедуп по ходу + усечение под лимит. */
    private fun seedHistory(hist: List<ChatMessage>) {
        chatMessages.clear()
        seenIds.clear()
        for (m in hist) {
            if (seenIds.contains(m.id)) continue
            chatMessages.add(m)
            seenIds.add(m.id)
        }
        while (chatMessages.size > chatWindowLimit) {
            val removed = chatMessages.removeAt(0)
            seenIds.remove(removed.id)
        }
    }


    private fun startChat(t: StreamerItem) {
        stopChat()
        chatMessages.clear()
        seenIds.clear()

        // 1. Мгновенно восстанавливаем кешированные сообщения стрима, если пользователь уже заходил
        val cached = streamChatCache[t.id]
        if (!cached.isNullOrEmpty()) {
            for (m in cached) {
                if (seenIds.add(m.id)) {
                    chatMessages.add(m)
                }
            }
        }

        viewModelScope.launch {
            val me = try {
                repository.getProfile()
            } catch (_: Exception) {
                return@launch
            }

            // 2. Сразу же подключаем EventSub WebSocket в фоне, чтобы не терять новые сообщения во время запроса истории
            val helixToken = tokenStore.helixToken ?: return@launch
            chatClient = ChatClient(helixToken, TwitchAuth.OWN_CLIENT_ID, me.id) { m ->
                insertMessage(m)
            }
            chatClient?.connect(t.id)

            // 3. Загружаем историю чата
            val hist = try {
                api.getRecentChat(t.login)
            } catch (_: Exception) {
                emptyList()
            }

            // 4. Мерджим историю: добавляем в начало только сообщения, которых ещё нет в чате
            if (hist.isNotEmpty()) {
                withContext(Dispatchers.Main.immediate) {
                    val older = hist.filter { it.id !in seenIds }
                    if (older.isNotEmpty()) {
                        chatMessages.addAll(0, older)
                        older.forEach { seenIds.add(it.id) }

                        val cache = streamChatCache.getOrPut(t.id) { mutableListOf() }
                        cache.addAll(0, older)
                        while (cache.size > chatWindowLimit) {
                            cache.removeAt(0)
                        }

                        while (chatMessages.size > chatWindowLimit) {
                            val removed = chatMessages.removeAt(0)
                            seenIds.remove(removed.id)
                        }
                    }
                }
            }
        }
    }

    /** Отправить сообщение в чат с поддержкой команд через слэш (/) и безопасного автоматического разбиения длинных сообщений. */
    fun sendChat(text: String, replyToId: String? = null) {
        val t = currentStream ?: return
        val isOwner = isCurrentStreamOwner
        viewModelScope.launch {
            val token = tokenStore.helixToken ?: tokenStore.accessToken ?: return@launch
            val me = user ?: try {
                repository.getProfile()
            } catch (_: Exception) {
                null
            } ?: tokenStore.userId?.takeIf { it.isNotBlank() }?.let { uid ->
                TwitchUser(
                    id = uid,
                    login = tokenStore.userLogin.orEmpty(),
                    displayName = tokenStore.userDisplayName ?: tokenStore.userLogin.orEmpty(),
                    avatarUrl = ""
                )
            } ?: return@launch

            val trimmed = text.trim()

            // 1. Обработка команд чата через слэш (/)
            if (trimmed.startsWith("/")) {
                val broadcasterId = if (isOwner) (tokenStore.userId ?: me.id) else t.id
                val handled = handleChatCommand(trimmed, broadcasterId, t.login, me.id, token)
                if (handled) return@launch
            }

            // 2. Обычная отправка сообщений
            if (clientSettingsStore.splitLongMessagesEnabled) {
                val chunks = MessageSplitter.split(trimmed)
                for (i in chunks.indices) {
                    val chunk = chunks[i]
                    val replyId = if (i == 0) replyToId else null
                    try {
                        api.sendChatMessage(t.id, me.id, chunk, token, replyId)
                    } catch (e: Exception) {
                        Log.e("MainViewModel", "Failed to send chat message chunk $i", e)
                    }
                    if (i < chunks.size - 1) {
                        val slowSeconds = channelModes.slowModeSeconds
                        if (slowSeconds > 0) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    getApplication(),
                                    "Медленный режим ($slowSeconds сек): отправка части ${i + 2}/${chunks.size}...",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            delay((slowSeconds * 1000L) + 500L)
                        } else {
                            delay(800)
                        }
                    }
                }
            } else {
                val safeText = if (trimmed.length > MessageSplitter.TWITCH_MAX_MESSAGE_LENGTH) {
                    trimmed.take(MessageSplitter.TWITCH_MAX_MESSAGE_LENGTH)
                } else {
                    trimmed
                }
                try {
                    api.sendChatMessage(t.id, me.id, safeText, token, replyToId)
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Failed to send chat message", e)
                }
            }
        }
    }

    /** Предварительное кэширование ID пользователя из недавних сообщений чата перед выполнением команды. */
    private fun cacheTargetIdFromChat(target: String) {
        val clean = target.removePrefix("@").trim()
        if (clean.isBlank()) return
        val m = chatMessages.findLast {
            it.sender.equals(clean, ignoreCase = true) || (it.senderLogin?.equals(clean, ignoreCase = true) == true)
        }
        if (m != null && !m.senderId.isNullOrBlank()) {
            modRepository.cacheUserId(clean, m.senderId)
            if (!m.senderLogin.isNullOrBlank()) {
                modRepository.cacheUserId(m.senderLogin!!, m.senderId)
            }
            if (m.sender.isNotBlank()) {
                modRepository.cacheUserId(m.sender, m.senderId)
            }
        }
    }

    /** Обработчик команд Twitch чата через слэш (/). */
    private suspend fun handleChatCommand(
        text: String,
        channelId: String,
        channelLogin: String,
        myUserId: String,
        token: String
    ): Boolean {
        if (!text.startsWith("/")) return false

        val parts = text.substring(1).trim().split(Regex("\\s+"))
        if (parts.isEmpty() || parts[0].isBlank()) return false
        val cmd = parts[0].lowercase()
        val args = parts.drop(1)

        when (cmd) {
            "mod" -> {
                val target = args.firstOrNull()?.removePrefix("@")
                if (target.isNullOrBlank()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(getApplication(), "Использование: /mod <пользователь>", Toast.LENGTH_SHORT).show()
                    }
                    return true
                }
                cacheTargetIdFromChat(target)
                val (ok, msg) = modRepository.addModerator(channelId, myUserId, target)
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show()
                }
                return true
            }
            "unmod" -> {
                val target = args.firstOrNull()?.removePrefix("@")
                if (target.isNullOrBlank()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(getApplication(), "Использование: /unmod <пользователь>", Toast.LENGTH_SHORT).show()
                    }
                    return true
                }
                cacheTargetIdFromChat(target)
                val (ok, msg) = modRepository.removeModerator(channelId, myUserId, target)
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show()
                }
                return true
            }
            "vip" -> {
                val target = args.firstOrNull()?.removePrefix("@")
                if (target.isNullOrBlank()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(getApplication(), "Использование: /vip <пользователь>", Toast.LENGTH_SHORT).show()
                    }
                    return true
                }
                cacheTargetIdFromChat(target)
                val (ok, msg) = modRepository.addVip(channelId, myUserId, target)
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show()
                }
                return true
            }
            "unvip" -> {
                val target = args.firstOrNull()?.removePrefix("@")
                if (target.isNullOrBlank()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(getApplication(), "Использование: /unvip <пользователь>", Toast.LENGTH_SHORT).show()
                    }
                    return true
                }
                cacheTargetIdFromChat(target)
                val (ok, msg) = modRepository.removeVip(channelId, myUserId, target)
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show()
                }
                return true
            }
            "ban" -> {
                val target = args.firstOrNull()?.removePrefix("@")
                if (target.isNullOrBlank()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(getApplication(), "Использование: /ban <пользователь> [причина]", Toast.LENGTH_SHORT).show()
                    }
                    return true
                }
                cacheTargetIdFromChat(target)
                val reason = if (args.size > 1) args.drop(1).joinToString(" ") else "Banned by command"
                val ok = modRepository.executeSliderAction(
                    channelId = channelId,
                    channelLogin = channelLogin,
                    myUserId = myUserId,
                    targetUser = target,
                    targetUserId = null,
                    messageId = "",
                    action = ModSliderAction.BAN,
                    targetUserLogin = target,
                    reason = reason
                )
                withContext(Dispatchers.Main) {
                    if (ok) {
                        chatMessages.removeAll { it.sender.equals(target, ignoreCase = true) || (it.senderLogin?.equals(target, ignoreCase = true) == true) }
                        Toast.makeText(getApplication(), "Пользователь @$target заблокирован", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(getApplication(), "Не удалось заблокировать @$target (проверьте права)", Toast.LENGTH_SHORT).show()
                    }
                }
                return true
            }
            "unban" -> {
                val target = args.firstOrNull()?.removePrefix("@")
                if (target.isNullOrBlank()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(getApplication(), "Использование: /unban <пользователь>", Toast.LENGTH_SHORT).show()
                    }
                    return true
                }
                cacheTargetIdFromChat(target)
                val ok = modRepository.unbanUser(
                    channelId = channelId,
                    myUserId = myUserId,
                    targetUser = target,
                    targetUserId = null,
                    targetUserLogin = target
                )
                withContext(Dispatchers.Main) {
                    val msg = if (ok) "Пользователь @$target разблокирован" else "Ошибка разблокировки @$target"
                    Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show()
                }
                return true
            }
            "timeout" -> {
                val target = args.firstOrNull()?.removePrefix("@")
                if (target.isNullOrBlank()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(getApplication(), "Использование: /timeout <пользователь> [секунды] [причина]", Toast.LENGTH_SHORT).show()
                    }
                    return true
                }
                cacheTargetIdFromChat(target)
                val sec = args.getOrNull(1)?.toIntOrNull() ?: 600
                val reason = if (args.size > 2) args.drop(2).joinToString(" ") else "Timeout by command"
                val ok = modRepository.timeoutUser(
                    channelId = channelId,
                    myUserId = myUserId,
                    targetUser = target,
                    durationSeconds = sec,
                    reason = reason
                )
                withContext(Dispatchers.Main) {
                    if (ok) {
                        chatMessages.removeAll { it.sender.equals(target, ignoreCase = true) || (it.senderLogin?.equals(target, ignoreCase = true) == true) }
                        Toast.makeText(getApplication(), "Таймаут для @$target на $sec сек", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(getApplication(), "Не удалось выдать таймаут @$target (проверьте права)", Toast.LENGTH_SHORT).show()
                    }
                }
                return true
            }
            "untimeout" -> {
                val target = args.firstOrNull()?.removePrefix("@")
                if (target.isNullOrBlank()) return true
                cacheTargetIdFromChat(target)
                val ok = modRepository.unbanUser(channelId, myUserId, target, null, target)
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), if (ok) "Таймаут с @$target снят" else "Ошибка снятия таймаута", Toast.LENGTH_SHORT).show()
                }
                return true
            }
            "clear" -> {
                val (ok, msg) = modRepository.clearChat(channelId, myUserId)
                withContext(Dispatchers.Main) {
                    if (ok) {
                        chatMessages.clear()
                    }
                    Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show()
                }
                return true
            }
            "slow" -> {
                val firstArg = args.firstOrNull()?.lowercase()
                val isTurnOff = firstArg == "off" || firstArg == "0"
                val sec = if (isTurnOff) 0 else (firstArg?.toIntOrNull() ?: 30)
                val ok = modRepository.setSlowMode(channelId, myUserId, sec)
                withContext(Dispatchers.Main) {
                    if (ok) {
                        channelModes = channelModes.copy(slowModeSeconds = sec)
                        val msg = if (sec > 0) "Медленный режим включен: $sec сек" else "Медленный режим выключен"
                        Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(getApplication(), "Не удалось изменить медленный режим (проверьте права)", Toast.LENGTH_SHORT).show()
                    }
                }
                return true
            }
            "slowoff" -> {
                val ok = modRepository.setSlowMode(channelId, myUserId, 0)
                withContext(Dispatchers.Main) {
                    if (ok) {
                        channelModes = channelModes.copy(slowModeSeconds = 0)
                        Toast.makeText(getApplication(), "Медленный режим выключен", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(getApplication(), "Не удалось выключить медленный режим (проверьте права)", Toast.LENGTH_SHORT).show()
                    }
                }
                return true
            }
            "followers" -> {
                val firstArg = args.firstOrNull()?.lowercase()
                val isTurnOff = firstArg == "off"
                val min = if (isTurnOff) -1 else (firstArg?.toIntOrNull() ?: 0)
                val ok = modRepository.setFollowersMode(channelId, myUserId, min)
                withContext(Dispatchers.Main) {
                    if (ok) {
                        channelModes = channelModes.copy(followersDurationMinutes = min)
                        val msg = if (min >= 0) "Режим 'Только фолловеры' включен ($min мин)" else "Режим фолловеров выключен"
                        Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(getApplication(), "Не удалось изменить режим фолловеров (проверьте права)", Toast.LENGTH_SHORT).show()
                    }
                }
                return true
            }
            "followersoff" -> {
                val ok = modRepository.setFollowersMode(channelId, myUserId, -1)
                withContext(Dispatchers.Main) {
                    if (ok) {
                        channelModes = channelModes.copy(followersDurationMinutes = -1)
                        Toast.makeText(getApplication(), "Режим фолловеров выключен", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(getApplication(), "Не удалось выключить режим фолловеров (проверьте права)", Toast.LENGTH_SHORT).show()
                    }
                }
                return true
            }
            "subscribers", "sub" -> {
                val firstArg = args.firstOrNull()?.lowercase()
                val enabled = firstArg != "off"
                val ok = modRepository.setSubOnlyMode(channelId, myUserId, enabled)
                withContext(Dispatchers.Main) {
                    if (ok) {
                        channelModes = channelModes.copy(subOnly = enabled)
                        val msg = if (enabled) "Режим 'Только сабы' включен" else "Режим 'Только сабы' выключен"
                        Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(getApplication(), "Не удалось изменить режим 'Только сабы' (проверьте права)", Toast.LENGTH_SHORT).show()
                    }
                }
                return true
            }
            "subscribersoff", "suboff" -> {
                val ok = modRepository.setSubOnlyMode(channelId, myUserId, false)
                withContext(Dispatchers.Main) {
                    if (ok) {
                        channelModes = channelModes.copy(subOnly = false)
                        Toast.makeText(getApplication(), "Режим 'Только сабы' выключен", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(getApplication(), "Не удалось выключить режим 'Только сабы' (проверьте права)", Toast.LENGTH_SHORT).show()
                    }
                }
                return true
            }
            "emoteonly", "emotes" -> {
                val firstArg = args.firstOrNull()?.lowercase()
                val enabled = firstArg != "off"
                val ok = modRepository.setEmoteOnlyMode(channelId, myUserId, enabled)
                withContext(Dispatchers.Main) {
                    if (ok) {
                        channelModes = channelModes.copy(emoteOnly = enabled)
                        val msg = if (enabled) "Режим 'Только смайлы' включен" else "Режим 'Только смайлы' выключен"
                        Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(getApplication(), "Не удалось изменить режим 'Только смайлы' (проверьте права)", Toast.LENGTH_SHORT).show()
                    }
                }
                return true
            }
            "emoteonlyoff", "emotesoff" -> {
                val ok = modRepository.setEmoteOnlyMode(channelId, myUserId, false)
                withContext(Dispatchers.Main) {
                    if (ok) {
                        channelModes = channelModes.copy(emoteOnly = false)
                        Toast.makeText(getApplication(), "Режим 'Только смайлы' выключен", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(getApplication(), "Не удалось выключить режим 'Только смайлы' (проверьте права)", Toast.LENGTH_SHORT).show()
                    }
                }
                return true
            }
            "announce" -> {
                val announceText = args.joinToString(" ")
                if (announceText.isNotBlank()) {
                    val ok = modRepository.sendAnnouncement(channelId, myUserId, announceText)
                    withContext(Dispatchers.Main) {
                        if (ok) {
                            Toast.makeText(getApplication(), "Анонс отправлен в чат", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(getApplication(), "Не удалось отправить анонс (проверьте права)", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                return true
            }
            "shield" -> {
                val newShield = !channelModes.shieldMode
                val ok = modRepository.setShieldMode(channelId, myUserId, newShield)
                withContext(Dispatchers.Main) {
                    if (ok) {
                        channelModes = channelModes.copy(shieldMode = newShield)
                        Toast.makeText(getApplication(), if (newShield) "Режим щита включен" else "Режим щита выключен", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(getApplication(), "Не удалось изменить режим щита (проверьте права)", Toast.LENGTH_SHORT).show()
                    }
                }
                return true
            }
            "commercial" -> {
                val sec = args.firstOrNull()?.toIntOrNull() ?: 30
                val (ok, msg) = modRepository.startCommercial(channelId, myUserId, sec)
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show()
                }
                return true
            }
            "color" -> {
                val colorArg = args.firstOrNull()
                if (colorArg.isNullOrBlank()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(getApplication(), "Использование: /color <название_цвета или hex>", Toast.LENGTH_SHORT).show()
                    }
                    return true
                }
                val (ok, msg) = modRepository.setChatColor(myUserId, colorArg)
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show()
                }
                return true
            }
            "help" -> {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        getApplication(),
                        "Команды: /mod, /unmod, /vip, /unvip, /ban, /unban, /timeout, /untimeout, /clear, /slow, /slowoff, /followers, /subscribers, /emoteonly, /announce, /shield, /commercial, /color",
                        Toast.LENGTH_LONG
                    ).show()
                }
                return true
            }
            else -> {
                // Если команда не из модераторских перехватов (например /me или кастомная),
                // отправляем её в чат целиком без дробления MessageSplitter
                try {
                    api.sendChatMessage(channelId, myUserId, text, token)
                } catch (e: Exception) {
                    Log.w("MainViewModel", "Command send failed: $text", e)
                }
                return true
            }
        }
    }

    /** Значки канала + глобальные. */
    private fun loadBadges(t: StreamerItem) {
        viewModelScope.launch {
            val token = tokenStore.helixToken ?: return@launch
            val globalBadges = globalBadgesCache ?: api.getGlobalBadges(token).also { globalBadgesCache = it }
            badgeMap = (api.getChannelBadges(t.id, token) + globalBadges).toMap()
        }
    }

    fun stopChat() {
        chatClient?.disconnect()
        chatClient = null
    }

    fun logout() {
        // Гасим сессию воспроизведения до выхода: плеер принадлежит сервису.
        runCatching {
            val ctx = getApplication<Application>()
            ctx.sendBroadcast(Intent(TeaAudioService.ACTION_DISMISS).setPackage(ctx.packageName))
        }
        tokenStore.clear(getApplication())
        repository.invalidateAll()
        PointsFarmer.stopAll()
        PointsPubSub.stop()
        FarmManager.clear(getApplication())
        user = null
        streamers = emptyList()
        categories = emptyList()
        channel = null
        currentStream = null
        targetFollowed = null
        tab = MainTab.View
        screen = Screen.Auth
    }

    private fun loadHomeData() {
        viewModelScope.launch {
            user = try {
                gql.currentUser() ?: repository.getProfile()
            } catch (e: Exception) {
                Log.e("TeaTV-VM", "profile exception", e)
                null
            }
            if (user == null) {
                Log.e(
                    "TeaTV-VM",
                    "profile null -> auth; token len=${tokenStore.accessToken?.length}," +
                            " apiCid=${api.clientId}"
                )
                tokenStore.clear(getApplication())
                repository.invalidateAll()
                screen = Screen.Auth
                return@launch
            }
            tokenStore.userId = user?.id
            tokenStore.userLogin = user?.login
            tokenStore.userDisplayName = user?.displayName
            // Оптимизация подгрузки: параллельная загрузка всех данных пачкой
            val channelDeferred = async {
                try { repository.getMyChannel() } catch (_: Exception) { null }
            }
            val streamersDeferred = async {
                try { repository.getFollowedStreamers() } catch (_: Exception) { emptyList() }
            }
            val categoriesDeferred = async {
                try { repository.getFollowedCategories() } catch (_: Exception) { emptyList() }
            }

            val newChannel = channelDeferred.await()
            val newStreamers = streamersDeferred.await()
            val newCategories = categoriesDeferred.await()
            val favIds = favoritesStore.allIds()
            val newFavorites = newStreamers.filter { it.id in favIds }

            // Разовое, плавное обновление состояния в один кадр без ступенчатых скачков интерфейса
            channel = newChannel
            streamers = newStreamers
            favorites = newFavorites
            categories = newCategories
        }
    }
}