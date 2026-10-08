package com.termoak.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuickActionsTest {
    @Test
    fun shortcutIntentsAreRecognised() {
        assertEquals(QuickAction.QuickConnect, QuickAction.parse(QuickAction.ACTION_QUICK_CONNECT, null, null))
        assertEquals(QuickAction.Join, QuickAction.parse(QuickAction.ACTION_JOIN, null, null))
        assertEquals(QuickAction.Host("h1", "acc"), QuickAction.parse(QuickAction.ACTION_HOST, "h1", "acc"))
        assertEquals(QuickAction.Host("h1", null), QuickAction.parse(QuickAction.ACTION_HOST, "h1", ""))
        assertNull(QuickAction.parse(QuickAction.ACTION_HOST, null, "acc"))
        assertNull(QuickAction.parse("android.intent.action.VIEW", "h1", null))
        assertNull(QuickAction.parse(null, null, null))
    }

    @Test
    fun recentHostsGoFirstWithoutRepeatsAndAreLimited() {
        var r = RecentHosts()
        for (i in 1..12) r = r.record("a/$i")
        r = r.record("a/5")
        assertEquals(RecentHosts.LIMIT, r.keys.size)
        assertEquals(listOf("a/5", "a/12", "a/11"), r.keys.take(3))
        assertEquals(1, r.keys.count { it == "a/5" })
    }

    @Test
    fun favouritesComeFirstThenTheRecentOnesThatStillExist() {
        val r = RecentHosts(listOf("a/3", "a/gone", "a/1", "a/4"))
        val available = setOf("a/1", "a/2", "a/3", "a/4")
        assertEquals(listOf("a/2", "a/1", "a/3"), r.pick(favorites = listOf("a/2", "a/1"), available = available))
        assertEquals(listOf("a/3", "a/1", "a/4"), r.pick(favorites = emptyList(), available = available))
        assertEquals(emptyList<String>(), RecentHosts().pick(emptyList(), available))
    }
}
