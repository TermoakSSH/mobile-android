package com.termoak.app.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * An asciicast v2 recording (`.cast`: a JSON header line, then one
 * `[time, code, data]` event per line), as the desktop's player reads it:
 * output (`"o"`) is replayed into a terminal, `"r"` resizes it
 * (`"COLSxROWS"`); input and markers are skipped.
 */
data class Asciicast(val width: Int, val height: Int, val events: List<Event>) {
    sealed interface Event {
        val time: Double
        data class Output(override val time: Double, val data: String) : Event
        data class Resize(override val time: Double, val cols: Int, val rows: Int) : Event
    }

    /** Length in seconds (the last event's time). */
    val duration: Double get() = events.lastOrNull()?.time ?: 0.0

    companion object {
        /** Reads a recording; `null` when it is not asciicast v2. */
        fun parse(text: String): Asciicast? {
            val lines = text.lineSequence().filter { it.isNotBlank() }.iterator()
            if (!lines.hasNext()) return null
            val header = runCatching { JSONObject(lines.next()) }.getOrNull() ?: return null
            if (header.optInt("version", 0) != 2) return null
            val width = header.optInt("width", 80).coerceIn(1, 1000)
            val height = header.optInt("height", 24).coerceIn(1, 500)
            val events = mutableListOf<Event>()
            var last = 0.0
            for (line in lines) {
                val a = runCatching { JSONArray(line) }.getOrNull() ?: continue
                if (a.length() < 3) continue
                // Times never go back (a broken file would replay out of order).
                val t = a.optDouble(0, last).takeIf { !it.isNaN() }?.coerceAtLeast(last) ?: last
                last = t
                when (a.optString(1)) {
                    "o" -> events += Event.Output(t, a.optString(2))
                    "r" -> size(a.optString(2))?.let { (c, r) -> events += Event.Resize(t, c, r) }
                }
            }
            return Asciicast(width, height, events)
        }

        /** `"120x40"` → (120, 40). */
        fun size(s: String): Pair<Int, Int>? {
            val p = s.trim().split('x')
            if (p.size != 2) return null
            val c = p[0].toIntOrNull()?.takeIf { it in 1..1000 } ?: return null
            val r = p[1].toIntOrNull()?.takeIf { it in 1..500 } ?: return null
            return c to r
        }

        /** The speeds the player offers. */
        val SPEEDS = listOf(1.0, 2.0, 4.0, 8.0)

        /**
         * How long to wait before an event at [time] after the previous one at
         * [previous], at [speed]; long pauses are cut to 2 s (as players do).
         */
        fun waitMs(previous: Double, time: Double, speed: Double): Long =
            (((time - previous).coerceIn(0.0, 2.0) / speed) * 1000).toLong()
    }
}
