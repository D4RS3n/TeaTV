package com.teatv.app.auth

import com.teatv.app.BuildConfig

object TwitchAuth {
    /** Родной client-id android-приложения Twitch. */
    const val ANDROID_CLIENT_ID = "kd1unb4b3q4t58fwlpcbzcbnm76a8fp"

    /**
     * Собственный Client-ID приложения Twitch Developer.
     * Защищён от открытой публикации: подтягивается из local.properties через BuildConfig,
     * а в случае сборки без local.properties декодируется из обфусцированного бинарного массива в рантайме.
     */
    val OWN_CLIENT_ID: String
        get() {
            val key = BuildConfig.TWITCH_OWN_CLIENT_ID
            if (!key.isNullOrBlank()) return key
            val obfuscated = byteArrayOf(86, 4, 19, 21, 11, 17, 24, 14, 90, 15, 18, 19, 91, 84, 22, 83, 22, 23, 13, 11, 8, 91, 16, 84, 6, 10, 82, 23, 86, 11)
            return String(ByteArray(obfuscated.size) { i -> (obfuscated[i].toInt() xor 0x62).toByte() }, Charsets.UTF_8)
        }

    const val WEB_CLIENT_ID = "kimne78kx3ncx6brgo4mv6wki5h1ko"

    /** User-Agent android-приложения. */
    const val ANDROID_USER_AGENT =
        "Dalvik/2.1.0 (Linux; U; Android 16; SM-S911B Build/TP1A.220624.014) tv.twitch.android.app/25.3.0/2503006"

    const val HELIX_USERS = "https://api.twitch.tv/helix/users"
}
