package com.teatv.app.auth

import android.content.Context
import java.util.UUID

class TokenStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("teatv_auth", Context.MODE_PRIVATE)

    /** Веб-кука auth-token: для GQL, баллов, spade, PubSub. */
    var accessToken: String?
        get() = prefs.getString("access_token", null)
        set(value) = prefs.edit().putString("access_token", value).apply()

    /** OAuth-токен своей заявки (implicit flow): для Helix. */
    var helixToken: String?
        get() = prefs.getString("helix_token", null)
        set(value) = prefs.edit().putString("helix_token", value).apply()

    val isLoggedIn: Boolean
        get() = accessToken != null && helixToken != null

    var userId: String?
        get() = prefs.getString("user_id", null)
        set(value) = prefs.edit().putString("user_id", value).apply()

    var userLogin: String?
        get() = prefs.getString("user_login", null)
        set(value) = prefs.edit().putString("user_login", value).apply()

    var userDisplayName: String?
        get() = prefs.getString("user_display_name", null)
        set(value) = prefs.edit().putString("user_display_name", value).apply()

    var deviceId: String
        get() = prefs.getString("device_id", null) ?: run {
            val v = UUID.randomUUID().toString()
            prefs.edit().putString("device_id", v).apply()
            v
        }
        set(value) = prefs.edit().putString("device_id", value).apply()

    fun clear(context: android.content.Context) {
        prefs.edit()
            .remove("access_token")
            .remove("helix_token")
            .remove("user_id")
            .remove("user_login")
            .remove("user_display_name")
            .apply()

        // Полная очистка веб-куки при выходе из аккаунта
        android.webkit.CookieManager.getInstance().removeAllCookies(null)
        android.webkit.CookieManager.getInstance().flush()
    }
}
