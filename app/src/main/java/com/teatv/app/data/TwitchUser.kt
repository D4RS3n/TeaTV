package com.teatv.app.data

/** Полные данные пользователя Twitch (GET /helix/users). */
data class TwitchUser(
    val id: String,
    val login: String,
    val displayName: String,
    val avatarUrl: String,
    val description: String = "",
    val broadcasterType: String = "",
    val offlineImageUrl: String = "",
    val createdAt: String = ""
)