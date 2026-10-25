package com.teatv.app.settings

import androidx.compose.ui.graphics.Color
import org.json.JSONObject

/**
 * Пользовательская или встроенная цветовая тема интерфейса TeaTV.
 */
data class CustomTheme(
    val id: String,
    val name: String,
    val accent: Int,
    val bg: Int,
    val surface: Int,
    val chatBg: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    val isBuiltIn: Boolean = false
) {
    val accentColor get() = Color(accent)
    val bgColor get() = Color(bg)
    val surfaceColor get() = Color(surface)
    val chatBgColor get() = Color(chatBg)
    val textPrimaryColor get() = Color(textPrimary)
    val textSecondaryColor get() = Color(textSecondary)

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("name", name)
            put("accent", accent)
            put("bg", bg)
            put("surface", surface)
            put("chatBg", chatBg)
            put("textPrimary", textPrimary)
            put("textSecondary", textSecondary)
            put("isBuiltIn", isBuiltIn)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): CustomTheme? {
            return try {
                CustomTheme(
                    id = json.getString("id"),
                    name = json.getString("name"),
                    accent = json.getInt("accent"),
                    bg = json.getInt("bg"),
                    surface = json.getInt("surface"),
                    chatBg = json.getInt("chatBg"),
                    textPrimary = json.getInt("textPrimary"),
                    textSecondary = json.getInt("textSecondary"),
                    isBuiltIn = json.optBoolean("isBuiltIn", false)
                )
            } catch (e: Exception) {
                null
            }
        }

        val BUILT_IN_THEMES = listOf(
            CustomTheme(
                id = "builtin_teatv",
                name = "TeaTV Классика",
                accent = 0xE6706300.toInt(),
                bg = 0xFF1C1917.toInt(),
                surface = 0xFF262119.toInt(),
                chatBg = 0xFF151009.toInt(),
                textPrimary = 0xFFD9CFA6.toInt(),
                textSecondary = 0xFF949000.toInt(),
                isBuiltIn = true
            ),
            CustomTheme(
                id = "builtin_oled",
                name = "Полночь OLED",
                accent = 0xFF6366F1.toInt(),
                bg = 0xFF000000.toInt(),
                surface = 0xFF121212.toInt(),
                chatBg = 0xFF080808.toInt(),
                textPrimary = 0xFFFFFFFF.toInt(),
                textSecondary = 0xFF9CA3AF.toInt(),
                isBuiltIn = true
            ),
            CustomTheme(
                id = "builtin_wine",
                name = "Бордо / 7TV",
                accent = 0xFFE11D48.toInt(),
                bg = 0xFF140A0D.toInt(),
                surface = 0xFF241217.toInt(),
                chatBg = 0xFF1A0D11.toInt(),
                textPrimary = 0xFFFFE4E6.toInt(),
                textSecondary = 0xFFFDA4AF.toInt(),
                isBuiltIn = true
            ),
            CustomTheme(
                id = "builtin_mint",
                name = "Мятный чай",
                accent = 0xFF10B981.toInt(),
                bg = 0xFF061A14.toInt(),
                surface = 0xFF0E2E24.toInt(),
                chatBg = 0xFF0A241C.toInt(),
                textPrimary = 0xFFECFDF5.toInt(),
                textSecondary = 0xFF6EE7B7.toInt(),
                isBuiltIn = true
            ),
            CustomTheme(
                id = "builtin_amethyst",
                name = "Аметист Неон",
                accent = 0xFF9146FF.toInt(),
                bg = 0xFF0E0C13.toInt(),
                surface = 0xFF18141F.toInt(),
                chatBg = 0xFF130F1A.toInt(),
                textPrimary = 0xFFF3EEFF.toInt(),
                textSecondary = 0xFFAD92DF.toInt(),
                isBuiltIn = true
            ),
            CustomTheme(
                id = "builtin_cyberpunk",
                name = "Киберпанк",
                accent = 0xFFFEE715.toInt(),
                bg = 0xFF101820.toInt(),
                surface = 0xFF1A2634.toInt(),
                chatBg = 0xFF141F2B.toInt(),
                textPrimary = 0xFFFEE715.toInt(),
                textSecondary = 0xFF00E5FF.toInt(),
                isBuiltIn = true
            )
        )
    }
}
