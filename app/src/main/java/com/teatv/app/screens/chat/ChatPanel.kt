package com.teatv.app.ui.screens.chat

import android.content.Context

import android.text.InputFilter

import android.text.InputType

import android.util.TypedValue

import android.view.KeyEvent

import android.view.inputmethod.EditorInfo

import android.view.inputmethod.InputMethodManager

import android.widget.EditText

import android.text.TextWatcher

import android.text.Selection

import android.view.inputmethod.InputConnection

import android.view.inputmethod.InputConnectionWrapper

import androidx.compose.ui.viewinterop.AndroidView

import androidx.core.widget.doAfterTextChanged

import androidx.compose.ui.graphics.toArgb

import androidx.compose.foundation.background

import androidx.compose.foundation.border

import androidx.compose.foundation.horizontalScroll

import androidx.compose.foundation.rememberScrollState

import androidx.compose.foundation.layout.Arrangement

import androidx.compose.ui.layout.ContentScale

import coil.compose.AsyncImage

import coil.request.ImageRequest

import androidx.compose.ui.platform.LocalContext

import androidx.compose.animation.AnimatedVisibility

import androidx.compose.animation.fadeIn

import androidx.compose.animation.fadeOut

import androidx.compose.animation.slideInVertically

import androidx.compose.animation.slideOutVertically

import androidx.compose.foundation.clickable

import androidx.compose.foundation.layout.Spacer

import androidx.compose.foundation.layout.width

import androidx.compose.runtime.derivedStateOf

import androidx.compose.runtime.rememberCoroutineScope

import kotlinx.coroutines.delay

import kotlinx.coroutines.launch

import androidx.compose.foundation.gestures.detectHorizontalDragGestures

import androidx.compose.foundation.gestures.detectTapGestures

import androidx.compose.foundation.layout.Box

import androidx.compose.foundation.layout.defaultMinSize

import androidx.compose.foundation.layout.Column

import androidx.compose.foundation.layout.Row

import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.foundation.layout.fillMaxWidth

import androidx.compose.foundation.layout.height

import androidx.compose.foundation.layout.offset

import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.layout.size

import androidx.compose.foundation.lazy.LazyColumn

import androidx.compose.foundation.lazy.items

import androidx.compose.foundation.lazy.rememberLazyListState

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.foundation.text.BasicTextField

import androidx.compose.foundation.text.KeyboardActions

import androidx.compose.foundation.text.KeyboardOptions

import androidx.compose.material3.Icon

import androidx.compose.material3.IconButton

import androidx.compose.material3.Text

import androidx.compose.runtime.Composable

import androidx.compose.runtime.LaunchedEffect

import androidx.compose.runtime.getValue

import androidx.compose.runtime.mutableFloatStateOf

import androidx.compose.runtime.mutableStateOf

import androidx.compose.runtime.remember

import androidx.compose.runtime.setValue

import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier

import androidx.compose.ui.draw.alpha

import androidx.compose.ui.draw.clip

import androidx.compose.ui.graphics.Brush

import androidx.compose.ui.graphics.Color

import androidx.compose.ui.graphics.SolidColor

import androidx.compose.ui.graphics.graphicsLayer

import androidx.compose.ui.hapticfeedback.HapticFeedbackType

import androidx.compose.ui.input.pointer.pointerInput

import androidx.compose.ui.platform.LocalDensity

import androidx.compose.ui.platform.LocalFocusManager

import androidx.compose.ui.platform.LocalSoftwareKeyboardController

import androidx.compose.ui.platform.LocalHapticFeedback

import androidx.compose.ui.res.painterResource

import androidx.compose.ui.text.TextStyle

import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.text.input.ImeAction

import androidx.compose.ui.text.input.KeyboardCapitalization

import androidx.compose.ui.unit.IntOffset

import androidx.compose.ui.unit.dp

import androidx.compose.ui.unit.sp

import com.teatv.app.R

import com.teatv.app.chat.ChatMessage

import com.teatv.app.chat.emotes.Emote

import com.teatv.app.chat.emotes.EmoteBundles

import com.teatv.app.chat.emotes.allEmotesLookup

import com.teatv.app.chat.emotes.buildMessageAnnotated

import com.teatv.app.chat.points.ChannelPointsState

import com.teatv.app.chat.points.PointReward

import com.teatv.app.mod.PinnedMessage

import com.teatv.app.screens.stream.mod.ModSliderAction

import com.teatv.app.screens.stream.mod.ModSliderHelper

import com.teatv.app.screens.stream.mod.PinnedMessageBanner

import com.teatv.app.settings.DefaultReplySettings

import com.teatv.app.settings.ReplySettings

import com.teatv.app.ui.theme.TeaColors

import kotlin.math.exp

import kotlin.math.roundToInt

@Composable

fun ChatPanel(

    messages: List<ChatMessage>,

    onSend: (String, String?) -> Unit,

    emotes: EmoteBundles,

    badges: Map<String, String>,

    points: ChannelPointsState?,

    onRefreshPoints: () -> Unit,

    onRedeemReward: (PointReward, String?) -> Unit,

    onPickEmote: (Emote) -> Unit,

    isBot: Boolean, onBotToggle: (Boolean) -> Unit,

    modifier: Modifier = Modifier,

    readOnly: Boolean = false,

    fontSizeStep: Int = 3,

    splitMessagesEnabled: Boolean = true,

    replySettings: ReplySettings = DefaultReplySettings,

    isModerator: Boolean = false,

    selectedMessageId: String? = null,

    pinnedMessage: PinnedMessage? = null,

    onMessageLongPress: ((ChatMessage) -> Unit)? = null,

    onModSliderAction: ((ChatMessage, ModSliderAction) -> Unit)? = null,

    onHidePinnedMessage: () -> Unit = {},

    onUnpinMessage: () -> Unit = {},

    onUserClick: ((ChatMessage) -> Unit)? = null

) {

    var editTextInstance by remember { mutableStateOf<EditText?>(null) }

    var hasInputText by remember { mutableStateOf(false) }

    var showEmotes by remember { mutableStateOf(false) }

    var showPoints by remember { mutableStateOf(false) }

    var replyTo by remember { mutableStateOf<ChatMessage?>(null) }

    val lookup = remember(emotes) { allEmotesLookup(emotes) }

    val fontSizeSp = remember(fontSizeStep) { 10f + (fontSizeStep.coerceIn(1, 5) - 1) * 2f }

    val context = LocalContext.current

    val listState = rememberLazyListState()

    val scope = rememberCoroutineScope()

    var autoScroll by remember { mutableStateOf(true) }

    val performSend = {

        editTextInstance?.let { et ->

            val raw = getRawTextWithEmotes(et.text)

            if (raw.isNotBlank()) {

                val sendText = if (!splitMessagesEnabled && raw.length > 500) {

                    raw.take(500).trim()

                } else {

                    raw.trim()

                }

                val replyId = if (replySettings.repliesEnabled) replyTo?.id else null

                onSend(sendText, replyId)

                et.text?.clear()

                replyTo = null

                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager

                imm?.hideSoftInputFromWindow(et.windowToken, 0)

                et.clearFocus()

                autoScroll = true

                scope.launch {

                    if (messages.isNotEmpty()) {

                        listState.scrollToItem(messages.size - 1)

                    }

                    delay(100)

                    if (messages.isNotEmpty()) {

                        listState.animateScrollToItem(messages.size - 1)

                    }

                }

            }

        }

    }

    Column(

        modifier = modifier

            .fillMaxSize()

            .background(TeaColors.ChatBg)

    ) {

        // Плашка закреплённого сообщения сверху чата

        var hiddenForMe by remember(pinnedMessage?.id) { mutableStateOf(false) }

        if (pinnedMessage != null && !hiddenForMe) {

            PinnedMessageBanner(

                message = pinnedMessage,

                isModerator = isModerator,

                onHideForMe = {

                    hiddenForMe = true

                    onHidePinnedMessage()

                },

                onUnpin = onUnpinMessage

            )

        }

        val lastVisibleItemIndex by remember {

            derivedStateOf {

                listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1

            }

        }

        val isAtBottom by remember {

            derivedStateOf {

                val total = listState.layoutInfo.totalItemsCount

                if (total <= 1) true

                else lastVisibleItemIndex >= total - 1

            }

        }

        // Отслеживаем действия пользователя:

        // Если пользователь свайпнул чат пальцем вверх — отключаем автоскролл.

        // Если долистал обратно до самого низа — включаем автоскролл обратно.

        LaunchedEffect(isAtBottom, listState.isScrollInProgress) {

            if (isAtBottom) {

                autoScroll = true

            } else if (listState.isScrollInProgress) {

                autoScroll = false

            }

        }

        // Автоскролл к новому сообщению, когда autoScroll активен.

        // Отслеживаем по ID последнего сообщения, чтобы скролл работал ВСЕГДА, даже после превышения лимита окна сообщений!

        val lastMessageId = messages.lastOrNull()?.id

        LaunchedEffect(lastMessageId) {

            if (messages.isNotEmpty() && autoScroll) {

                listState.scrollToItem(messages.size - 1)

            }

        }

        val showScrollToBottom by remember {

            derivedStateOf {

                !isAtBottom && !autoScroll

            }

        }

        Box(

            modifier = Modifier

                .weight(1f)

                .fillMaxWidth()

        ) {

            LazyColumn(

                state = listState,

                modifier = Modifier

                    .fillMaxSize()

                    .padding(horizontal = 10.dp, vertical = 4.dp)

            ) {

                items(messages, key = { it.id }) { m ->

                    val isSelected = m.id == selectedMessageId

                    if (readOnly) {

                        MessageRow(

                            m = m,

                            lookup = lookup,

                            badgeMap = badges,

                            fontSizeSp = fontSizeSp,

                            replySettings = replySettings,

                            isModerator = false,

                            isSelected = isSelected,

                            onLongPress = { onMessageLongPress?.invoke(m) },

                            onModAction = {},

                            onReply = {},

                            onUserClick = { onUserClick?.invoke(m) }

                        )

                    } else {

                        MessageRow(

                            m = m,

                            lookup = lookup,

                            badgeMap = badges,

                            fontSizeSp = fontSizeSp,

                            replySettings = replySettings,

                            isModerator = isModerator,

                            isSelected = isSelected,

                            onLongPress = { onMessageLongPress?.invoke(m) },

                            onModAction = { action -> onModSliderAction?.invoke(m, action) },

                            onReply = { replyTo = m },

                            onUserClick = { onUserClick?.invoke(m) }

                        )

                    }

                }

            }

            androidx.compose.animation.AnimatedVisibility(

                visible = showScrollToBottom,

                enter = fadeIn() + slideInVertically { it / 2 },

                exit = fadeOut() + slideOutVertically { it / 2 },

                modifier = Modifier

                    .align(Alignment.BottomCenter)

                    .padding(bottom = 8.dp)

            ) {

                Row(

                    modifier = Modifier

                        .clip(RoundedCornerShape(16.dp))

                        .background(TeaColors.Surface.copy(alpha = 0.95f))

                        .clickable {

                            autoScroll = true

                            scope.launch {

                                if (messages.isNotEmpty()) {

                                    listState.animateScrollToItem(messages.size - 1)

                                }

                            }

                        }

                        .padding(horizontal = 12.dp, vertical = 6.dp),

                    verticalAlignment = Alignment.CenterVertically

                ) {

                    Icon(

                        painter = painterResource(R.drawable.ic_arrow_down),

                        contentDescription = "Вниз",

                        tint = TeaColors.Accent,

                        modifier = Modifier.size(14.dp)

                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    Text(

                        text = "Вниз",

                        color = TeaColors.TextPrimary,

                        fontSize = 11.sp,

                        fontWeight = FontWeight.SemiBold

                    )

                }

            }

        }

        if (!readOnly) {

            if (replySettings.repliesEnabled) {

                replyTo?.let { r ->

                    Row(

                        verticalAlignment = Alignment.CenterVertically,

                        modifier = Modifier

                            .fillMaxWidth()

                            .padding(horizontal = 10.dp)

                            .background(TeaColors.Surface, RoundedCornerShape(8.dp))

                            .padding(horizontal = 10.dp, vertical = 4.dp)

                    ) {

                        Text(

                            text = "↪ @${r.sender}: ${r.text.take(48)}",

                            color = TeaColors.TextSecondary,

                            fontSize = 11.sp,

                            modifier = Modifier.weight(1f)

                        )

                        IconButton(onClick = { replyTo = null }, modifier = Modifier.size(24.dp)) {

                            Text("✕", color = TeaColors.TextSecondary, fontSize = 12.sp)

                        }

                    }

                }

            }

            Row(

                verticalAlignment = Alignment.CenterVertically,

                modifier = Modifier

                    .fillMaxWidth()

                    .padding(horizontal = 10.dp, vertical = 6.dp)

                    .height(40.dp)

                    .background(

                        Brush.horizontalGradient(

                            listOf(

                                TeaColors.Surface,

                                TeaColors.Surface,

                                TeaColors.Accent.copy(alpha = 0.12f)

                            )

                        ),

                        RoundedCornerShape(20.dp)

                    )

                    .padding(start = 14.dp, end = 4.dp)

            ) {

                if (points != null && points.rewards.isNotEmpty()) {

                    IconButton(

                        onClick = {

                            showPoints = true

                            onRefreshPoints()

                        },

                        modifier = Modifier.size(28.dp)

                    ) {

                        PointIcon(points.iconUrl, 20)

                    }

                }

                AndroidView(

                    modifier = Modifier

                        .weight(1f)

                        .padding(horizontal = 4.dp),

                    factory = { ctx ->

                        object : EditText(ctx) {

                            override fun getSelectionStart(): Int {

                                val start = super.getSelectionStart()

                                val end = super.getSelectionEnd()

                                if (start != end) {

                                    val t = text

                                    if (t is android.text.Spanned) {

                                        val spans = t.getSpans(start, end, Any::class.java)

                                        val isComposing = spans.any { span ->

                                            (t.getSpanFlags(span) and android.text.Spanned.SPAN_COMPOSING) != 0

                                        }

                                        if (isComposing) {

                                            return end

                                        }

                                    }

                                }

                                return start

                            }

                            override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
                                val outer = this

                                val conn = super.onCreateInputConnection(outAttrs) ?: return null

                                return object : InputConnectionWrapper(conn, false) {

                                    override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {

                                        val res = super.setComposingText(text, newCursorPosition)

                                        val editable = outer.text

                                        if (editable is android.text.Spannable && editable.isNotEmpty()) {

                                            val spans = editable.getSpans(0, editable.length, Any::class.java)

                                            for (span in spans) {

                                                if ((editable.getSpanFlags(span) and android.text.Spanned.SPAN_COMPOSING) != 0) {

                                                    val cEnd = editable.getSpanEnd(span)

                                                    if (cEnd in 0..editable.length) {

                                                        Selection.setSelection(editable, cEnd)

                                                    }

                                                    break

                                                }

                                            }

                                        }

                                        return res

                                    }

                                }

                            }

                        }.apply {

                            setBackground(null)

                            val pxPadH = (4 * resources.displayMetrics.density).toInt()

                            val pxPadV = (2 * resources.displayMetrics.density).toInt()

                            setPadding(pxPadH, pxPadV, pxPadH, pxPadV)

                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)

                            setTextColor(TeaColors.TextPrimary.toArgb())

                            setHintTextColor(TeaColors.TextSecondary.toArgb())

                            hint = "Сообщение"

                            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES

                            maxLines = 1

                            setSingleLine(true)

                            isFocusable = true

                            isFocusableInTouchMode = true

                            isCursorVisible = true

                            val density = resources.displayMetrics.density

                            val cursorWidth = (2 * density).toInt().coerceAtLeast(2)

                            val cursorHeight = (textSize * 1.25f).toInt()

                            textCursorDrawable = android.graphics.drawable.GradientDrawable().apply {

                                shape = android.graphics.drawable.GradientDrawable.RECTANGLE

                                setColor(TeaColors.Accent.toArgb())

                                setSize(cursorWidth, cursorHeight)

                            }

                            imeOptions = EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_EXTRACT_UI

                            if (!splitMessagesEnabled) {

                                filters = arrayOf(InputFilter.LengthFilter(500))

                            }

                            setOnEditorActionListener { _, actionId, event ->

                                if (actionId == EditorInfo.IME_ACTION_SEND ||

                                    (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)

                                ) {

                                    performSend()

                                    true

                                } else {

                                    false

                                }

                            }

                            setOnFocusChangeListener { _, hasFocus ->

                                if (hasFocus) {

                                    showEmotes = false

                                    showPoints = false

                                }

                            }

                            setOnClickListener {

                                showEmotes = false

                                showPoints = false

                            }

                            addTextChangedListener(object : TextWatcher {

                                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                                override fun afterTextChanged(s: android.text.Editable?) {

                                    val hasText = !s.isNullOrBlank()

                                    if (hasInputText != hasText) {

                                        hasInputText = hasText

                                    }

                                }

                            })

                            editTextInstance = this

                        }

                    },

                    update = { et ->

                        val textColor = TeaColors.TextPrimary.toArgb()

                        if (et.currentTextColor != textColor) {

                            et.setTextColor(textColor)

                        }

                        val hintColor = TeaColors.TextSecondary.toArgb()

                        if (et.currentHintTextColor != hintColor) {

                            et.setHintTextColor(hintColor)

                        }

                        if (!splitMessagesEnabled) {

                            if (et.filters.none { it is InputFilter.LengthFilter }) {

                                et.filters = arrayOf(InputFilter.LengthFilter(500))

                            }

                        } else {

                            if (et.filters.isNotEmpty()) {

                                et.filters = emptyArray()

                            }

                        }

                    }

                )

                IconButton(

                    onClick = {

                        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager

                        editTextInstance?.let { et ->

                            imm?.hideSoftInputFromWindow(et.windowToken, 0)

                            et.clearFocus()

                        }

                        showEmotes = true

                    },

                    modifier = Modifier.size(40.dp)

                ) {

                    Icon(

                        painter = painterResource(R.drawable.ic_emote),

                        contentDescription = "Смайлы",

                        tint = TeaColors.Accent,

                        modifier = Modifier.size(22.dp)

                    )

                }

                IconButton(

                    onClick = { performSend() },

                    modifier = Modifier.size(40.dp)

                ) {

                    Icon(

                        painter = painterResource(R.drawable.ic_send),

                        contentDescription = "Отправить",

                        tint = if (hasInputText) TeaColors.Accent else TeaColors.Accent.copy(alpha = 0.5f),

                        modifier = Modifier.size(20.dp)

                    )

                }

            }

        } else {

            Box(

                modifier = Modifier

                    .fillMaxWidth()

                    .padding(horizontal = 10.dp, vertical = 8.dp)

                    .height(40.dp)

                    .background(TeaColors.Surface.copy(alpha = 0.4f), RoundedCornerShape(20.dp)),

                contentAlignment = Alignment.Center

            ) {

                Text(

                    "Чат в режиме чтения",

                    color = TeaColors.TextSecondary.copy(alpha = 0.7f),

                    fontSize = 12.sp

                )

            }

        }

    }

    if (showEmotes) {

        EmotePanel(

            bundles = emotes,

            onPick = { e ->

                editTextInstance?.let { et ->

                    insertEmoteIntoEditText(et, e, context)

                }

                onPickEmote(e)

            },

            onDismiss = { showEmotes = false }

        )

    }

    if (showPoints && points != null) {

        PointsPanel(

            state = points,

            onRedeem = { r, input -> onRedeemReward(r, input) },

            isBot = isBot, onBotToggle = onBotToggle,

            onDismiss = { showPoints = false }

        )

    }

}

/**

 * Строка чата:

 * - Свайп влево = реплай (с резиновым сопротивлением)

 * - Свайп вправо = модераторский слайдер 7TV (Удалить -> 1с -> 10м -> 1ч -> 24ч -> Бан)

 * - Долгий тап = выделение пользователя благородным бордовым цветом (#5A1E28)

 */

@Composable

private fun MessageRow(

    m: ChatMessage,

    lookup: Map<String, Emote>,

    badgeMap: Map<String, String>,

    fontSizeSp: Float,

    replySettings: ReplySettings,

    isModerator: Boolean = false,

    isSelected: Boolean = false,

    onLongPress: () -> Unit = {},

    onModAction: (ModSliderAction) -> Unit = {},

    onReply: (ChatMessage) -> Unit,

    onUserClick: () -> Unit = {}

) {

    val density = LocalDensity.current

    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    var drag by remember { mutableFloatStateOf(0f) }

    var currentModAction by remember { mutableStateOf(ModSliderAction.NONE) }

    val canModSwipe = isModerator && isSelected

    val isReply = m.replyToId != null

    val shownReply = dampDrag(drag)

    val hasTriggerWord = remember(m.text, replySettings.triggerWords) {

        if (replySettings.triggerWords.isEmpty()) false

        else replySettings.triggerWords.any { word -> m.text.contains(word, ignoreCase = true) }

    }

    val rowBackground = when {

        isSelected -> Color(0xFF5A1E28) // Благородный бордовый цвет выделения пользователя

        hasTriggerWord -> replySettings.highlightColor.copy(alpha = replySettings.highlightAlpha)

        isReply -> TeaColors.Surface.copy(alpha = 0.5f)

        else -> Color.Transparent

    }

    val trackBg = when {

        canModSwipe && drag > 0f && currentModAction != ModSliderAction.NONE ->

            currentModAction.color.copy(alpha = 0.22f)

        drag != 0f -> TeaColors.Surface.copy(alpha = 0.55f)

        else -> Color.Transparent

    }

    val (ann, inline) = remember(m.id, lookup, badgeMap, fontSizeSp, m.twitchEmotes) {

        buildMessageAnnotated(

            m.sender,

            nickColor(m.color),

            m.text,

            TeaColors.TextPrimary,

            lookup,

            badgeUrls = m.badges.mapNotNull { badgeMap[it] },

            fontSizeSp = fontSizeSp,

            messageEmotes = m.twitchEmotes

        )

    }

    var textLayoutResult by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }

    Box(

        modifier = Modifier

            .fillMaxWidth()

            .defaultMinSize(minHeight = if (drag != 0f) 34.dp else 0.dp)

            .background(trackBg, RoundedCornerShape(6.dp))

    ) {

        // Иконка реплая справа при свайпе влево

        if (replySettings.repliesEnabled && drag < 0f) {

            Text(

                text = "↪",

                color = TeaColors.Accent,

                fontSize = 18.sp,

                fontWeight = FontWeight.ExtraBold,

                modifier = Modifier

                    .align(Alignment.CenterEnd)

                    .padding(end = 12.dp)

                    .alpha((shownReply / 120f).coerceIn(0f, 1f))

            )

        }

        // Всплывающий бейдж действия при модераторском свайпе вправо (7TV)

        // Расположен в открывающемся слева слоте: центрирован по вертикали и никогда не обрезается!

        if (canModSwipe && drag > 0f && currentModAction != ModSliderAction.NONE) {

            Row(

                modifier = Modifier

                    .align(Alignment.CenterStart)

                    .padding(start = 10.dp)

                    .background(currentModAction.color, RoundedCornerShape(8.dp))

                    .padding(horizontal = 10.dp, vertical = 4.dp),

                verticalAlignment = Alignment.CenterVertically

            ) {

                Text(

                    text = "${currentModAction.iconEmoji} ${currentModAction.title}",

                    color = Color.White,

                    fontSize = 12.sp,

                    fontWeight = FontWeight.Bold

                )

            }

        }

        // Область свайпа и долгого тапа

        val tightThresholdPx = with(density) { 35.dp.toPx() }

        Box(

            modifier = Modifier

                .fillMaxWidth()

                .graphicsLayer {

                    translationX = if (drag < 0f) {

                        if (replySettings.repliesEnabled) -shownReply else 0f

                    } else {

                        if (canModSwipe) {

                            // "Тугой" свайп: до 35dp сдвиг едва заметен (сопротивление 0.22f),

                            // чтобы ползунок не дергался при вертикальном скролле чата

                            if (drag < tightThresholdPx) drag * 0.22f

                            else (tightThresholdPx * 0.22f) + (drag - tightThresholdPx) * 0.85f

                        } else 0f

                    }

                }

                .pointerInput(m.id, canModSwipe, replySettings.repliesEnabled) {

                    val maxLeftPx = if (replySettings.repliesEnabled) with(density) { (-160).dp.toPx() } else 0f

                    val maxRightPx = if (canModSwipe) with(density) { 320.dp.toPx() } else 0f

                    detectHorizontalDragGestures(

                        onDragEnd = {

                            if (drag < 0f && -drag > 120f && replySettings.repliesEnabled) {

                                onReply(m)

                            } else if (drag > 0f && canModSwipe) {

                                val dragDp = with(density) { drag.toDp() }

                                val action = ModSliderHelper.resolveAction(dragDp)

                                if (action != ModSliderAction.NONE) {

                                    onModAction(action)

                                }

                            }

                            drag = 0f

                            currentModAction = ModSliderAction.NONE

                        },

                        onDragCancel = {

                            drag = 0f

                            currentModAction = ModSliderAction.NONE

                        }

                    ) { change, amount ->

                        val newDrag = drag + amount

                        drag = newDrag.coerceIn(maxLeftPx, maxRightPx)

                        // Не блокируем вертикальный скролл чата, пока смещение незначительно (< 24px)

                        if (kotlin.math.abs(drag) > 24f) {

                            change.consume()

                        }

                        // Пересчёт текущей ступени 7TV и виброотклик

                        if (drag > 0f && canModSwipe) {

                            val dragDp = with(density) { drag.toDp() }

                            val action = ModSliderHelper.resolveAction(dragDp)

                            if (action != currentModAction) {

                                currentModAction = action

                                if (action != ModSliderAction.NONE) {

                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                                }

                            }

                        }

                    }

                }

                .pointerInput(m.id) {

                    detectTapGestures(

                        onDoubleTap = {

                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager

                            val clip = android.content.ClipData.newPlainText("chat_message", m.text)

                            clipboard?.setPrimaryClip(clip)

                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                            android.widget.Toast.makeText(context, "Сообщение скопировано", android.widget.Toast.LENGTH_SHORT).show()

                        },

                        onTap = { offset ->

                            textLayoutResult?.let { layout ->

                                val charOffset = layout.getOffsetForPosition(offset)

                                val nickAnn = ann.getStringAnnotations("NICKNAME", charOffset, charOffset).firstOrNull()

                                if (nickAnn != null) {

                                    onUserClick()

                                }

                            }

                        },

                        onLongPress = { onLongPress() }

                    )

                }

                .background(

                    rowBackground,

                    if (rowBackground != Color.Transparent) RoundedCornerShape(6.dp) else androidx.compose.ui.graphics.RectangleShape

                )

                .padding(vertical = 1.dp, horizontal = 4.dp)

        ) {

            Column {

                if (!m.redemptionTitle.isNullOrBlank()) {

                    Row(verticalAlignment = Alignment.CenterVertically) {

                        PointIcon(m.redemptionIcon, 18)

                        Text(

                            text = "${m.sender} redeemed ${m.redemptionTitle}",

                            color = TeaColors.Accent,

                            fontSize = (fontSizeSp - 3f).coerceAtLeast(9f).sp,

                            fontWeight = FontWeight.SemiBold,

                            modifier = Modifier.padding(start = 6.dp)

                        )

                    }

                }

                if (!m.replyToName.isNullOrBlank()) {

                    Text(

                        text = "↪ @${m.replyToName}",

                        color = TeaColors.TextSecondary,

                        fontSize = (fontSizeSp - 4f).coerceAtLeast(8f).sp

                    )

                }

                Text(

                    text = ann,

                    inlineContent = inline,

                    onTextLayout = { textLayoutResult = it }

                )

            }

        }

    }

}

/** Резиновое сопротивление: чем дальше тянем, тем тяжелее идёт. */

private fun dampDrag(raw: Float, limit: Float = 220f): Float {

    val x = -raw.coerceAtMost(0f)

    return limit * (1f - exp(-x / limit))

}

private fun nickColor(hex: String): Color = try {

    if (hex.isBlank()) TeaColors.Accent else Color(android.graphics.Color.parseColor(hex))

} catch (_: Exception) {

    TeaColors.Accent

}
