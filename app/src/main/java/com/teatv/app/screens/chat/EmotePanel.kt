package com.teatv.app.ui.screens.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.teatv.app.chat.emotes.Emote
import com.teatv.app.chat.emotes.EmoteBundles
import com.teatv.app.ui.theme.TeaColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmotePanel(
    bundles: EmoteBundles,
    categoryOrder: List<String> = emptyList(),
    keepOpen: Boolean = true,
    onKeepOpenChange: (Boolean) -> Unit = {},
    onPick: (Emote) -> Unit,
    onToggleFavorite: (Emote) -> Boolean,
    onDismiss: () -> Unit
) {
    val unlockedUserCodes = remember(bundles.user) {
        bundles.user.map { it.code }.toSet()
    }
    val favoriteCodes = remember(bundles.favorite) {
        bundles.favorite.map { it.code }.toSet()
    }

    val handlePick = { emote: Emote ->
        onPick(emote)
        if (!keepOpen) {
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = TeaColors.ChatBg
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "СМАЙЛЫ",
                color = TeaColors.TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onKeepOpenChange(!keepOpen) }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "Не закрывать",
                    color = if (keepOpen) TeaColors.TextPrimary else TeaColors.TextSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(end = 4.dp)
                )
                Switch(
                    checked = keepOpen,
                    onCheckedChange = onKeepOpenChange,
                    modifier = Modifier.scale(0.75f),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = TeaColors.Accent,
                        uncheckedThumbColor = TeaColors.TextSecondary,
                        uncheckedTrackColor = TeaColors.Surface
                    )
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(6),
            contentPadding = PaddingValues(8.dp),
            modifier = Modifier.heightIn(max = 480.dp)
        ) {
            // 1. Избранные смайлики (всегда первее всех остальных. Если нет - не отображать)
            if (bundles.favorite.isNotEmpty()) {
                section(
                    title = "★ Избранные",
                    emotes = bundles.favorite,
                    onPick = handlePick,
                    onToggleFavorite = onToggleFavorite,
                    favoriteCodes = favoriteCodes
                )
            }

            // Группировка смайлов подписок по каналам
            val subGroups = if (bundles.user.isNotEmpty()) {
                bundles.user.groupBy { it.channelName ?: "Подписки" }
            } else emptyMap()

            val renderedCats = mutableSetOf<String>()

            // 2. Отображаем разделы согласно пользовательскому порядку
            categoryOrder.forEach { catName ->
                when {
                    catName.contains("текущего канала", ignoreCase = true) || catName.equals("Смайлы канала", ignoreCase = true) -> {
                        section(
                            title = "Смайлы канала",
                            emotes = bundles.channel,
                            onPick = handlePick,
                            onToggleFavorite = onToggleFavorite,
                            favoriteCodes = favoriteCodes,
                            unlockedCodes = unlockedUserCodes,
                            isChannelSection = true
                        )
                        renderedCats.add("Смайлы канала")
                    }
                    catName.equals("7TV", ignoreCase = true) -> {
                        section("7TV", bundles.seven, handlePick, onToggleFavorite, favoriteCodes)
                        renderedCats.add("7TV")
                    }
                    catName.equals("BTTV", ignoreCase = true) -> {
                        section("BTTV", bundles.bttv, handlePick, onToggleFavorite, favoriteCodes)
                        renderedCats.add("BTTV")
                    }
                    catName.equals("Twitch", ignoreCase = true) -> {
                        section("Twitch", bundles.global, handlePick, onToggleFavorite, favoriteCodes)
                        renderedCats.add("Twitch")
                    }
                    subGroups.containsKey(catName) -> {
                        section(catName, subGroups[catName] ?: emptyList(), handlePick, onToggleFavorite, favoriteCodes)
                        renderedCats.add(catName)
                    }
                    catName.contains("подписок", ignoreCase = true) || catName.equals("Подписки", ignoreCase = true) -> {
                        subGroups.forEach { (chName, list) ->
                            if (chName !in renderedCats) {
                                section(chName, list, handlePick, onToggleFavorite, favoriteCodes)
                                renderedCats.add(chName)
                            }
                        }
                    }
                }
            }

            // 3. Любые каналы подписок, не попавшие в явный список порядка
            subGroups.forEach { (chName, list) ->
                if (chName !in renderedCats) {
                    section(chName, list, handlePick, onToggleFavorite, favoriteCodes)
                    renderedCats.add(chName)
                }
            }

            // 4. Оставшиеся стандартные категории, если их не было в categoryOrder (по умолчанию)
            if ("Смайлы канала" !in renderedCats) {
                section(
                    title = "Смайлы канала",
                    emotes = bundles.channel,
                    onPick = handlePick,
                    onToggleFavorite = onToggleFavorite,
                    favoriteCodes = favoriteCodes,
                    unlockedCodes = unlockedUserCodes,
                    isChannelSection = true
                )
            }
            if ("7TV" !in renderedCats) {
                section("7TV", bundles.seven, handlePick, onToggleFavorite, favoriteCodes)
            }
            if ("BTTV" !in renderedCats) {
                section("BTTV", bundles.bttv, handlePick, onToggleFavorite, favoriteCodes)
            }
            if ("Twitch" !in renderedCats) {
                section("Twitch", bundles.global, handlePick, onToggleFavorite, favoriteCodes)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun LazyGridScope.section(
    title: String,
    emotes: List<Emote>,
    onPick: (Emote) -> Unit,
    onToggleFavorite: (Emote) -> Boolean,
    favoriteCodes: Set<String> = emptySet(),
    unlockedCodes: Set<String> = emptySet(),
    isChannelSection: Boolean = false
) {
    if (emotes.isEmpty()) return
    item(span = { GridItemSpan(maxLineSpan) }) {
        Text(
            text = title,
            color = TeaColors.TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        )
    }
    items(emotes, key = { it.code + it.url + title }) { e ->
        val context = LocalContext.current
        val haptic = LocalHapticFeedback.current
        val isLocked = isChannelSection && unlockedCodes.isNotEmpty() && (e.code !in unlockedCodes && !e.isUnlocked)
        val isFav = e.code in favoriteCodes

        Box(
            modifier = Modifier
                .size(48.dp)
                .padding(4.dp)
                .alpha(if (isLocked) 0.3f else 1f)
                .clip(RoundedCornerShape(6.dp))
                .combinedClickable(
                    enabled = !isLocked,
                    onClick = { onPick(e) },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val added = onToggleFavorite(e)
                        val msg = if (added) "Добавлено в Избранное: ${e.code}" else "Удалено из Избранного: ${e.code}"
                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(e.url)
                    .size(128, 128)
                    .crossfade(false)
                    .build(),
                contentDescription = e.code,
                contentScale = ContentScale.Fit,
                placeholder = ColorPainter(TeaColors.Surface),
                error = ColorPainter(TeaColors.Accent),
                modifier = Modifier.size(36.dp)
            )
            if (isLocked) {
                Text(
                    text = "🔒",
                    fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.BottomEnd)
                )
            } else if (isFav) {
                Text(
                    text = "★",
                    color = Color(0xFFFFD700),
                    fontSize = 9.sp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 1.dp, top = 1.dp)
                )
            }
        }
    }
}
