package com.termoak.app.term

import android.app.Application
import com.termoak.ffi.KeyModifiers
import com.termoak.ffi.TerminalKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The key bar's layout (the iOS app's keys, groups and JSON), custom keys and what keys type. */
@RunWith(RobolectricTestRunner::class) // org.json is the Android one.
@Config(sdk = [34], application = Application::class)
class KeyLayoutTest {
    private val none = KeyModifiers(shift = false, alt = false, ctrl = false)

    @Test
    fun theDefaultBarIsTheIosOne() {
        val bar = KeyboardLayout.STANDARD.bar.map { it.id }
        assertEquals(
            listOf(
                "pegar", "esp.intro", "esp.esc", "mod.ctrl", "mod.alt", "esp.tab", "esp.izquierda", "esp.derecha", "esp.arriba",
                "esp.abajo", "ctl.c", "txt.|", "txt./", "txt.-", "txt.~",
            ),
            bar,
        )
        assertEquals(KeyGroup.BuiltIn, KeyboardLayout.STANDARD.groups.map { it.id })
        // Enter, Paste, ^C and backspace are there; arrows and delete repeat.
        assertTrue(KeyboardLayout.STANDARD.all.single { it.id == "esp.retroceso" }.repeats)
        assertTrue(KeyboardLayout.STANDARD.bar.filter { it.id.startsWith("esp.") && it.label in setOf("←", "→", "↑", "↓") }.all { it.repeats })
    }

    @Test
    fun theLayoutGoesThroughJson() {
        val custom = BarKey.custom(" Deploy ", listOf(BarStep.Ctrl("b"), BarStep.Text("c"), BarStep.Special(BarSpecial.ENTER)))
        val layout = KeyboardLayout.STANDARD.let { l ->
            l.copy(
                bar = l.bar.drop(2) + custom,
                groups = l.groups.map { if (it.id == KeyGroup.CUSTOM) it.copy(keys = listOf(custom), name = "Mine") else it.copy(visible = it.id != "tmux") },
            )
        }
        val back = KeyboardLayout.fromJson(layout.toJson())
        assertEquals(layout, back)
        assertEquals("Deploy", back.bar.last().label)
    }

    @Test
    fun readsTheIosJson() {
        // As Swift's JSONEncoder writes it (enums with associated values as {"case": {"_0": ...}}).
        val json = """{"barra":[{"id":"pegar","etiqueta":"Paste","icono":"doc.on.clipboard","accion":{"pegar":{}},"repetir":false},
            {"id":"mod.ctrl","etiqueta":"ctrl","accion":{"modificador":{"_0":"ctrl"}},"repetir":false},
            {"id":"esp.izquierda","etiqueta":"←","icono":"arrow.left","accion":{"pasos":{"_0":[{"especial":{"_0":"izquierda"}}]}},"repetir":true}],
            "grupos":[{"id":"tmux","nombre":"","teclas":[{"id":"tmux.c","etiqueta":"ctrl+B, c",
            "accion":{"pasos":{"_0":[{"ctrl":{"_0":"b"}},{"texto":{"_0":"c"}}]}},"repetir":false}],"visible":false}]}"""
        val l = KeyboardLayout.fromJson(json)
        assertEquals(BarAction.Paste, l.bar[0].action)
        assertEquals(BarAction.Modifier(ctrl = true), l.bar[1].action)
        assertEquals(BarAction.Steps(listOf(BarStep.Special(BarSpecial.LEFT))), l.bar[2].action)
        assertTrue(l.bar[2].repeats)
        assertEquals(listOf(BarStep.Ctrl("b"), BarStep.Text("c")), (l.groups[0].keys[0].action as BarAction.Steps).steps)
        assertEquals(false, l.groups[0].visible)
    }

    @Test
    fun aBrokenLayoutIsTheDefault() {
        assertEquals(KeyboardLayout.STANDARD, KeyboardLayout.fromJson(null))
        assertEquals(KeyboardLayout.STANDARD, KeyboardLayout.fromJson("{"))
        assertEquals(KeyboardLayout.STANDARD, KeyboardLayout.fromJson("""{"barra":[{"id":"x"}],"grupos":[]}"""))
    }

    @Test
    fun renamedGroups() {
        assertEquals(false, KeyGroup("basicas", "", emptyList()).renamed)
        // The names early iOS versions saved count as the default.
        assertEquals(false, KeyGroup("flechas", "Flechas y saltos", emptyList()).renamed)
        assertEquals(true, KeyGroup("flechas", "Moving", emptyList()).renamed)
    }

    @Test
    fun combinations() {
        assertEquals(
            KeyCombination.Result.Ok(listOf(BarStep.Ctrl("b"), BarStep.Special(BarSpecial.ESC), BarStep.Ctrl("c"), BarStep.Alt("X"))),
            KeyCombination.parse("ctrl+B, escape ^c alt+X"),
        )
        assertEquals(
            KeyCombination.Result.Ok(listOf(BarStep.Special(BarSpecial.ENTER), BarStep.Special(BarSpecial.F5), BarStep.Special(BarSpecial.PAGE_DOWN))),
            KeyCombination.parse("return f5 pgdn"),
        )
        assertEquals(KeyCombination.Result.Ok(emptyList()), KeyCombination.parse("  "))
        assertEquals(KeyCombination.Result.Error("ctrl+xy"), KeyCombination.parse("esc ctrl+xy"))
        assertEquals("ctrl+b, “c”, enter", KeyCombination.describe(listOf(BarStep.Ctrl("b"), BarStep.Text("c"), BarStep.Special(BarSpecial.ENTER))))
        assertEquals("“ls⏎”", KeyCombination.describe(listOf(BarStep.Text("ls\r"))))
    }

    @Test
    fun whatKeysType() {
        // The sticky Ctrl and Alt apply to the first step only.
        assertEquals(
            listOf(TermInput.Text("b", KeyModifiers(shift = false, alt = false, ctrl = true)), TermInput.Text("c", none)),
            keyInputs(listOf(BarStep.Ctrl("b"), BarStep.Text("c")), ctrl = false, alt = false),
        )
        assertEquals(
            listOf(TermInput.Text("x", KeyModifiers(shift = false, alt = true, ctrl = true)), TermInput.Text("yz", none)),
            keyInputs(listOf(BarStep.Text("xyz")), ctrl = true, alt = true),
        )
        assertEquals(
            listOf(TermInput.Key(TerminalKey.Tab, KeyModifiers(shift = true, alt = false, ctrl = false))),
            keyInputs(listOf(BarStep.Special(BarSpecial.SHIFT_TAB)), ctrl = false, alt = false),
        )
        assertEquals(
            listOf(TermInput.Key(TerminalKey.Up, KeyModifiers(shift = false, alt = false, ctrl = true)), TermInput.Key(TerminalKey.Function(12u), none)),
            keyInputs(listOf(BarStep.Special(BarSpecial.UP), BarStep.Special(BarSpecial.F12)), ctrl = true, alt = false),
        )
        assertEquals(listOf(TermInput.Text("q", KeyModifiers(shift = false, alt = true, ctrl = false))), keyInputs(listOf(BarStep.Alt("q")), false, false))
    }
}
