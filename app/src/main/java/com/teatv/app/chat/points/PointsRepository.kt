package com.teatv.app.chat.points

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.dnsoverhttps.DnsOverHttps
import org.json.JSONObject
import java.net.InetAddress
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Баллы канала через Twitch GQL: у Helix нет баланса зрителя. */
object PointsRepository {

    private const val GQL_URL = "https://gql.twitch.tv/gql"
    private const val WEB_CLIENT_ID = "kimne78kx3ncx6brgo4mv6wki5h1ko"
    private const val PREF_NAME = "tea_points_prefs"
    private const val KEY_REDEEM_MUTATION = "redeem_mutation_name"
    private const val PREF_RELAY_URL = "gql_relay_url"

    private var relayUrl: String? = null

    /** DoH-резолвер: обход DNS-блокировок оператора. */
    private val doh = DnsOverHttps.Builder()
        .client(OkHttpClient())
        .url("https://cloudflare-dns.com/dns-query".toHttpUrl())
        .bootstrapDnsHosts(
            listOf(
                InetAddress.getByName("1.1.1.1"),
                InetAddress.getByName("1.0.0.1")
            )
        )
        .build()

    private val client = OkHttpClient.Builder()
        .dns(doh)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private const val QUERY = """
query TeaPoints(${'$'}ch: ID!) {
  channel(id: ${'$'}ch) {
    self { communityPoints { balance } }
    communityPointsSettings {
      name
      image { url }
      customRewards {
        id
        title
        cost
        isPaused
        isInStock
        isUserInputRequired
        image { url }
      }
    }
  }
}
"""

    /** Возможные имена мутации списания (до первого успеха). */
    private val REDEEM_CANDIDATES = listOf(
        "redeemCommunityPointsCustomReward",
        "redeemCustomReward",
        "claimCommunityPointsReward",
        "redeemCommunityPointsReward"
    )

    /** Вызвать один раз в Application.onCreate. */
    fun init(context: Context) {
        relayUrl = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(PREF_RELAY_URL, "")?.takeIf { it.isNotBlank() }
    }

    private fun endpoint(): String = relayUrl ?: GQL_URL

    /** Client-ID веб-клиента для смежных модулей. */
    fun webClientId(): String = WEB_CLIENT_ID

    suspend fun load(channelId: String, token: String, force: Boolean = false): ChannelPointsState? =
        withContext(Dispatchers.IO) {
            val effectiveUrl = if (force) {
                // Добавляем случайный параметр для обхода CDN-кеша
                val endpoint = endpoint()
                if (endpoint.contains("?")) "$endpoint&_t=${System.currentTimeMillis()}"
                else "$endpoint?_t=${System.currentTimeMillis()}"
            } else {
                endpoint()
            }
            try {
                val body = JSONObject().apply {
                    put("query", QUERY)
                    put("variables", JSONObject().put("ch", channelId))
                }.toString()
                val json = postGql(effectiveUrl, body, token) ?: return@withContext null
                val ch = json.optJSONObject("data")?.optJSONObject("channel")
                    ?: return@withContext null
                val settings = ch.optJSONObject("communityPointsSettings")
                    ?: return@withContext null
                val rewards = mutableListOf<PointReward>()
                val arr = settings.optJSONArray("customRewards")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val r = arr.getJSONObject(i)
                        rewards += PointReward(
                            id = r.optString("id"),
                            title = r.optString("title"),
                            cost = r.optInt("cost"),
                            imageUrl = r.optJSONObject("image")?.optString("url")
                                ?.takeIf { it.isNotBlank() },
                            backgroundColor = null,
                            isPaused = r.optBoolean("isPaused"),
                            isOutOfStock = !r.optBoolean("isInStock", true),
                            isUserInputRequired = r.optBoolean("isUserInputRequired")
                        )
                    }
                }
                rewards.sortBy { it.cost }
                ChannelPointsState(
                    balance = ch.optJSONObject("self")
                        ?.optJSONObject("communityPoints")
                        ?.let { if (it.has("balance")) it.getInt("balance") else null },
                    pointName = settings.optString("name").takeIf { it.isNotBlank() },
                    iconUrl = settings.optJSONObject("image")?.optString("url")
                        ?.takeIf { it.isNotBlank() },
                    rewards = rewards
                )
            } catch (e: Exception) {
                Log.e("TeaTV-Points", "load failed", e)
                null
            }
        }

    /** Потратить баллы; имя мутации подбирается один раз и запоминается. */
    suspend fun redeem(
        context: Context,
        channelId: String,
        reward: PointReward,
        userInput: String?,
        token: String
    ): Boolean = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val savedName = prefs.getString(KEY_REDEEM_MUTATION, null)
        val candidates = if (savedName != null) listOf(savedName) else REDEEM_CANDIDATES

        val safeTitle = reward.title.replace("\"", "\\\"")
        val safeInput = userInput?.replace("\"", "\\\"")
        val txId = UUID.randomUUID().toString().replace("-", "")
        val inputFields = buildString {
            append("channelID: \"").append(channelId).append('"')
            append(", rewardID: \"").append(reward.id).append('"')
            append(", cost: ").append(reward.cost)
            append(", title: \"").append(safeTitle).append('"')
            append(", transactionID: \"").append(txId).append('"')
            if (safeInput != null) {
                append(", textInput: \"").append(safeInput).append('"')
            }
        }

        for (name in candidates) {
            try {
                val query = "mutation TeaRedeem {\n  " + name +
                        "(input: {" + inputFields + "}) { __typename }\n}"
                val body = JSONObject().put("query", query).toString()
                val json = postGql(endpoint(), body, token) ?: return@withContext false
                if (json.has("errors")) {
                    val errs = json.optJSONArray("errors")?.toString().orEmpty()
                    if (errs.contains("Cannot query field")) continue
                    Log.e("TeaTV-Points", "redeem errors: $errs")
                    return@withContext false
                }
                val payload = json.optJSONObject("data")?.optJSONObject(name)
                if (payload != null) {
                    val err = payload.optJSONObject("error")
                    if (err != null) {
                        Log.e("TeaTV-Points", "redeem payload error on $name: $err")
                        return@withContext false
                    }
                    if (savedName == null) {
                        prefs.edit().putString(KEY_REDEEM_MUTATION, name).apply()
                    }
                    return@withContext true
                }
            } catch (e: Exception) {
                Log.e("TeaTV-Points", "redeem failed on $name", e)
                return@withContext false
            }
        }
        false
    }

    /** Клиент с DoH для смежных модулей. */
    fun httpClient(): OkHttpClient = client

    /** GQL-пост для смежных модулей (фармер). */
    fun postGqlPublic(body: String, token: String): JSONObject? =
        postGql(endpoint(), body, token)

    private fun postGql(url: String, body: String, token: String): JSONObject? =
        try {
            val first = postOnce(url, body, token, "OAuth")
            val pair = if (first.first == 401) {
                Log.w("TeaTV-Points", "OAuth scheme rejected, retry with Bearer")
                postOnce(url, body, token, "Bearer")
            } else {
                first
            }
            val (code, raw) = pair
            if (code !in 200..299) {
                Log.e("TeaTV-Points", "HTTP $code: $raw")
                null
            } else {
                JSONObject(raw)
            }
        } catch (e: Exception) {
            Log.e("TeaTV-Points", "gql failed", e)
            null
        }

    private fun postOnce(
        url: String,
        body: String,
        token: String,
        scheme: String
    ): Pair<Int, String> {
        val req = Request.Builder()
            .url(url)
            .post(body.toRequestBody("application/json".toMediaType()))
            .header("Client-ID", WEB_CLIENT_ID)
            .header("Authorization", "$scheme $token")
            .header("Cache-Control", "no-cache")
            .header("Pragma", "no-cache")
            .build()
        client.newCall(req).execute().use { resp ->
            return resp.code to resp.body?.string().orEmpty()
        }
    }
}