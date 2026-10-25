package com.teatv.app.ui.screens.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.teatv.app.data.ChannelInfo
import com.teatv.app.data.FollowedCategory
import com.teatv.app.data.StreamerItem
import com.teatv.app.data.TwitchUser
import com.teatv.app.ui.theme.TeaColors

@OptIn(ExperimentalMaterialApi::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    user: TwitchUser?,
    channel: ChannelInfo?,
    streamers: List<StreamerItem>,
    favorites: List<StreamerItem>,
    categories: List<FollowedCategory>,
    onOpenStream: (StreamerItem) -> Unit,
    onOpenChannel: (StreamerItem) -> Unit,
    onToggleFavorite: (StreamerItem) -> Unit,
    onToggleBot: (StreamerItem) -> Unit,
    isBotForChannel: (String) -> Boolean,
    onOpenAccount: () -> Unit,
    onOpenCategory: (FollowedCategory) -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val refreshState = rememberPullRefreshState(isRefreshing, onRefresh)
    val live = remember(streamers) { streamers.filter { it.isLive } }
    val offline = remember(streamers) { streamers.filter { !it.isLive } }
    val favoritesAll = favorites

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TeaColors.Background)
            .statusBarsPadding()
            .pullRefresh(refreshState)
    ) {
        LazyColumn {
            item { HeaderRow(user, channel, onOpenAccount) }

            if (favoritesAll.isNotEmpty()) {
                item { SectionTitle("Избранные") }
                items(favoritesAll, key = { "fav_${it.id}" }) { s ->
                    StreamerRow(
                        s = s,
                        onOpenChannel = { onOpenChannel(s) },
                        onOpenStream = { onOpenStream(s) },
                        isFavorite = true,
                        isBot = isBotForChannel(s.id),
                        onToggleFavorite = onToggleFavorite,
                        onToggleBot = onToggleBot
                    )
                }
            }

            item { SectionTitle("Сейчас в эфире") }
            if (live.isEmpty()) {
                item { EmptyNote("Никто из подписок не стримит") }
            } else {
                items(live, key = { it.id }) { s ->
                    StreamerRow(
                        s = s,
                        onOpenChannel = { onOpenChannel(s) },
                        onOpenStream = { onOpenStream(s) },
                        isFavorite = favorites.any { it.id == s.id },
                        isBot = isBotForChannel(s.id),
                        onToggleFavorite = onToggleFavorite,
                        onToggleBot = onToggleBot
                    )
                }
            }

            item { SectionTitle("Категории") }
            if (categories.isEmpty()) {
                item { EmptyNote("Подпишись на категории в официальном Twitch — они появятся здесь") }
            } else {
                item { CategoryRow(categories, onOpenCategory) }
            }

            item { SectionTitle("Офлайн") }
            if (offline.isEmpty()) {
                item { EmptyNote("Все в эфире!") }
            } else {
                items(offline, key = { it.id }) { s ->
                    StreamerRow(
                        s = s,
                        onOpenChannel = { onOpenChannel(s) },
                        onOpenStream = { onOpenStream(s) },
                        isFavorite = favorites.any { it.id == s.id },
                        isBot = isBotForChannel(s.id),
                        onToggleFavorite = onToggleFavorite,
                        onToggleBot = onToggleBot
                    )
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

@Composable
private fun HeaderRow(
    user: TwitchUser?,
    channel: ChannelInfo?,
    onOpenAccount: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenAccount() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = user?.avatarUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(TeaColors.Surface)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user?.displayName ?: "...",
                color = TeaColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = channel?.title?.takeIf { it.isNotBlank() } ?: "твой канал",
                color = TeaColors.TextSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        color = TeaColors.TextPrimary,
        fontSize = 17.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
    )
}

@Composable
private fun EmptyNote(text: String) {
    Text(
        text = text,
        color = TeaColors.TextSecondary,
        fontSize = 13.sp,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
    )
}

@Composable
private fun CategoryRow(
    categories: List<FollowedCategory>,
    onOpenCategory: (FollowedCategory) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(categories, key = { it.id }) { CategoryCard(it, onOpenCategory) }
    }
}

@Composable
private fun CategoryCard(
    c: FollowedCategory,
    onOpenCategory: (FollowedCategory) -> Unit
) {
    Column(
        modifier = Modifier
            .width(96.dp)
            .clickable { onOpenCategory(c) }
    ) {
        AsyncImage(
            model = c.boxArtUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(96.dp)
                .height(128.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(TeaColors.Surface)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = c.name,
            color = TeaColors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StreamerRow(
    s: StreamerItem,
    onOpenChannel: () -> Unit,
    onOpenStream: () -> Unit,
    isFavorite: Boolean,
    isBot: Boolean,
    onToggleFavorite: (StreamerItem) -> Unit,
    onToggleBot: (StreamerItem) -> Unit
) {
    var showPopup by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            AsyncImage(
                model = s.avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(TeaColors.Surface)
                    .clickable { onOpenChannel() }
            )

            Spacer(Modifier.width(12.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .combinedClickable(
                        onClick = if (s.isLive) onOpenStream else onOpenChannel,
                        onLongClick = { showPopup = true }
                    )
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = s.name,
                        color = TeaColors.TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(8.dp))
                    if (s.isLive) {
                        Text(
                            text = "LIVE",
                            color = TeaColors.TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(TeaColors.Live, CircleShape)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    } else {
                        Text(text = "offline", color = TeaColors.TextSecondary, fontSize = 12.sp)
                    }
                }

                Spacer(Modifier.height(2.dp))

                Text(
                    text = if (s.isLive) {
                        "${s.stream?.gameName.orEmpty()} • ${s.stream?.viewerCount ?: 0} зрителей"
                    } else {
                        s.login
                    },
                    color = TeaColors.TextSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (s.isLive && s.stream?.thumbnailUrl != null) {
                    Spacer(Modifier.height(8.dp))
                    AsyncImage(
                        model = s.stream.thumbnailUrl.replace("{width}x{height}", "320x180"),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(90.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(TeaColors.Surface)
                    )
                }
            }
        }

        DropdownMenu(
            expanded = showPopup,
            onDismissRequest = { showPopup = false },
            modifier = Modifier.background(TeaColors.Surface)
        ) {
            Text(
                text = s.name,
                color = TeaColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            Text(
                text = if (s.isLive) {
                    "${s.stream?.gameName.orEmpty()} • ${s.stream?.viewerCount ?: 0} зрителей"
                } else {
                    "Офлайн"
                },
                color = TeaColors.TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            Spacer(Modifier.height(8.dp))
            DropdownMenuItem(
                text = {
                    Text(
                        text = if (isFavorite) "Убрать из избранного" else "Добавить в избранное",
                        color = TeaColors.TextPrimary,
                        fontSize = 14.sp
                    )
                },
                onClick = {
                    onToggleFavorite(s)
                    showPopup = false
                }
            )
            DropdownMenuItem(
                text = {
                    Text(
                        text = if (isBot) "Выключить бота" else "Включить бота",
                        color = TeaColors.TextPrimary,
                        fontSize = 14.sp
                    )
                },
                onClick = {
                    onToggleBot(s)
                    showPopup = false
                }
            )
        }
    }
}