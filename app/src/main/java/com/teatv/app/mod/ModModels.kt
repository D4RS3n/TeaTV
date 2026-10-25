package com.teatv.app.mod

/** Информация о чаттере для левой шторки (Twitch + 7TV). */
data class ChatterInfo(
    val userId: String,
    val userLogin: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val createdAt: String? = null,
    val followedAt: String? = null,
    val isSubscribed: Boolean = false,
    val subTier: String? = null,
    val isBanned: Boolean = false,
    val isTimedOut: Boolean = false,
    val timeoutExpiresAt: Long? = null,
    val sevenTvBadges: List<String> = emptyList(),
    val sevenTvPaint: String? = null
)

/** Запись в журнале модераторских действий. */
data class ModLogItem(
    val id: String,
    val timestamp: Long,
    val modName: String,
    val targetName: String?,
    val action: String,
    val details: String? = null
)

/** Элемент очереди запросов на награды за баллы канала. */
data class RewardRequestItem(
    val id: String,
    val rewardId: String,
    val rewardTitle: String,
    val rewardCost: Int,
    val userName: String,
    val userInput: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/** Вариант исхода прогноза. */
data class PredictionOption(
    val id: String,
    val title: String,
    val points: Int = 0,
    val userCount: Int = 0
)

/** Модель текущего прогноза на канале. */
data class PredictionInfo(
    val id: String,
    val title: String,
    val status: String, // "ACTIVE", "LOCKED", "RESOLVED", "CANCELED"
    val options: List<PredictionOption>,
    val winningOptionId: String? = null,
    val durationSeconds: Int = 120,
    val createdAt: Long = System.currentTimeMillis()
)

/** Запрос на снятие блокировки (апелляция на разбан). */
data class UnbanRequestItem(
    val id: String,
    val userId: String,
    val userLogin: String,
    val userName: String,
    val banReason: String? = null,
    val appealMessage: String,
    val createdAt: String = "Недавно"
)

/** Текущие режимы канала (Shield, Sub-only, Emote-only, Followers, Slow). */
data class ChannelModesState(
    val shieldMode: Boolean = false,
    val subOnly: Boolean = false,
    val emoteOnly: Boolean = false,
    val followersDurationMinutes: Int = -1, // -1 = выкл, 0 = 0 мин (любые фолловеры), 10, 30, 60...
    val slowModeSeconds: Int = 0, // 0 = выкл, 3, 5, 10, 30, 60, 120...
    val blockedTermsCount: Int = 0,
    val permittedTermsCount: Int = 0
)

/** Закреплённое сообщение чата. */
data class PinnedMessage(
    val id: String,
    val messageId: String = id,
    val sender: String,
    val senderColor: String = "",
    val text: String,
    val pinnedBy: String,
    val pinnedAt: Long = System.currentTimeMillis()
)
