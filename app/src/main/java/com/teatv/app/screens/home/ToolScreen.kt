package com.teatv.app.ui.screens.home

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
internal fun ToolsScreen(
    modModeEnabled: Boolean,
    onModModeChange: (Boolean) -> Unit,
    splitMessagesEnabled: Boolean,
    onSplitMessagesChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TeaColors.Background)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Шапка экрана
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_shield),
                contentDescription = null,
                tint = TeaColors.Accent,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "Инструменты",
                color = TeaColors.TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Карточка: Модераторский режим
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(TeaColors.Surface)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Модераторский режим",
                        color = TeaColors.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "Включает жесты свайпа для модерации сообщений чата (7TV) и боковые шторки на каналах, где у вас есть права модератора.",
                        color = TeaColors.TextSecondary,
                        fontSize = 12.sp
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = modModeEnabled,
                    onCheckedChange = onModModeChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = TeaColors.Accent,
                        uncheckedThumbColor = TeaColors.TextSecondary,
                        uncheckedTrackColor = TeaColors.Background
                    )
                )
            }
        }

        // Карточка: Разделение длинных сообщений
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(TeaColors.Surface)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Разделение сообщений",
                        color = TeaColors.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "Позволяет писать текст любой длины без лимита. При превышении 500 символов сообщение будет автоматически разбито и отправлено частями с безопасной антиспам-задержкой.",
                        color = TeaColors.TextSecondary,
                        fontSize = 12.sp
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = splitMessagesEnabled,
                    onCheckedChange = onSplitMessagesChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = TeaColors.Accent,
                        uncheckedThumbColor = TeaColors.TextSecondary,
                        uncheckedTrackColor = TeaColors.Background
                    )
                )
            }
        }
    }
}
