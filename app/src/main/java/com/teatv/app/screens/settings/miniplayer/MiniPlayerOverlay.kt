package com.teatv.app.screens.settings.miniplayer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.ui.PlayerView
import com.teatv.app.player.PlayerHolder
import com.teatv.app.ui.theme.TeaColors
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun MiniPlayerOverlay(
    sizeStep: Int,
    onMaximize: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val player = PlayerHolder.player ?: return
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val scope = rememberCoroutineScope()

    // Размеры мини-плеера в зависимости от настройки
    val (widthDp, heightDp) = when (sizeStep) {
        0 -> 140.dp to 79.dp
        2 -> 220.dp to 124.dp
        else -> 180.dp to 101.dp // 1 (стандарт)
    }

    val screenWidthPx = with(density) { config.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { config.screenHeightDp.dp.toPx() }
    val widthPx = with(density) { widthDp.toPx() }
    val heightPx = with(density) { heightDp.toPx() }
    val marginPx = with(density) { 16.dp.toPx() }
    val bottomNavMarginPx = with(density) { 90.dp.toPx() } // чтобы не закрывать нижнюю панель

    // Начальная позиция: правый нижний угол
    val offsetX = remember { Animatable(screenWidthPx - widthPx - marginPx) }
    val offsetY = remember { Animatable(screenHeightPx - heightPx - bottomNavMarginPx) }

    // Гарантируем, что видеодекодер включен для отображения в мини-плеере
    LaunchedEffect(player) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false)
            .build()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt()) }
                .size(widthDp, heightDp)
                .shadow(12.dp, RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black)
                .border(1.5.dp, TeaColors.Accent.copy(alpha = 0.8f), RoundedCornerShape(10.dp))
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = {
                            scope.launch {
                                // Если смахнули далеко за левый или правый край — закрываем стрим
                                val swipedLeft = offsetX.value < -widthPx * 0.4f
                                val swipedRight = offsetX.value > screenWidthPx - widthPx * 0.6f

                                if (swipedLeft || swipedRight) {
                                    val targetDismissX = if (swipedLeft) -widthPx * 1.5f else screenWidthPx + widthPx
                                    offsetX.animateTo(targetDismissX, spring())
                                    onClose()
                                } else {
                                    // Доводка (магнит) к ближайшей стороне экрана
                                    val snapToLeft = offsetX.value < (screenWidthPx - widthPx) / 2
                                    val targetX = if (snapToLeft) marginPx else screenWidthPx - widthPx - marginPx
                                    val targetY = offsetY.value.coerceIn(marginPx, screenHeightPx - heightPx - bottomNavMarginPx)

                                    launch { offsetX.animateTo(targetX, spring()) }
                                    launch { offsetY.animateTo(targetY, spring()) }
                                }
                            }
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        scope.launch {
                            offsetX.snapTo(offsetX.value + dragAmount.x)
                            offsetY.snapTo(offsetY.value + dragAmount.y)
                        }
                    }
                }
                .clickable { onMaximize() }
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        this.player = player
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Кнопка быстрого закрытия миниатюры
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.75f))
                    .clickable { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "✕",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 15.sp
                )
            }
        }
    }
}
