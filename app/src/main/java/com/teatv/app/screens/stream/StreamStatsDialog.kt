package com.teatv.app.ui.screens.stream

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.teatv.app.data.StreamerItem
import com.teatv.app.ui.theme.TeaColors

@Composable
internal fun StreamStatsDialog(
    target: StreamerItem,
    viewers: List<String>?,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .background(TeaColors.Surface.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                .padding(14.dp)
        ) {
            Text(
                text = target.name,
                color = TeaColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            StatRow("Зрители", fmt(target.stream?.viewerCount ?: 0))
            StatRow("Категория", target.stream?.gameName ?: "—")
            StatRow("В эфире", uptime(target.stream?.startedAt))

            Spacer(Modifier.height(8.dp))
            when {
                viewers == null -> Text(
                    "Загрузка…",
                    color = TeaColors.TextSecondary,
                    fontSize = 12.sp
                )
                viewers.isEmpty() -> Text(
                    "Ники доступны только модераторам канала",
                    color = TeaColors.TextSecondary,
                    fontSize = 12.sp
                )
                else -> {
                    Text(
                        "В чате (${viewers.size}):",
                        color = TeaColors.TextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    LazyColumn {
                        items(viewers) { nick ->
                            Text(
                                text = nick,
                                color = TeaColors.TextPrimary,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}