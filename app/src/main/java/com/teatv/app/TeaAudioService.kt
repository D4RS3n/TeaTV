package com.teatv.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media.app.NotificationCompat.MediaStyle
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.teatv.app.chat.points.FarmManager
import com.teatv.app.chat.points.PointsFarmer
import com.teatv.app.player.PlayerHolder

/** Сессия воспроизведения: единственный владелец плеера и уведомления. */
class TeaAudioService : Service() {

    private var session: MediaSessionCompat? = null
    private var ending = false
    private val fallbackIcon by lazy {
        BitmapFactory.decodeResource(resources, R.drawable.teatvlogo2)
    }

    private val sessionCallback = object : MediaSessionCompat.Callback() {
        override fun onPlay() {
            PlayerHolder.player?.playWhenReady = true
            promote()
        }

        override fun onPause() {
            PlayerHolder.player?.playWhenReady = false
        }

        override fun onStop() {
            endSession()
        }
    }

    private val toggleReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                ACTION_TOGGLE -> {
                    val p = PlayerHolder.player ?: return
                    p.playWhenReady = !p.playWhenReady
                    if (p.playWhenReady) promote()
                }
                ACTION_DISMISS -> endSession()
            }
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                Log.i(TAG, "stream ended, closing session")
                endSession()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.w(TAG, "player error: ${error.message}")
            PlayerHolder.player?.playWhenReady = false
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (playWhenReady) promote() else demote()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        session = MediaSessionCompat(this, "TeaTV").apply {
            setCallback(sessionCallback)
            isActive = true
        }
        ContextCompat.registerReceiver(
            this,
            toggleReceiver,
            IntentFilter().apply {
                addAction(ACTION_TOGGLE)
                addAction(ACTION_DISMISS)
            },
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CH_ID, "Воспроизведение", NotificationManager.IMPORTANCE_LOW)
        )
        val p = PlayerHolder.player
        if (p == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        p.removeListener(playerListener)
        p.addListener(playerListener)
        startForeground(NOTIF_ID, buildNotification())
        if (!p.playWhenReady) {
            stopForeground(STOP_FOREGROUND_DETACH)
            push()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        instance = null
        PlayerHolder.player?.removeListener(playerListener)
        runCatching { unregisterReceiver(toggleReceiver) }
        session?.isActive = false
        session?.release()
        session = null
        super.onDestroy()
    }

    /** Играет: foreground и закреплённая карточка. */
    private fun promote() {
        if (PlayerHolder.player == null) return
        startForeground(NOTIF_ID, buildNotification())
    }

    /** Пауза: живём в фоне, карточка смахиваема. */
    private fun demote() {
        if (PlayerHolder.player == null) return
        stopForeground(STOP_FOREGROUND_DETACH)
        push()
    }

    /** Смерть сессии: единственная точка всей уборки. */
    private fun endSession() {
        if (ending) return
        ending = true
        Log.i(TAG, "session end")
        // Сессия просмотра умирает вместе с медиа-сессией; бот-сессии независимы.
        val viewedId = PlayerHolder.streamId
        if (viewedId != null && !FarmManager.isBot(viewedId)) {
            PointsFarmer.stopSession(viewedId)
        }
        val p = PlayerHolder.player
        p?.removeListener(playerListener)
        p?.stop()
        p?.release()
        PlayerHolder.player = null
        PlayerHolder.streamName = null
        PlayerHolder.streamId = null
        PlayerHolder.streamGame = null
        PlayerHolder.streamAvatar = null
        // Уведомление отцеплено от сервиса (detach) — убираем вручную.
        getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
        stopSelf()
    }

    private fun push() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val playing = PlayerHolder.player?.playWhenReady == true

        session?.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                            PlaybackStateCompat.ACTION_PAUSE or
                            PlaybackStateCompat.ACTION_STOP
                )
                .setState(
                    if (playing) PlaybackStateCompat.STATE_PLAYING
                    else PlaybackStateCompat.STATE_PAUSED,
                    PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                    1f
                )
                .build()
        )

        val togglePi = PendingIntent.getBroadcast(
            this, 1,
            Intent(ACTION_TOGGLE).setPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val openPi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val dismissPi = PendingIntent.getBroadcast(
            this, 3,
            Intent(ACTION_DISMISS).setPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CH_ID)
            .setSmallIcon(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
            .setLargeIcon(PlayerHolder.streamAvatar ?: fallbackIcon)
            .setContentTitle(PlayerHolder.streamName ?: "TeaTV")
            .setContentText(PlayerHolder.streamGame ?: "Фоновое воспроизведение")
            .addAction(
                NotificationCompat.Action.Builder(
                    if (playing) R.drawable.ic_pause else R.drawable.ic_play,
                    if (playing) "Пауза" else "Играть",
                    togglePi
                ).build()
            )
            .addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_delete,
                    "Закрыть",
                    dismissPi
                ).build()
            )
            .setStyle(
                MediaStyle()
                    .setMediaSession(session?.sessionToken)
                    .setShowActionsInCompactView(0, 1)
            )
            .setContentIntent(openPi)
            .setDeleteIntent(dismissPi)
            .setOngoing(playing)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    companion object {
        @Volatile
        var instance: TeaAudioService? = null
            private set

        const val ACTION_TOGGLE = "com.teatv.app.action.TOGGLE"
        const val ACTION_DISMISS = "com.teatv.app.action.DISMISS"
        private const val TAG = "TeaTV-AudioService"
        private const val CH_ID = "teatv_media"
        private const val NOTIF_ID = 77
    }
}