package com.termoak.app.term

import org.junit.Assert.assertEquals
import org.junit.Test

/** The terminal's bars and keys from its theme (the iOS app's rule). */
class TermChromeTest {
    @Test
    fun blends() {
        assertEquals(0xFF000000.toInt(), TermChrome.blend(0xFF000000.toInt(), -1, 0f))
        assertEquals(-1, TermChrome.blend(0xFF000000.toInt(), -1, 1f))
        assertEquals(0xFF808080.toInt(), TermChrome.blend(0xFF000000.toInt(), -1, 0.5f))
    }

    @Test
    fun barsAreLighterOnDarkThemesAndDarkerOnLightOnes() {
        val dark = TermChrome.of(0x12151D, 0xD6DBE4, 0x3FB27F, light = false)
        assertEquals(0xFF12151D.toInt(), dark.screen)
        // 6 % and 12 % towards white.
        assertEquals(0xFF20232B.toInt(), dark.bar)
        assertEquals(0xFF2E3138.toInt(), dark.key)
        assertEquals(0xFFD6DBE4.toInt(), dark.text)
        assertEquals(0xFF3FB27F.toInt(), dark.accent)
        val light = TermChrome.of(0xFFFFFF, 0x24292F, 0x116329, light = true)
        assertEquals(0xFFF0F0F0.toInt(), light.bar)
        assertEquals(0xFFE0E0E0.toInt(), light.key)
        assertEquals(TermChrome.DEFAULT, dark)
    }
}
