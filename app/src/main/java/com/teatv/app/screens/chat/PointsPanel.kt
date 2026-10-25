package com.teatv.app.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.teatv.app.R
import com.teatv.app.chat.points.ChannelPointsState
import com.teatv.app.chat.points.PointReward
import com.teatv.app.ui.theme.TeaColors
import coil.request.ImageRequest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PointsPanel(
    state: ChannelPointsState,
    onRedeem: (PointReward, String?) -> Unit,
    onDismiss: () -> Unit,
    isBot: Boolean,
    onBotToggle: (Boolean) -> Unit,
) {
    var inputTarget by remember { mutableStateOf<PointReward?>(null) }
    var inputText by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = TeaColors.ChatBg
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TeaColors.Surface, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                PointIcon(state.iconUrl, 32)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = state.balance
                            ?.let { String.format("%,d", it).replace(',', ' ') }
                            ?: "—",
                        color = TeaColors.TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = state.pointName ?: "баллы канала",
                        color = TeaColors.TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Фарм-мод",
                        color = TeaColors.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Фоновый фарм баллов",
                        color = TeaColors.TextSecondary,
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = isBot,
                    onCheckedChange = onBotToggle,
                    colors = SwitchDefaults.colors(checkedTrackColor = TeaColors.Accent)
                )
            }
            Spacer(Modifier.height(12.dp))
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                items(state.rewards) { r ->
                    RewardRow(r, state.balance) {
                        if (r.isUserInputRequired) {
                            inputTarget = r
                            inputText = ""
                        } else {
                            onRedeem(r, null)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    inputTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { inputTarget = null },
            containerColor = TeaColors.Surface,
            title = {
                Text(
                    text = target.title,
                    color = TeaColors.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    label = { Text("Сообщение для награды") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !(target.isUserInputRequired && inputText.isBlank()),
                    onClick = {
                        onRedeem(target, inputText.takeIf { it.isNotBlank() })
                        inputTarget = null
                    }
                ) {
                    Text("Потратить ${target.cost}", color = TeaColors.Accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { inputTarget = null }) {
                    Text("Отмена", color = TeaColors.TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun RewardRow(
    r: PointReward,
    balance: Int?,
    onRedeem: () -> Unit
) {
    val dim = r.isPaused || r.isOutOfStock
    val affordable = balance != null && balance >= r.cost
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .alpha(if (dim) 0.4f else 1f)
            .clickable(enabled = !dim) { onRedeem() }
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        if (r.imageUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(r.imageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier.size(36.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(parseColorSafe(r.backgroundColor))
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = r.title,
            color = TeaColors.TextPrimary,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = String.format("%,d", r.cost).replace(',', ' '),
            color = if (affordable) TeaColors.Accent else TeaColors.TextSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun PointIcon(url: String?, sizeDp: Int) {
    if (url != null) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(url)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(sizeDp.dp)
        )
    } else {
        Icon(
            painter = painterResource(R.drawable.ic_points),
            contentDescription = null,
            tint = TeaColors.Accent,
            modifier = Modifier.size(sizeDp.dp)
        )
    }
}

private fun parseColorSafe(hex: String?): Color = try {
    if (hex.isNullOrBlank()) TeaColors.Surface else Color(android.graphics.Color.parseColor(hex))
} catch (_: Exception) {
    TeaColors.Surface
}