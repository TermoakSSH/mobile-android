package com.termoak.app.term

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.termoak.app.MainActivity
import com.termoak.app.R
import com.termoak.app.TermoakApp
import com.termoak.app.localized

/**
 * Keeps the app alive while there are open terminals: without it, Android
 * freezes the app shortly after leaving it and the SSH connections drop.
 */
class TerminalService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val count = intent?.getIntExtra(EXTRA_COUNT, 1) ?: 1
        // In the app language (also on Android 12 and older).
        val res = localized().resources
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL, res.getString(R.string.notification_channel_terminals), NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = res.getString(R.string.notification_channel_terminals_description)
                setShowBadge(false)
            },
        )
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val closeAll = PendingIntent.getService(
            this, 1,
            Intent(this, TerminalService::class.java).setAction(ACTION_CLOSE_ALL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        if (intent?.action == ACTION_CLOSE_ALL) {
            (application as TermoakApp).sessions.closeAll()
            stopSelf()
            return START_NOT_STICKY
        }
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_terminal)
            .setContentTitle(res.getQuantityString(R.plurals.notification_terminals_open, count, count))
            .setContentText(res.getString(R.string.notification_tap_to_return))
            .setContentIntent(open)
            .addAction(0, res.getString(R.string.notification_close_all), closeAll)
            .setOngoing(true)
            .setSilent(true)
            .build()
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification,
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
        return START_NOT_STICKY
    }

    companion object {
        const val EXTRA_COUNT = "count"
        const val ACTION_CLOSE_ALL = "com.termoak.CLOSE_ALL"
        private const val CHANNEL = "terminals"
        private const val NOTIFICATION_ID = 1
    }
}
