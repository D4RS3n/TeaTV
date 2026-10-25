package com.teatv.app.screens.stream.mod

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class ModSliderAction(
    val step: Int,
    val title: String,
    val iconEmoji: String,
    val color: Color,
    val durationSeconds: Int? = null
) {
    NONE(0, "Отмена", "", Color.Transparent, null),
    DELETE(1, "Удалить", "🗑", Color(0xFFFF9800), null),
    TIMEOUT_1S(2, "1 сек", "⏱", Color(0xFFFFB300), 1),
    TIMEOUT_1M(3, "1 мин", "⏱", Color(0xFFFFA000), 60),
    TIMEOUT_5M(4, "5 мин", "⏱", Color(0xFFFB8C00), 300),
    TIMEOUT_10M(5, "10 мин", "⏱", Color(0xFFF57C00), 600),
    TIMEOUT_30M(6, "30 мин", "⏱", Color(0xFFEF6C00), 1800),
    TIMEOUT_1H(7, "1 час", "⏱", Color(0xFFE65100), 3600),
    TIMEOUT_4H(8, "4 часа", "⏱", Color(0xFFF4511E), 14400),
    TIMEOUT_24H(9, "24 часа", "⏱", Color(0xFFE53935), 86400),
    TIMEOUT_7D(10, "7 дней", "⏱", Color(0xFFD32F2F), 604800),
    BAN(11, "Бан", "⛔", Color(0xFFB71C1C), null)
}

object ModSliderHelper {

    /**
     * Полная градация 7TV с безопасным стартом от 70dp и шагами по 20dp:
     * Удалить -> 1с -> 1м -> 5м -> 10м -> 30м -> 1ч -> 4ч -> 24ч -> 7д -> Бан (с 270dp).
     */
    fun resolveAction(dragDp: Dp): ModSliderAction = when {
        dragDp < 70.dp -> ModSliderAction.NONE
        dragDp < 90.dp -> ModSliderAction.DELETE
        dragDp < 110.dp -> ModSliderAction.TIMEOUT_1S
        dragDp < 130.dp -> ModSliderAction.TIMEOUT_1M
        dragDp < 150.dp -> ModSliderAction.TIMEOUT_5M
        dragDp < 170.dp -> ModSliderAction.TIMEOUT_10M
        dragDp < 190.dp -> ModSliderAction.TIMEOUT_30M
        dragDp < 210.dp -> ModSliderAction.TIMEOUT_1H
        dragDp < 230.dp -> ModSliderAction.TIMEOUT_4H
        dragDp < 250.dp -> ModSliderAction.TIMEOUT_24H
        dragDp < 270.dp -> ModSliderAction.TIMEOUT_7D
        else -> ModSliderAction.BAN
    }
}
