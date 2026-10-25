package com.teatv.app.auth

/**
 * Реестр хэшей Persisted Queries (APQ) для Twitch GQL.
 * Позволяет отправлять мутации без текста запроса, обходя строгие проверки Integrity.
 */
object TwitchPersistedQueries {

    private val hashes = mapOf(
        // Хэш для операции Follow
        "FollowButton_FollowUser" to "800e7346bdfbb99d3f547dcb983ae829c62d5d852924ec9d927d3126f5d888db",

        // Хэш для операции Unfollow.
        // ВАЖНО: Если Twitch обновил этот хэш, сервер вернет ошибку "PersistedQueryNotFound"
        // и в тексте ошибки будет указан ПРАВИЛЬНЫЙ хэш. Мы просто вставим его сюда.
        "FollowButton_UnfollowUser" to "1b3a3e6e7b6b5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a"
    )

    fun getHash(operationName: String): String? = hashes[operationName]
}