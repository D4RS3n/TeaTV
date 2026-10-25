package com.teatv.app.screens.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.teatv.app.ui.theme.TeaColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TriggerWordsDialog(
    initialWords: Set<String>,
    onDismiss: () -> Unit,
    onSave: (Set<String>) -> Unit
) {
    var words by remember { mutableStateOf(initialWords.toMutableSet()) }
    var newWordText by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(TeaColors.Surface)
                .padding(20.dp)
        ) {
            Text(
                "Слова для подсветки",
                color = TeaColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Сообщения с этими словами будут подсвечиваться в чате, а также присылать уведомление.",
                color = TeaColors.TextSecondary,
                fontSize = 12.sp
            )

            Spacer(Modifier.height(16.dp))

            // Поле добавления нового слова
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(TeaColors.Background)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = newWordText,
                    onValueChange = { newWordText = it },
                    singleLine = true,
                    textStyle = TextStyle(color = TeaColors.TextPrimary, fontSize = 14.sp),
                    cursorBrush = SolidColor(TeaColors.Accent),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        if (newWordText.isEmpty()) {
                            Text("Введите слово или ник...", color = TeaColors.TextSecondary.copy(alpha = 0.6f), fontSize = 13.sp)
                        }
                        inner()
                    }
                )

                TextButton(
                    onClick = {
                        val trimmed = newWordText.trim()
                        if (trimmed.isNotBlank()) {
                            words = (words + trimmed).toMutableSet()
                            newWordText = ""
                        }
                    }
                ) {
                    Text("+", color = TeaColors.Accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(16.dp))

            // Чипсы со словами
            if (words.isEmpty()) {
                Text(
                    "Список пуст. Добавьте ваш ник или ключевые слова.",
                    color = TeaColors.TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    words.forEach { word ->
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(TeaColors.Background)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(word, color = TeaColors.TextPrimary, fontSize = 13.sp)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "✕",
                                color = TeaColors.TextSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable {
                                        words = (words - word).toMutableSet()
                                    }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Отмена", color = TeaColors.TextSecondary)
                }
                TextButton(onClick = { onSave(words) }) {
                    Text("Сохранить", color = TeaColors.Accent, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
