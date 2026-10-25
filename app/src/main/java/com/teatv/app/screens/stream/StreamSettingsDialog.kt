package com.teatv.app.ui.screens.stream

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import com.teatv.app.AudioPolicy
import com.teatv.app.ViewMode
import com.teatv.app.player.QualityOption
import com.teatv.app.ui.theme.TeaColors

@Composable
internal fun StreamSettingsDialog(
    playbackUrl: String?,
    qualities: List<QualityOption>,
    selectedQualityLabel: String? = null,
    viewMode: ViewMode,
    audioPolicy: AudioPolicy,
    dropOffset: IntOffset,
    onSelectQuality: (QualityOption) -> Unit,
    onSelectViewMode: (ViewMode) -> Unit,
    onSelectPolicy: (AudioPolicy) -> Unit,
    onDismiss: () -> Unit
) {
    var viewExpanded by remember { mutableStateOf(false) }
    var policyExpanded by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .background(TeaColors.Surface.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Text("Качество", color = TeaColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            if (qualities.isEmpty()) {
                Text(
                    text = "Качество отсутствует",
                    color = TeaColors.TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            } else {
                qualities.forEach { q ->
                    val isSelected = if (!selectedQualityLabel.isNullOrBlank()) {
                        q.label.equals(selectedQualityLabel, ignoreCase = true)
                    } else {
                        q.url == playbackUrl
                    }
                    TextButton(onClick = { onSelectQuality(q) }) {
                        Text(
                            text = q.label + if (isSelected) " •" else "",
                            color = if (isSelected) TeaColors.Accent else TeaColors.TextPrimary,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text("Просмотр", color = TeaColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Box {
                TextButton(onClick = {
                    viewExpanded = !viewExpanded
                    policyExpanded = false
                }) {
                    Text(
                        text = viewLabel(viewMode) + if (viewExpanded) " ▴" else " ▾",
                        color = TeaColors.Accent,
                        fontSize = 13.sp
                    )
                }
                if (viewExpanded) {
                    Popup(
                        alignment = androidx.compose.ui.Alignment.TopStart,
                        offset = dropOffset,
                        onDismissRequest = { viewExpanded = false }
                    ) {
                        Column(
                            modifier = Modifier
                                .width(220.dp)
                                .shadow(6.dp, RoundedCornerShape(10.dp))
                                .background(TeaColors.Surface, RoundedCornerShape(10.dp))
                                .padding(4.dp)
                        ) {
                            listOf(ViewMode.Normal, ViewMode.AudioOnly).forEach { m ->
                                TextButton(onClick = {
                                    onSelectViewMode(m)
                                    viewExpanded = false
                                }) {
                                    Text(
                                        text = viewLabel(m),
                                        color = if (m == viewMode) TeaColors.Accent else TeaColors.TextPrimary,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "Внешнее воспроизведение",
                color = TeaColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Box {
                TextButton(onClick = {
                    policyExpanded = !policyExpanded
                    viewExpanded = false
                }) {
                    Text(
                        text = policyLabel(audioPolicy) + if (policyExpanded) " ▴" else " ▾",
                        color = TeaColors.Accent,
                        fontSize = 13.sp
                    )
                }
                if (policyExpanded) {
                    Popup(
                        alignment = androidx.compose.ui.Alignment.TopStart,
                        offset = dropOffset,
                        onDismissRequest = { policyExpanded = false }
                    ) {
                        Column(
                            modifier = Modifier
                                .width(220.dp)
                                .shadow(6.dp, RoundedCornerShape(10.dp))
                                .background(TeaColors.Surface, RoundedCornerShape(10.dp))
                                .padding(4.dp)
                        ) {
                            listOf(AudioPolicy.Headphones, AudioPolicy.Always, AudioPolicy.Never)
                                .forEach { p ->
                                    TextButton(onClick = {
                                        onSelectPolicy(p)
                                        policyExpanded = false
                                    }) {
                                        Text(
                                            text = policyLabel(p),
                                            color = if (p == audioPolicy) TeaColors.Accent else TeaColors.TextPrimary,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                        }
                    }
                }
            }
        }
    }
}