package com.teatv.app.ui.screens.channel

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import com.teatv.app.data.ChannelClip
import com.teatv.app.data.ChannelPage
import com.teatv.app.data.ChannelPanel
import com.teatv.app.data.ChannelVideo
import com.teatv.app.data.LiveStream
import com.teatv.app.data.SocialLink
import com.teatv.app.ui.theme.TeaColors

@Composable
fun ChannelTabHome(
    live: LiveStream?,
    page: ChannelPage?,
    brand: Color,
    onOpenStream: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        if (live != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(TeaColors.Surface)
                    .border(1.dp, brand.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                    .clickable { onOpenStream() }
                    .padding(14.dp)
            ) {
                AsyncImage(
                    model = live.thumbnailUrl.replace("{width}x{height}", "640x360"),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(TeaColors.ChatBg)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    live.title,
                    color = TeaColors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Смотреть и чат →",
                    color = TeaColors.TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        } else {
            Text(
                "Канал офлайн",
                color = TeaColors.TextSecondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            val preview = page?.description?.takeIf { it.isNotBlank() }
            if (preview != null) {
                Text(
                    preview,
                    color = TeaColors.TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Полностью — во вкладке «Описание»",
                    color = TeaColors.TextSecondary,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun ChannelTabAbout(
    description: String?,
    panels: List<ChannelPanel>,
    socials: List<SocialLink>,
    brand: Color
) {
    val context = LocalContext.current
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        if (description?.isNotBlank() == true) {
            Text(
                description,
                color = TeaColors.TextSecondary,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
            Spacer(Modifier.height(16.dp))
        }
        panels.forEach { p ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(TeaColors.Surface)
                    .border(1.dp, brand.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                    .clickable { if (p.linkUrl.isNotBlank()) openUrl(context, p.linkUrl) }
                    .padding(12.dp)
            ) {
                if (p.imageUrl.isNotBlank()) {
                    AsyncImage(
                        model = p.imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                    )
                    Spacer(Modifier.height(8.dp))
                }
                if (p.title.isNotBlank()) {
                    Text(
                        p.title,
                        color = TeaColors.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (p.description.isNotBlank()) {
                    Text(
                        p.description,
                        color = TeaColors.TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        if (socials.isEmpty() && panels.isEmpty()) {
            Text(
                "Ссылок канала нет",
                color = TeaColors.TextSecondary,
                fontSize = 13.sp
            )
        } else {
            Text(
                "Ссылки канала",
                color = TeaColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            socials.forEach { s ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(TeaColors.Surface)
                        .border(1.dp, brand.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                        .clickable { openUrl(context, s.url) }
                        .padding(14.dp)
                ) {
                    Text(
                        s.label.ifBlank { s.url },
                        color = TeaColors.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        s.url,
                        color = TeaColors.TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
fun ChannelTabMedia(
    videos: List<ChannelVideo>,
    clips: List<ChannelClip>,
    brand: Color,
    onOpenVod: (ChannelVideo) -> Unit,      // <-- ДОБАВЛЕНО
    onOpenClip: (ChannelClip) -> Unit       // <-- ДОБАВЛЕНО
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Text("Видео", color = TeaColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 10.dp))
        if (videos.isEmpty()) {
            Text("Записей нет", color = TeaColors.TextSecondary, fontSize = 13.sp)
        } else {
            videos.take(10).forEach { v -> VideoRow(v) { onOpenVod(v) } }
        }
        Spacer(Modifier.height(18.dp))
        Text("Клипы", color = TeaColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 10.dp))
        if (clips.isEmpty()) {
            Text("Клипов нет", color = TeaColors.TextSecondary, fontSize = 13.sp)
        } else {
            clips.take(10).forEach { c -> ClipRow(c) { onOpenClip(c) } }
        }
    }
}

@Composable
private fun VideoRow(v: ChannelVideo, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = mediaThumbUrl(v.thumbnailUrl),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(120.dp)
                .height(68.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(TeaColors.Surface)
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                v.title,
                color = TeaColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${v.duration} • ${formatCount(v.viewCount)} просм.",
                color = TeaColors.TextSecondary,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun ClipRow(c: ChannelClip, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = mediaThumbUrl(c.thumbnailUrl),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(120.dp)
                .height(68.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(TeaColors.Surface)
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                c.title,
                color = TeaColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${formatDuration(c.durationSeconds)} • ${formatCount(c.viewCount)} просм.",
                color = TeaColors.TextSecondary,
                fontSize = 12.sp
            )
        }
    }
}

internal fun openUrl(context: android.content.Context, url: String) {
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, url.toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Exception) {
    }
}

internal fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return if (m > 0) "$m:${s.toString().padStart(2, '0')} мин" else "$s сек"
}

internal fun mediaThumbUrl(url: String): String = url
    .replace("%7Bwidth%7Dx%7Bheight%7D", "240x135")
    .replace("%{width}x%{height}", "240x135")
    .replace("{width}x{height}", "240x135")
    .replace("%{width}", "240")
    .replace("{width}", "240")
    .replace("%{height}", "135")
    .replace("{height}", "135")