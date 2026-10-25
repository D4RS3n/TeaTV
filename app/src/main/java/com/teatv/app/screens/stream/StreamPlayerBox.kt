package com.teatv.app.ui.screens.stream

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.teatv.app.R
import com.teatv.app.data.StreamerItem

private val haze = Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.4f), Color.Transparent))

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun StreamPlayerBox(
    target: StreamerItem,
    player: ExoPlayer,
    useFull: Boolean,
    audioOnly: Boolean,
    showControls: Boolean,
    playing: Boolean,
    onToggleControls: () -> Unit,
    onTogglePlaying: () -> Unit,
    onShare: () -> Unit,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit,
    onToggleFullscreen: () -> Unit
) {
    Box(
        modifier = Modifier
            .then(
                when {
                    useFull -> Modifier.fillMaxSize()
                    audioOnly -> Modifier.fillMaxWidth().height(64.dp)
                    else -> Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                }
            )
            .background(Color.Black)
            .clickable { onToggleControls() }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    setPlayer(player)
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (showControls) {
            IconButton(
                onClick = onTogglePlaying,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(64.dp)
                    .background(Color.Black.copy(alpha = 0.35f), CircleShape)
            ) {
                Icon(
                    painter = painterResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play),
                    contentDescription = "Пауза/плей",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }

            Row(modifier = Modifier.align(Alignment.TopEnd)) {
                IconButton(
                    onClick = onShare,
                    modifier = Modifier.size(40.dp).background(haze)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_share),
                        contentDescription = "Поделиться",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.size(40.dp).background(haze)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_settings),
                        contentDescription = "Настройки",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            IconButton(
                onClick = onRefresh,
                modifier = Modifier.align(Alignment.BottomStart).size(40.dp).background(haze)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_refresh),
                    contentDescription = "Обновить",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            if (!audioOnly) IconButton(
                onClick = onToggleFullscreen,
                modifier = Modifier.align(Alignment.BottomEnd).size(40.dp).background(haze)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_fullscreen),
                    contentDescription = "Во весь экран",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}