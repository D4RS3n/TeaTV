package com.teatv.app.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.teatv.app.R
import com.teatv.app.settings.ClientSettingsStore
import com.teatv.app.settings.CustomTheme
import com.teatv.app.ui.theme.TeaColors

/**
 * Экран управления темами оформления:
 * выбор встроенных пресетов, сохранение текущей палитры,
 * переименование и удаление пользовательских тем.
 */
@Composable
fun ThemeManagerScreen(
    clientSettingsStore: ClientSettingsStore,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var customThemes by remember { mutableStateOf(clientSettingsStore.getCustomThemes()) }
    var refreshKey by remember { mutableStateOf(0) }

    // Состояния диалогов
    var showSaveDialog by remember { mutableStateOf(false) }
    var newThemeName by remember { mutableStateOf("") }

    var renameTargetTheme by remember { mutableStateOf<CustomTheme?>(null) }
    var renameThemeName by remember { mutableStateOf("") }

    var deleteTargetTheme by remember { mutableStateOf<CustomTheme?>(null) }

    fun refresh() {
        customThemes = clientSettingsStore.getCustomThemes()
        refreshKey++
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TeaColors.Background)
            .statusBarsPadding()
            .padding(16.dp)
    ) {
        // Шапка экрана
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
                "Темы оформления",
                color = TeaColors.TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )

            // Кнопка быстрого сохранения в шапке
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(TeaColors.Accent)
                    .clickable {
                        newThemeName = "Тема #${customThemes.size + 1}"
                        showSaveDialog = true
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    "+ Сохранить",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Карточка текущей палитры
            CurrentPaletteBanner(
                onSaveCurrent = {
                    newThemeName = "Тема #${customThemes.size + 1}"
                    showSaveDialog = true
                }
            )

            // Секция: Мои темы
            Text(
                "Мои темы (${customThemes.size})",
                color = TeaColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )

            if (customThemes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(TeaColors.Surface)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Пока нет сохраненных тем.\nНастройте цвета палитры и нажмите «+ Сохранить».",
                        color = TeaColors.TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            } else {
                customThemes.forEach { theme ->
                    val isActive = clientSettingsStore.matchesActiveTheme(theme)
                    ThemeCard(
                        theme = theme,
                        isActive = isActive,
                        onApply = {
                            clientSettingsStore.applyTheme(theme)
                            TeaColors.applyFromStore(clientSettingsStore)
                            refresh()
                        },
                        onRename = {
                            renameTargetTheme = theme
                            renameThemeName = theme.name
                        },
                        onDelete = {
                            deleteTargetTheme = theme
                        }
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            // Секция: Встроенные темы
            Text(
                "Встроенные темы",
                color = TeaColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )

            CustomTheme.BUILT_IN_THEMES.forEach { theme ->
                val isActive = clientSettingsStore.matchesActiveTheme(theme)
                ThemeCard(
                    theme = theme,
                    isActive = isActive,
                    onApply = {
                        clientSettingsStore.applyTheme(theme)
                        TeaColors.applyFromStore(clientSettingsStore)
                        refresh()
                    },
                    onRename = null,
                    onDelete = null
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    // Диалог сохранения новой темы
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = {
                Text(
                    "Сохранить тему",
                    color = TeaColors.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        "Текущие 6 цветов палитры будут сохранены как отдельная тема:",
                        color = TeaColors.TextSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = newThemeName,
                        onValueChange = { newThemeName = it },
                        singleLine = true,
                        placeholder = { Text("Название темы", color = TeaColors.TextSecondary.copy(alpha = 0.6f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TeaColors.Accent,
                            unfocusedBorderColor = TeaColors.TextSecondary.copy(alpha = 0.4f),
                            focusedTextColor = TeaColors.TextPrimary,
                            unfocusedTextColor = TeaColors.TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        clientSettingsStore.saveCustomTheme(
                            name = newThemeName,
                            accent = TeaColors.Accent,
                            bg = TeaColors.Background,
                            surface = TeaColors.Surface,
                            chatBg = TeaColors.ChatBg,
                            textPrimary = TeaColors.TextPrimary,
                            textSecondary = TeaColors.TextSecondary
                        )
                        showSaveDialog = false
                        refresh()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TeaColors.Accent)
                ) {
                    Text("Сохранить", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("Отмена", color = TeaColors.TextSecondary)
                }
            },
            containerColor = TeaColors.Surface
        )
    }

    // Диалог переименования темы
    renameTargetTheme?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTargetTheme = null },
            title = {
                Text(
                    "Переименовать тему",
                    color = TeaColors.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    OutlinedTextField(
                        value = renameThemeName,
                        onValueChange = { renameThemeName = it },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TeaColors.Accent,
                            unfocusedBorderColor = TeaColors.TextSecondary.copy(alpha = 0.4f),
                            focusedTextColor = TeaColors.TextPrimary,
                            unfocusedTextColor = TeaColors.TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        clientSettingsStore.renameCustomTheme(target.id, renameThemeName)
                        renameTargetTheme = null
                        refresh()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TeaColors.Accent)
                ) {
                    Text("Готово", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTargetTheme = null }) {
                    Text("Отмена", color = TeaColors.TextSecondary)
                }
            },
            containerColor = TeaColors.Surface
        )
    }

    // Диалог подтверждения удаления темы
    deleteTargetTheme?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTargetTheme = null },
            title = {
                Text(
                    "Удалить тему?",
                    color = TeaColors.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "Вы действительно хотите удалить тему «${target.name}»?",
                    color = TeaColors.TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        clientSettingsStore.deleteCustomTheme(target.id)
                        deleteTargetTheme = null
                        refresh()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Удалить", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTargetTheme = null }) {
                    Text("Отмена", color = TeaColors.TextSecondary)
                }
            },
            containerColor = TeaColors.Surface
        )
    }
}

/** Баннер активных текущих цветов палитры с возможностью быстрого сохранения */
@Composable
private fun CurrentPaletteBanner(
    onSaveCurrent: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(TeaColors.Surface)
            .border(1.dp, TeaColors.Accent.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Текущая палитра",
                color = TeaColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Активна сейчас",
                color = TeaColors.Accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.height(10.dp))

        // Ряд цветных кружочков
        PaletteDotsRow(
            accent = TeaColors.Accent,
            bg = TeaColors.Background,
            surface = TeaColors.Surface,
            chatBg = TeaColors.ChatBg,
            textPrimary = TeaColors.TextPrimary,
            textSecondary = TeaColors.TextSecondary
        )

        Spacer(Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(TeaColors.Accent.copy(alpha = 0.15f))
                .border(1.dp, TeaColors.Accent.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                .clickable { onSaveCurrent() }
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "💾 Сохранить текущие цвета как тему",
                color = TeaColors.Accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/** Карточка отдельной темы (пользовательской или встроенной) */
@Composable
private fun ThemeCard(
    theme: CustomTheme,
    isActive: Boolean,
    onApply: () -> Unit,
    onRename: (() -> Unit)?,
    onDelete: (() -> Unit)?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(TeaColors.Surface)
            .border(
                1.dp,
                if (isActive) TeaColors.Accent else Color.Transparent,
                RoundedCornerShape(12.dp)
            )
            .clickable { onApply() }
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    theme.name,
                    color = TeaColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (isActive) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(TeaColors.Accent.copy(alpha = 0.25f))
                            .border(1.dp, TeaColors.Accent, RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            "✓ Активна",
                            color = TeaColors.Accent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Кнопки управления (только для пользовательских тем)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onRename != null) {
                    Text(
                        "✎",
                        color = TeaColors.TextSecondary,
                        fontSize = 16.sp,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onRename() }
                            .padding(6.dp)
                    )
                }
                if (onDelete != null) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        painter = painterResource(id = R.drawable.ic_delete),
                        contentDescription = "Удалить",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .clickable { onDelete() }
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Ряд кружочков цветов
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PaletteDotsRow(
                accent = theme.accentColor,
                bg = theme.bgColor,
                surface = theme.surfaceColor,
                chatBg = theme.chatBgColor,
                textPrimary = theme.textPrimaryColor,
                textSecondary = theme.textSecondaryColor
            )

            if (!isActive) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(TeaColors.Accent)
                        .clickable { onApply() }
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text(
                        "Применить",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/** Ряд из 6 кружочков, демонстрирующий цвета темы */
@Composable
private fun PaletteDotsRow(
    accent: Color,
    bg: Color,
    surface: Color,
    chatBg: Color,
    textPrimary: Color,
    textSecondary: Color
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ColorCircle(accent, "Акцент")
        ColorCircle(bg, "Фон")
        ColorCircle(surface, "Карточки")
        ColorCircle(chatBg, "Чат")
        ColorCircle(textPrimary, "Текст 1")
        ColorCircle(textSecondary, "Текст 2")
    }
}

@Composable
private fun ColorCircle(color: Color, tooltip: String) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
    )
}
