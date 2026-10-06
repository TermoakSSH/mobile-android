package com.termoak.app.data

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
import androidx.core.net.toUri
import com.termoak.app.MainActivity
import com.termoak.app.R
import com.termoak.app.localized
import com.termoak.app.term.Sessions
import com.termoak.app.term.ShareNotice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Locale

/** An invitation link: `termoak://join?server=…&token=…` or `https://<server>/join/<token>`. */
data class JoinLinkRef(val server: String, val token: String) {
    companion object {
        private val TOKEN = Regex("^[A-Za-z0-9_-]+$")

        fun parse(text: String?): JoinLinkRef? {
            val uri = runCatching { (text?.trim() ?: return null).toUri() }.getOrNull() ?: return null
            val scheme = uri.scheme?.lowercase(Locale.ROOT)
            return when {
                scheme == "termoak" && uri.host == "join" -> {
                    val server = uri.getQueryParameter("server")?.trim()?.trimEnd('/')
                    val token = uri.getQueryParameter("token")?.trim()
                    if (server.isNullOrEmpty() || token == null || !TOKEN.matches(token)) null else JoinLinkRef(server, token)
                }
                (scheme == "https" || scheme == "http") && uri.host != null -> {
                    // /join/<token> (web page) or /api/v1/join/<token>.
                    val seg = uri.pathSegments
                    val i = seg.indexOf("join")
                    val token = seg.getOrNull(i + 1)
                    if (i < 0 || token == null || !TOKEN.matches(token)) return null
                    val port = if (uri.port > 0) ":${uri.port}" else ""
                    JoinLinkRef("$scheme://${uri.host}$port", token)
                }
                else -> null
            }
        }

        /** Same server, whatever the trailing slash or letter case. */
        fun sameServer(a: String?, b: String?): Boolean {
            fun norm(s: String?) = s?.trim()?.trimEnd('/')?.lowercase(Locale.ROOT)
            return a != null && b != null && norm(a) == norm(b)
        }
    }
}

/**
 * Sharing notices for the whole app: join and keyboard requests (from the
 * open terminals and from the server's events), sessions shared with you and
 * keyboard changes. The app shows them as snackbars; in the background, join
 * and keyboard requests and new shares also become notifications.
 */
class ShareNotices(private val context: Context, private val sessions: Sessions, private val accounts: Accounts) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _notices = MutableSharedFlow<ShareNotice>(extraBufferCapacity = 16)
    val notices: SharedFlow<ShareNotice> = _notices

    /** Opened from a notification: the app goes to that terminal. */
    val openRequest = MutableStateFlow<ShareNotice?>(null)

    /** An activity of the app is visible. */
    @Volatile var inForeground = false

    private val recent = HashMap<String, Long>()
    private var started = false
    private var nextId = 100

    fun start() {
        if (started) return
        started = true
        scope.launch { sessions.notices.collect { emit(it) } }
        scope.launch { accounts.sessionNotices.collect { n -> fromServer(n)?.let { emit(it) } } }
    }

    private fun fromServer(n: JSONObject): ShareNotice? {
        val kind = when (n.optString("type")) {
            "join_request" -> ShareNotice.Kind.JOIN_REQUEST
            "control_request" -> ShareNotice.Kind.CONTROL_REQUEST
            "session_shared" -> ShareNotice.Kind.SHARED_WITH_YOU
            "control_granted" -> ShareNotice.Kind.CONTROL_GRANTED
            "control_revoked" -> ShareNotice.Kind.CONTROL_REVOKED
            else -> return null
        }
        val session = n.optJSONObject("session")
        val sessionId = n.optString("session_id").ifEmpty { session?.optString("id").orEmpty() }.ifEmpty { null }
        val title = n.optString("title").ifEmpty { session?.optString("title").orEmpty() }
        val participant = n.optJSONObject("participant")
        val name = participant?.optString("name")?.ifEmpty { null } ?: n.optString("by")
        val tab = sessionId?.let { sessions.bySessionId(it) }
        return ShareNotice(
            kind, tab?.id, sessionId, title.ifBlank { tab?.label.orEmpty() }, name, participant?.optString("id"),
            accountId = n.optString("account_id").ifEmpty { null },
        )
    }

    private fun emit(n: ShareNotice) {
        // The same request can come from the terminal and from the events.
        val key = "${n.kind}:${n.sessionId ?: n.tabId}:${n.participantId ?: n.name}"
        val now = System.currentTimeMillis()
        if (recent[key]?.let { now - it < 15_000 } == true) return
        recent[key] = now
        recent.entries.removeAll { now - it.value > 60_000 }
        _notices.tryEmit(n)
        if (!inForeground) notify(n)
    }

    private fun notify(n: ShareNotice) {
        if (n.kind == ShareNotice.Kind.CONTROL_GRANTED || n.kind == ShareNotice.Kind.CONTROL_REVOKED) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val res = context.localized().resources
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, res.getString(R.string.share_channel), NotificationManager.IMPORTANCE_HIGH).apply {
                description = res.getString(R.string.share_channel_description)
            },
        )
        val title = n.title.ifBlank { res.getString(R.string.common_session) }
        val (head, body) = when (n.kind) {
            ShareNotice.Kind.JOIN_REQUEST ->
                res.getString(R.string.share_notice_join_title) to res.getString(R.string.share_notice_join, n.name, title)
            ShareNotice.Kind.CONTROL_REQUEST ->
                res.getString(R.string.share_notice_control_title) to res.getString(R.string.share_notice_control, n.name, title)
            else -> res.getString(R.string.share_notice_shared_title) to res.getString(R.string.share_notice_shared, n.name, title)
        }
        val id = nextId++
        val open = PendingIntent.getActivity(
            context, id,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_KIND, n.kind.name)
                .putExtra(EXTRA_TAB, n.tabId)
                .putExtra(EXTRA_SESSION, n.sessionId)
                .putExtra(EXTRA_TITLE, n.title)
                .putExtra(EXTRA_ACCOUNT, n.accountId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        manager.notify(
            id,
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_terminal)
                .setContentTitle(head)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .build(),
        )
    }

    /** A notification of ours was tapped. */
    fun handleIntent(intent: Intent?): Boolean {
        val kind = intent?.getStringExtra(EXTRA_KIND)?.let { k -> ShareNotice.Kind.entries.firstOrNull { it.name == k } } ?: return false
        openRequest.value = ShareNotice(
            kind, intent.getStringExtra(EXTRA_TAB), intent.getStringExtra(EXTRA_SESSION),
            intent.getStringExtra(EXTRA_TITLE).orEmpty(), "", accountId = intent.getStringExtra(EXTRA_ACCOUNT),
        )
        intent.removeExtra(EXTRA_KIND)
        return true
    }

    companion object {
        private const val CHANNEL = "sharing"
        private const val EXTRA_KIND = "share_kind"
        private const val EXTRA_TAB = "share_tab"
        private const val EXTRA_SESSION = "share_session"
        private const val EXTRA_TITLE = "share_title"
        private const val EXTRA_ACCOUNT = "share_account"
    }
}
