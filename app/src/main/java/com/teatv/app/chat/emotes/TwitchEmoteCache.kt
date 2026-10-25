package com.teatv.app.chat.emotes

import java.util.concurrent.ConcurrentHashMap

/** Глобальный кэш динамических смайлов Twitch (включая саб-смайлы любых каналов). */
object TwitchEmoteCache {
    private val cache = ConcurrentHashMap<String, Emote>()

    fun put(code: String, url: String) {
        if (code.isNotBlank() && url.isNotBlank()) {
            cache[code] = Emote(code, url, EmoteKind.TWITCH)
        }
    }

    fun get(code: String): Emote? = cache[code]

    fun getAll(): Map<String, Emote> = cache
}
