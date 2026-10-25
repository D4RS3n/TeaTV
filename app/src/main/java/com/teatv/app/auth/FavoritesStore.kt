package com.teatv.app.auth

import android.content.Context

/** Избранные каналы пользователя. */
class FavoritesStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("teatv_favorites", Context.MODE_PRIVATE)

    fun allIds(): Set<String> =
        prefs.getStringSet("ids", emptySet()) ?: emptySet()

    fun isFavorite(channelId: String): Boolean =
        prefs.getStringSet("ids", emptySet())?.contains(channelId) == true

    fun setFavorite(channelId: String, value: Boolean) {
        val set = prefs.getStringSet("ids", emptySet())?.toMutableSet() ?: mutableSetOf()
        if (value) set.add(channelId) else set.remove(channelId)
        prefs.edit().putStringSet("ids", set).apply()
    }

}