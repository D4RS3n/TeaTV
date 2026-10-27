package com.teatv.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.ui.res.painterResource
import com.teatv.app.R
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.teatv.app.data.CategoryDetails
import com.teatv.app.data.FollowedCategory
import com.teatv.app.data.StreamerItem
import com.teatv.app.ui.theme.TeaColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryScreen(
    category: FollowedCategory,
    details: CategoryDetails?,
    streams: List<StreamerItem>,
    loading: Boolean,
    loadingMore: Boolean,
    onBack: () -> Unit,
    onOpenStream: (StreamerItem) -> Unit,
    onToggleFollow: () -> Unit,
    onLoadMore: () -> Unit
) {
    val listState = rememberLazyListState()

    // Догрузка следующей пачки, когда остаётся 3 элемента до конца.
    LaunchedEffect(listState, streams.size) {
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = info.totalItemsCount
            lastVisible to total
        }.collect { (lastVisible, total) ->
            if (total > 0 && lastVisible >= total - 3) {
                onLoadMore()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TeaColors.Background)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item(key = "header") {
                CategoryHeader(
                    category = category,
                    details = details,
                    onBack = onBack,
                    onToggleFollow = onToggleFollow
                )
            }

            item(key = "section_title") {
                Text(
                    text = "Активные каналы",
                    color = TeaColors.TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp)
                )
            }

            if (loading) {
                item(key = "loading_first") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = TeaColors.Accent)
                    }
                }
            } else if (streams.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = "Пока никто не стримит эту категорию",
                        color = TeaColors.TextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            } else {
                itemsIndexed(streams, key = { _, s -> s.id }) { _, s ->
                    CategoryStreamerRow(s = s, onClick = { onOpenStream(s) })
                }
            }

            if (loadingMore) {
                item(key = "loading_more") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = TeaColors.Accent,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryHeader(
    category: FollowedCategory,
    details: CategoryDetails?,
    onBack: () -> Unit,
    onToggleFollow: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 8.dp, top = 4.dp, end = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_arrow_back),
                    contentDescription = "Назад",
                    tint = TeaColors.TextPrimary
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onToggleFollow) {
                Icon(
                    painter = painterResource(
                        id = if (details?.isFollowed == true)
                            R.drawable.ic_follow else R.drawable.ic_follow_outline
                    ),
                    contentDescription = if (details?.isFollowed == true)
                        "Отписаться" else "Подписаться",
                    tint = if (details?.isFollowed == true)
                        TeaColors.Accent else TeaColors.TextPrimary,
                    modifier = Modifier.size(30.dp)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.Top
        ) {
            AsyncImage(
                model = details?.boxArtUrl?.takeIf { it.isNotBlank() } ?: category.boxArtUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(88.dp)
                    .height(118.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(TeaColors.Surface)
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = details?.name?.takeIf { it.isNotBlank() } ?: category.name,
                    color = TeaColors.TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                if (details != null) {
                    val viewersText =
                        (if (details.viewersCount >= 0) formatCount(details.viewersCount) else "—") +
                                " зрителей"
                    val followersText =
                        (if (details.followersCount >= 0) formatCount(details.followersCount) else "—") +
                                " фолловеров"
                    Text(
                        text = "$viewersText — $followersText",
                        color = TeaColors.TextSecondary,
                        fontSize = 13.sp
                    )
                } else {
                    Text(
                        text = "Загрузка...",
                        color = TeaColors.TextSecondary,
                        fontSize = 13.sp
                    )
                }
                if (!details?.tags.isNullOrEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        details!!.tags.forEach { tag ->
                            TagChip(tag)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TagChip(text: String) {
    Box(
        modifier = Modifier
            .background(TeaColors.Surface, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            color = TeaColors.TextSecondary,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun CategoryStreamerRow(s: StreamerItem, onClick: () -> Unit) {
    val previewUrl = remember(s.stream?.thumbnailUrl) {
        s.stream?.thumbnailUrl
            ?.replace("{width}", "360")
            ?.replace("{height}", "202")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Превью трансляции
        Box(
            modifier = Modifier
                .width(120.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(TeaColors.Surface)
        ) {
            if (!previewUrl.isNullOrBlank()) {
                AsyncImage(
                    model = previewUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            // LIVE бейдж и зрители поверх превью
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(TeaColors.Live, CircleShape)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = formatCount(s.stream?.viewerCount ?: 0),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        // Данные канала
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = s.avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(TeaColors.Surface)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = s.name,
                    color = TeaColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = s.stream?.title ?: "не в сети",
                color = TeaColors.TextSecondary,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun formatCount(n: Int): String {
    if (n < 1_000) return n.toString()
    if (n < 1_000_000) {
        val k = n / 1000.0
        return if (k >= 10) "${k.toInt()}K" else String.format("%.1fK", k).replace(',', '.')
    }
    val m = n / 1_000_000.0
    return String.format("%.1fM", m).replace(',', '.')
}