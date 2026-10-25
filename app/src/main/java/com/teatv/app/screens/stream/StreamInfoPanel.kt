package com.teatv.app.ui.screens.stream

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teatv.app.R
import com.teatv.app.data.StreamerItem
import com.teatv.app.ui.theme.TeaColors

@Composable
internal fun StreamInfoPanel(
    target: StreamerItem,
    followed: Boolean?,
    notified: Boolean,
    onToggleFollow: () -> Unit,
    onToggleNotify: () -> Unit,
    onViewers: () -> Unit,
    vodTitle: String? = null
) {
    var expanded by remember { mutableStateOf(false) }
    val isVod = vodTitle != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .background(TeaColors.Surface)
            .clickable { expanded = !expanded }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        if (!expanded) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = target.name,
                    color = TeaColors.TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (!isVod) ViewersChip(target.stream?.viewerCount) { onViewers() }
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = target.name,
                        color = TeaColors.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (isVod) vodTitle!! else (target.stream?.title ?: "не в сети"),
                        color = TeaColors.TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    if (!isVod) ViewersChip(target.stream?.viewerCount) { onViewers() }
                }
                Column(horizontalAlignment = Alignment.End) {
                    IconButton(onClick = onToggleFollow, modifier = Modifier.size(36.dp)) {
                        Icon(
                            painter = painterResource(R.drawable.ic_follow),
                            contentDescription = "Фолоу",
                            tint = if (followed == true) TeaColors.Accent else TeaColors.TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onToggleNotify, modifier = Modifier.size(36.dp)) {
                        Icon(
                            painter = painterResource(R.drawable.ic_bell),
                            contentDescription = "Уведомления",
                            tint = if (notified) TeaColors.Accent else TeaColors.TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}