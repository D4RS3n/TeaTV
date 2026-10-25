package com.teatv.app.screens.stream.mod

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teatv.app.R
import com.teatv.app.mod.PinnedMessage
import com.teatv.app.ui.theme.TeaColors

@Composable
fun PinnedMessageBanner(
    message: PinnedMessage,
    isModerator: Boolean,
    onHideForMe: () -> Unit,
    onUnpin: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(TeaColors.Surface)
            .border(1.dp, TeaColors.Accent.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .animateContentSize()
            .clickable { expanded = !expanded }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = if (expanded) Alignment.Top else Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_pin),
                contentDescription = "Закреплено",
                tint = TeaColors.Accent,
                modifier = Modifier
                    .size(16.dp)
                    .padding(top = if (expanded) 3.dp else 0.dp)
            )

            Spacer(Modifier.width(8.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (expanded) Modifier
                            .heightIn(max = 150.dp)
                            .verticalScroll(rememberScrollState())
                        else Modifier
                    )
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Закреплено: @${message.sender}",
                        color = TeaColors.Accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (expanded) "▲" else "▼",
                        color = TeaColors.TextSecondary,
                        fontSize = 9.sp
                    )
                }

                Text(
                    text = message.text,
                    color = TeaColors.TextPrimary,
                    fontSize = 12.sp,
                    maxLines = if (expanded) Int.MAX_VALUE else 1,
                    overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(6.dp))

            // Кнопки справа: Скрыть для себя (перечёркнутый глаз)
            IconButton(
                onClick = onHideForMe,
                modifier = Modifier.size(26.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_eye_off),
                    contentDescription = "Скрыть для себя",
                    tint = TeaColors.TextSecondary,
                    modifier = Modifier.size(17.dp)
                )
            }

            // Если модератор: Открепить для всех (перечёркнутая булавка)
            if (isModerator) {
                Spacer(Modifier.width(2.dp))
                IconButton(
                    onClick = onUnpin,
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_pin_off),
                        contentDescription = "Открепить на канале",
                        tint = TeaColors.Live,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
    }
}
