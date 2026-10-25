package com.teatv.app.ui.screens.chat

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.teatv.app.chat.emotes.Emote
import com.teatv.app.chat.emotes.EmoteBundles
import com.teatv.app.ui.theme.TeaColors
import androidx.compose.ui.platform.LocalContext
import coil.request.ImageRequest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmotePanel(
    bundles: EmoteBundles,
    onPick: (Emote) -> Unit,
    onDismiss: () -> Unit
) {
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
            section("Мои смайлы", bundles.user, onPick)
            section("Смайлы канала", bundles.channel, onPick)
            section("7TV", bundles.seven, onPick)
            section("BTTV", bundles.bttv, onPick)
            section("Twitch", bundles.global, onPick)
        }
    }
}

private fun LazyGridScope.section(
    title: String,
    emotes: List<Emote>,
    onPick: (Emote) -> Unit
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
            modifier = Modifier
                .size(48.dp)
                .padding(5.dp)
                .clickable { onPick(e) }
        )
    }
}