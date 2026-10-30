package com.teatv.app.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import com.teatv.app.R
import com.teatv.app.ui.theme.TeaColors

@Composable
fun EmoteOrderSettingsScreen(
    categories: List<String>,
    onSaveOrder: (List<String>) -> Unit,
    onResetDefault: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var list by remember(categories) { mutableStateOf(categories) }

    fun moveItem(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in list.indices || toIndex !in list.indices || fromIndex == toIndex) return
        val mutable = list.toMutableList()
        val item = mutable.removeAt(fromIndex)
        mutable.add(toIndex, item)
        list = mutable
        onSaveOrder(mutable)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TeaColors.Background)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Шапка экрана
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_arrow_back),
                    contentDescription = "Назад",
                    tint = TeaColors.Accent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Порядок категорий смайлов",
                color = TeaColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(10.dp))

        // Информационная карточка о закреплении "Избранные"
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(TeaColors.Surface)
                .padding(12.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "★",
                        color = Color(0xFFFFD700),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Избранные смайлики",
                        color = TeaColors.TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Категория «Избранные» всегда закреплена первее всех остальных. Смайлы добавляются в неё зажатием на панели. Если избранных смайлов нет — категория скрыта.",
                    color = TeaColors.TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Настройте порядок остальных категорий стрелками:",
            color = TeaColors.TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(list, key = { _, item -> item }) { index, categoryName ->
                CategoryOrderItem(
                    index = index + 1,
                    title = categoryName,
                    canMoveUp = index > 0,
                    canMoveDown = index < list.size - 1,
                    onMoveUp = { moveItem(index, index - 1) },
                    onMoveDown = { moveItem(index, index + 1) }
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Кнопка сброса по умолчанию
        OutlinedButton(
            onClick = {
                onResetDefault()
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = TeaColors.TextSecondary
            )
        ) {
            Text(
                text = "Сбросить порядок по умолчанию",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun CategoryOrderItem(
    index: Int,
    title: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(TeaColors.Surface)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Номер позиции
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .background(TeaColors.Background, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = index.toString(),
                    color = TeaColors.Accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = TeaColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = when {
                        title.equals("7TV", ignoreCase = true) -> "Глобальные и канальные смайлы 7TV"
                        title.equals("BTTV", ignoreCase = true) -> "Смайлы BetterTTV и FrankerFaceZ"
                        title.equals("Twitch", ignoreCase = true) -> "Официальные глобальные смайлы Twitch"
                        title.contains("текущего канала", ignoreCase = true) || title.equals("Смайлы канала", ignoreCase = true) -> "Смайлы текущей трансляции"
                        else -> "Смайлы канала подписки"
                    },
                    color = TeaColors.TextSecondary,
                    fontSize = 11.sp
                )
            }

            Spacer(Modifier.width(8.dp))

            // Кнопка ВВЕРХ
            IconButton(
                onClick = onMoveUp,
                enabled = canMoveUp,
                modifier = Modifier.size(32.dp)
            ) {
                Text(
                    text = "▲",
                    color = if (canMoveUp) TeaColors.Accent else TeaColors.TextSecondary.copy(alpha = 0.3f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Кнопка ВНИЗ
            IconButton(
                onClick = onMoveDown,
                enabled = canMoveDown,
                modifier = Modifier.size(32.dp)
            ) {
                Text(
                    text = "▼",
                    color = if (canMoveDown) TeaColors.Accent else TeaColors.TextSecondary.copy(alpha = 0.3f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
