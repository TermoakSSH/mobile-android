package com.termoak.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The palette's actions that apply now and the keys it remembers (the iOS app's rules). */
class PaletteCommandTest {
    @Test
    fun availableActions() {
        assertEquals(listOf(PaletteCommand.TOGGLE_THEME), PaletteCommand.available(PaletteCommand.State()))
        val one = PaletteCommand.available(PaletteCommand.State(tabs = 1, terminalShown = true))
        assertEquals(
            listOf(PaletteCommand.HOME, PaletteCommand.CLOSE_TAB, PaletteCommand.ZOOM_IN, PaletteCommand.ZOOM_OUT, PaletteCommand.ZOOM_RESET, PaletteCommand.TOGGLE_THEME),
            one,
        )
        val split = PaletteCommand.available(PaletteCommand.State(tabs = 3, terminalShown = true, splitAvailable = true, splitActive = true))
        assertTrue(PaletteCommand.ADD_TO_SPLIT in split && PaletteCommand.FOCUS_MODE in split && PaletteCommand.BROADCAST in split && PaletteCommand.NEXT_TAB in split)
        assertEquals("cmd:zoomIn", PaletteCommand.ZOOM_IN.key)
    }

    @Test
    fun rememberedKeys() {
        assertFalse(PaletteKey.remembered(PaletteKey.tab("1")))
        assertTrue(PaletteKey.remembered(PaletteKey.host("a/1")))
        assertTrue(PaletteKey.remembered(PaletteCommand.HOME.key))
    }
}
