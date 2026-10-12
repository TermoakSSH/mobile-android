package com.termoak.app.store

import com.termoak.ffi.TerminalColors
import com.termoak.ffi.TerminalThemeInfo
import java.io.File

/**
 * The engine's terminal themes, read from its source (core's
 * crates/termoak-core/src/themes.rs): the same ids, names and palettes.
 */
object Themes {
    const val DEFAULT = "termoak"

    private fun source(): String {
        val candidates = listOf("../core/crates/termoak-core/src/themes.rs", "core/crates/termoak-core/src/themes.rs")
        return candidates.map(::File).first { it.isFile }.readText()
    }

    val all: List<TerminalThemeInfo> by lazy {
        val block = Regex(
            """TerminalTheme \{\s*id: "([^"]+)",\s*name: "([^"]+)",\s*background: 0x([0-9A-Fa-f]{6}),\s*""" +
                """foreground: 0x([0-9A-Fa-f]{6}),\s*cursor: 0x([0-9A-Fa-f]{6}),\s*ansi: \[([^\]]*)\]""",
        )
        block.findAll(source()).map { m ->
            val (id, name, bg, fg, cursor, ansi) = m.destructured
            val palette = Regex("0x([0-9A-Fa-f]{6})").findAll(ansi).map { it.groupValues[1].toUInt(16) }.toList()
            val background = bg.toUInt(16)
            val cur = cursor.toUInt(16)
            val light = luminance(background) > 0.5
            TerminalThemeInfo(
                id, name, light,
                TerminalColors(background, fg.toUInt(16), cur, blend(background, cur, if (light) 0.25 else 0.38), palette),
            )
        }.toList().also { check(it.size >= 10) { "themes.rs: only ${it.size} themes read" } }
    }

    fun colors(id: String): TerminalColors = (all.firstOrNull { it.id == id } ?: all.first()).colors

    private fun channel(c: UInt, shift: Int) = ((c shr shift) and 0xFFu).toInt()

    private fun luminance(c: UInt): Double {
        fun lin(v: Int): Double {
            val s = v / 255.0
            return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * lin(channel(c, 16)) + 0.7152 * lin(channel(c, 8)) + 0.0722 * lin(channel(c, 0))
    }

    private fun blend(a: UInt, b: UInt, t: Double): UInt {
        fun mix(s: Int) = Math.round(channel(a, s) + (channel(b, s) - channel(a, s)) * t).toInt().coerceIn(0, 255)
        return ((mix(16) shl 16) or (mix(8) shl 8) or mix(0)).toUInt()
    }
}
