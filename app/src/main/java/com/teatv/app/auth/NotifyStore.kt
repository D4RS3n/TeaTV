package com.teatv.app.auth

import android.content.Context

/** Локальные live-уведомления: кого оповещать (до движка уведомлений). */
class NotifyStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("teatv_notify", Context.MODE_PRIVATE)

    fun isNotified(id: String): Boolean =
        prefs.getStringSet("ids", emptySet())?.contains(id) == true

    fun setNotified(id: String, on: Boolean) {
        val s = prefs.getStringSet("ids", emptySet()).orEmpty().toMutableSet()
        if (on) s.add(id) else s.remove(id)
        prefs.edit().putStringSet("ids", s).apply()
    }
}