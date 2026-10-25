package com.teatv.app.ui.screens.chat

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Animatable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.text.Editable
import android.text.Spanned
import android.text.style.ReplacementSpan
import android.widget.EditText
import coil.Coil
import coil.request.ImageRequest
import com.teatv.app.chat.emotes.Emote
import kotlin.math.roundToInt

/**
 * Атомарный спан эмодзи/смайлика в строке ввода сообщений чата.
 * Заменяет символ-заполнитель \uFFFC изображением смайлика и удаляется
 * нажатием одной клавиши Backspace как в официальном клиенте Twitch.
 */
class EmoteSpan(
    val code: String,
    val url: String,
    var drawable: Drawable,
    val sizePx: Int
) : ReplacementSpan() {

    override fun getSize(
        paint: Paint,
        text: CharSequence?,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int {
        if (fm != null) {
            val fontHeight = fm.descent - fm.ascent
            val halfDiff = (sizePx - fontHeight) / 2
            if (halfDiff > 0) {
                fm.ascent -= halfDiff
                fm.top -= halfDiff
                fm.descent += halfDiff
                fm.bottom += halfDiff
            }
        }
        return sizePx + 4
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint
    ) {
        canvas.save()
        val transY = ((bottom + top) - sizePx) / 2
        canvas.translate(x, transY.toFloat())
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(canvas)
        canvas.restore()
    }
}

/**
 * Извлекает полный текст сообщения для отправки в Twitch,
 * заменяя все атомарные спаны смайликов (\uFFFC) на их оригинальные строковые коды (например, "Kappa").
 */
fun getRawTextWithEmotes(editable: Editable?): String {
    if (editable == null || editable.isEmpty()) return ""
    val sb = StringBuilder()
    var i = 0
    val length = editable.length
    while (i < length) {
        if (editable[i] == '\uFFFC') {
            val spans = editable.getSpans(i, i + 1, EmoteSpan::class.java)
            val span = spans.firstOrNull { editable.getSpanStart(it) == i }
            if (span != null) {
                sb.append(span.code)
                i = editable.getSpanEnd(span)
            } else {
                i++
            }
        } else {
            sb.append(editable[i])
            i++
        }
    }
    return sb.toString()
}

/**
 * Вставляет смайлик в позицию курсора внутри EditText в виде атомарного символа \uFFFC с EmoteSpan.
 * При нажатии Backspace смайлик полностью удаляется за одно нажатие клавиши.
 */
fun insertEmoteIntoEditText(
    editText: EditText,
    emote: Emote,
    context: Context
) {
    val editable = editText.text ?: return
    val start = editText.selectionStart.coerceAtLeast(0)
    val end = editText.selectionEnd.coerceAtLeast(start)

    // Если перед курсором нет пробела (и курсор не в начале строки) — добавляем пробел перед смайликом
    val needsLeadingSpace = start > 0 && editable[start - 1] != ' '
    val prefix = if (needsLeadingSpace) " " else ""
    val emoteToken = "\uFFFC"
    val suffix = " "
    val insertString = "$prefix$emoteToken$suffix"

    val tokenStart = start + prefix.length
    val tokenEnd = tokenStart + emoteToken.length

    editable.replace(start, end, insertString)

    // Базовый размер смайлика в строке набора — 22dp
    val density = context.resources.displayMetrics.density
    val sizePx = (22 * density).roundToInt().coerceAtLeast(16)
    val placeholder = ColorDrawable(android.graphics.Color.TRANSPARENT).apply {
        setBounds(0, 0, sizePx, sizePx)
    }

    val span = EmoteSpan(
        code = emote.code,
        url = emote.url,
        drawable = placeholder,
        sizePx = sizePx
    )

    editable.setSpan(span, tokenStart, tokenEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    editText.setSelection((tokenEnd + suffix.length).coerceAtMost(editable.length))

    // Асинхронно загружаем изображение смайлика через Coil
    val imageLoader = Coil.imageLoader(context)
    val request = ImageRequest.Builder(context)
        .data(emote.url)
        .size(sizePx, sizePx)
        .target(
            onSuccess = { resultDrawable ->
                resultDrawable.setBounds(0, 0, sizePx, sizePx)
                span.drawable = resultDrawable
                if (resultDrawable is Animatable) {
                    resultDrawable.callback = object : Drawable.Callback {
                        override fun invalidateDrawable(who: Drawable) {
                            editText.invalidate()
                        }
                        override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
                            editText.postDelayed(what, `when` - SystemClock.uptimeMillis())
                        }
                        override fun unscheduleDrawable(who: Drawable, what: Runnable) {
                            editText.removeCallbacks(what)
                        }
                    }
                    resultDrawable.start()
                }
                editText.invalidate()
            }
        )
        .build()
    imageLoader.enqueue(request)
}
