package com.teatv.app.ui.screens.stream

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teatv.app.AudioPolicy
import com.teatv.app.R
import com.teatv.app.ViewMode
import com.teatv.app.ui.theme.TeaColors
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

internal fun fmt(n: Int): String =
    if (n >= 1000) "${n / 1000}.${(n % 1000) / 100}K" else n.toString()

internal fun uptime(iso: String?): String {
    if (iso.isNullOrBlank()) return "—"
    return try {
        val f = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
        val parsed = f.parse(iso) ?: return "—"
        val ms = System.currentTimeMillis() - parsed.time
        val h = TimeUnit.MILLISECONDS.toHours(ms)
        val m = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
        if (h > 0) "${h}ч ${m}м" else "${m}м"
    } catch (_: Exception) {
        "—"
    }
}

internal fun policyLabel(p: AudioPolicy): String = when (p) {
    AudioPolicy.Headphones -> "Только в наушниках"
    AudioPolicy.Always -> "Всегда"
    AudioPolicy.Never -> "Никогда"
}

internal fun viewLabel(m: ViewMode): String = when (m) {
    ViewMode.Normal -> "Обычный"
    ViewMode.AudioOnly -> "Только звук"
}

@Composable
internal fun StatRow(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(text = "$label: ", color = TeaColors.TextSecondary, fontSize = 13.sp)
        Text(
            text = value,
            color = TeaColors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
internal fun ViewersChip(count: Int?, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable { onClick() }
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_viewers),
            contentDescription = "Статистика",
            tint = TeaColors.Live,
            modifier = Modifier.size(12.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(text = fmt(count ?: 0), color = TeaColors.TextSecondary, fontSize = 11.sp)
    }
}