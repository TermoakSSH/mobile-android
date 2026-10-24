package com.termoak.app.data

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Reading asciicast v2 recordings for the vault recordings player. */
@RunWith(RobolectricTestRunner::class) // org.json is Android's.
@Config(sdk = [34], application = Application::class)
class AsciicastTest {
    @Test
    fun readsOutputAndResizes() {
        val c = Asciicast.parse(
            """{"version":2,"width":100,"height":30,"timestamp":1}
              |[0.1,"o","$ "]
              |[0.5,"i","ls\r"]
              |[0.4,"o","a b\r\n"]
              |[1.0,"r","120x40"]
              |[1.2,"m","mark"]
              |not json
              |[3.0,"o","bye"]
              |""".trimMargin(),
        )!!
        assertEquals(100, c.width)
        assertEquals(30, c.height)
        assertEquals(4, c.events.size)
        // Times never go back.
        assertEquals(0.5, c.events[1].time, 0.0)
        assertEquals(Asciicast.Event.Resize(1.0, 120, 40), c.events[2])
        assertEquals(3.0, c.duration, 0.0)
        assertNull(Asciicast.parse("""{"version":1}"""))
        assertNull(Asciicast.parse(""))
        assertNull(Asciicast.size("12x"))
        // Long pauses are cut to 2 s; speed divides.
        assertEquals(2000L, Asciicast.waitMs(0.0, 10.0, 1.0))
        assertEquals(250L, Asciicast.waitMs(1.0, 2.0, 4.0))
    }
}
