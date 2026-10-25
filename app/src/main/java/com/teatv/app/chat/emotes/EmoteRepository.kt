package com.teatv.app.chat.emotes

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Смайлы 7TV и BTTV (без авторизации). */
object EmoteRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun sevenTvChannel(twitchId: String): List<Emote> = withContext(Dispatchers.IO) {
        try {
            val body = getObject("https://7tv.io/v3/users/twitch/$twitchId")
                ?: return@withContext emptyList()
            parseSeven(body.optJSONObject("emote_set"))
        } catch (_: Exception) {
            emptyList()
        }
    }

    private var sevenGlobalCache: List<Emote>? = null
    private var bttvGlobalCache: List<Emote>? = null

    suspend fun sevenTvGlobal(): List<Emote> = withContext(Dispatchers.IO) {
        sevenGlobalCache?.let { return@withContext it }
        try {
            parseSeven(getObject("https://7tv.io/v3/emote-sets/global")).also {
                if (it.isNotEmpty()) sevenGlobalCache = it
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun bttvChannel(twitchId: String): List<Emote> = withContext(Dispatchers.IO) {
        try {
            val body = getObject("https://api.betterttv.net/3/cached/users/twitch/$twitchId")
                ?: return@withContext emptyList()
            val out = mutableListOf<Emote>()
            listOf("channelEmotes", "sharedEmotes").forEach { key ->
                val arr = body.optJSONArray(key) ?: return@forEach
                for (i in 0 until arr.length()) {
                    val e = arr.getJSONObject(i)
                    out += Emote(
                        code = e.optString("code"),
                        url = "https://cdn.betterttv.net/emote/${e.optString("id")}/2x",
                        kind = EmoteKind.BTTV
                    )
                }
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun bttvGlobal(): List<Emote> = withContext(Dispatchers.IO) {
        bttvGlobalCache?.let { return@withContext it }
        try {
            val arr = getArray("https://api.betterttv.net/3/cached/emotes/global")
                ?: return@withContext emptyList()
            List(arr.length()) { i ->
                val e = arr.getJSONObject(i)
                Emote(
                    code = e.optString("code"),
                    url = "https://cdn.betterttv.net/emote/${e.optString("id")}/2x",
                    kind = EmoteKind.BTTV
                )
            }.also {
                if (it.isNotEmpty()) bttvGlobalCache = it
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private var ffzGlobalCache: List<Emote>? = null

    suspend fun ffzChannel(login: String): List<Emote> = withContext(Dispatchers.IO) {
        if (login.isBlank()) return@withContext emptyList()
        try {
            val body = getObject("https://api.frankerfacez.com/v1/room/$login")
                ?: return@withContext emptyList()
            val sets = body.optJSONObject("sets") ?: return@withContext emptyList()
            val out = mutableListOf<Emote>()
            val keys = sets.keys()
            while (keys.hasNext()) {
                val setObj = sets.optJSONObject(keys.next()) ?: continue
                val emoticons = setObj.optJSONArray("emoticons") ?: continue
                for (i in 0 until emoticons.length()) {
                    val e = emoticons.optJSONObject(i) ?: continue
                    val name = e.optString("name")
                    val urls = e.optJSONObject("urls")
                    val url = urls?.optString("2") ?: urls?.optString("1") ?: continue
                    val fullUrl = if (url.startsWith("//")) "https:$url" else url
                    out += Emote(name, fullUrl, EmoteKind.BTTV)
                }
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun ffzGlobal(): List<Emote> = withContext(Dispatchers.IO) {
        ffzGlobalCache?.let { return@withContext it }
        try {
            val body = getObject("https://api.frankerfacez.com/v1/set/global")
                ?: return@withContext emptyList()
            val sets = body.optJSONObject("sets") ?: return@withContext emptyList()
            val out = mutableListOf<Emote>()
            val keys = sets.keys()
            while (keys.hasNext()) {
                val setObj = sets.optJSONObject(keys.next()) ?: continue
                val emoticons = setObj.optJSONArray("emoticons") ?: continue
                for (i in 0 until emoticons.length()) {
                    val e = emoticons.optJSONObject(i) ?: continue
                    val name = e.optString("name")
                    val urls = e.optJSONObject("urls")
                    val url = urls?.optString("2") ?: urls?.optString("1") ?: continue
                    val fullUrl = if (url.startsWith("//")) "https:$url" else url
                    out += Emote(name, fullUrl, EmoteKind.BTTV)
                }
            }
            out.also { if (it.isNotEmpty()) ffzGlobalCache = it }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseSeven(set: JSONObject?): List<Emote> {
        val arr = set?.optJSONArray("emotes") ?: return emptyList()
        return List(arr.length()) { i ->
            val e = arr.getJSONObject(i)
            Emote(
                code = e.optString("name"),
                url = "https://cdn.7tv.app/emote/${e.optString("id")}/2x.webp",
                kind = EmoteKind.SEVENTV
            )
        }
    }

    private fun getObject(url: String): JSONObject? {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            return JSONObject(resp.body?.string().orEmpty())
        }
    }

    private fun getArray(url: String): JSONArray? {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            return JSONArray(resp.body?.string().orEmpty())
        }
    }
}