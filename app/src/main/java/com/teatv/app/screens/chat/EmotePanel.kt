package com.teatv.app.ui.screens.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.teatv.app.chat.emotes.Emote
import com.teatv.app.chat.emotes.EmoteBundles
import com.teatv.app.chat.emotes.EmoteKind
import com.teatv.app.ui.theme.TeaColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmotePanel(
    bundles: EmoteBundles,
    onPick: (Emote) -> Unit,
    onDismiss: () -> Unit
) {
    val unlockedUserCodes = remember(bundles.user) {
        bundles.user.map { it.code }.toSet()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = TeaColors.ChatBg
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(6),
            contentPadding = PaddingValues(8.dp),
            modifier = Modifier.heightIn(max = 480.dp)
        ) {
            section("Часто используемые", bundles.frequent, onPick)

            // Смайлы подписок (разделенные по отдельным каналам наверх)
            if (bundles.user.isNotEmpty()) {
                val subGroups = bundles.user.groupBy { it.channelName ?: "Подписки" }
                subGroups.forEach { (channelName, emotes) ->
                    section(channelName, emotes, onPick)
                }
            }

            section(
                title = "Смайлы канала",
                emotes = bundles.channel,
                onPick = onPick,
                unlockedCodes = unlockedUserCodes,
                isChannelSection = true
            )
            section("7TV", bundles.seven, onPick)
            section("BTTV", bundles.bttv, onPick)

            // Официальные смайлы Twitch (в самом низу)
            section("Twitch", bundles.global, onPick)
        }
    }
}

private fun LazyGridScope.section(
    title: String,
    emotes: List<Emote>,
    onPick: (Emote) -> Unit,
    unlockedCodes: Set<String> = emptySet(),
    isChannelSection: Boolean = false
) {
    if (emotes.isEmpty()) return
    item(span = { GridItemSpan(maxLineSpan) }) {
        Text(
            text = title,
            color = TeaColors.TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        )
    }
    items(emotes) { e ->
        val context = LocalContext.current
        val isLocked = isChannelSection && unlockedCodes.isNotEmpty() && (e.code !in unlockedCodes && !e.isUnlocked)

        Box(
            modifier = Modifier
                .size(48.dp)
                .padding(4.dp)
                .alpha(if (isLocked) 0.3f else 1f)
                .clickable(enabled = !isLocked) { onPick(e) },
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
            }
        }
    }
}
