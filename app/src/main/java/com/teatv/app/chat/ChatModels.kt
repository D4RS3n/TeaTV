package com.teatv.app.chat

/** Одно сообщение чата. */
data class ChatMessage(
    val id: String,
    val sender: String,
    val color: String,
    val text: String,
    val badges: List<String> = emptyList(),
    val replyToId: String?,
    val replyToName: String?,
    val replyToBody: String?,
    val redemptionTitle: String? = null,
    val redemptionIcon: String? = null,
    val senderId: String? = null,
    val senderLogin: String? = null,
    val twitchEmotes: Map<String, String> = emptyMap()
)
