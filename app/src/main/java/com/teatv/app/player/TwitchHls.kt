package com.teatv.app.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

/** Качество стрима из мастер-плейлиста Twitch. */
data class QualityOption(
    val label: String,
    val url: String,
    val audioOnly: Boolean,
    val isSource: Boolean = false
)

object TwitchHls {

    private val http = OkHttpClient()

    fun masterUrl(login: String, tokenValue: String, signature: String): String =
        "https://usher.ttvnw.net/api/channel/hls/$login.m3u8" +
                "?sig=$signature" +
                "&token=" + URLEncoder.encode(tokenValue, "UTF-8") +
                "&allow_source=true&allow_audio_only=true&fast_bread=true" +
                "&low_latency=true" +
                "&player_backend=mediaplayer&playlist_include_framerate=true"

    /** Мастер-плейлист VOD (архива) по usher, с теми же флагами, что и лайв. */
    fun vodMasterUrl(videoId: String, tokenValue: String, signature: String): String =
        "https://usher.ttvnw.net/vod/$videoId.m3u8" +
                "?sig=$signature" +
                "&token=" + URLEncoder.encode(tokenValue, "UTF-8") +
                "&allow_source=true&allow_audio_only=true&fast_bread=true" +
                "&player_backend=mediaplayer&playlist_include_framerate=true"

    /** Парсинг мастер-плейлиста: видео-варианты (сортировка, кириллица) + source + «только звук». */
    suspend fun fetchQualities(masterUrl: String): List<QualityOption> =
        withContext(Dispatchers.IO) {
            val req = Request.Builder().url(masterUrl).build()
            val text = http.newCall(req).execute().use { it.body?.string().orEmpty() }
            val video = mutableListOf<Triple<Int, Int, QualityOption>>()
            val audio = mutableListOf<QualityOption>()
            var source: QualityOption? = null
            val lines = text.lines()
            var i = 0
            while (i < lines.size) {
                val l = lines[i]
                if (l.startsWith("#EXT-X-STREAM-INF")) {
                    val name = Regex("VIDEO=\"([^\"]*)\"").find(l)?.groupValues?.get(1)
                        ?: Regex("NAME=\"([^\"]*)\"").find(l)?.groupValues?.get(1)
                        ?: "auto"
                    var j = i + 1
                    while (j < lines.size && lines[j].startsWith("#")) j++
                    if (j < lines.size) {
                        val url = lines[j].trim()
                        val lower = name.lowercase()
                        when {
                            lower == "none" || lower == "audio_only" || lower.contains("audio") ->
                                audio += QualityOption("Только звук", url, true)
                            lower == "chunked" || lower.contains("source") ->
                                source = QualityOption("Источник", url, false, true)
                            else -> {
                                val m = Regex("(\\d+)p(\\d+)?").find(lower)
                                val p = m?.groupValues?.get(1)?.toIntOrNull() ?: 0
                                val fps = m?.groupValues?.get(2)?.toIntOrNull() ?: 0
                                val label = when {
                                    p <= 0 -> name
                                    fps > 0 -> "${p}p, ${fps} к/с"
                                    else -> "${p}p"
                                }
                                video += Triple(p, fps, QualityOption(label, url, false))
                            }
                        }
                        i = j
                    }
                }
                i++
            }
            video.sortedWith(
                compareByDescending<Triple<Int, Int, QualityOption>> { it.first }
                    .thenByDescending { it.second }
            ).map { it.third } + listOfNotNull(source) + audio
        }
}