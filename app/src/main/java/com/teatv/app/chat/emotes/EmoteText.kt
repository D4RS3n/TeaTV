package com.teatv.app.chat.emotes

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.ui.platform.LocalContext
import coil.request.ImageRequest

/** Словарь код -> смайл из всех пачек + динамический кэш смайлов Twitch. */
fun allEmotesLookup(b: EmoteBundles): Map<String, Emote> =
    (b.frequent + b.user + b.channel + b.seven + b.bttv + b.global + TwitchEmoteCache.getAll().values)
        .distinctBy { it.code }
        .associateBy { it.code }

private fun emoteInline(e: Emote, sizeSp: Float = 20f): InlineTextContent = InlineTextContent(
    Placeholder(
        width = sizeSp.sp,
        height = sizeSp.sp,
        placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
    )
) {
    val context = LocalContext.current

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
    builder.withStyle(
        SpanStyle(color = senderColor, fontWeight = FontWeight.ExtraBold, fontSize = senderSizeSp.sp)
    ) { append(sender) }
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
    var last = 0
    for (m in WORD_REGEX.findAll(text)) {
        if (m.range.first > last) append(text.substring(last, m.range.first))
        val e = lookup[m.value]
            ?: messageEmotes[m.value]?.let { Emote(m.value, it, EmoteKind.TWITCH) }
            ?: TwitchEmoteCache.get(m.value)
        if (e != null) {
            val id = e.kind.name + e.code
            appendInlineContent(id, m.value)
            inline[id] = emoteInline(e, emoteSizeSp)
        } else {
            append(m.value)
        }
        last = m.range.last + 1
    }
    if (last < text.length) append(text.substring(last))
}
