package com.teatv.app.settings

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.teatv.app.ui.theme.TeaColors
import org.json.JSONArray
import java.util.UUID

/** Хранилище общих настроек клиента: размер шрифта чата, цвета темы, мини-плеер, темы оформления. */
class ClientSettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("teatv_client_settings", Context.MODE_PRIVATE)

    /** Шаг размера шрифта чата: от 1 до 5 (по умолчанию 3 = 14sp). */
    var chatFontSizeStep: Int
        get() = prefs.getInt(KEY_CHAT_FONT_STEP, 3)
        set(value) = prefs.edit().putInt(KEY_CHAT_FONT_STEP, value.coerceIn(1, 5)).apply()

    /** Вкл/выкл внутриприложенного мини-проигрывателя. */
    var miniPlayerEnabled: Boolean
        get() = prefs.getBoolean(KEY_MINI_PLAYER_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_MINI_PLAYER_ENABLED, value).apply()

    /** Размер мини-проигрывателя: 0 = Компактный (140dp), 1 = Стандартный (180dp), 2 = Большой (220dp). */
    var miniPlayerSizeStep: Int
        get() = prefs.getInt(KEY_MINI_PLAYER_SIZE, 1)
        set(value) = prefs.edit().putInt(KEY_MINI_PLAYER_SIZE, value.coerceIn(0, 2)).apply()

    /** Размер буфера видео в секундах: 5, 10, 15, 30, 60 (по умолчанию 15). */
    var videoBufferSizeSeconds: Int
        get() = prefs.getInt(KEY_VIDEO_BUFFER_SECONDS, 15)
        set(value) = prefs.edit().putInt(KEY_VIDEO_BUFFER_SECONDS, value.coerceIn(5, 60)).apply()

    /** Модераторский режим (свайпы и боковые шторки). По умолчанию включён. */
    var modModeEnabled: Boolean
        get() = prefs.getBoolean(KEY_MOD_MODE_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_MOD_MODE_ENABLED, value).apply()

    /** Автоматическое разделение длинных сообщений (>500 символов) на несколько частей. По умолчанию включено. */
    var splitLongMessagesEnabled: Boolean
        get() = prefs.getBoolean(KEY_SPLIT_LONG_MESSAGES, true)
        set(value) = prefs.edit().putBoolean(KEY_SPLIT_LONG_MESSAGES, value).apply()

    fun getSavedColor(key: String): Color? {
        if (!prefs.contains(key)) return null
        val argb = prefs.getInt(key, 0)
        return Color(argb)
    }

    fun saveColor(key: String, color: Color) {
        prefs.edit().putInt(key, color.toArgb()).apply()
    }

    fun resetColors() {
        prefs.edit()
            .remove(COLOR_ACCENT)
            .remove(COLOR_BG)
            .remove(COLOR_SURFACE)
            .remove(COLOR_CHAT_BG)
            .remove(COLOR_TEXT_PRIMARY)
            .remove(COLOR_TEXT_SECONDARY)
            .apply()
    }

    // --- Управление темами оформления ---

    fun getCustomThemes(): List<CustomTheme> {
        val raw = prefs.getString(KEY_SAVED_THEMES, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            val list = mutableListOf<CustomTheme>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                CustomTheme.fromJson(obj)?.let { list.add(it) }
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getAllThemes(): List<CustomTheme> {
        return CustomTheme.BUILT_IN_THEMES + getCustomThemes()
    }

    fun saveCustomTheme(
        name: String,
        accent: Color,
        bg: Color,
        surface: Color,
        chatBg: Color,
        textPrimary: Color,
        textSecondary: Color
    ): CustomTheme {
        val theme = CustomTheme(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifEmpty { "Моя тема" },
            accent = accent.toArgb(),
            bg = bg.toArgb(),
            surface = surface.toArgb(),
            chatBg = chatBg.toArgb(),
            textPrimary = textPrimary.toArgb(),
            textSecondary = textSecondary.toArgb(),
            isBuiltIn = false
        )
        val current = getCustomThemes().toMutableList()
        current.add(theme)
        saveCustomThemesList(current)
        return theme
    }

    fun renameCustomTheme(id: String, newName: String) {
        val current = getCustomThemes().map {
            if (it.id == id) it.copy(name = newName.trim().ifEmpty { it.name }) else it
        }
        saveCustomThemesList(current)
    }

    fun deleteCustomTheme(id: String) {
        val current = getCustomThemes().filterNot { it.id == id }
        saveCustomThemesList(current)
    }

    private fun saveCustomThemesList(themes: List<CustomTheme>) {
        val array = JSONArray()
        themes.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_SAVED_THEMES, array.toString()).apply()
    }

    fun applyTheme(theme: CustomTheme) {
        saveColor(COLOR_ACCENT, Color(theme.accent))
        saveColor(COLOR_BG, Color(theme.bg))
        saveColor(COLOR_SURFACE, Color(theme.surface))
        saveColor(COLOR_CHAT_BG, Color(theme.chatBg))
        saveColor(COLOR_TEXT_PRIMARY, Color(theme.textPrimary))
        saveColor(COLOR_TEXT_SECONDARY, Color(theme.textSecondary))
    }

    fun matchesActiveTheme(theme: CustomTheme): Boolean {
        val currentAccent = getSavedColor(COLOR_ACCENT) ?: TeaColors.DefaultAccent
        val currentBg = getSavedColor(COLOR_BG) ?: TeaColors.DefaultBackground
        val currentSurface = getSavedColor(COLOR_SURFACE) ?: TeaColors.DefaultSurface
        val currentChatBg = getSavedColor(COLOR_CHAT_BG) ?: TeaColors.DefaultChatBg
        val currentTextPrimary = getSavedColor(COLOR_TEXT_PRIMARY) ?: TeaColors.DefaultTextPrimary
        val currentTextSecondary = getSavedColor(COLOR_TEXT_SECONDARY) ?: TeaColors.DefaultTextSecondary

        return currentAccent.toArgb() == theme.accent &&
                currentBg.toArgb() == theme.bg &&
                currentSurface.toArgb() == theme.surface &&
                currentChatBg.toArgb() == theme.chatBg &&
                currentTextPrimary.toArgb() == theme.textPrimary &&
                currentTextSecondary.toArgb() == theme.textSecondary
    }

    companion object {
        private const val KEY_CHAT_FONT_STEP = "chat_font_step"
        private const val KEY_MINI_PLAYER_ENABLED = "mini_player_enabled"
        private const val KEY_MINI_PLAYER_SIZE = "mini_player_size"
        private const val KEY_VIDEO_BUFFER_SECONDS = "video_buffer_seconds"
        private const val KEY_MOD_MODE_ENABLED = "mod_mode_enabled"
        private const val KEY_SPLIT_LONG_MESSAGES = "split_long_messages_enabled"
        private const val KEY_SAVED_THEMES = "saved_custom_themes"

        const val COLOR_ACCENT = "color_accent"
        const val COLOR_BG = "color_bg"
        const val COLOR_SURFACE = "color_surface"
        const val COLOR_CHAT_BG = "color_chat_bg"
        const val COLOR_TEXT_PRIMARY = "color_text_primary"
        const val COLOR_TEXT_SECONDARY = "color_text_secondary"
    }
}
