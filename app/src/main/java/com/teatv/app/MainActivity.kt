package com.teatv.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import com.teatv.app.screens.settings.AccountSettingsScreen
import com.teatv.app.screens.settings.ClientSettingsScreen
import com.teatv.app.screens.settings.SettingsScreen
import com.teatv.app.screens.settings.miniplayer.MiniPlayerOverlay
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.teatv.app.chat.points.FarmManager
import com.teatv.app.data.StreamerItem
import com.teatv.app.player.PlayerHolder
import com.teatv.app.ui.screens.AuthScreen
import com.teatv.app.ui.screens.CategoryScreen
import com.teatv.app.ui.screens.home.MainScreen
import com.teatv.app.ui.screens.stream.StreamScreen
import com.teatv.app.ui.theme.TeaTVTheme
import com.teatv.app.ui.screens.account.AccountScreen
import com.teatv.app.ui.screens.account.SubscriptionsScreen
import com.teatv.app.ui.screens.channel.ChannelPageScreen
import com.teatv.app.ui.screens.videos.VodPlayerScreen

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    private val notifLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val plugReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            if (i?.action == Intent.ACTION_HEADSET_PLUG) {
                if (i.getIntExtra("state", 0) == 0) onHeadsetUnplugged()
            } else {
                onHeadsetUnplugged()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        ProcessGuard.appForeground = true
        FarmManager.wake(this)
        vm.onForeground()
    }

    override fun onStop() {
        super.onStop()
        val p = PlayerHolder.player ?: return
        if (!p.playWhenReady) return
        when (vm.audioPolicy) {
            AudioPolicy.Never -> p.playWhenReady = false
            AudioPolicy.Headphones -> if (!isHeadsetOn()) p.playWhenReady = false
            AudioPolicy.Always -> Unit
        }
        // Если воспроизведение продолжается в фоне — гасим видеодекодер для сохранения батареи
        if (p.playWhenReady) {
            p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, true)
                .build()
        }
    }

    override fun onResume() {
        super.onResume()
        // Возвращаем видеопоток при возврате в приложение (если не выбран режим AudioOnly)
        PlayerHolder.player?.let { p ->
            if (vm.viewMode != ViewMode.AudioOnly) {
                p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, false)
                    .build()
            }
        }
        vm.onForeground()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(plugReceiver) }
        super.onDestroy()
        if (vm.clientSettingsStore.killBackgroundProcesses) {
            try {
                stopService(Intent(this, FarmService::class.java))
                stopService(Intent(this, TeaAudioService::class.java))
            } catch (_: Exception) {}
        }
    }

    private fun onHeadsetUnplugged() {
        if (vm.audioPolicy != AudioPolicy.Headphones) return
        PlayerHolder.player?.playWhenReady = false
    }

    @Suppress("DEPRECATION")
    private fun isHeadsetOn(): Boolean {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return am.isWiredHeadsetOn || am.isBluetoothA2dpOn
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        registerReceiver(plugReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_HEADSET_PLUG)
            addAction("android.media.AUDIO_BECOMING_NOISY")
        })

        setContent {
            BackHandler(
                enabled = vm.screen == Screen.Channel ||
                        vm.screen == Screen.Account ||
                        vm.screen == Screen.Subscriptions ||
                        vm.screen == Screen.Settings ||
                        vm.screen == Screen.AccountSettings ||
                        vm.screen == Screen.ClientSettings
            ) {
                when (vm.screen) {
                    Screen.Subscriptions -> vm.backToAccount()
                    Screen.Settings -> vm.openAccount()
                    Screen.AccountSettings, Screen.ClientSettings -> vm.openSettings()
                    else -> vm.backToHome()
                }
            }
            TeaTVTheme {
                BackHandler(enabled = vm.screen == Screen.Home && vm.tab != MainTab.View) {
                    vm.selectTab(MainTab.View)
                }
                BackHandler(enabled = vm.screen == Screen.Stream) {
                    if (vm.clientSettingsStore.miniPlayerEnabled) {
                        vm.minimizeStream()
                    } else {
                        vm.closeStream()
                    }
                }
                BackHandler(enabled = vm.screen == Screen.Category) {
                    vm.closeCategory()
                }
                BackHandler(enabled = vm.screen == Screen.Vod) {
                    vm.closeVod()
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    Scaffold(modifier = Modifier.fillMaxSize()) { _ ->
                    when (vm.screen) {
                        Screen.Auth -> AuthScreen(onLoginClick = vm::startLogin)

                        Screen.Stream -> vm.currentStream?.let {
                            StreamScreen(
                                target = it,
                                followed = vm.targetFollowed,
                                notified = vm.targetNotified,
                                viewers = vm.viewers,
                                onToggleFollow = vm::toggleFollow,
                                onToggleNotify = vm::toggleNotify,
                                onShowViewers = vm::loadViewers,
                                qualities = vm.qualities,
                                playbackRefreshTrigger = vm.playbackRefreshTrigger,
                                selectedQualityLabel = vm.selectedQualityLabel,
                                onSelectQuality = vm::selectQuality,
                                onRefresh = vm::refreshPlayback,
                                viewMode = vm.viewMode,
                                audioPolicy = vm.audioPolicy,
                                onSelectViewMode = vm::selectViewMode,
                                onSelectPolicy = vm::selectAudioPolicy,
                                playbackUrl = vm.playbackUrl,
                                onSend = vm::sendChat,
                                emotes = vm.emotes,
                                onRedeemReward = vm::redeemReward,
                                badges = vm.badgeMap,
                                points = vm.points,
                                onRefreshPoints = { vm.refreshPoints(force = true) },
                                onPickEmote = vm::pickEmote,
                                isBot = vm.botActive,
                                onBotToggle = vm::setBotMode,
                                chatMessages = vm.chatMessages,
                                chatFontSizeStep = vm.clientSettingsStore.chatFontSizeStep,
                                replySettings = vm.replySettings,
                                isMinimized = { vm.isStreamMinimized },
                                isModerator = vm.isCurrentStreamModerator && vm.isModModeActive,
                                bufferSeconds = vm.videoBufferSizeSeconds,
                                splitMessagesEnabled = vm.isSplitMessagesActive,
                                selectedChatter = vm.selectedChatterInfo,
                                selectedChatMessage = vm.selectedChatMessage,
                                pinnedMessage = vm.pinnedMessage,
                                channelModes = vm.channelModes,
                                modLogs = vm.modLogs,
                                rewardRequests = vm.rewardRequests,
                                prediction = vm.activePrediction,
                                unbanRequests = vm.unbanRequests,
                                onMessageLongPress = vm::toggleSelectChatMessage,
                                onModSliderAction = vm::executeModSliderAction,
                                onHidePinnedMessage = vm::hidePinnedMessageForMe,
                                onUnpinMessage = vm::unpinChatMessage,
                                onUnbanChatter = vm::unbanChatter,
                                onToggleShield = vm::toggleShieldMode,
                                onToggleSubOnly = vm::setSubOnlyMode,
                                onToggleEmoteOnly = vm::setEmoteOnlyMode,
                                onSelectFollowersDuration = vm::setFollowersDuration,
                                onSelectSlowMode = vm::setSlowMode,
                                onFulfillReward = vm::fulfillReward,
                                onRejectReward = vm::rejectReward,
                                onFulfillAllRewards = vm::fulfillAllRewards,
                                onRejectAllRewards = vm::rejectAllRewards,
                                onCreatePrediction = vm::createPrediction,
                                onResolvePrediction = vm::resolvePrediction,
                                onCancelPrediction = vm::cancelPrediction,
                                onApproveUnban = vm::approveUnban,
                                onDenyUnban = vm::denyUnban,
                                onSendAnnounce = vm::sendAnnouncement,
                                onPinMessage = vm::pinChatMessage,
                                onMinimize = vm::minimizeStream,
                                viewerProfile = vm.selectedViewerInfo,
                                isLoadingViewerProfile = vm.isLoadingViewerInfo,
                                onUserClick = { msg -> vm.openViewerProfile(msg.senderLogin ?: msg.sender, msg.senderId) },
                                onDismissViewerProfile = vm::closeViewerProfile
                            )
                        }

                        Screen.Account -> AccountScreen(
                            user = vm.user,
                            onBack = vm::backToHome,
                            onOpenChannel = { vm.user?.let { vm.openChannel(it.id) } },
                            onOpenSubscriptions = vm::openSubscriptions,
                            onOpenSettings = vm::openSettings
                        )



                        Screen.Settings -> SettingsScreen(
                            onBack = vm::openAccount,
                            onOpenAccountSettings = vm::openAccountSettings,
                            onOpenClientSettings = vm::openClientSettings
                        )

                        Screen.AccountSettings -> AccountSettingsScreen(
                            user = vm.user,
                            onBack = vm::openSettings,
                            onLogout = vm::logout
                        )

                        Screen.ClientSettings -> ClientSettingsScreen(
                            clientSettingsStore = vm.clientSettingsStore,
                            replySettingsStore = vm.replySettingsStore,
                            onBack = vm::openSettings
                        )

                        Screen.Subscriptions -> SubscriptionsScreen(
                            subscriptions = vm.mySubscriptions,
                            onBack = vm::backToAccount
                        )

                        Screen.Channel -> ChannelPageScreen(
                            user = vm.channelPageUser,
                            page = vm.channelPageInfo,
                            live = vm.channelPageLive,
                            videos = vm.channelVideos,
                            clips = vm.channelClips,
                            isFollowed = vm.channelIsFollowed,
                            isSubscribed = vm.channelIsSubscribed,
                            isNotifyEnabled = vm.channelNotifyEnabled,
                            isOwnChannel = vm.channelIsOwn,
                            onOpenVod = vm::openVod,
                            onOpenClip = vm::openClip,
                            onToggleFollow = vm::toggleChannelFollow,
                            onToggleNotify = vm::toggleChannelNotify,
                            onToggleSubscribe = vm::toggleChannelSubscription,
                            onOpenStream = {
                                val u = vm.channelPageUser
                                if (u != null) {
                                    vm.openStream(
                                        StreamerItem(
                                            id = u.id,
                                            login = u.login,
                                            name = u.displayName,
                                            avatarUrl = u.avatarUrl,
                                            isLive = vm.channelPageLive != null,
                                            stream = vm.channelPageLive
                                        )
                                    )
                                }
                            },
                            isRefreshing = vm.isChannelRefreshing,
                            onRefresh = { vm.refreshChannel() }
                        )

                        Screen.Category -> vm.currentCategory?.let {
                            CategoryScreen(
                                category = it,
                                details = vm.categoryDetails,
                                streams = vm.categoryStreams,
                                loading = vm.categoryLoading,
                                loadingMore = vm.categoryLoadingMore,
                                onBack = vm::closeCategory,
                                onOpenStream = vm::openStream,
                                onToggleFollow = vm::toggleCategoryFollow,
                                onLoadMore = vm::loadMoreCategoryStreams
                            )
                        }

                        Screen.Vod -> {
                            val u = vm.channelPageUser
                            val live = vm.channelPageLive
                            val videoId = vm.currentVodVideo?.id ?: vm.currentVodClip?.id ?: ""
                            val isClip = vm.currentVodClip != null
                            if (u != null) {
                                val target = StreamerItem(
                                    id = u.id,
                                    login = u.login,
                                    name = u.displayName,
                                    avatarUrl = u.avatarUrl,
                                    isLive = live != null,
                                    stream = live
                                )
                                VodPlayerScreen(
                                    title = vm.vodTitle,
                                    streamUrl = vm.vodPlaybackUrl,
                                    isClip = isClip,
                                    target = target,
                                    followed = vm.channelIsFollowed,
                                    notified = vm.channelNotifyEnabled,
                                    onToggleFollow = vm::toggleChannelFollow,
                                    onToggleNotify = vm::toggleChannelNotify,
                                    chatMessages = vm.vodChatMessages,
                                    emotes = vm.emotes,
                                    onBack = vm::closeVod,
                                    onPositionChanged = { pos ->
                                        if (videoId.isNotBlank() && !isClip) {
                                            vm.loadVodChatAt(videoId, (pos / 1000).toInt())
                                        }
                                    }
                                )
                            }
                        }

                        Screen.Home -> MainScreen(
                            user = vm.user,
                            channel = vm.channel,
                            streamers = vm.streamers,
                            favorites = vm.favorites,
                            categories = vm.categories,
                            tab = vm.tab,
                            onTab = vm::selectTab,
                            onLogout = vm::logout,
                            onOpenStream = vm::openStream,
                            onOpenCategory = vm::openCategory,
                            onToggleFavorite = vm::toggleFavorite,
                            onToggleBot = vm::toggleBotForChannel,
                            searchQuery = vm.searchQuery,
                            searchResults = vm.searchResults,
                            searchLoading = vm.searchLoading,
                            onSearchQuery = vm::onSearchQuery,
                            onOpenChannel = { vm.openChannel(it.id) },
                            onOpenChannelFromSearch = { sc -> vm.openChannel(sc.id) },
                            onOpenAccount = vm::openAccount,
                            isRefreshing = vm.isHomeRefreshing,
                            onRefresh = { vm.refreshHome() },
                            isBotForChannel = vm::isBotForChannel,
                            bufferSeconds = vm.videoBufferSizeSeconds,
                            onBufferChange = vm::setVideoBufferSize,
                            modModeEnabled = vm.isModModeActive,
                            onModModeChange = vm::setModModeEnabled,
                            splitMessagesEnabled = vm.isSplitMessagesActive,
                            onSplitMessagesChange = vm::setSplitMessagesEnabled
                        )
                    }
                }

                if (vm.isStreamMinimized && vm.currentStream != null) {
                    MiniPlayerOverlay(
                        sizeStep = vm.clientSettingsStore.miniPlayerSizeStep,
                        onMaximize = vm::maximizeStream,
                        onClose = vm::closeStream
                    )
                }
            }
        }
    }
}
}
