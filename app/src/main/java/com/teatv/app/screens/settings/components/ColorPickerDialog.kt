package com.teatv.app.screens.settings.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.teatv.app.ui.theme.TeaColors
import kotlin.math.roundToInt

@Composable
fun ColorPickerDialog(
    title: String,
    initialColor: Color,
    supportsAlpha: Boolean = false,
    onDismiss: () -> Unit,
    onColorSelected: (Color) -> Unit
) {
    val hsv = remember(initialColor) {
        val array = FloatArray(3)
        android.graphics.Color.colorToHSV(initialColor.toArgb(), array)
        array
    }

    var hue by remember(initialColor) { mutableFloatStateOf(hsv[0]) }
    var sat by remember(initialColor) { mutableFloatStateOf(hsv[1]) }
    var value by remember(initialColor) { mutableFloatStateOf(hsv[2]) }
    var alpha by remember(initialColor) { mutableFloatStateOf(initialColor.alpha) }

    fun currentColor(): Color {
        val argb = android.graphics.Color.HSVToColor((alpha * 255).roundToInt(), floatArrayOf(hue, sat, value))
        return Color(argb)
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(TeaColors.Surface)
                .padding(20.dp)
        ) {
            Text(
                text = title,
                color = TeaColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )

            Spacer(Modifier.height(16.dp))

            // Основная панель выбора цвета (Saturation/Value квадрат + Hue полоса справа)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                // Поле Saturation / Value
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, TeaColors.TextSecondary.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(hue) {
                                detectTapGestures { offset ->
                                    sat = (offset.x / size.width).coerceIn(0f, 1f)
                                    value = (1f - offset.y / size.height).coerceIn(0f, 1f)
                                }
                            }
                            .pointerInput(hue) {
                                detectDragGestures { change, _ ->
                                    sat = (change.position.x / size.width).coerceIn(0f, 1f)
                                    value = (1f - change.position.y / size.height).coerceIn(0f, 1f)
                                }
                            }
                    ) {
                        val pureHue = Color.hsv(hue, 1f, 1f)
                        // Горизонтальный градиент от белого к цвету Hue
                        drawRect(
                            brush = Brush.horizontalGradient(listOf(Color.White, pureHue))
                        )
                        // Вертикальный градиент от прозрачного к черному
                        drawRect(
                            brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black))
                        )

                        // Круглый селектор выбранной точки
                        val selX = sat * size.width
                        val selY = (1f - value) * size.height
                        drawCircle(Color.Black, radius = 9.dp.toPx(), center = Offset(selX, selY), style = Stroke(width = 2.dp.toPx()))
                        drawCircle(Color.White, radius = 7.dp.toPx(), center = Offset(selX, selY), style = Stroke(width = 2.dp.toPx()))
                    }
                }

                Spacer(Modifier.width(16.dp))

                // Вертикальная радужная полоса Hue (Оттенок)
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, TeaColors.TextSecondary.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                ) {
                    // Прямой спектр от 0 до 360 градусов (Красный -> Желтый -> Зеленый -> Голубой -> Синий -> Пурпурный -> Красный)
                    val rainbow = remember {
                        listOf(
                            Color.Red,
                            Color.Yellow,
                            Color.Green,
                            Color.Cyan,
                            Color.Blue,
                            Color.Magenta,
                            Color.Red
                        )
                    }

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    hue = (offset.y / size.height).coerceIn(0f, 1f) * 360f
                                }
                            }
                            .pointerInput(Unit) {
                                detectDragGestures { change, _ ->
                                    hue = (change.position.y / size.height).coerceIn(0f, 1f) * 360f
                                }
                            }
                    ) {
                        drawRect(brush = Brush.verticalGradient(rainbow))

                        val selectorY = (hue / 360f) * size.height
                        drawCircle(Color.Black, radius = 6.dp.toPx(), center = Offset(size.width / 2, selectorY), style = Stroke(2.dp.toPx()))
                        drawCircle(Color.White, radius = 4.dp.toPx(), center = Offset(size.width / 2, selectorY))
                    }
                }
            }

            // Настройка прозрачности (если поддерживается)
            if (supportsAlpha) {
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Прозрачность: ${(alpha * 100).roundToInt()}%",
                        color = TeaColors.TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.width(120.dp)
                    )
                    Slider(
                        value = alpha,
                        onValueChange = { alpha = it },
                        valueRange = 0.05f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = TeaColors.Accent,
                            activeTrackColor = TeaColors.Accent,
                            inactiveTrackColor = TeaColors.Background
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Предпросмотр выбранного цвета и кнопки
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(currentColor())
                        .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                )

                Spacer(Modifier.width(12.dp))

                Text(
                    text = String.format("#%08X", currentColor().toArgb()),
                    color = TeaColors.TextSecondary,
                    fontSize = 12.sp
                )

                Spacer(Modifier.weight(1f))

                TextButton(onClick = onDismiss) {
                    Text("Отмена", color = TeaColors.TextSecondary)
                }

                Spacer(Modifier.width(8.dp))

                TextButton(
                    onClick = {
                        onColorSelected(currentColor())
                    }
                ) {
                    Text(
                        "Выбрать",
                        color = TeaColors.Accent,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                }
            }
        }
    }
}
