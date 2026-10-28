package com.teatv.app.ui.screens.stream

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.teatv.app.mod.ChatterInfo
import com.teatv.app.ui.theme.TeaColors

/**
 * Окно профиля пользователя для обычных зрителей (стилизовано как функция красного глазика).
 */
@Composable
fun ViewerProfileDialog(
    chatter: ChatterInfo?,
    isLoading: Boolean = false,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .background(TeaColors.Surface.copy(alpha = 0.95f), RoundedCornerShape(14.dp))
                .padding(16.dp)
        ) {
            // Шапка
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (chatter?.avatarUrl != null) {
                        AsyncImage(
                            model = chatter.avatarUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(TeaColors.Background)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(TeaColors.Background),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = (chatter?.displayName?.take(1) ?: "?").uppercase(),
                                color = TeaColors.TextPrimary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(Modifier.width(12.dp))

                    Column {
                        Text(
                            text = chatter?.displayName ?: "Зритель",
                            color = TeaColors.TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (!chatter?.userLogin.isNullOrBlank()) {
                            Text(
                                text = "@${chatter?.userLogin}",
                                color = TeaColors.TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Text(
                    text = "✕",
                    color = TeaColors.TextSecondary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onDismiss() }
                        .padding(6.dp)
                )
            }

            Spacer(Modifier.height(14.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(90.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = TeaColors.Accent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(28.dp)
                    )
                }
            } else if (chatter != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(TeaColors.Background.copy(alpha = 0.6f))
                        .padding(12.dp)
                ) {
                    ProfileInfoRow("Регистрация:", chatter.createdAt ?: "—")
                    ProfileInfoRow("Фолловит канал:", chatter.followedAt ?: "—")
                    if (chatter.isSubscribed) {
                        ProfileInfoRow("Подписка:", chatter.subTier ?: "Активна")
                    }

                    if (chatter.sevenTvBadges.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "7TV:",
                                color = TeaColors.TextSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.width(110.dp)
                            )
                            chatter.sevenTvBadges.take(6).forEach { bUrl ->
                                AsyncImage(
                                    model = bUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .padding(end = 4.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = "Не удалось загрузить информацию",
                    color = TeaColors.TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun ProfileInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = TeaColors.TextSecondary,
            fontSize = 12.sp
        )
        Text(
            text = value,
            color = TeaColors.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
