package com.teatv.app.screens.stream.mod

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.teatv.app.R
import com.teatv.app.chat.ChatMessage
import com.teatv.app.mod.ChannelModesState
import com.teatv.app.mod.ChatterInfo
import com.teatv.app.ui.theme.TeaColors

@Composable
fun ChatterDrawer(
    channelName: String,
    chatter: ChatterInfo?,
    modes: ChannelModesState,
    onClose: () -> Unit,
    onUnban: (String) -> Unit,
    onToggleShield: () -> Unit,
    onToggleSubOnly: (Boolean) -> Unit,
    onToggleEmoteOnly: (Boolean) -> Unit,
    onSelectFollowersDuration: (Int) -> Unit,
    onSelectSlowMode: (Int) -> Unit,
    onOpenBlockedWords: () -> Unit,
    onOpenPermittedWords: () -> Unit,
    chatterMessages: List<ChatMessage> = emptyList(),
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(320.dp)
            .background(TeaColors.Surface)
            .statusBarsPadding()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Шапка шторки
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Чаттер и режимы",
                color = TeaColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "✕",
                color = TeaColors.TextSecondary,
                fontSize = 18.sp,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onClose() }
                    .padding(4.dp)
            )
        }

        Spacer(Modifier.height(16.dp))

        // 1. Блок выделенного пользователя
        if (chatter == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(TeaColors.Background)
                    .border(1.dp, TeaColors.Accent.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Пользователь не выделен",
                        color = TeaColors.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Зажмите сообщение или ник в чате для выделения пользователя",
                        color = TeaColors.TextSecondary,
                        fontSize = 11.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(TeaColors.Background)
                    .border(1.dp, TeaColors.Accent, RoundedCornerShape(10.dp))
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = chatter.avatarUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(TeaColors.Surface)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = chatter.displayName,
                            color = TeaColors.TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "@${chatter.userLogin}",
                            color = TeaColors.TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Модераторская статистика активности
                InfoRow("Сообщений в чате:", "${chatterMessages.size}")
                val firstMsg = chatterMessages.firstOrNull()
                val lastMsg = chatterMessages.lastOrNull()
                if (firstMsg != null && lastMsg != null) {
                    val sdf = remember { java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()) }
                    InfoRow("Первое сообщение:", sdf.format(java.util.Date(firstMsg.timestamp)))
                    InfoRow("Последнее сообщение:", sdf.format(java.util.Date(lastMsg.timestamp)))
                }

                Spacer(Modifier.height(8.dp))

                // Раскрывающийся / листаемый список всех сообщений чаттера
                var showMessagesList by remember { mutableStateOf(true) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { showMessagesList = !showMessagesList }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Все сообщения (${chatterMessages.size})",
                        color = TeaColors.TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (showMessagesList) "▲ Скрыть" else "▼ Открыть",
                        color = TeaColors.Accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (showMessagesList) {
                    if (chatterMessages.isEmpty()) {
                        Text(
                            text = "Сообщений на текущем стриме нет",
                            color = TeaColors.TextSecondary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    } else {
                        val sdf = remember { java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()) }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(TeaColors.Surface)
                                .padding(8.dp)
                        ) {
                            LazyColumn {
                                items(chatterMessages) { msg ->
                                    val timeStr = sdf.format(java.util.Date(msg.timestamp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "[$timeStr] ",
                                            color = TeaColors.TextSecondary,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = msg.text,
                                            color = TeaColors.TextPrimary,
                                            fontSize = 11.sp,
                                            lineHeight = 15.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Кнопка разбана
                if (chatter.isBanned || chatter.isTimedOut) {
                    Spacer(Modifier.height(10.dp))
                    TextButton(
                        onClick = { onUnban(chatter.userLogin) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(TeaColors.Accent.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                    ) {
                        Text("Разблокировать пользователя", color = TeaColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // 2. Блок «Режимы Канала» (точь-в-точь по скриншоту)
        Text(
            text = "Режимы Канала $channelName",
            color = TeaColors.TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(12.dp))

        // Защитный режим
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_shield),
                contentDescription = null,
                tint = if (modes.shieldMode) TeaColors.Live else TeaColors.TextSecondary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "Защитный режим",
                color = TeaColors.TextPrimary,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (modes.shieldMode) "ВКЛЮЧЕНО" else "ВЫКЛЮЧЕНО",
                color = if (modes.shieldMode) TeaColors.Live else TeaColors.TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onToggleShield() }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        // Чат только для подписчиков
        ModeSwitchRow(
            iconRes = R.drawable.ic_star,
            title = "Чат только для подписчиков",
            checked = modes.subOnly,
            onCheckedChange = onToggleSubOnly
        )

        // Чат только для смайликов
        ModeSwitchRow(
            iconRes = R.drawable.ic_emote,
            title = "Чат только для смайликов",
            checked = modes.emoteOnly,
            onCheckedChange = onToggleEmoteOnly
        )

        // Только для фолловеров (селектор)
        var showFollowersDropdown by remember { mutableStateOf(false) }
        val followerDurations = listOf(
            -1 to "Выкл.",
            0 to "0 мин (Все)",
            10 to "10 мин",
            30 to "30 мин",
            60 to "1 час",
            1440 to "1 день",
            10080 to "1 неделя"
        )
        val currentFollowerLabel = followerDurations.firstOrNull { it.first == modes.followersDurationMinutes }?.second ?: "Выкл."

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_follow),
                contentDescription = null,
                tint = TeaColors.TextSecondary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "Только для фолловеров",
                color = TeaColors.TextPrimary,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f)
            )
            Box {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(TeaColors.Background)
                        .clickable { showFollowersDropdown = true }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(currentFollowerLabel, color = TeaColors.TextPrimary, fontSize = 12.sp)
                    Spacer(Modifier.width(4.dp))
                    Text("↕", color = TeaColors.TextSecondary, fontSize = 11.sp)
                }

                DropdownMenu(
                    expanded = showFollowersDropdown,
                    onDismissRequest = { showFollowersDropdown = false }
                ) {
                    followerDurations.forEach { (mins, label) ->
                        DropdownMenuItem(
                            text = { Text(label, color = TeaColors.TextPrimary) },
                            onClick = {
                                onSelectFollowersDuration(mins)
                                showFollowersDropdown = false
                            }
                        )
                    }
                }
            }
        }

        // Медленный режим (селектор)
        var showSlowDropdown by remember { mutableStateOf(false) }
        val slowDurations = listOf(
            0 to "Выкл.",
            3 to "3 сек",
            5 to "5 сек",
            10 to "10 сек",
            30 to "30 сек",
            60 to "60 сек",
            120 to "120 сек"
        )
        val currentSlowLabel = slowDurations.firstOrNull { it.first == modes.slowModeSeconds }?.second ?: "Выкл."

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_hourglass),
                contentDescription = null,
                tint = TeaColors.TextSecondary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "Медленный режим",
                color = TeaColors.TextPrimary,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f)
            )
            Box {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(TeaColors.Background)
                        .clickable { showSlowDropdown = true }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(currentSlowLabel, color = TeaColors.TextPrimary, fontSize = 12.sp)
                    Spacer(Modifier.width(4.dp))
                    Text("↕", color = TeaColors.TextSecondary, fontSize = 11.sp)
                }

                DropdownMenu(
                    expanded = showSlowDropdown,
                    onDismissRequest = { showSlowDropdown = false }
                ) {
                    slowDurations.forEach { (secs, label) ->
                        DropdownMenuItem(
                            text = { Text(label, color = TeaColors.TextPrimary) },
                            onClick = {
                                onSelectSlowMode(secs)
                                showSlowDropdown = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Блокируемые слова
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { onOpenBlockedWords() }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Блокируемые слова",
                color = TeaColors.TextPrimary,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${modes.blockedTermsCount}",
                color = TeaColors.TextSecondary,
                fontSize = 13.sp
            )
            Spacer(Modifier.width(6.dp))
            Text("›", color = TeaColors.TextSecondary, fontSize = 18.sp)
        }

        // Разрешенные слова
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { onOpenPermittedWords() }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Разрешенные слова",
                color = TeaColors.TextPrimary,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${modes.permittedTermsCount}",
                color = TeaColors.TextSecondary,
                fontSize = 13.sp
            )
            Spacer(Modifier.width(6.dp))
            Text("›", color = TeaColors.TextSecondary, fontSize = 18.sp)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TeaColors.TextSecondary, fontSize = 12.sp)
        Text(value, color = TeaColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ModeSwitchRow(
    iconRes: Int,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = TeaColors.TextSecondary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            title,
            color = TeaColors.TextPrimary,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = TeaColors.Accent,
                uncheckedThumbColor = TeaColors.TextSecondary,
                uncheckedTrackColor = TeaColors.Background
            )
        )
    }
}
