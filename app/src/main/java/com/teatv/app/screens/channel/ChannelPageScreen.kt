package com.teatv.app.ui.screens.channel

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import coil.compose.AsyncImage
import com.teatv.app.R
import com.teatv.app.data.ChannelClip
import com.teatv.app.data.ChannelPage
import com.teatv.app.data.ChannelVideo
import com.teatv.app.data.LiveStream
import com.teatv.app.data.TwitchUser
import com.teatv.app.ui.theme.TeaColors
import java.util.Locale
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.rememberPullRefreshState
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ChannelPageScreen(
    user: TwitchUser?,
    page: ChannelPage?,
    live: LiveStream?,
    videos: List<ChannelVideo>,
    clips: List<ChannelClip>,
    isFollowed: Boolean?,
    isSubscribed: Boolean?,
    isNotifyEnabled: Boolean,
    isOwnChannel: Boolean,
    onOpenVod: (ChannelVideo) -> Unit,
    onOpenClip: (ChannelClip) -> Unit,
    onToggleFollow: () -> Unit,
    onToggleNotify: () -> Unit,
    onToggleSubscribe: () -> Unit,
    onOpenStream: () -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val brand = remember(page?.brandColor) { parseBrand(page?.brandColor) }
    val refreshState = rememberPullRefreshState(isRefreshing, onRefresh)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TeaColors.Background)
            .statusBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .pullRefresh(refreshState)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item { ChannelBanner(live, page, brand) }
                item { ChannelInfoRow(user, page, live) }
                item {
                    ChannelIconRow(
                        brand = brand,
                        isOwnChannel = isOwnChannel,
                        isFollowed = isFollowed,
                        isSubscribed = isSubscribed,
                        isNotifyEnabled = isNotifyEnabled,
                        onToggleFollow = onToggleFollow,
                        onToggleNotify = onToggleNotify,
                        onToggleSubscribe = onToggleSubscribe,
                        onShare = {
                            val login = user?.login ?: return@ChannelIconRow
                            try {
                                context.startActivity(
                                    Intent.createChooser(
                                        Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(
                                                Intent.EXTRA_TEXT,
                                                "https://www.twitch.tv/$login"
                                            )
                                        },
                                        "Поделиться каналом"
                                    )
                                )
                            } catch (_: Exception) {
                            }
                        }
                    )
                }
                item { Spacer(Modifier.height(10.dp)) }
                item {
                    ChannelTabBar(
                        brand = brand,
                        selected = selectedTab,
                        onSelect = { i ->
                            if (i == 3) onOpenStream()
                            else selectedTab = i
                        }
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
                item {
                    when (selectedTab) {
                        0 -> ChannelTabHome(live, page, brand, onOpenStream)
                        1 -> ChannelTabAbout(
                            user?.description,
                            page?.panels ?: emptyList(),
                            page?.socials ?: emptyList(),
                            brand
                        )
                        2 -> ChannelTabMedia(videos, clips, brand, onOpenVod, onOpenClip)
                    }
                }
            }

            PullRefreshIndicator(
                refreshing = isRefreshing,
                state = refreshState,
                modifier = Modifier.align(Alignment.TopCenter),
                backgroundColor = TeaColors.Surface, // Цвет фона кружка
                contentColor = TeaColors.Accent      // Цвет самой крутящейся стрелочки/индикатора
            )
        }
    }
}

@Composable
private fun ChannelBanner(live: LiveStream?, page: ChannelPage?, brand: Color) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .background(TeaColors.Surface)
        ) {
            val model = if (live != null) {
                live.thumbnailUrl.replace("{width}x{height}", "640x360")
            } else {
                page?.bannerUrl
            }
            if (!model.isNullOrBlank()) {
                AsyncImage(
                    model = model,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (live != null) {
                Text(
                    "LIVE",
                    color = TeaColors.TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(12.dp)
                        .background(TeaColors.Live, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(brand)
        )
    }
}

@Composable
private fun ChannelInfoRow(
    user: TwitchUser?,
    page: ChannelPage?,
    live: LiveStream?,
    onOpenChannel: (() -> Unit)? = null // <-- ДОБАВЛЕНО
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = user?.avatarUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(TeaColors.Surface)
        )
        Spacer(Modifier.width(16.dp))

        // Делаем текстовый блок кликабельным
        Column(
            modifier = Modifier
                .weight(1f)
                .then(if (onOpenChannel != null) Modifier.clickable { onOpenChannel() } else Modifier)
        ) {
            Text(
                user?.displayName ?: "",
                color = TeaColors.TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if ((page?.followersCount ?: -1) >= 0) {
                Text(
                    "${formatCount(page?.followersCount ?: 0)} фоловеров",
                    color = TeaColors.TextSecondary,
                    fontSize = 13.sp
                )
            }
            if (live != null) {
                Text(
                    "${live.gameName} • ${formatCount(live.viewerCount)} зрителей",
                    color = TeaColors.Live,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    "Офлайн",
                    color = TeaColors.TextSecondary,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun ChannelIconRow(
    brand: Color,
    isOwnChannel: Boolean,
    isFollowed: Boolean?,
    isSubscribed: Boolean?,
    isNotifyEnabled: Boolean,
    onToggleFollow: () -> Unit,
    onToggleNotify: () -> Unit,
    onToggleSubscribe: () -> Unit,
    onShare: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ChannelIconButton(
            modifier = Modifier.weight(1f),
            iconRes = if (isFollowed == true && !isOwnChannel) R.drawable.ic_follow
            else R.drawable.ic_follow_outline,
            tint = when {
                isOwnChannel -> TeaColors.TextSecondary.copy(alpha = 0.4f)
                isFollowed == true -> TeaColors.Live
                else -> TeaColors.TextPrimary
            },
            onClick = { if (!isOwnChannel) onToggleFollow() }
        )
        ChannelIconButton(
            modifier = Modifier.weight(1f),
            iconRes = R.drawable.ic_bell,
            tint = if (isNotifyEnabled) brand else TeaColors.TextPrimary,
            onClick = onToggleNotify
        )
        ChannelIconButton(
            modifier = Modifier.weight(1f),
            iconRes = R.drawable.ic_points,
            tint = when {
                isOwnChannel -> TeaColors.TextSecondary.copy(alpha = 0.4f)
                isSubscribed == true -> brand
                else -> TeaColors.TextPrimary
            },
            onClick = { if (!isOwnChannel) onToggleSubscribe() }
        )
        ChannelIconButton(
            modifier = Modifier.weight(1f),
            iconRes = R.drawable.ic_share,
            tint = TeaColors.TextPrimary,
            onClick = onShare
        )
    }
}

@Composable
private fun ChannelIconButton(
    iconRes: Int,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(26.dp)
        )
    }
}

@Composable
private fun ChannelTabBar(brand: Color, selected: Int, onSelect: (Int) -> Unit) {
    val titles = listOf("Главная", "Описание", "Медиа", "Чат")
    val scroll = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        titles.forEachIndexed { i, title ->
            Box(
                modifier = Modifier
                    .height(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (i == selected) brand.copy(alpha = 0.22f)
                        else TeaColors.Surface
                    )
                    .border(
                        width = 1.dp,
                        color = if (i == selected) brand.copy(alpha = 0.5f)
                        else brand.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(14.dp)
                    )
                    .clickable { onSelect(i) }
                    .padding(horizontal = 22.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    title,
                    color = if (i == selected) TeaColors.TextPrimary
                    else TeaColors.TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }
    }
}

internal fun parseBrand(hex: String?): Color {
    if (hex.isNullOrBlank()) return TeaColors.Accent
    val normalized = if (hex.startsWith("#")) hex else "#$hex"
    return try {
        Color(normalized.toColorInt())
    } catch (_: Exception) {
        TeaColors.Accent
    }
}

internal fun formatCount(n: Int): String {
    return when {
        n >= 1_000_000 -> String.format(Locale.getDefault(), "%.1f млн", n / 1_000_000.0)
        n >= 1_000 -> String.format(Locale.getDefault(), "%.1f тыс", n / 1_000.0)
        else -> n.toString()
    }
}