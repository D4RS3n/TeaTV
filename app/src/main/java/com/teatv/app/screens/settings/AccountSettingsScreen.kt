package com.teatv.app.screens.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.teatv.app.R
import com.teatv.app.data.TwitchUser
import com.teatv.app.ui.theme.TeaColors

@Composable
fun AccountSettingsScreen(
    user: TwitchUser?,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showLogoutDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TeaColors.Background)
            .statusBarsPadding()
            .padding(16.dp)
    ) {
        // Шапка
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
                "Учётная запись",
                color = TeaColors.TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(24.dp))

        // Профиль пользователя
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(TeaColors.Surface)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = user?.avatarUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(TeaColors.Background)
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    user?.displayName ?: "Пользователь",
                    color = TeaColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                if (!user?.login.isNullOrBlank()) {
                    Text(
                        user?.login.orEmpty(),
                        color = TeaColors.TextSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Пункт 1: Редактировать профиль (заглушка)
        AccountActionItem(
            title = "Редактировать профиль",
            subtitle = "Изменение аватара, описания и настроек профиля",
            onClick = {
                Toast.makeText(context, "Редактирование профиля будет доступно позже", Toast.LENGTH_SHORT).show()
            }
        )

        Spacer(Modifier.height(12.dp))

        // Пункт 2: Выйти из аккаунта
        AccountActionItem(
            title = "Выйти из аккаунта",
            subtitle = "Завершение текущей сессии Twitch",
            isDestructive = true,
            onClick = { showLogoutDialog = true }
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = {
                Text(
                    "Выход из аккаунта",
                    color = TeaColors.TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "Вы уверены, что хотите выйти из учетной записи?",
                    color = TeaColors.TextSecondary
                )
            },
            containerColor = TeaColors.Surface,
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        onLogout()
                    }
                ) {
                    Text("Выйти", color = TeaColors.Live, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Отмена", color = TeaColors.TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun AccountActionItem(
    title: String,
    subtitle: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(TeaColors.Surface)
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = if (isDestructive) TeaColors.Live else TeaColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                color = TeaColors.TextSecondary,
                fontSize = 12.sp
            )
        }
        Text(
            "›",
            color = if (isDestructive) TeaColors.Live else TeaColors.TextSecondary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
