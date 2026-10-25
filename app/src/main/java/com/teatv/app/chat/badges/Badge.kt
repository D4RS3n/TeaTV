package com.teatv.app.chat.badges

data class BadgeRef(val setId: String, val version: String, val url: String)

/** Ключ "set/version" -> ссылка на картинку. */
fun List<BadgeRef>.toMap(): Map<String, String> =
    associate { "${it.setId}/${it.version}" to it.url }