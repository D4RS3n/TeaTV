package com.teatv.app.chat.emotes

enum class EmoteKind { TWITCH, SEVENTV, BTTV }

data class Emote(
    val code: String,
    val url: String,
    val kind: EmoteKind,
    val aspectRatio: Float = 1.0f,
    val isZeroWidth: Boolean = false,
    val isUnlocked: Boolean = true,
    val ownerId: String? = null,
    val channelName: String? = null,
    val emoteType: String? = null
)

/** Все пачки смайлов для панели. */
data class EmoteBundles(
    val frequent: List<Emote> = emptyList(),
    val user: List<Emote> = emptyList(),
    val channel: List<Emote> = emptyList(),
    val seven: List<Emote> = emptyList(),
    val bttv: List<Emote> = emptyList(),
    val global: List<Emote> = emptyList()
)
