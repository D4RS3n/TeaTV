package com.teatv.app.screens.stream.mod

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teatv.app.R
import com.teatv.app.chat.ChatMessage
import com.teatv.app.mod.ModLogItem
import com.teatv.app.mod.PinnedMessage
import com.teatv.app.mod.PredictionInfo
import com.teatv.app.mod.PredictionOption
import com.teatv.app.mod.RewardRequestItem
import com.teatv.app.mod.UnbanRequestItem
import com.teatv.app.ui.theme.TeaColors
import kotlin.math.roundToInt

enum class ModToolScreen {
    GRID,
    LOGS,
    REWARDS,
    PREDICTIONS,
    UNBAN_REQUESTS,
    ANNOUNCEMENTS
}

@Composable
fun ModToolsDrawer(
    channelName: String,
    logs: List<ModLogItem>,
    rewards: List<RewardRequestItem>,
    prediction: PredictionInfo?,
    unbanRequests: List<UnbanRequestItem>,
    pinnedMessage: PinnedMessage?,
    selectedChatMessage: ChatMessage?,
    onClose: () -> Unit,
    onFulfillReward: (RewardRequestItem) -> Unit,
    onRejectReward: (RewardRequestItem) -> Unit,
    onFulfillAllRewards: () -> Unit,
    onRejectAllRewards: () -> Unit,
    onCreatePrediction: (String, List<String>, Int) -> Unit,
    onResolvePrediction: (String) -> Unit,
    onCancelPrediction: () -> Unit,
    onApproveUnban: (UnbanRequestItem) -> Unit,
    onDenyUnban: (UnbanRequestItem) -> Unit,
    onSendAnnounce: (String) -> Unit,
    onPinMessage: (ChatMessage) -> Unit,
    onUnpinMessage: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentScreen by remember { mutableStateOf(ModToolScreen.GRID) }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(330.dp)
            .background(TeaColors.Surface)
            .statusBarsPadding()
            .padding(16.dp)
    ) {
        // Шапка шторки: если в подэкране — кнопка Назад, если в меню — заголовок и закрыть
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (currentScreen != ModToolScreen.GRID) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { currentScreen = ModToolScreen.GRID }
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = "Назад",
                        tint = TeaColors.Accent,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = when (currentScreen) {
                            ModToolScreen.LOGS -> "Журнал действий"
                            ModToolScreen.REWARDS -> "Запросы на награды"
                            ModToolScreen.PREDICTIONS -> "Прогнозы"
                            ModToolScreen.UNBAN_REQUESTS -> "Снятие банов"
                            ModToolScreen.ANNOUNCEMENTS -> "Оповещения и закреп"
                            ModToolScreen.GRID -> ""
                        },
                        color = TeaColors.TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Text(
                    text = "Модерация канала",
                    color = TeaColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

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

        when (currentScreen) {
            ModToolScreen.GRID -> {
                ModGridMenu(onSelect = { currentScreen = it })
            }
            ModToolScreen.LOGS -> {
                ModLogsContent(logs)
            }
            ModToolScreen.REWARDS -> {
                ModRewardsContent(
                    rewards = rewards,
                    onFulfill = onFulfillReward,
                    onReject = onRejectReward,
                    onFulfillAll = onFulfillAllRewards,
                    onRejectAll = onRejectAllRewards
                )
            }
            ModToolScreen.PREDICTIONS -> {
                ModPredictionsContent(
                    prediction = prediction,
                    onCreate = onCreatePrediction,
                    onResolve = onResolvePrediction,
                    onCancel = onCancelPrediction
                )
            }
            ModToolScreen.UNBAN_REQUESTS -> {
                ModUnbanRequestsContent(
                    requests = unbanRequests,
                    onApprove = onApproveUnban,
                    onDeny = onDenyUnban
                )
            }
            ModToolScreen.ANNOUNCEMENTS -> {
                ModAnnounceContent(
                    pinnedMessage = pinnedMessage,
                    selectedChatMessage = selectedChatMessage,
                    onSendAnnounce = onSendAnnounce,
                    onPin = onPinMessage,
                    onUnpin = onUnpinMessage
                )
            }
        }
    }
}

/** Меню из 5 квадратных иконок (Mobile Twitch Mod View style). */
@Composable
private fun ModGridMenu(onSelect: (ModToolScreen) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ModSquareButton(
                title = "Журнал действий",
                iconRes = R.drawable.ic_mod_log,
                modifier = Modifier.weight(1f),
                onClick = { onSelect(ModToolScreen.LOGS) }
            )
            ModSquareButton(
                title = "Запросы на награды",
                iconRes = R.drawable.ic_mod_rewards,
                modifier = Modifier.weight(1f),
                onClick = { onSelect(ModToolScreen.REWARDS) }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ModSquareButton(
                title = "Прогнозы",
                iconRes = R.drawable.ic_mod_prediction,
                modifier = Modifier.weight(1f),
                onClick = { onSelect(ModToolScreen.PREDICTIONS) }
            )
            ModSquareButton(
                title = "Снятие блокировок",
                iconRes = R.drawable.ic_mod_unban,
                modifier = Modifier.weight(1f),
                onClick = { onSelect(ModToolScreen.UNBAN_REQUESTS) }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ModSquareButton(
                title = "Оповещения и закреп",
                iconRes = R.drawable.ic_mod_announce,
                modifier = Modifier.weight(1f),
                onClick = { onSelect(ModToolScreen.ANNOUNCEMENTS) }
            )
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun ModSquareButton(
    title: String,
    iconRes: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .aspectRatio(1.1f)
            .clip(RoundedCornerShape(12.dp))
            .background(TeaColors.Background)
            .border(1.dp, TeaColors.Accent.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = title,
            tint = TeaColors.Accent,
            modifier = Modifier.size(32.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = title,
            color = TeaColors.TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 13.sp
        )
    }
}

/** 1. Журнал действий. */
@Composable
private fun ModLogsContent(logs: List<ModLogItem>) {
    if (logs.isEmpty()) {
        EmptyPlaceholder("Журнал действий пуст")
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(logs, key = { it.id }) { item ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(TeaColors.Background)
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(item.modName, color = TeaColors.Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(item.action, color = TeaColors.TextSecondary, fontSize = 11.sp)
                    }
                    if (!item.targetName.isNullOrBlank()) {
                        Text("Цель: @${item.targetName}", color = TeaColors.TextPrimary, fontSize = 12.sp)
                    }
                    if (!item.details.isNullOrBlank()) {
                        Text(item.details, color = TeaColors.TextSecondary, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

/** 2. Запросы на награды со свайпом и массовыми кнопками. */
@Composable
private fun ModRewardsContent(
    rewards: List<RewardRequestItem>,
    onFulfill: (RewardRequestItem) -> Unit,
    onReject: (RewardRequestItem) -> Unit,
    onFulfillAll: () -> Unit,
    onRejectAll: () -> Unit
) {
    var showConfirmFulfillAll by remember { mutableStateOf(false) }
    var showConfirmRejectAll by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Две кнопки вверху: Выполнить все / Отклонить все
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { showConfirmFulfillAll = true },
                colors = ButtonDefaults.buttonColors(containerColor = TeaColors.Accent),
                modifier = Modifier.weight(1f),
                enabled = rewards.isNotEmpty()
            ) {
                Text("Выполнить все", fontSize = 11.sp, maxLines = 1)
            }
            Button(
                onClick = { showConfirmRejectAll = true },
                colors = ButtonDefaults.buttonColors(containerColor = TeaColors.Live.copy(alpha = 0.8f)),
                modifier = Modifier.weight(1f),
                enabled = rewards.isNotEmpty()
            ) {
                Text("Отклонить все", fontSize = 11.sp, maxLines = 1)
            }
        }

        Spacer(Modifier.height(10.dp))

        if (rewards.isEmpty()) {
            EmptyPlaceholder("Очередь наград пуста")
        } else {
            Text(
                "Свайп вправо — выполнить, влево — отклонить",
                color = TeaColors.TextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(rewards, key = { it.id }) { item ->
                    SwipeableRewardItem(
                        item = item,
                        onFulfill = { onFulfill(item) },
                        onReject = { onReject(item) }
                    )
                }
            }
        }
    }

    if (showConfirmFulfillAll) {
        ConfirmDialog(
            title = "Выполнить все награды?",
            text = "Будут подтверждены все запросы в очереди (${rewards.size}).",
            confirmText = "Выполнить",
            confirmColor = TeaColors.Accent,
            onConfirm = {
                showConfirmFulfillAll = false
                onFulfillAll()
            },
            onDismiss = { showConfirmFulfillAll = false }
        )
    }

    if (showConfirmRejectAll) {
        ConfirmDialog(
            title = "Отклонить все награды?",
            text = "Баллы будут возвращены пользователям (${rewards.size}).",
            confirmText = "Отклонить",
            confirmColor = TeaColors.Live,
            onConfirm = {
                showConfirmRejectAll = false
                onRejectAll()
            },
            onDismiss = { showConfirmRejectAll = false }
        )
    }
}

@Composable
private fun SwipeableRewardItem(
    item: RewardRequestItem,
    onFulfill: () -> Unit,
    onReject: () -> Unit
) {
    var dragX by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    dragX > 80f -> TeaColors.Accent.copy(alpha = 0.3f)
                    dragX < -80f -> TeaColors.Live.copy(alpha = 0.3f)
                    else -> TeaColors.Background
                }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(dragX.roundToInt(), 0) }
                .pointerInput(item.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (dragX > 140f) onFulfill()
                            else if (dragX < -140f) onReject()
                            dragX = 0f
                        },
                        onDragCancel = { dragX = 0f }
                    ) { change, amount ->
                        change.consume()
                        dragX = (dragX + amount).coerceIn(-220f, 220f)
                    }
                }
                .background(TeaColors.Background)
                .border(1.dp, TeaColors.Surface, RoundedCornerShape(8.dp))
                .padding(10.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(item.rewardTitle, color = TeaColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("${item.rewardCost} 🪙", color = TeaColors.TextSecondary, fontSize = 11.sp)
                }
                Text("От: @${item.userName}", color = TeaColors.TextPrimary, fontSize = 12.sp)
                if (!item.userInput.isNullOrBlank()) {
                    Text("«${item.userInput}»", color = TeaColors.TextSecondary, fontSize = 12.sp)
                }
            }
        }
    }
}

/** 3. Прогнозы на канале. */
@Composable
private fun ModPredictionsContent(
    prediction: PredictionInfo?,
    onCreate: (String, List<String>, Int) -> Unit,
    onResolve: (String) -> Unit,
    onCancel: () -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    if (prediction == null || prediction.status == "RESOLVED" || prediction.status == "CANCELED") {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            EmptyPlaceholder("Нет активного прогноза")
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { showCreateDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = TeaColors.Accent)
            ) {
                Text("Создать прогноз", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(TeaColors.Background)
                .padding(14.dp)
        ) {
            Text(prediction.title, color = TeaColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Статус: ${prediction.status}", color = TeaColors.Accent, fontSize = 11.sp)
            Spacer(Modifier.height(12.dp))

            prediction.options.forEach { opt ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(TeaColors.Surface)
                        .clickable { onResolve(opt.id) }
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(opt.title, color = TeaColors.TextPrimary, fontSize = 13.sp)
                    Text("Победа ›", color = TeaColors.Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(12.dp))
            TextButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Отменить прогноз (вернуть баллы)", color = TeaColors.Live, fontSize = 12.sp)
            }
        }
    }

    if (showCreateDialog) {
        CreatePredictionDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { title, opts, dur ->
                onCreate(title, opts, dur)
                showCreateDialog = false
            }
        )
    }
}

@Composable
private fun CreatePredictionDialog(
    onDismiss: () -> Unit,
    onCreate: (String, List<String>, Int) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var opt1 by remember { mutableStateOf("Да") }
    var opt2 by remember { mutableStateOf("Нет") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Создать прогноз", color = TeaColors.TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InputBox(value = title, onValueChange = { title = it }, placeholder = "Вопрос прогноза...")
                InputBox(value = opt1, onValueChange = { opt1 = it }, placeholder = "Вариант 1...")
                InputBox(value = opt2, onValueChange = { opt2 = it }, placeholder = "Вариант 2...")
            }
        },
        containerColor = TeaColors.Surface,
        confirmButton = {
            TextButton(
                onClick = {
                    if (title.isNotBlank() && opt1.isNotBlank() && opt2.isNotBlank()) {
                        onCreate(title.trim(), listOf(opt1.trim(), opt2.trim()), 120)
                    }
                }
            ) {
                Text("Создать", color = TeaColors.Accent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена", color = TeaColors.TextSecondary) }
        }
    )
}

/** 4. Запросы на снятие блокировок (Unban Requests). */
@Composable
private fun ModUnbanRequestsContent(
    requests: List<UnbanRequestItem>,
    onApprove: (UnbanRequestItem) -> Unit,
    onDeny: (UnbanRequestItem) -> Unit
) {
    if (requests.isEmpty()) {
        EmptyPlaceholder("Нет запросов на разбан")
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(requests, key = { it.id }) { item ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(TeaColors.Background)
                        .padding(12.dp)
                ) {
                    Text("@${item.userLogin}", color = TeaColors.Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    if (!item.banReason.isNullOrBlank()) {
                        Text("Причина бана: ${item.banReason}", color = TeaColors.TextSecondary, fontSize = 11.sp)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("«${item.appealMessage}»", color = TeaColors.TextPrimary, fontSize = 12.sp)

                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { onDeny(item) }) {
                            Text("Отклонить", color = TeaColors.Live, fontSize = 12.sp)
                        }
                        TextButton(onClick = { onApprove(item) }) {
                            Text("Разбанить", color = TeaColors.Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/** 5. Оповещения /announce и закрепление сообщения. */
@Composable
private fun ModAnnounceContent(
    pinnedMessage: PinnedMessage?,
    selectedChatMessage: ChatMessage?,
    onSendAnnounce: (String) -> Unit,
    onPin: (ChatMessage) -> Unit,
    onUnpin: () -> Unit
) {
    var announceText by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Блок отправки /announce
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(TeaColors.Background)
                .padding(12.dp)
        ) {
            Text("Отправить оповещение (/announce)", color = TeaColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            InputBox(value = announceText, onValueChange = { announceText = it }, placeholder = "Текст оповещения...")
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    if (announceText.isNotBlank()) {
                        onSendAnnounce(announceText.trim())
                        announceText = ""
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = TeaColors.Accent),
                modifier = Modifier.align(Alignment.End),
                enabled = announceText.isNotBlank()
            ) {
                Text("Отправить", fontSize = 11.sp)
            }
        }

        // Блок закреплённого сообщения
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(TeaColors.Background)
                .padding(12.dp)
        ) {
            Text("Закреплённое сообщение", color = TeaColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            if (pinnedMessage != null) {
                Text("@${pinnedMessage.sender}: ${pinnedMessage.text}", color = TeaColors.TextPrimary, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onUnpin,
                    colors = ButtonDefaults.buttonColors(containerColor = TeaColors.Live.copy(alpha = 0.8f))
                ) {
                    Text("Открепить сообщение", fontSize = 11.sp)
                }
            } else {
                Text("Нет закреплённого сообщения.", color = TeaColors.TextSecondary, fontSize = 12.sp)
                if (selectedChatMessage != null) {
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { onPin(selectedChatMessage) },
                        colors = ButtonDefaults.buttonColors(containerColor = TeaColors.Accent)
                    ) {
                        Text("Закрепить выбранное (@${selectedChatMessage.sender})", fontSize = 11.sp)
                    }
                } else {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Зажмите сообщение в чате, чтобы выбрать его для закрепления.",
                        color = TeaColors.TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun InputBox(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = TextStyle(color = TeaColors.TextPrimary, fontSize = 13.sp),
        cursorBrush = SolidColor(TeaColors.Accent),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(TeaColors.Surface)
            .padding(10.dp),
        decorationBox = { inner ->
            if (value.isEmpty()) {
                Text(placeholder, color = TeaColors.TextSecondary.copy(alpha = 0.6f), fontSize = 12.sp)
            }
            inner()
        }
    )
}

@Composable
private fun EmptyPlaceholder(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = TeaColors.TextSecondary, fontSize = 13.sp)
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    text: String,
    confirmText: String,
    confirmColor: Color,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = TeaColors.TextPrimary, fontWeight = FontWeight.Bold) },
        text = { Text(text, color = TeaColors.TextSecondary) },
        containerColor = TeaColors.Surface,
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText, color = confirmColor, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена", color = TeaColors.TextSecondary)
            }
        }
    )
}
