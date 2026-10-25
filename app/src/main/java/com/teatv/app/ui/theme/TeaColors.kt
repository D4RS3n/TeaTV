package com.teatv.app.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.teatv.app.settings.ClientSettingsStore

/** Единая палитра TeaTV с поддержкой кастомизации на лету. */
object TeaColors {
    val DefaultBackground = Color(0xFF1C1917)
    val DefaultSurface = Color(0xFF262119)
    val DefaultTextPrimary = Color(0xFFD9CFA6)
    val DefaultTextSecondary = Color(0xFF949000)
    val DefaultAccent = Color(0xE6706300)
    val DefaultChatBg = Color(0xFF151009)
    val Live = Color(0xFFEB0400)

    var Background by mutableStateOf(DefaultBackground)
    var Surface by mutableStateOf(DefaultSurface)
    var TextPrimary by mutableStateOf(DefaultTextPrimary)
    var TextSecondary by mutableStateOf(DefaultTextSecondary)
    var Accent by mutableStateOf(DefaultAccent)
    var ChatBg by mutableStateOf(DefaultChatBg)

    fun applyFromStore(store: ClientSettingsStore) {
        Background = store.getSavedColor(ClientSettingsStore.COLOR_BG) ?: DefaultBackground
        Surface = store.getSavedColor(ClientSettingsStore.COLOR_SURFACE) ?: DefaultSurface
        TextPrimary = store.getSavedColor(ClientSettingsStore.COLOR_TEXT_PRIMARY) ?: DefaultTextPrimary
        TextSecondary = store.getSavedColor(ClientSettingsStore.COLOR_TEXT_SECONDARY) ?: DefaultTextSecondary
        Accent = store.getSavedColor(ClientSettingsStore.COLOR_ACCENT) ?: DefaultAccent
        ChatBg = store.getSavedColor(ClientSettingsStore.COLOR_CHAT_BG) ?: DefaultChatBg
    }

    fun resetToDefaults(store: ClientSettingsStore) {
        store.resetColors()
        Background = DefaultBackground
        Surface = DefaultSurface
        TextPrimary = DefaultTextPrimary
        TextSecondary = DefaultTextSecondary
        Accent = DefaultAccent
        ChatBg = DefaultChatBg
    }
}
