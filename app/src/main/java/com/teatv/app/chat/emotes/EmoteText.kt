package com.teatv.app.chat.emotes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest

/** Словарь код -> смайл из всех пачек + динамический кэш смайлов Twitch. */
fun allEmotesLookup(b: EmoteBundles): Map<String, Emote> =
    (b.frequent + b.user + b.channel + b.seven + b.bttv + b.global + TwitchEmoteCache.getAll().values)
        .distinctBy { it.code }
        .associateBy { it.code }

private fun emoteInline(
    e: Emote,
    sizeSp: Float = 20f,
    overlays: List<Emote> = emptyList()
): InlineTextContent {
    val widthSp = (sizeSp * e.aspectRatio).coerceIn(sizeSp * 0.4f, sizeSp * 4.0f)
    return InlineTextContent(
        Placeholder(
            width = widthSp.sp,
            height = sizeSp.sp,
            placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
        )
    ) {
        val context = LocalContext.current
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(e.url)
                    .size(96, 96)
                    .crossfade(false)
                    .build(),
                contentDescription = e.code,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
            // 7TV Zero-width модификаторы: накладываются поверх базового смайла
            overlays.forEach { ov ->
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(ov.url)
                        .size(96, 96)
                        .crossfade(false)
                        .build(),
                    contentDescription = ov.code,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/** Сообщение чата: ник + текст со встроенными смайлами. */
fun buildMessageAnnotated(
    sender: String,
    senderColor: Color,
    text: String,
    textColor: Color,
    lookup: Map<String, Emote>,
    badgeUrls: List<String> = emptyList(),
    fontSizeSp: Float = 14f,
    messageEmotes: Map<String, String> = emptyMap()
): Pair<AnnotatedString, Map<String, InlineTextContent>> {
    val builder = AnnotatedString.Builder()
    val inline = mutableMapOf<String, InlineTextContent>()
    val badgeSizeSp = fontSizeSp
    val emoteSizeSp = fontSizeSp + 6f
    val senderSizeSp = (fontSizeSp - 1f).coerceAtLeast(9f)

    badgeUrls.forEachIndexed { i, url ->
        val id = "badge$i"
        builder.appendInlineContent(id, " ")
        inline[id] = InlineTextContent(
            Placeholder(
                width = badgeSizeSp.sp,
                height = badgeSizeSp.sp,
                placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
            )
        ) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
        builder.append(" ")
    }
    builder.pushStringAnnotation(tag = "NICKNAME", annotation = sender)
    builder.withStyle(
        SpanStyle(color = senderColor, fontWeight = FontWeight.ExtraBold, fontSize = senderSizeSp.sp)
    ) { append(sender) }
    builder.pop()
    builder.withStyle(SpanStyle(color = textColor, fontSize = fontSizeSp.sp)) {
        appendTextWithEmotes(": " + text, lookup, inline, emoteSizeSp, messageEmotes)
    }
    return builder.toAnnotatedString() to inline
}

val WORD_REGEX = Regex("\\S+")

private fun AnnotatedString.Builder.appendTextWithEmotes(
    text: String,
    lookup: Map<String, Emote>,
    inline: MutableMap<String, InlineTextContent>,
    emoteSizeSp: Float = 20f,
    messageEmotes: Map<String, String> = emptyMap()
) {
    val matches = WORD_REGEX.findAll(text).toList()
    var last = 0
    var index = 0

    while (index < matches.size) {
        val m = matches[index]
        if (m.range.first > last) {
            append(text.substring(last, m.range.first))
        }

        val e = lookup[m.value]
            ?: messageEmotes[m.value]?.let { Emote(m.value, it, EmoteKind.TWITCH) }
            ?: TwitchEmoteCache.get(m.value)

        if (e != null) {
            // Проверяем следующие слова на наличие 7TV zero-width смайлов (overlays)
            val overlays = mutableListOf<Emote>()
            var nextIndex = index + 1
            while (nextIndex < matches.size) {
                val nextMatch = matches[nextIndex]
                val nextEmote = lookup[nextMatch.value]
                if (nextEmote != null && nextEmote.isZeroWidth) {
                    overlays += nextEmote
                    nextIndex++
                } else {
                    break
                }
            }

            val inlineId = "e_${inline.size}_${e.code}"
            appendInlineContent(inlineId, m.value)
            inline[inlineId] = emoteInline(e, emoteSizeSp, overlays)

            // Пропускаем поглощённые zero-width смайлы
            val lastConsumedMatch = if (overlays.isNotEmpty()) matches[nextIndex - 1] else m
            last = lastConsumedMatch.range.last + 1
            index = nextIndex
        } else {
            append(m.value)
            last = m.range.last + 1
            index++
        }
    }
    if (last < text.length) {
        append(text.substring(last))
    }
}
