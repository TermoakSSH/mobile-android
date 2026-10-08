package com.termoak.app.data

/**
 * The app's shortcuts (touch and hold its icon), as iOS's Quick Actions:
 * Quick connect, Join with a link and up to three hosts (favourites, then
 * the ones opened last). Pure, for the JVM tests; [com.termoak.app.AppShortcuts]
 * publishes them.
 */
sealed class QuickAction {
    data object QuickConnect : QuickAction()
    data object Join : QuickAction()
    /** [accountId]: `null` for a This-device host. */
    data class Host(val id: String, val accountId: String?) : QuickAction()

    companion object {
        const val ACTION_QUICK_CONNECT = "com.termoak.app.action.QUICK_CONNECT"
        const val ACTION_JOIN = "com.termoak.app.action.JOIN"
        const val ACTION_HOST = "com.termoak.app.action.HOST"
        const val EXTRA_HOST = "host"
        const val EXTRA_ACCOUNT = "account"

        /** The action of a shortcut's intent (`null`: not one of ours). */
        fun parse(action: String?, host: String?, account: String?): QuickAction? = when (action) {
            ACTION_QUICK_CONNECT -> QuickConnect
            ACTION_JOIN -> Join
            ACTION_HOST -> host?.takeIf { it.isNotEmpty() }?.let { Host(it, account?.takeIf { a -> a.isNotEmpty() }) }
            else -> null
        }
    }
}

/** The hosts opened last (most recent first), as [uidOf] keys, to offer them as shortcuts. */
data class RecentHosts(val keys: List<String> = emptyList()) {
    /** [key] opened now: first, without repeating it, at most [LIMIT]. */
    fun record(key: String): RecentHosts = RecentHosts((listOf(key) + keys.filter { it != key }).take(LIMIT))

    /**
     * Up to [count] hosts for the shortcuts: the [favorites] first (in that
     * order), then the ones opened last; [available]: the hosts that exist.
     */
    fun pick(favorites: List<String>, available: Set<String>, count: Int = 3): List<String> =
        (favorites + keys).filter { it in available }.distinct().take(count)

    companion object {
        const val LIMIT = 10
    }
}
