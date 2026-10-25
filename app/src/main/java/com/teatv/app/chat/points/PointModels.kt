package com.teatv.app.chat.points

data class PointReward(
    val id: String,
    val title: String,
    val cost: Int,
    val imageUrl: String?,
    val backgroundColor: String?,
    val isPaused: Boolean,
    val isOutOfStock: Boolean,
    val isUserInputRequired: Boolean
)

data class ChannelPointsState(
    val balance: Int?,
    val pointName: String?,
    val iconUrl: String?,
    val rewards: List<PointReward>
)