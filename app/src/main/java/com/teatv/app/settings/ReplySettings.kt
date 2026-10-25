package com.teatv.app.settings

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/** Модель настроек реплаев и подсветки сообщений в чате. */
data class ReplySettings(
    val repliesEnabled: Boolean = true,
    val highlightColor: Color = Color(0xFFFFD54F),
    val highlightAlpha: Float = 0.18f,
    val notifyOnMention: Boolean = true,
    val triggerWords: Set<String> = emptySet()
)

val DefaultReplySettings = ReplySettings()

/** Хранилище настроек реплаев и упоминаний в SharedPreferences. */
class ReplySettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("teatv_reply_settings", Context.MODE_PRIVATE)

    fun load(): ReplySettings {
        val enabled = prefs.getBoolean(KEY_REPLIES_ENABLED, true)
        val colorArgb = prefs.getInt(KEY_HIGHLIGHT_COLOR, Color(0xFFFFD54F).toArgb())
        val alpha = prefs.getFloat(KEY_HIGHLIGHT_ALPHA, 0.18f)
        val notify = prefs.getBoolean(KEY_NOTIFY_MENTION, true)
        val words = prefs.getStringSet(KEY_TRIGGER_WORDS, emptySet()).orEmpty()
        return ReplySettings(
            repliesEnabled = enabled,
            highlightColor = Color(colorArgb),
            highlightAlpha = alpha,
            notifyOnMention = notify,
            triggerWords = words
        )
    }

    fun save(settings: ReplySettings) {
        prefs.edit()
            .putBoolean(KEY_REPLIES_ENABLED, settings.repliesEnabled)
            .putInt(KEY_HIGHLIGHT_COLOR, settings.highlightColor.toArgb())
            .putFloat(KEY_HIGHLIGHT_ALPHA, settings.highlightAlpha)
            .putBoolean(KEY_NOTIFY_MENTION, settings.notifyOnMention)
            .putStringSet(KEY_TRIGGER_WORDS, settings.triggerWords)
            .apply()
    }

    companion object {
        private const val KEY_REPLIES_ENABLED = "replies_enabled"
        private const val KEY_HIGHLIGHT_COLOR = "highlight_color"
        private const val KEY_HIGHLIGHT_ALPHA = "highlight_alpha"
        private const val KEY_NOTIFY_MENTION = "notify_mention"
        private const val KEY_TRIGGER_WORDS = "trigger_words"
    }
}
