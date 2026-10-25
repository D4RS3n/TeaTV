package com.teatv.app.data

import android.content.Context

/** Настройки плеера (режим внешнего воспроизведения). */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("teatv_settings", Context.MODE_PRIVATE)

    fun audioPolicy(): String =
        prefs.getString("audio_policy", "HEADPHONES") ?: "HEADPHONES"

    fun setAudioPolicy(v: String) {
        prefs.edit().putString("audio_policy", v).apply()
    }
}