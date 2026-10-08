package com.termoak.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which windows get the desktop layout (Settings → Appearance → Layout on wide screens). */
class WideLayoutTest {
    // Window size classes: width Expanded ≥ 840 dp, height Compact < 480 dp.
    private val tablet = booleanArrayOf(true, false)
    private val phoneLandscape = booleanArrayOf(true, true)
    private val phonePortrait = booleanArrayOf(false, false)
    private val narrowAndShort = booleanArrayOf(false, true)

    private fun WideLayout.on(w: BooleanArray) = desktop(expandedWidth = w[0], compactHeight = w[1])

    @Test
    fun automaticOnlyOnTabletsFoldablesAndChromebooks() {
        assertTrue(WideLayout.AUTO.on(tablet))
        assertFalse(WideLayout.AUTO.on(phoneLandscape))
        assertFalse(WideLayout.AUTO.on(phonePortrait))
        assertFalse(WideLayout.AUTO.on(narrowAndShort))
    }

    @Test
    fun phoneLayoutNever() {
        listOf(tablet, phoneLandscape, phonePortrait, narrowAndShort).forEach { assertFalse(WideLayout.PHONE.on(it)) }
    }

    @Test
    fun desktopLayoutWheneverTheWindowIsWide() {
        assertTrue(WideLayout.DESKTOP.on(tablet))
        assertTrue(WideLayout.DESKTOP.on(phoneLandscape))
        assertFalse(WideLayout.DESKTOP.on(phonePortrait))
        assertFalse(WideLayout.DESKTOP.on(narrowAndShort))
    }
}
