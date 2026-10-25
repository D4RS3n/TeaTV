package com.teatv.app.ui.screens.stream

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLivePlaybackSpeedControl
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.teatv.app.AudioPolicy
import com.teatv.app.TeaAudioService
import com.teatv.app.ViewMode
import com.teatv.app.chat.ChatMessage
import com.teatv.app.chat.emotes.Emote
import com.teatv.app.chat.emotes.EmoteBundles
import com.teatv.app.chat.points.ChannelPointsState
import com.teatv.app.chat.points.PointReward
import com.teatv.app.chat.points.PointsRepository
import com.teatv.app.data.StreamerItem
import com.teatv.app.player.PlayerHolder
import com.teatv.app.player.QualityOption
import com.teatv.app.ui.theme.TeaColors
import com.teatv.app.ui.screens.chat.ChatPanel
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.teatv.app.R
import com.teatv.app.settings.DefaultReplySettings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import com.teatv.app.mod.ChannelModesState
import com.teatv.app.mod.ChatterInfo
import com.teatv.app.mod.ModLogItem
import com.teatv.app.mod.PinnedMessage
import com.teatv.app.mod.PredictionInfo
import com.teatv.app.mod.RewardRequestItem
import com.teatv.app.mod.UnbanRequestItem
import com.teatv.app.screens.stream.mod.ChatterDrawer
import com.teatv.app.screens.stream.mod.ModSliderAction
import com.teatv.app.screens.stream.mod.ModToolsDrawer
import com.teatv.app.settings.ReplySettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun StreamScreen(
    target: StreamerItem,
    followed: Boolean?,
    notified: Boolean,
    viewers: List<String>?,
    playbackUrl: String?,
    qualities: List<QualityOption>,
    playbackRefreshTrigger: Long = 0L,
    selectedQualityLabel: String? = null,
    chatMessages: List<ChatMessage>,
    onSend: (String, String?) -> Unit,
    viewMode: ViewMode,
    badges: Map<String, String>,
    onRefreshPoints: () -> Unit,
    points: ChannelPointsState?,
    onRedeemReward: (PointReward, String?) -> Unit,
    audioPolicy: AudioPolicy,
    onToggleFollow: () -> Unit,
    onToggleNotify: () -> Unit,
    onShowViewers: () -> Unit,
    isBot: Boolean, onBotToggle: (Boolean) -> Unit,
    emotes: EmoteBundles, onPickEmote: (Emote) -> Unit,
    onSelectQuality: (QualityOption) -> Unit,
    onSelectViewMode: (ViewMode) -> Unit,
    onSelectPolicy: (AudioPolicy) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    chatFontSizeStep: Int = 3,
    splitMessagesEnabled: Boolean = true,
    replySettings: ReplySettings = DefaultReplySettings,
    isMinimized: () -> Boolean = { false },
    isModerator: Boolean = false,
    selectedChatter: ChatterInfo? = null,
    selectedChatMessage: ChatMessage? = null,
    pinnedMessage: PinnedMessage? = null,
    channelModes: ChannelModesState = ChannelModesState(),
    modLogs: List<ModLogItem> = emptyList(),
    rewardRequests: List<RewardRequestItem> = emptyList(),
    prediction: PredictionInfo? = null,
    unbanRequests: List<UnbanRequestItem> = emptyList(),
    onMessageLongPress: (ChatMessage) -> Unit = {},
    onModSliderAction: (ChatMessage, ModSliderAction) -> Unit = { _, _ -> },
    onHidePinnedMessage: () -> Unit = {},
    onUnpinMessage: () -> Unit = {},
    onUnbanChatter: (String) -> Unit = {},
    onToggleShield: () -> Unit = {},
    onToggleSubOnly: (Boolean) -> Unit = {},
    onToggleEmoteOnly: (Boolean) -> Unit = {},
    onSelectFollowersDuration: (Int) -> Unit = {},
    onSelectSlowMode: (Int) -> Unit = {},
    onFulfillReward: (RewardRequestItem) -> Unit = {},
    onRejectReward: (RewardRequestItem) -> Unit = {},
    onFulfillAllRewards: () -> Unit = {},
    onRejectAllRewards: () -> Unit = {},
    onCreatePrediction: (String, List<String>, Int) -> Unit = { _, _, _ -> },
    onResolvePrediction: (String) -> Unit = {},
    onCancelPrediction: () -> Unit = {},
    onApproveUnban: (UnbanRequestItem) -> Unit = {},
    onDenyUnban: (UnbanRequestItem) -> Unit = {},
    onSendAnnounce: (String) -> Unit = {},
    onPinMessage: (ChatMessage) -> Unit = {},
    bufferSeconds: Int = 15
) {
    var showStats by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showLeftDrawer by remember { mutableStateOf(false) }
    var showRightDrawer by remember { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(PlayerHolder.player?.playWhenReady ?: true) }
    val scope = rememberCoroutineScope()

    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val isLandscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val useFull = fullscreen && isLandscape
    val density = LocalDensity.current
    val dropOffset = IntOffset(0, with(density) { 44.dp.roundToPx() })

    val maxBufferMs = (bufferSeconds * 1000).coerceIn(4000, 60000)
    val minBufferMs = (maxBufferMs / 3).coerceIn(1500, 15000)
    val bufferForPlayback = minOf(1000, minBufferMs)
    val bufferAfterRebuffer = minOf(2000, minBufferMs)

    // Переиспользуем живой плеер фоновой сессии; создаём только если его нет.
    val player = remember {
        PlayerHolder.player ?: ExoPlayer.Builder(context)
            .setLivePlaybackSpeedControl(
                DefaultLivePlaybackSpeedControl.Builder()
                    .setFallbackMaxPlaybackSpeed(1.05f)
                    .setFallbackMinPlaybackSpeed(0.97f)
                    .setMinUpdateIntervalMs(1000)
                    .setProportionalControlFactor(0.1f)
                    .build()
            )
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(minBufferMs, maxBufferMs, bufferForPlayback, bufferAfterRebuffer)
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .setBackBuffer(2000, false)
                    .build()
            )
            .build()
            .apply {
                setAudioAttributes(AudioAttributes.DEFAULT, true)
                setSeekParameters(SeekParameters.CLOSEST_SYNC)
                playWhenReady = true
            }
    }
    // Уход с экрана: гасим видео, плеер не трогаем — владелец сервис.
    DisposableEffect(Unit) {
        onDispose {
            if (!isMinimized()) {
                val p = PlayerHolder.player
                if (p != null) {
                    p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
                        .build()
                }
            }
        }
    }
    // Wake Lock + обработка ошибок плеера
    DisposableEffect(player) {
        playing = player.playWhenReady

        // Wake Lock: экран не гаснет во время стрима
        val wakeLock = (context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager)
            .newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "TeaTV:StreamWakeLock")

        if (player.playWhenReady && player.playbackState != Player.STATE_IDLE) {
            wakeLock.acquire(10*60*1000L /*10 минут*/)
        }

        var retryCount = 0
        val maxRetries = 3

        val l = object : Player.Listener {
            override fun onPlayWhenReadyChanged(ready: Boolean, reason: Int) {
                playing = ready
                // Обновляем Wake Lock при изменении состояния
                if (ready && player.playbackState != Player.STATE_IDLE) {
                    wakeLock.acquire(10*60*1000L)
                } else {
                    if (wakeLock.isHeld) wakeLock.release()
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                // Wake Lock при старте воспроизведения
                if (state == Player.STATE_READY && player.playWhenReady) {
                    wakeLock.acquire(10*60*1000L)
                }
                // Сброс счётчика retry при успешном старте
                if (state == Player.STATE_READY) {
                    retryCount = 0
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                android.util.Log.e("TeaTV-Stream", "Playback error: ${error.errorCodeName} - ${error.message}")

                // Автоматический retry только для сетевых ошибок
                if (error.errorCode in listOf(
                        androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                        androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                        androidx.media3.common.PlaybackException.ERROR_CODE_IO_UNSPECIFIED
                    ) && retryCount < maxRetries
                ) {
                    retryCount++
                    android.util.Log.d("TeaTV-Stream", "Auto-retry $retryCount/$maxRetries in 3s...")

                    scope.launch(kotlinx.coroutines.Dispatchers.Main) {
                        kotlinx.coroutines.delay(3000)
                        player.prepare()  // Перезапускаем плеер
                        player.playWhenReady = true
                    }
                } else {
                    // Не сетевая ошибка или исчерпаны retry — отпускаем Wake Lock
                    if (wakeLock.isHeld) wakeLock.release()
                }
            }
        }

        player.addListener(l)

        onDispose {
            player.removeListener(l)
            if (wakeLock.isHeld) wakeLock.release()
        }
    }



    LaunchedEffect(Unit) {
        val needAvatar = PlayerHolder.streamId != target.id || PlayerHolder.streamAvatar == null
        PlayerHolder.player = player
        PlayerHolder.streamName = target.name
        PlayerHolder.streamId = target.id
        PlayerHolder.streamGame = target.stream?.gameName
        if (needAvatar) {
            launch(Dispatchers.IO) {
                val cachedForId = PlayerHolder.streamId
                val bm = loadAvatarBitmap(target.avatarUrl, 96)
                if (PlayerHolder.streamId == cachedForId && bm != null) {
                    PlayerHolder.streamAvatar = bm
                }
            }
        }
        // Вернулись из фонового аудио — включаем видео обратно (если не AudioOnly).
        if (viewMode != ViewMode.AudioOnly) {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false)
                .build()
        }
        // Восстановление видео-рендера после возврата из фона
        launch(kotlinx.coroutines.Dispatchers.Main) {
            kotlinx.coroutines.delay(500)
            if (player.playWhenReady && player.playbackState == Player.STATE_READY) {
                // Форсируем перерисовку видео
                player.seekTo(player.currentPosition)
            }
        }
    }
    LaunchedEffect(playbackUrl, bufferSeconds, playbackRefreshTrigger) {
        val url = playbackUrl
        if (url.isNullOrBlank()) {
            player.stop()
            player.clearMediaItems()
            playing = false
            return@LaunchedEffect
        }
        val targetOffsetMs = when (bufferSeconds) {
            5 -> 2000L   // Ультра-низкая задержка (~2 сек)
            10 -> 3000L  // Низкая задержка (~3 сек)
            15 -> 4000L  // Стандартная задержка Twitch (~4 сек)
            30 -> 12000L // Буфер для нестабильной сети (~12 сек)
            60 -> 25000L // Максимальный буфер для слабой сети (~25 сек)
            else -> 4000L
        }
        val mediaItem = MediaItem.Builder()
            .setUri(url)
            .setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder()
                    .setTargetOffsetMs(targetOffsetMs)
                    .setMinOffsetMs((targetOffsetMs * 0.5f).toLong().coerceAtLeast(1000L))
                    .setMaxOffsetMs((targetOffsetMs * 2.0f).toLong().coerceAtLeast(6000L))
                    .setMinPlaybackSpeed(0.97f)
                    .setMaxPlaybackSpeed(1.05f)
                    .build()
            )
            .build()

        player.setMediaSource(
            HlsMediaSource.Factory(DefaultHttpDataSource.Factory())
                .setAllowChunklessPreparation(true)
                .createMediaSource(mediaItem)
        )
        player.prepare()
        player.seekToDefaultPosition()
        playing = player.playWhenReady
        // Сессия стартовала: сервис становится владельцем плеера и уведомления.
        ContextCompat.startForegroundService(
            context,
            Intent(context, TeaAudioService::class.java)
        )
    }
    LaunchedEffect(viewMode) {
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, viewMode == ViewMode.AudioOnly)
            .build()
    }

    LaunchedEffect(fullscreen) {
        activity?.requestedOrientation =
            if (fullscreen) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        activity?.let { a ->
            val c = WindowCompat.getInsetsController(a.window, a.window.decorView)
            if (fullscreen) c.hide(WindowInsetsCompat.Type.systemBars())
            else c.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            activity?.let { a ->
                WindowCompat.getInsetsController(a.window, a.window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
    BackHandler(enabled = fullscreen) { fullscreen = false }
    BackHandler(enabled = showLeftDrawer || showRightDrawer) {
        showLeftDrawer = false
        showRightDrawer = false
    }

    Box(modifier = modifier.fillMaxSize().background(TeaColors.Background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(TeaColors.Background)
            .then(
                if (useFull) Modifier
                else Modifier
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
            )
    ) {
        StreamPlayerBox(
            target = target,
            player = player,
            useFull = useFull,
            audioOnly = viewMode == ViewMode.AudioOnly,
            showControls = showControls,
            playing = playing,
            onToggleControls = { showControls = !showControls },
            onTogglePlaying = {
                playing = !playing
                player.playWhenReady = playing
            },
            onShare = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(
                    ClipData.newPlainText("teatv-stream", "https://www.twitch.tv/${target.login}")
                )
            },
            onOpenSettings = { showSettings = true },
            onRefresh = {
                player.seekToDefaultPosition()
                onRefresh()
            },
            onToggleFullscreen = { fullscreen = !fullscreen }
        )

        if (!useFull) {
            StreamInfoPanel(
                target = target,
                followed = followed,
                notified = notified,
                onToggleFollow = onToggleFollow,
                onToggleNotify = onToggleNotify,
                onViewers = {
                    showStats = true
                    onShowViewers()
                }
            )
        }
        if (!useFull) {
            ChatPanel(
                messages = chatMessages,
                onSend = onSend,
                emotes = emotes,
                onPickEmote = onPickEmote,
                badges = badges,
                points = points,
                onRefreshPoints = onRefreshPoints,
                onRedeemReward = onRedeemReward,
                isBot = isBot, onBotToggle = onBotToggle,
                fontSizeStep = chatFontSizeStep,
                splitMessagesEnabled = splitMessagesEnabled,
                replySettings = replySettings,
                isModerator = isModerator,
                selectedMessageId = selectedChatMessage?.id,
                pinnedMessage = pinnedMessage,
                onMessageLongPress = onMessageLongPress,
                onModSliderAction = onModSliderAction,
                onHidePinnedMessage = onHidePinnedMessage,
                onUnpinMessage = onUnpinMessage,
                modifier = Modifier.weight(1f)
            )
        }
    }

    // Если активен режим модератора: аккуратные маркеры шторок по краям экрана
    val isKeyboardOpen = WindowInsets.ime.getBottom(density) > 0
    if (isModerator && !useFull && !isKeyboardOpen) {
        // Левый маркер (Шторка чаттера и режимов)
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(y = 40.dp)
                .clip(RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp))
                .background(TeaColors.Surface.copy(alpha = 0.9f))
                .border(1.dp, TeaColors.Accent.copy(alpha = 0.6f), RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp))
                .clickable { showLeftDrawer = true }
                .padding(horizontal = 6.dp, vertical = 10.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_shield),
                contentDescription = "Шторка чаттера",
                tint = TeaColors.Accent,
                modifier = Modifier.size(18.dp)
            )
        }

        // Правый маркер (Шторка модераторского инструментария)
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(y = 40.dp)
                .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                .background(TeaColors.Surface.copy(alpha = 0.9f))
                .border(1.dp, TeaColors.Accent.copy(alpha = 0.6f), RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                .clickable { showRightDrawer = true }
                .padding(horizontal = 6.dp, vertical = 10.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.tab_tools),
                contentDescription = "Модерский инструментарий",
                tint = TeaColors.Accent,
                modifier = Modifier.size(18.dp)
            )
        }
    }

    // Левая шторка: Чаттер и режимы канала
    AnimatedVisibility(
        visible = showLeftDrawer,
        enter = slideInHorizontally { -it } + fadeIn(),
        exit = slideOutHorizontally { -it } + fadeOut()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable { showLeftDrawer = false }
            )
            ChatterDrawer(
                channelName = target.name,
                chatter = selectedChatter,
                modes = channelModes,
                onClose = { showLeftDrawer = false },
                onUnban = onUnbanChatter,
                onToggleShield = onToggleShield,
                onToggleSubOnly = onToggleSubOnly,
                onToggleEmoteOnly = onToggleEmoteOnly,
                onSelectFollowersDuration = onSelectFollowersDuration,
                onSelectSlowMode = onSelectSlowMode,
                onOpenBlockedWords = { },
                onOpenPermittedWords = { },
                modifier = Modifier.align(Alignment.CenterStart)
            )
        }
    }

    // Правая шторка: 5 квадратов модерации
    AnimatedVisibility(
        visible = showRightDrawer,
        enter = slideInHorizontally { it } + fadeIn(),
        exit = slideOutHorizontally { it } + fadeOut()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable { showRightDrawer = false }
            )
            ModToolsDrawer(
                channelName = target.name,
                logs = modLogs,
                rewards = rewardRequests,
                prediction = prediction,
                unbanRequests = unbanRequests,
                pinnedMessage = pinnedMessage,
                selectedChatMessage = selectedChatMessage,
                onClose = { showRightDrawer = false },
                onFulfillReward = onFulfillReward,
                onRejectReward = onRejectReward,
                onFulfillAllRewards = onFulfillAllRewards,
                onRejectAllRewards = onRejectAllRewards,
                onCreatePrediction = onCreatePrediction,
                onResolvePrediction = onResolvePrediction,
                onCancelPrediction = onCancelPrediction,
                onApproveUnban = onApproveUnban,
                onDenyUnban = onDenyUnban,
                onSendAnnounce = onSendAnnounce,
                onPinMessage = onPinMessage,
                onUnpinMessage = onUnpinMessage,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
    }

    if (showSettings) {
        StreamSettingsDialog(
            playbackUrl = playbackUrl,
            qualities = qualities,
            selectedQualityLabel = selectedQualityLabel,
            viewMode = viewMode,
            audioPolicy = audioPolicy,
            dropOffset = dropOffset,
            onSelectQuality = { q ->
                onSelectQuality(q)
                showSettings = false
            },
            onSelectViewMode = onSelectViewMode,
            onSelectPolicy = onSelectPolicy,
            onDismiss = { showSettings = false }
        )
    }

    if (showStats) {
        StreamStatsDialog(
            target = target,
            viewers = viewers,
            onDismiss = { showStats = false }
        )
    }
}

private suspend fun loadAvatarBitmap(url: String, sizePx: Int): Bitmap? = withContext(Dispatchers.IO) {
    if (url.isBlank()) return@withContext null
    try {
        val req = Request.Builder().url(url).build()
        PointsRepository.httpClient().newCall(req).execute().use { resp ->
            val raw = resp.body?.byteStream()?.use { BitmapFactory.decodeStream(it) }
                ?: return@withContext null
            if (raw.width == sizePx && raw.height == sizePx) {
                raw
            } else {
                val scaled = Bitmap.createScaledBitmap(raw, sizePx, sizePx, true)
                if (scaled !== raw) raw.recycle()
                scaled
            }
        }
    } catch (_: Exception) {
        null
    }
}