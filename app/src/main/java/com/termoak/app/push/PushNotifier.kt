package com.termoak.app.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.termoak.app.MainActivity
import com.termoak.app.R
import com.termoak.app.localized

/** Shows and withdraws the server's push notifications, one channel per kind of notice. */
class PushNotifier(private val context: Context) {
    private val manager get() = context.getSystemService(NotificationManager::class.java)

    /** The channels (Settings → Notifications of the system lets the user tune each). */
    fun ensureChannels() {
        val m = manager ?: return
        val res = context.localized().resources
        PushRoutes.CHANNELS.forEach { id ->
            val (name, description, importance) = when (id) {
                "approvals" -> Triple(R.string.push_channel_approvals, R.string.push_channel_approvals_hint, NotificationManager.IMPORTANCE_HIGH)
                "sessions" -> Triple(R.string.push_channel_sessions, R.string.push_channel_sessions_hint, NotificationManager.IMPORTANCE_HIGH)
                "security_keys" -> Triple(R.string.push_channel_security_keys, R.string.push_channel_security_keys_hint, NotificationManager.IMPORTANCE_HIGH)
                "ai" -> Triple(R.string.push_channel_ai, R.string.push_channel_ai_hint, NotificationManager.IMPORTANCE_DEFAULT)
                else -> Triple(R.string.push_channel_general, R.string.push_channel_general_hint, NotificationManager.IMPORTANCE_DEFAULT)
            }
            m.createNotificationChannel(
                NotificationChannel(channelId(id), res.getString(name), importance).apply { this.description = res.getString(description) },
            )
        }
    }

    fun canNotify(): Boolean = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun show(plan: PushPlan) {
        if (!canNotify()) return
        val m = manager ?: return
        ensureChannels()
        val res = context.localized().resources
        val title = plan.title.ifBlank { res.getString(fallbackTitle(plan.channel)) }
        val body = plan.body.ifBlank { res.getString(R.string.push_open_app) }
        val b = NotificationCompat.Builder(context, channelId(plan.channel))
            .setSmallIcon(R.drawable.ic_stat_terminal)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(intent(plan, null))
            .setAutoCancel(true)
            .setPriority(if (plan.urgent) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(if (plan.urgent) NotificationCompat.CATEGORY_MESSAGE else NotificationCompat.CATEGORY_STATUS)
            // The lock screen shows that something waits, not what.
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        plan.timeoutMs?.let { b.setTimeoutAfter(it) }
        plan.actions.forEach { a ->
            val label = res.getString(if (a == PushAction.APPROVE) R.string.push_action_approve else R.string.push_action_deny)
            b.addAction(0, label, intent(plan, a))
        }
        m.notify(plan.tag, plan.id, b.build())
    }

    fun cancel(id: Int, tag: String) {
        manager?.cancel(tag, id)
    }

    private fun intent(plan: PushPlan, action: PushAction?): PendingIntent {
        val i = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(PushRoutes.EXTRA_NOTIFICATION, plan.id)
            .putExtra(EXTRA_TAG, plan.tag)
        PushRoutes.extras(plan.open, action).forEach { (k, v) -> i.putExtra(k, v) }
        // A distinct request code per notification and button, so their extras don't overwrite each other.
        val code = plan.id * 3 + (action?.ordinal?.plus(1) ?: 0)
        return PendingIntent.getActivity(context, code, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun fallbackTitle(channel: String) = when (channel) {
        "approvals" -> R.string.push_channel_approvals
        "sessions" -> R.string.push_channel_sessions
        "security_keys" -> R.string.push_channel_security_keys
        "ai" -> R.string.push_channel_ai
        else -> R.string.app_name
    }

    companion object {
        const val EXTRA_TAG = "push_tag"

        /** The system's channel id (prefixed: the app has other channels). */
        fun channelId(channel: String) = "push_$channel"
    }
}
