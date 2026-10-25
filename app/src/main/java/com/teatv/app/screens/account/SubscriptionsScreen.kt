package com.teatv.app.ui.screens.account

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teatv.app.R
import com.teatv.app.data.MySubscription
import com.teatv.app.ui.theme.TeaColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Composable
fun SubscriptionsScreen(
    subscriptions: List<MySubscription>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TeaColors.Background)
            .statusBarsPadding()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_arrow_back),
                contentDescription = "Назад",
                tint = TeaColors.TextPrimary,
                modifier = Modifier
                    .size(28.dp)
                    .clickable { onBack() }
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "Подписки",
                color = TeaColors.TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(16.dp))

        if (subscriptions.isEmpty()) {
            Text(
                "Нет активных подписок",
                color = TeaColors.TextSecondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 32.dp)
            )
        } else {
            LazyColumn {
                items(subscriptions, key = { it.broadcasterId }) { sub ->
                    SubscriptionRow(sub)
                }
            }
        }
    }
}

@Composable
private fun SubscriptionRow(sub: MySubscription) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                sub.broadcasterName,
                color = TeaColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            val tierText = when (sub.tier) {
                "1000" -> "Tier 1"
                "2000" -> "Tier 2"
                "3000" -> "Tier 3"
                else -> "Подписка"
            }
            Text(
                "$tierText • ${sub.planName}",
                color = TeaColors.TextSecondary,
                fontSize = 13.sp
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            val left = sub.createdAt?.let { daysLeft(it) }
            Text(
                left?.let { "обновится через $it дн." } ?: "активна",
                color = TeaColors.Accent,
                fontSize = 12.sp
            )
            Text(
                sub.createdAt?.let { "продление: ${sinceDate(it)}" } ?: "",
                color = TeaColors.TextSecondary,
                fontSize = 11.sp
            )
        }
    }
}

private fun sinceDate(iso: String): String = try {
    DateTimeFormatter.ofPattern("dd.MM.yyyy")
        .withZone(ZoneId.systemDefault())
        .format(Instant.parse(iso))
} catch (_: Exception) {
    iso.take(10)
}

private fun daysLeft(iso: String): Long? = try {
    val date = Instant.parse(iso).atZone(ZoneId.systemDefault()).toLocalDate()
    val now = LocalDate.now()
    if (date.isAfter(now)) {
        ChronoUnit.DAYS.between(now, date)
    } else {
        var next = date
        while (!next.isAfter(now)) next = next.plusMonths(1)
        ChronoUnit.DAYS.between(now, next)
    }
} catch (_: Exception) {
    null
}