package com.termoak.app

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.termoak.app.data.Prefs
import com.termoak.app.data.QuickAction
import com.termoak.app.data.uid
import com.termoak.ffi.ItemFilter
import com.termoak.ffi.SshHost
import com.termoak.ffi.TermoakCore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The app's shortcuts (touch and hold its icon), as iOS's Quick Actions:
 * Quick connect, Join with a link and up to three hosts (favourites first,
 * then the ones opened last on this phone).
 */
class AppShortcuts(private val context: Context, private val core: TermoakCore, private val prefs: Prefs) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** [host] was opened: first among the recent ones. */
    fun record(host: SshHost) {
        prefs.recentHosts = prefs.recentHosts.record(host.uid)
        used("host:${host.uid}")
    }

    /** A shortcut of the app was used (Quick connect, Join): launchers rank them by use. */
    fun used(action: QuickAction) {
        when (action) {
            QuickAction.QuickConnect -> used("quick-connect")
            QuickAction.Join -> used("join")
            // Connecting records it.
            is QuickAction.Host -> Unit
        }
    }

    private fun used(id: String) {
        runCatching { ShortcutManagerCompat.reportShortcutUsed(context, id) }
    }

    /** Publishes the shortcuts again (the hosts may have changed). */
    fun update() {
        scope.launch {
            runCatching {
                val hosts = core.listHosts(ItemFilter(accountIds = null, vaultIds = null, includeDevice = true))
                val byKey = hosts.associateBy { it.uid }
                val favorites = hosts.filter { it.favorite }.sortedBy { it.label.lowercase() }.map { it.uid }
                val picked = prefs.recentHosts.pick(favorites, byKey.keys).mapNotNull { byKey[it] }
                val list = listOf(
                    shortcut("quick-connect", QuickAction.ACTION_QUICK_CONNECT, context.getString(R.string.tabs_quick_connect), R.drawable.ic_shortcut_bolt),
                    shortcut("join", QuickAction.ACTION_JOIN, context.getString(R.string.join_with_link), R.drawable.ic_shortcut_link),
                ) + picked.map { h ->
                    shortcut(
                        "host:${h.uid}", QuickAction.ACTION_HOST, h.label,
                        if (h.favorite) R.drawable.ic_shortcut_star else R.drawable.ic_shortcut_terminal,
                    ) {
                        putExtra(QuickAction.EXTRA_HOST, h.id)
                        h.accountId?.let { putExtra(QuickAction.EXTRA_ACCOUNT, it) }
                    }
                }
                val max = ShortcutManagerCompat.getMaxShortcutCountPerActivity(context).takeIf { it > 0 } ?: list.size
                ShortcutManagerCompat.setDynamicShortcuts(context, list.take(max))
            }
        }
    }

    private fun shortcut(
        id: String, action: String, label: String, icon: Int, extras: Intent.() -> Unit = {},
    ): ShortcutInfoCompat {
        val intent = Intent(context, MainActivity::class.java).setAction(action)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).apply(extras)
        return ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(label)
            .setLongLabel(label)
            .setIcon(IconCompat.createWithResource(context, icon))
            .setIntent(intent)
            .build()
    }
}
