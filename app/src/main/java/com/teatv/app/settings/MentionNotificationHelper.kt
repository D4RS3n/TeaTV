package com.teatv.app.settings

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.teatv.app.MainActivity
import com.teatv.app.R

/** Показ системных уведомлений при упоминании в чате (когда стрим свёрнут). */
object MentionNotificationHelper {

    private const val CHANNEL_ID = "teatv_mentions"
    private const val CHANNEL_NAME = "Упоминания в чате"
    private var channelCreated = false

    private fun ensureChannel(context: Context) {
        if (channelCreated) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Уведомления, когда вас упоминают в чате стрима"
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
        channelCreated = true
    }

    fun notifyMention(context: Context, sender: String, messageText: String, streamName: String?) {
        ensureChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pi = PendingIntent.getActivity(
            context,
            sender.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = if (!streamName.isNullOrBlank()) "Вас упомянули на стриме $streamName" else "Вас упомянули в чате"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_emote)
            .setContentTitle(title)
            .setContentText("$sender: $messageText")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$sender: $messageText"))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(System.currentTimeMillis().toInt(), notification)
    }
}
