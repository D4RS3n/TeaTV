package com.teatv.app.ui.screens.videos

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.teatv.app.R
import com.teatv.app.auth.TwitchAuth
import com.teatv.app.chat.ChatMessage
import com.teatv.app.chat.emotes.EmoteBundles
import com.teatv.app.data.StreamerItem
import com.teatv.app.player.PlayerHolder
import com.teatv.app.ui.screens.chat.ChatPanel
import com.teatv.app.ui.screens.stream.StreamInfoPanel
import com.teatv.app.ui.screens.stream.StreamPlayerBox
import com.teatv.app.ui.theme.TeaColors
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.microseconds

private val haze = Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.4f), Color.Transparent))

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun VodPlayerScreen(
    title: String,
    streamUrl: String?,
    isClip: Boolean,
    target: StreamerItem,
    followed: Boolean?,
    notified: Boolean,
    onToggleFollow: () -> Unit,
    onToggleNotify: () -> Unit,
    chatMessages: List<ChatMessage>,
    emotes: EmoteBundles,
    onBack: () -> Unit,
    onPositionChanged: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var showControls by remember { mutableStateOf(false) }


    var fullscreen by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(PlayerHolder.player?.playWhenReady ?: true) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isEnded by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val activity = context as? ComponentActivity

    // Автоскрытие элементов управления через 3.5 секунды
    LaunchedEffect(showControls, playing) {
        if (showControls && playing) {
            kotlinx.coroutines.delay(3500)
            showControls = false
        }
    }

    // Защита от затухания и выключения экрана при просмотре VOD
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    val isLandscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val useFull = fullscreen && isLandscape

    val player = remember {
        PlayerHolder.player ?: ExoPlayer.Builder(context)
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(8000, 20000, 1500, 2500)
                    .setBackBuffer(10000, false)
                    .build()
            )
            .build()
            .apply {
                setAudioAttributes(AudioAttributes.DEFAULT, true)
                setSeekParameters(SeekParameters.CLOSEST_SYNC)
                playWhenReady = true
            }
    }

    DisposableEffect(Unit) {
        onDispose {
            val p = PlayerHolder.player
            if (p != null) {
                p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
                    .build()
            }
        }
    }

    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onPlayWhenReadyChanged(ready: Boolean, reason: Int) {
                playing = ready
            }

            // НОВОЕ: Отслеживаем конец воспроизведения
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    isEnded = true
                    playing = false
                } else if (playbackState == Player.STATE_READY || playbackState == Player.STATE_BUFFERING) {
                    isEnded = false
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("TeaTV-Vod", "playback error ${error.errorCodeName}: ${error.message} | cause=${error.cause}")
                // При ошибке тоже можно считать, что воспроизведение остановлено
                playing = false
            }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }

    LaunchedEffect(Unit) {
        PlayerHolder.player = player
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false)
            .build()
    }

    LaunchedEffect(streamUrl) {
        if (streamUrl.isNullOrBlank()) {
            Log.w("TeaTV-Vod", "empty url, clearing player clip=$isClip")
            player.stop()
            player.clearMediaItems()
            playing = false
            return@LaunchedEffect
        }
        Log.i("TeaTV-Vod", "loading url=${streamUrl} clip=$isClip")
        val ds = DefaultHttpDataSource.Factory().setDefaultRequestProperties(
            mapOf(
                "Client-ID" to TwitchAuth.ANDROID_CLIENT_ID,
                "Referer" to "https://www.twitch.tv/"
            )
        )
        player.setMediaSource(
            DefaultMediaSourceFactory(ds)
                .createMediaSource(MediaItem.fromUri(streamUrl))
        )
        player.prepare()
    }

    // НОВОЕ: Функция умной перемотки с автоматическим возобновлением
    val smartSeek: (Long) -> Unit = { newPosition ->
        player.seekTo(newPosition)
        player.playWhenReady = true // Принудительно возобновляем воспроизведение
        playing = true
        isEnded = false // Сбрасываем флаг окончания
    }

    LaunchedEffect(player) {
        while (true) {
            kotlinx.coroutines.delay(250) // 4 раза в секунду вместо 2000 раз/сек для экономии CPU и батареи
            val newPos = player.currentPosition
            currentPosition = newPos
            duration = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
            if (!isClip) onPositionChanged(newPos)
        }
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

    Column(
        modifier = modifier
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
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            StreamPlayerBox(
                target = target,
                player = player,
                useFull = useFull,
                audioOnly = false,
                showControls = false,
                playing = playing,
                onToggleControls = { showControls = !showControls },
                onTogglePlaying = {
                    playing = !playing
                    player.playWhenReady = playing
                },
                onShare = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("teatv-vod", title))
                },
                onOpenSettings = { },
                onRefresh = { },
                onToggleFullscreen = { fullscreen = !fullscreen }
            )

            if (showControls) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .size(40.dp)
                        .background(haze, CircleShape)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = "Назад",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("teatv-vod", title))
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(40.dp)
                        .background(haze, CircleShape)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_share),
                        contentDescription = "Поделиться",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconButton(
                        onClick = { smartSeek((currentPosition - 15000).coerceAtLeast(0)) },
                        modifier = Modifier.size(40.dp).background(haze, CircleShape)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_rewind),
                            contentDescription = "-15с",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // НОВОЕ: Кнопка Play/Pause или Replay в зависимости от состояния
                    IconButton(
                        onClick = {
                            if (isEnded) {
                                // Логика повтора: сброс в начало и запуск
                                player.seekTo(0)
                                smartSeek(0)
                            } else {
                                // Обычная логика паузы/плея
                                playing = !playing
                                player.playWhenReady = playing
                            }
                        },
                        modifier = Modifier.size(64.dp).background(haze, CircleShape)
                    ) {
                        val iconRes = when {
                            isEnded -> R.drawable.ic_refresh // Или R.drawable.ic_replay, если есть такая иконка
                            playing -> R.drawable.ic_pause
                            else -> R.drawable.ic_play
                        }
                        Icon(
                            painter = painterResource(iconRes),
                            contentDescription = if (isEnded) "Повторить" else "Пауза/плей",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    IconButton(
                        onClick = { smartSeek((currentPosition + 15000).coerceAtMost(duration)) },
                        modifier = Modifier.size(40.dp).background(haze, CircleShape)
                    ) {
                        // Примечание: тут у тебя стоял ic_refresh, логичнее ic_forward, но оставил как было
                        Icon(
                            painter = painterResource(R.drawable.ic_refresh),
                            contentDescription = "+15с",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                IconButton(
                    onClick = { fullscreen = !fullscreen },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(40.dp)
                        .background(haze, CircleShape)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_fullscreen),
                        contentDescription = "Во весь экран",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                if (duration > 0) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 52.dp, top = 8.dp, bottom = 8.dp)
                    ) {
                        VodSeekBar(
                            position = currentPosition,
                            duration = duration,
                            onSeek = { smartSeek(it) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(formatTime(currentPosition), color = Color.White, fontSize = 10.sp)
                            Text(formatTime(duration), color = Color.White, fontSize = 10.sp)
                        }
                    }
                }
            }
        }

        if (!useFull) {
            StreamInfoPanel(
                target = target,
                followed = followed,
                notified = notified,
                onToggleFollow = onToggleFollow,
                onToggleNotify = onToggleNotify,
                onViewers = { },
                vodTitle = title
            )

            val currentSec = (currentPosition / 1000).toInt()
            val visibleChatMessages = remember(chatMessages, currentSec) {
                if (isClip) {
                    chatMessages
                } else {
                    chatMessages.filter { it.offsetSeconds <= currentSec }.takeLast(100)
                }
            }

            ChatPanel(
                messages = visibleChatMessages,
                onSend = { _, _ -> },
                emotes = emotes,
                onPickEmote = {},
                badges = emptyMap(),
                points = null,
                onRefreshPoints = {},
                onRedeemReward = { _, _ -> },
                isBot = false,
                onBotToggle = {},
                modifier = Modifier.weight(1f),
                readOnly = true
            )
        }
    }
}

@Composable
private fun VodSeekBar(
    position: Long,
    duration: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var widthPx by remember { mutableFloatStateOf(1f) }
    var isDragging by remember { mutableStateOf(false) }
    var dragFrac by remember { mutableFloatStateOf(0f) }

    val actualFrac = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val displayFrac = if (isDragging) dragFrac else actualFrac
    val accent = TeaColors.Accent

    Box(
        modifier = modifier
            .height(36.dp)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(widthPx, duration) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (duration > 0) {
                        isDragging = true
                        dragFrac = (down.position.x / widthPx).coerceIn(0f, 1f)
                        down.consume()
                    }
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) {
                            if (isDragging && duration > 0) {
                                onSeek((dragFrac * duration).toLong())
                            }
                            isDragging = false
                            break
                        }
                        if (duration > 0) {
                            dragFrac = (change.position.x / widthPx).coerceIn(0f, 1f)
                            change.consume()
                        }
                    }
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(if (isDragging) 4.dp else 3.dp)
                .background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(2.dp))
        )
        Box(
            Modifier
                .fillMaxWidth(displayFrac)
                .height(if (isDragging) 4.dp else 3.dp)
                .background(accent, RoundedCornerShape(2.dp))
        )
        Box(
            Modifier
                .size(if (isDragging) 16.dp else 12.dp)
                .offset {
                    val halfPx = (if (isDragging) 8.dp else 6.dp).toPx()
                    IntOffset(((widthPx * displayFrac) - halfPx).roundToInt(), 0)
                }
                .background(Color.White, CircleShape)
        )
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).toInt()
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }
}