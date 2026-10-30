package com.teatv.app.ui.screens.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.teatv.app.data.FollowedCategory
import com.teatv.app.data.LiveStream
import com.teatv.app.data.SearchBundle
import com.teatv.app.data.SearchCategory
import com.teatv.app.data.SearchChannel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.platform.LocalContext
import com.teatv.app.settings.ClientSettingsStore
import com.teatv.app.ui.theme.TeaColors

@Composable
internal fun SearchScreen(
    query: String,
    results: SearchBundle,
    loading: Boolean,
    onQuery: (String) -> Unit,
    onOpenChannel: (SearchChannel) -> Unit,
    onOpenCategory: (FollowedCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val clientSettingsStore = remember(context) { ClientSettingsStore(context) }
    var searchHistory by remember { mutableStateOf(clientSettingsStore.getSearchHistory()) }
    val screenBg by animateColorAsState(
        targetValue = if (focused) TeaColors.ChatBg else TeaColors.Background,
        animationSpec = tween(220),
        label = "search_bg"
    )
    val hasResults = results.categories.isNotEmpty() ||
            results.channels.isNotEmpty() ||
            results.streams.isNotEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(screenBg)
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            TeaColors.Surface.copy(alpha = 0.85f),
                            TeaColors.Surface.copy(alpha = 0.55f)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    color = if (focused) TeaColors.Accent.copy(alpha = 0.5f)
                    else TeaColors.Surface,
                    shape = RoundedCornerShape(28.dp)
                )
                .padding(horizontal = 18.dp, vertical = 13.dp)
        ) {
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focused = it.isFocused },
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = TeaColors.TextPrimary,
                    fontSize = 15.sp
                ),
                cursorBrush = SolidColor(TeaColors.Accent),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.fillMaxWidth()) {
                        if (query.isEmpty()) {
                            Text(
                                "Категории, каналы, стримы",
                                color = TeaColors.TextSecondary.copy(alpha = 0.8f),
                                fontSize = 14.sp
                            )
                        }
                        inner()
                        if (query.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .clickable { onQuery("") },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("✕", color = TeaColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            )
        }

        if (focused && searchHistory.isNotEmpty()) {
            val filteredHistory = remember(query, searchHistory) {
                if (query.isBlank()) searchHistory
                else searchHistory.filter { it.contains(query.trim(), ignoreCase = true) }
            }
            if (filteredHistory.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(TeaColors.Surface.copy(alpha = 0.95f))
                        .border(1.dp, TeaColors.Accent.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                        .padding(vertical = 4.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ИСТОРИЯ ПОИСКА",
                                color = TeaColors.TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Очистить всё",
                                color = TeaColors.Accent,
                                fontSize = 11.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable {
                                        clientSettingsStore.clearSearchHistory()
                                        searchHistory = emptyList()
                                    }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }

                        filteredHistory.take(8).forEach { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onQuery(item)
                                        clientSettingsStore.addSearchHistory(item)
                                        searchHistory = clientSettingsStore.getSearchHistory()
                                    }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "🕒",
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(end = 10.dp)
                                )
                                Text(
                                    text = item,
                                    color = TeaColors.TextPrimary,
                                    fontSize = 14.sp,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            clientSettingsStore.removeSearchHistory(item)
                                            searchHistory = clientSettingsStore.getSearchHistory()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "✕",
                                        color = TeaColors.TextSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = TeaColors.Accent)
            }
        } else if (query.trim().length >= 2) {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
                    .background(TeaColors.Surface.copy(alpha = 0.28f))
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                ) {
                    if (results.categories.isNotEmpty()) {
                        item { SearchSection("Категории") }
                        items(results.categories, key = { "cat_${it.id}" }) { c ->
                            SearchCategoryRow(c) {
                                if (query.isNotBlank()) {
                                    clientSettingsStore.addSearchHistory(query)
                                    searchHistory = clientSettingsStore.getSearchHistory()
                                }
                                onOpenCategory(it)
                            }
                        }
                    }
                    if (results.channels.isNotEmpty()) {
                        item { SearchSection("Каналы") }
                        items(results.channels, key = { "ch_${it.id}" }) { c ->
                            SearchChannelRow(c) {
                                if (query.isNotBlank()) {
                                    clientSettingsStore.addSearchHistory(query)
                                    searchHistory = clientSettingsStore.getSearchHistory()
                                }
                                onOpenChannel(it)
                            }
                        }
                    }
                    if (results.streams.isNotEmpty()) {
                        item { SearchSection("Стримы") }
                        items(results.streams, key = { "st_${it.userId}" }) { s ->
                            SearchStreamRow(s) {
                                if (query.isNotBlank()) {
                                    clientSettingsStore.addSearchHistory(query)
                                    searchHistory = clientSettingsStore.getSearchHistory()
                                }
                                onOpenChannel(it)
                            }
                        }
                    }
                    if (!hasResults) {
                        item {
                            Text(
                                "Ничего не найдено",
                                color = TeaColors.TextSecondary,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SearchSection(title: String) {
    Text(
        text = title.uppercase(),
        color = TeaColors.TextSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(start = 12.dp, top = 14.dp, bottom = 4.dp)
    )
}

@Composable
internal fun SearchChannelRow(c: SearchChannel, onClick: (SearchChannel) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick(c) }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = c.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(TeaColors.Surface)
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                c.name,
                color = TeaColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Text(
                if (c.isLive) "${c.gameName} • LIVE" else c.gameName,
                color = if (c.isLive) TeaColors.Live else TeaColors.TextSecondary,
                fontSize = 12.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
internal fun SearchCategoryRow(c: SearchCategory, onClick: (FollowedCategory) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable {
                onClick(
                    FollowedCategory(
                        id = c.id,
                        name = c.name,
                        boxArtUrl = c.boxArtUrl
                    )
                )
            }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = c.boxArtUrl,
            contentDescription = null,
            modifier = Modifier
                .width(40.dp)
                .height(54.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(TeaColors.Surface)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            c.name,
            color = TeaColors.TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
internal fun SearchStreamRow(s: LiveStream, onClick: (SearchChannel) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable {
                onClick(
                    SearchChannel(
                        id = s.userId,
                        login = s.userName.lowercase(),
                        displayName = s.userName,
                        gameName = s.gameName,
                        title = s.title,
                        isLive = true,
                        thumbnailUrl = s.thumbnailUrl
                    )
                )
            }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = s.thumbnailUrl.replace("{width}x{height}", "160x90"),
            contentDescription = null,
            modifier = Modifier
                .width(84.dp)
                .height(47.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(TeaColors.Surface)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                s.userName,
                color = TeaColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Text(
                s.title,
                color = TeaColors.TextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${s.gameName} • ${s.viewerCount} зрителей",
                color = TeaColors.TextSecondary.copy(alpha = 0.8f),
                fontSize = 11.sp,
                maxLines = 1
            )
        }
    }
}