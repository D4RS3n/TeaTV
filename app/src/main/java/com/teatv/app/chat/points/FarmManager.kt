package com.teatv.app.chat.points

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import com.teatv.app.FarmService
import org.json.JSONObject

/** Реестр бот-каналов: данные и будильник координатора. Решений о жизненном цикле здесь нет. */
object FarmManager {

    private const val TAG = "TeaTV-Farm"
    private const val PREF = "tea_farm_prefs"
    private const val KEY = "bot_channels"

    private val channels = mutableMapOf<String, String>() // id -> login
    private var loaded = false
    private var appCtx: Context? = null

    @Volatile
    var viewingId: String? = null
        private set

    fun isBot(channelId: String): Boolean = channels.containsKey(channelId)

    fun count(): Int = channels.size

    fun registry(): Map<String, String> = channels.toMap()

    fun setViewing(id: String?) {
        viewingId = id
    }

    fun ensureLoaded(context: Context) {
        if (!loaded) load(context)
    }

    /** Галочка бота: только правка реестра + будильник. */
    fun setBotEnabled(
        context: Context,
        channelId: String,
        login: String,
        token: String,
        enabled: Boolean,
        startSessionNow: Boolean
    ) {
        ensureLoaded(context)
        if (enabled) channels[channelId] = login else channels.remove(channelId)
        save(context)
        Log.i(TAG, "bot ${if (enabled) "on" else "off"}: $login")
        wake(context)
    }

    /** Будильник координатора: сервис сам решит, жить ему или умереть. */
    fun wake(context: Context) {
        appCtx = context.applicationContext
        ensureLoaded(context)
        val i = Intent(context, FarmService::class.java)
        if (channels.isEmpty()) {
            context.stopService(i)
        } else {
            context.startService(i)
        }
    }

    /** Сессия фарма умерла (офлайн канала): координатор перепроверяет состояние. */
    fun sessionDied() {
        appCtx?.let { wake(it) }
    }

    /** Старт приложения / рестарт сервиса: реестр с диска + будильник. */
    fun restore(context: Context, token: String) {
        wake(context)
    }

    /** Полная очистка (logout). */
    fun clear(context: Context) {
        channels.clear()
        loaded = true
        save(context)
        PointsFarmer.stopAll()
        context.stopService(Intent(context, FarmService::class.java))
    }

    /** Разовый системный запрос: не оптимизировать батарею. */
    fun requestIgnoreOptimizations(context: Context) {
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
                val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:" + context.packageName))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(i)
            }
        } catch (e: Exception) {
            Log.w(TAG, "optimizations request failed", e)
        }
    }

    private fun save(context: Context) {
        val json = JSONObject()
        channels.forEach { (id, login) -> json.put(id, login) }
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY, json.toString()).apply()
    }

    private fun load(context: Context) {
        channels.clear()
        loaded = true
        val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return
        try {
            val json = JSONObject(raw)
            json.keys().forEach { id -> channels[id] = json.optString(id) }
        } catch (e: Exception) {
            Log.w(TAG, "load failed", e)
        }
    }
}