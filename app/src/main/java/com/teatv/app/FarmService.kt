package com.teatv.app

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.teatv.app.auth.TokenStore
import com.teatv.app.chat.points.FarmManager
import com.teatv.app.chat.points.PointsFarmer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.collections.iterator

/** Невидимый координатор фарма: живёт по правилу состояния, а не по действиям пользователя. */
class FarmService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        FarmManager.ensureLoaded(this)
        reconcile()
        return START_STICKY
    }

    override fun onDestroy() {
        instance = null
        scope.cancel()
        super.onDestroy()
        ProcessGuard.killIfEmpty()
    }

    /** Единственная воронка решений: приводит «что фармит» к «что должно фармить». */
    private fun reconcile() {
        scope.launch {
            val token = TokenStore(this@FarmService).accessToken
            if (token == null) {
                stopSelf()
                return@launch
            }
            val registry = FarmManager.registry()
            if (registry.isEmpty()) {
                Log.i(TAG, "registry empty, stopping")
                stopSelf()
                return@launch
            }
            // Сессии вне реестра и не просматриваемые сейчас — гасим.
            for (id in PointsFarmer.runningIds()) {
                if (!registry.containsKey(id) && FarmManager.viewingId != id) {
                    PointsFarmer.stopSession(id)
                }
            }
            // Кто из зафлаженных в сети.
            val online = mutableSetOf<String>()
            for ((id, _) in registry) {
                if (PointsFarmer.broadcastIdFor(id, token) != null) online.add(id)
            }
            // Дозапускаем недостающие бот-сессии.
            for ((id, login) in registry) {
                val viewingActive = FarmManager.viewingId == id && PointsFarmer.isRunning(id)
                if (viewingActive) continue
                if (id in online && !PointsFarmer.isRunning(id)) {
                    PointsFarmer.startSession(
                        channelId = id,
                        channelLogin = login,
                        token = token,
                        onClaimed = { Log.i(TAG, "bot chest: $login") },
                        onBalance = { },
                        onRaided = { Log.i(TAG, "bot raid: $login") },
                        onOffline = { FarmManager.sessionDied() }
                    )
                }
            }
            val anyRunning = registry.keys.any { PointsFarmer.isRunning(it) }
            if (!anyRunning) {
                Log.i(TAG, "no live sessions, stopping")
                stopSelf()
            }
        }
    }

    companion object {
        private const val TAG = "TeaTV-Farm"

        @Volatile
        var instance: FarmService? = null
            private set
    }
}