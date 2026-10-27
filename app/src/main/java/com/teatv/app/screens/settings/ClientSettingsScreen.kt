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
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teatv.app.R
import com.teatv.app.screens.settings.components.ColorPickerDialog
import com.teatv.app.screens.settings.components.TriggerWordsDialog
import com.teatv.app.settings.ClientSettingsStore
import com.teatv.app.settings.ReplySettings
import com.teatv.app.settings.ReplySettingsStore
import com.teatv.app.ui.theme.TeaColors
import kotlin.math.roundToInt

@Composable
fun ClientSettingsScreen(
    clientSettingsStore: ClientSettingsStore,
    replySettingsStore: ReplySettingsStore,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var fontStep by remember { mutableIntStateOf(clientSettingsStore.chatFontSizeStep) }
    var miniPlayerEnabled by remember { mutableStateOf(clientSettingsStore.miniPlayerEnabled) }
    var miniPlayerSize by remember { mutableIntStateOf(clientSettingsStore.miniPlayerSizeStep) }

    var replySettings by remember { mutableStateOf(replySettingsStore.load()) }

    var showThemeManager by remember { mutableStateOf(false) }

    if (showThemeManager) {
        ThemeManagerScreen(
            clientSettingsStore = clientSettingsStore,
            onBack = { showThemeManager = false }
        )
        return
    }

    // Диалоги
    var colorPickerTarget by remember { mutableStateOf<Pair<String, Color>?>(null) }
    var colorPickerKey by remember { mutableStateOf<String?>(null) }
    var colorPickerSupportsAlpha by remember { mutableStateOf(false) }
    var showTriggerWordsDialog by remember { mutableStateOf(false) }

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
                "Клиент и модификации",
                color = TeaColors.TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Секция 1: Размер шрифта чата
            SettingsCard(title = "Размер текста в чате") {
                val previewSp = 10f + (fontStep - 1) * 2f
                Text(
                    text = "Масштабирует размер шрифта сообщений, ников и смайлов в чате.",
                    color = TeaColors.TextSecondary,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(10.dp))

                // Превью
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(TeaColors.ChatBg)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Чат: TeaTV приятного просмотра! ($fontStep/5)",
                        color = TeaColors.TextPrimary,
                        fontSize = previewSp.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(Modifier.height(10.dp))

                Slider(
                    value = fontStep.toFloat(),
                    onValueChange = {
                        fontStep = it.roundToInt()
                        clientSettingsStore.chatFontSizeStep = fontStep
                    },
                    valueRange = 1f..5f,
                    steps = 3,
                    colors = SliderDefaults.colors(
                        thumbColor = TeaColors.Accent,
                        activeTrackColor = TeaColors.Accent,
                        inactiveTrackColor = TeaColors.Background
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("1 (10sp)", color = TeaColors.TextSecondary, fontSize = 11.sp)
                    Text("2 (12sp)", color = TeaColors.TextSecondary, fontSize = 11.sp)
                    Text("3 (14sp)", color = TeaColors.TextSecondary, fontSize = 11.sp)
                    Text("4 (16sp)", color = TeaColors.TextSecondary, fontSize = 11.sp)
                    Text("5 (18sp)", color = TeaColors.TextSecondary, fontSize = 11.sp)
                }
            }

            // Секция 2: Оттенок клиента и палитра
            SettingsCard(
                title = "Оттенок клиента (Палитра)",
                headerAction = {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(TeaColors.Accent.copy(alpha = 0.2f))
                            .border(1.dp, TeaColors.Accent.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .clickable { showThemeManager = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "🎨 Темы",
                            color = TeaColors.Accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            ) {
                Text(
                    text = "Нажмите на миниатюру цвета для плавной настройки оттенка.",
                    color = TeaColors.TextSecondary,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(8.dp))

                PaletteColorRow(
                    name = "Основной акцент",
                    description = "Кнопки, выделения, фокусные элементы",
                    color = TeaColors.Accent,
                    onClick = {
                        colorPickerTarget = "Основной акцент" to TeaColors.Accent
                        colorPickerKey = ClientSettingsStore.COLOR_ACCENT
                        colorPickerSupportsAlpha = false
                    }
                )
                PaletteColorRow(
                    name = "Фон приложения",
                    description = "Базовый цвет темной темы",
                    color = TeaColors.Background,
                    onClick = {
                        colorPickerTarget = "Фон приложения" to TeaColors.Background
                        colorPickerKey = ClientSettingsStore.COLOR_BG
                        colorPickerSupportsAlpha = false
                    }
                )
                PaletteColorRow(
                    name = "Поверхность карточек",
                    description = "Фон списков, карточек стримов и меню",
                    color = TeaColors.Surface,
                    onClick = {
                        colorPickerTarget = "Поверхность карточек" to TeaColors.Surface
                        colorPickerKey = ClientSettingsStore.COLOR_SURFACE
                        colorPickerSupportsAlpha = false
                    }
                )
                PaletteColorRow(
                    name = "Фон чата",
                    description = "Фон контейнера сообщений чата",
                    color = TeaColors.ChatBg,
                    onClick = {
                        colorPickerTarget = "Фон чата" to TeaColors.ChatBg
                        colorPickerKey = ClientSettingsStore.COLOR_CHAT_BG
                        colorPickerSupportsAlpha = false
                    }
                )
                PaletteColorRow(
                    name = "Основной текст",
                    description = "Текст заголовков и сообщений",
                    color = TeaColors.TextPrimary,
                    onClick = {
                        colorPickerTarget = "Основной текст" to TeaColors.TextPrimary
                        colorPickerKey = ClientSettingsStore.COLOR_TEXT_PRIMARY
                        colorPickerSupportsAlpha = false
                    }
                )
                PaletteColorRow(
                    name = "Вторичный текст",
                    description = "Подписи, категории, метаданные",
                    color = TeaColors.TextSecondary,
                    onClick = {
                        colorPickerTarget = "Вторичный текст" to TeaColors.TextSecondary
                        colorPickerKey = ClientSettingsStore.COLOR_TEXT_SECONDARY
                        colorPickerSupportsAlpha = false
                    }
                )
                PaletteColorRow(
                    name = "Цвет выделения модерации",
                    description = "Цвет подсветки сообщения при выборе и свайпе",
                    color = TeaColors.ModHighlight,
                    onClick = {
                        colorPickerTarget = "Цвет выделения модерации" to TeaColors.ModHighlight
                        colorPickerKey = ClientSettingsStore.COLOR_MOD_HIGHLIGHT
                        colorPickerSupportsAlpha = false
                    }
                )

                Spacer(Modifier.height(6.dp))

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    TextButton(
                        onClick = { TeaColors.resetToDefaults(clientSettingsStore) }
                    ) {
                        Text(
                            "Сбросить цвета по умолчанию",
                            color = TeaColors.Accent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Секция: Тактильный отклик и вибрация
            SettingsCard(title = "Тактильный отклик и вибрация") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Вибрация при действиях",
                            color = TeaColors.TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Легкий виброотклик при наградах за баллы, свайпах модератора и переключениях",
                            color = TeaColors.TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    var hapticEnabled by remember { mutableStateOf(clientSettingsStore.hapticFeedbackEnabled) }
                    Switch(
                        checked = hapticEnabled,
                        onCheckedChange = { enabled ->
                            hapticEnabled = enabled
                            clientSettingsStore.hapticFeedbackEnabled = enabled
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = TeaColors.Accent,
                            uncheckedThumbColor = TeaColors.TextSecondary,
                            uncheckedTrackColor = TeaColors.Background
                        )
                    )
                }
            }

            // Секция 3: Реплаи и упоминания
            SettingsCard(title = "Реплаи и упоминания") {
                // Тумблер реплаев
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Функция ответов (реплаи)",
                            color = TeaColors.TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Свайп сообщения влево для ответа в чате",
                            color = TeaColors.TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = replySettings.repliesEnabled,
                        onCheckedChange = { enabled ->
                            replySettings = replySettings.copy(repliesEnabled = enabled)
                            replySettingsStore.save(replySettings)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = TeaColors.Accent,
                            uncheckedThumbColor = TeaColors.TextSecondary,
                            uncheckedTrackColor = TeaColors.Background
                        )
                    )
                }

                Spacer(Modifier.height(10.dp))

                // Кнопка настройки слов-триггеров
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(TeaColors.Background)
                        .clickable { showTriggerWordsDialog = true }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Слова для подсветки и триггеров",
                            color = TeaColors.TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(2.dp))
                        val wordCount = replySettings.triggerWords.size
                        Text(
                            if (wordCount == 0) "Список пуст (нажмите для добавления)"
                            else "$wordCount слов(а) настроено",
                            color = TeaColors.TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    Text("›", color = TeaColors.TextSecondary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(10.dp))

                // Тумблер уведомлений
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Уведомления об упоминаниях",
                            color = TeaColors.TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "В шторке телефона, если стрим свернут",
                            color = TeaColors.TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = replySettings.notifyOnMention,
                        onCheckedChange = { notify ->
                            replySettings = replySettings.copy(notifyOnMention = notify)
                            replySettingsStore.save(replySettings)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = TeaColors.Accent,
                            uncheckedThumbColor = TeaColors.TextSecondary,
                            uncheckedTrackColor = TeaColors.Background
                        )
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Цвет подсветки
                PaletteColorRow(
                    name = "Цвет подсветки сообщений",
                    description = "Фоновое выделение триггерных фраз",
                    color = replySettings.highlightColor.copy(alpha = replySettings.highlightAlpha),
                    onClick = {
                        colorPickerTarget = "Подсветка упоминаний" to replySettings.highlightColor.copy(alpha = replySettings.highlightAlpha)
                        colorPickerKey = "HIGHLIGHT_COLOR"
                        colorPickerSupportsAlpha = true
                    }
                )
            }

            // Секция 4: Мини-проигрыватель
            SettingsCard(title = "Мини-проигрыватель") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Включить мини-проигрыватель",
                            color = TeaColors.TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Сворачивание трансляции в плавающее окно при возврате на главный экран",
                            color = TeaColors.TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = miniPlayerEnabled,
                        onCheckedChange = { enabled ->
                            miniPlayerEnabled = enabled
                            clientSettingsStore.miniPlayerEnabled = enabled
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = TeaColors.Accent,
                            uncheckedThumbColor = TeaColors.TextSecondary,
                            uncheckedTrackColor = TeaColors.Background
                        )
                    )
                }

                if (miniPlayerEnabled) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Размер плавающего окна:",
                        color = TeaColors.TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SizeOptionChip(
                            label = "140dp",
                            sublabel = "Компакт",
                            selected = miniPlayerSize == 0,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                miniPlayerSize = 0
                                clientSettingsStore.miniPlayerSizeStep = 0
                            }
                        )
                        SizeOptionChip(
                            label = "180dp",
                            sublabel = "Стандарт",
                            selected = miniPlayerSize == 1,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                miniPlayerSize = 1
                                clientSettingsStore.miniPlayerSizeStep = 1
                            }
                        )
                        SizeOptionChip(
                            label = "220dp",
                            sublabel = "Большой",
                            selected = miniPlayerSize == 2,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                miniPlayerSize = 2
                                clientSettingsStore.miniPlayerSizeStep = 2
                            }
                        )
                    }
                }
            }

            // Секция 5: Буфер видеопотока
            var bufferSeconds by remember { mutableIntStateOf(clientSettingsStore.videoBufferSizeSeconds) }
            SettingsCard(title = "Размер буфера видео") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Задержка и стабильность",
                        color = TeaColors.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(TeaColors.Accent.copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            "$bufferSeconds сек",
                            color = TeaColors.Accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    "Для нестабильного интернета. Чем больше буфер — тем лучше для нестабильной сети, но взамен расходуется больше заряда батареи и увеличивается задержка трансляции.",
                    color = TeaColors.TextSecondary,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(10.dp))

                val bufferOptions = listOf(
                    5 to "5 сек\n(Мин.)",
                    10 to "10 сек\n(Стандарт)",
                    15 to "15 сек\n(Баланс)",
                    30 to "30 сек\n(Моб. сеть)",
                    60 to "60 сек\n(Слабая)"
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    bufferOptions.forEach { (sec, label) ->
                        val selected = bufferSeconds == sec
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) TeaColors.Accent.copy(alpha = 0.22f) else TeaColors.Background)
                                .border(
                                    1.dp,
                                    if (selected) TeaColors.Accent else Color.Transparent,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    bufferSeconds = sec
                                    clientSettingsStore.videoBufferSizeSeconds = sec
                                }
                                .padding(vertical = 8.dp, horizontal = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label,
                                color = if (selected) TeaColors.Accent else TeaColors.TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    // Диалог выбора цвета
    colorPickerTarget?.let { (title, currentColor) ->
        ColorPickerDialog(
            title = title,
            initialColor = currentColor,
            supportsAlpha = colorPickerSupportsAlpha,
            onDismiss = { colorPickerTarget = null },
            onColorSelected = { selected ->
                val key = colorPickerKey
                if (key == "HIGHLIGHT_COLOR") {
                    replySettings = replySettings.copy(
                        highlightColor = selected.copy(alpha = 1f),
                        highlightAlpha = selected.alpha
                    )
                    replySettingsStore.save(replySettings)
                } else if (key != null) {
                    clientSettingsStore.saveColor(key, selected)
                    TeaColors.applyFromStore(clientSettingsStore)
                }
                colorPickerTarget = null
            }
        )
    }

    // Диалог слов-триггеров
    if (showTriggerWordsDialog) {
        TriggerWordsDialog(
            initialWords = replySettings.triggerWords,
            onDismiss = { showTriggerWordsDialog = false },
            onSave = { updated ->
                replySettings = replySettings.copy(triggerWords = updated)
                replySettingsStore.save(replySettings)
                showTriggerWordsDialog = false
            }
        )
    }
}

@Composable
private fun SettingsCard(
    title: String,
    headerAction: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(TeaColors.Surface)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                color = TeaColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            headerAction?.invoke()
        }
        Spacer(Modifier.height(4.dp))
        content()
    }
}

@Composable
private fun PaletteColorRow(
    name: String,
    description: String,
    color: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                name,
                color = TeaColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(2.dp))
            Text(
                description,
                color = TeaColors.TextSecondary,
                fontSize = 11.sp
            )
        }
        Spacer(Modifier.width(12.dp))
        // Миниатюра цвета как в Android Studio
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(color)
                .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
        )
    }
}

@Composable
private fun SizeOptionChip(
    label: String,
    sublabel: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) TeaColors.Accent.copy(alpha = 0.2f) else TeaColors.Background)
            .border(
                1.dp,
                if (selected) TeaColors.Accent else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            label,
            color = if (selected) TeaColors.Accent else TeaColors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            sublabel,
            color = TeaColors.TextSecondary,
            fontSize = 11.sp
        )
    }
}
