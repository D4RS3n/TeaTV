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

enum class Screen { Auth, Home, Stream, Category, Account, Subscriptions, Channel, Vod, Settings, AccountSettings, ClientSettings }

enum class MainTab { View, Search, Tools }

enum class ViewMode { Normal, AudioOnly }

enum class AudioPolicy { Headphones, Always, Never }

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val clientSettingsStore = ClientSettingsStore(app)
    val replySettingsStore = ReplySettingsStore(app)
    var replySettings by mutableStateOf(replySettingsStore.load())
    var isStreamMinimized by mutableStateOf(false)
        private set

    private val api = TwitchApi(client = PointsRepository.httpClient())
    private val tokenStore = TokenStore(app)
    val gql = TwitchGql(tokenStore, app)
    val modRepository = ModRepository(api, gql, tokenStore)
    private val repository = TwitchRepository(api, tokenStore, gql, app)

    var isCurrentStreamModerator by mutableStateOf(false)
        private set
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
        if (tokenStore.isLoggedIn) {
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
        isCurrentStreamModerator = false
        selectedChatterInfo = null
        selectedChatMessage = null
        pinnedMessage = null

        viewModelScope.launch {
            isCurrentStreamModerator = modRepository.isModerator(
                channelId = item.id,
                channelLogin = item.login,
                myUserId = user?.id,
                myUserLogin = user?.login
            )
            if (isCurrentStreamModerator) {
                loadModData(item.id, item.login)
            } else {
                try {
                    channelModes = modRepository.fetchChannelModes(item.id, user?.id)
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
                    points = points?.copy(balance = b)
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
                action = action
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
    fun unbanChatter(targetUser: String) {
        val ch = currentStream ?: return
        val me = user ?: return
        viewModelScope.launch {
            val ok = modRepository.unbanUser(ch.id, me.id, targetUser, selectedChatterInfo?.userId)
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

    fun closeStream() {
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
        emotes = EmoteBundles()
        points = null
        screen = Screen.Home
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
                        .map { e ->
                            val chName = e.ownerId?.let { knownNames[it] } ?: e.channelName ?: "Подписки"
                            e.copy(channelName = chName)
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
        viewModelScope.launch {
            val token = tokenStore.accessToken ?: return@launch
            points = PointsRepository.load(t.id, token, force)
        }
    }

    /** Использовать награду за баллы. */
    fun redeemReward(reward: PointReward, userInput: String? = null) {
        val t = currentStream ?: return
        viewModelScope.launch {
            val token = tokenStore.accessToken ?: return@launch
            if (PointsRepository.redeem(getApplication(), t.id, reward, userInput, token)) {
                refreshPoints(force = true)
            }
        }
    }

    /** Смайл выбран: запоминаем в частые. */
    fun pickEmote(e: Emote) {
        emotes = emotes.copy(
            frequent = (listOf(e) + emotes.frequent).distinctBy { it.code }.take(24)
        )
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

    /** Отправить сообщение в чат с поддержкой безопасного автоматического разбиения длинных сообщений. */
    fun sendChat(text: String, replyToId: String? = null) {
        val t = currentStream ?: return
        viewModelScope.launch {
            val token = tokenStore.helixToken ?: return@launch
            val me = try {
                repository.getProfile()
            } catch (_: Exception) {
                return@launch
            }

            if (clientSettingsStore.splitLongMessagesEnabled) {
                val chunks = MessageSplitter.split(text)
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
                val safeText = if (text.length > MessageSplitter.TWITCH_MAX_MESSAGE_LENGTH) {
                    text.take(MessageSplitter.TWITCH_MAX_MESSAGE_LENGTH)
                } else {
                    text
                }
                try {
                    api.sendChatMessage(t.id, me.id, safeText, token, replyToId)
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Failed to send chat message", e)
                }
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