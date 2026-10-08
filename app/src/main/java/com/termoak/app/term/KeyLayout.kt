package com.termoak.app.term

import com.termoak.ffi.KeyModifiers
import com.termoak.ffi.TerminalKey
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

// The key bar above the keyboard and the groups of the quick access panel,
// as in the iOS app (Model/Keys.swift): the same keys, ids, groups and JSON
// (stored in the preferences), so a layout reads the same on both.

/** Keys the phone keyboard doesn't have. [raw] is the stored name (the iOS app's), [short] the one custom keys use. */
enum class BarSpecial(val raw: String, val short: String) {
    ESC("esc", "esc"), TAB("tab", "tab"), SHIFT_TAB("shiftTab", "shift+tab"), ENTER("intro", "enter"),
    BACKSPACE("retroceso", "bksp"), INS("ins", "ins"), DEL("supr", "del"),
    HOME("inicio", "home"), END("fin", "end"), PAGE_UP("rePag", "pgup"), PAGE_DOWN("avPag", "pgdn"),
    UP("arriba", "up"), DOWN("abajo", "down"), LEFT("izquierda", "left"), RIGHT("derecha", "right"),
    F1("f1", "f1"), F2("f2", "f2"), F3("f3", "f3"), F4("f4", "f4"), F5("f5", "f5"), F6("f6", "f6"),
    F7("f7", "f7"), F8("f8", "f8"), F9("f9", "f9"), F10("f10", "f10"), F11("f11", "f11"), F12("f12", "f12");

    val isArrow: Boolean get() = this == UP || this == DOWN || this == LEFT || this == RIGHT

    companion object {
        fun ofRaw(raw: String): BarSpecial? = entries.firstOrNull { it.raw == raw }
    }
}

/** One part of what a key sends. */
sealed class BarStep {
    data class Text(val text: String) : BarStep()
    data class Special(val key: BarSpecial) : BarStep()
    /** Ctrl + a character (`ctrl+b` = 0x02). */
    data class Ctrl(val char: String) : BarStep()
    data class Alt(val char: String) : BarStep()
}

sealed class BarAction {
    /** Ctrl or Alt: stays pressed until the next key. */
    data class Modifier(val ctrl: Boolean) : BarAction()
    data class Steps(val steps: List<BarStep>) : BarAction()
    /** Pastes the clipboard. */
    data object Paste : BarAction()
}

/** A key of the bar or of a panel group. [icon] is the iOS app's symbol name (drawn with a Material icon). */
data class BarKey(
    val id: String,
    val label: String,
    val icon: String? = null,
    val action: BarAction,
    /** Repeats while held (arrows, delete). */
    val repeats: Boolean = false,
) {
    /** Takes two slots in the panel grid. */
    val wide: Boolean get() = icon == null && label.length > 5

    companion object {
        fun special(key: BarSpecial, label: String, icon: String? = null, repeats: Boolean = false) =
            BarKey("esp.${key.raw}", label, icon, BarAction.Steps(listOf(BarStep.Special(key))), repeats)

        fun text(t: String) = BarKey("txt.$t", t, action = BarAction.Steps(listOf(BarStep.Text(t))))

        /** Ctrl + letter (`^C`). */
        fun control(letter: String) =
            BarKey("ctl.${letter.lowercase()}", "^${letter.uppercase()}", action = BarAction.Steps(listOf(BarStep.Ctrl(letter.lowercase()))))

        /** tmux prefix (Ctrl+B) and a key. */
        fun tmux(t: String) = BarKey("tmux.$t", "ctrl+B, $t", action = BarAction.Steps(listOf(BarStep.Ctrl("b"), BarStep.Text(t))))

        val CTRL = BarKey("mod.ctrl", "ctrl", action = BarAction.Modifier(ctrl = true))
        val ALT = BarKey("mod.alt", "alt", action = BarAction.Modifier(ctrl = false))
        const val PASTE_ID = "pegar"
        val PASTE = BarKey(PASTE_ID, "Paste", "doc.on.clipboard", BarAction.Paste)

        /** A custom key made in the editor. */
        fun custom(label: String, steps: List<BarStep>) =
            BarKey("custom.${UUID.randomUUID()}", label.trim(), action = BarAction.Steps(steps))
    }
}

data class KeyGroup(
    val id: String,
    /** Name chosen by the user; empty for a built-in group that keeps its default (translated) name. */
    val name: String,
    val keys: List<BarKey>,
    val visible: Boolean = true,
) {
    /** The user renamed it (otherwise the app shows the translated default name). */
    val renamed: Boolean get() = name.isNotEmpty() && name != LegacyNames[id]

    companion object {
        /** The group of the user's own keys. */
        const val CUSTOM = "propias"
        val BuiltIn = listOf("basicas", "flechas", "tmux", "simbolos", "control", "funciones", CUSTOM)

        /** Names the built-in groups were saved with by early iOS versions: they still count as not renamed. */
        private val LegacyNames = mapOf(
            "basicas" to "Básicas", "flechas" to "Flechas y saltos", "tmux" to "tmux", "simbolos" to "Símbolos",
            "control" to "Control", "funciones" to "Funciones", CUSTOM to "Mis teclas",
        )
    }
}

/** What can be customized: the bar above the keyboard and the groups of the quick access panel. */
data class KeyboardLayout(val bar: List<BarKey>, val groups: List<KeyGroup>) {
    /** Every key once (to add them to the bar). */
    val all: List<BarKey> get() = (groups.flatMap { it.keys } + bar).distinctBy { it.id }

    fun toJson(): String = JSONObject().apply {
        put("barra", JSONArray(bar.map(::keyJson)))
        put("grupos", JSONArray(groups.map { g ->
            JSONObject().put("id", g.id).put("nombre", g.name).put("visible", g.visible)
                .put("teclas", JSONArray(g.keys.map(::keyJson)))
        }))
    }.toString()

    companion object {
        private val ARROWS = listOf(
            BarKey.special(BarSpecial.LEFT, "←", "arrow.left", repeats = true),
            BarKey.special(BarSpecial.RIGHT, "→", "arrow.right", repeats = true),
            BarKey.special(BarSpecial.UP, "↑", "arrow.up", repeats = true),
            BarKey.special(BarSpecial.DOWN, "↓", "arrow.down", repeats = true),
        )
        private val ENTER = BarKey.special(BarSpecial.ENTER, "enter", "return")
        private val ESC = BarKey.special(BarSpecial.ESC, "esc")
        private val TAB = BarKey.special(BarSpecial.TAB, "tab")

        /** The default layout (the iOS app's). */
        val STANDARD = KeyboardLayout(
            bar = listOf(BarKey.PASTE, ENTER, ESC, BarKey.CTRL, BarKey.ALT, TAB) + ARROWS +
                listOf(BarKey.control("c"), BarKey.text("|"), BarKey.text("/"), BarKey.text("-"), BarKey.text("~")),
            groups = listOf(
                KeyGroup(
                    "basicas", "",
                    listOf(
                        BarKey.PASTE, ENTER, ESC, TAB, BarKey.CTRL, BarKey.ALT, BarKey.special(BarSpecial.SHIFT_TAB, "shift+tab"),
                        BarKey.special(BarSpecial.BACKSPACE, "bksp", "delete.left", repeats = true),
                        BarKey.special(BarSpecial.INS, "ins"), BarKey.special(BarSpecial.DEL, "del"),
                    ),
                ),
                KeyGroup(
                    "flechas", "",
                    ARROWS + listOf(
                        BarKey.special(BarSpecial.HOME, "home"), BarKey.special(BarSpecial.PAGE_UP, "pgUp"),
                        BarKey.special(BarSpecial.PAGE_DOWN, "pgDn"), BarKey.special(BarSpecial.END, "end"),
                    ),
                ),
                KeyGroup("tmux", "", listOf("c", "n", "p", "d", "%", "\"", "o", "x", "z", "[").map(BarKey::tmux)),
                KeyGroup(
                    "simbolos", "",
                    listOf(
                        "|", "\\", "/", "?", "~", "@", "$", "#", ":", ";", "!", "%", "&", "*", "=", "`",
                        "'", "\"", "-", "_", "+", "^", "<", ">", "(", ")", "{", "}", "[", "]",
                    ).map(BarKey::text),
                ),
                KeyGroup(
                    "control", "",
                    listOf("c", "d", "z", "l", "r", "a", "e", "u", "w", "k", "s", "q").map(BarKey::control) +
                        BarKey("ctl._", "^_", action = BarAction.Steps(listOf(BarStep.Ctrl("_")))),
                ),
                KeyGroup(
                    "funciones", "",
                    listOf(
                        BarSpecial.F1, BarSpecial.F2, BarSpecial.F3, BarSpecial.F4, BarSpecial.F5, BarSpecial.F6,
                        BarSpecial.F7, BarSpecial.F8, BarSpecial.F9, BarSpecial.F10, BarSpecial.F11, BarSpecial.F12,
                    ).map { BarKey.special(it, it.raw.uppercase()) },
                ),
                KeyGroup(KeyGroup.CUSTOM, "", emptyList()),
            ),
        )

        /** A stored layout, or the default one if it can't be read. */
        fun fromJson(json: String?): KeyboardLayout {
            if (json.isNullOrBlank()) return STANDARD
            return runCatching {
                val o = JSONObject(json)
                val bar = o.getJSONArray("barra").let { a -> (0 until a.length()).map { keyOf(a.getJSONObject(it)) } }
                val groups = o.getJSONArray("grupos").let { a ->
                    (0 until a.length()).map {
                        val g = a.getJSONObject(it)
                        val keys = g.getJSONArray("teclas")
                        KeyGroup(
                            g.getString("id"), g.optString("nombre"), (0 until keys.length()).map { k -> keyOf(keys.getJSONObject(k)) },
                            g.optBoolean("visible", true),
                        )
                    }
                }
                KeyboardLayout(bar, groups)
            }.getOrDefault(STANDARD)
        }

        private fun keyJson(k: BarKey): JSONObject = JSONObject().apply {
            put("id", k.id)
            put("etiqueta", k.label)
            k.icon?.let { put("icono", it) }
            put("accion", actionJson(k.action))
            put("repetir", k.repeats)
        }

        // Swift's Codable shape for enums with associated values: {"case": {"_0": value}}.
        private fun actionJson(a: BarAction): JSONObject = when (a) {
            is BarAction.Modifier -> JSONObject().put("modificador", JSONObject().put("_0", if (a.ctrl) "ctrl" else "alt"))
            BarAction.Paste -> JSONObject().put("pegar", JSONObject())
            is BarAction.Steps -> JSONObject().put("pasos", JSONObject().put("_0", JSONArray(a.steps.map(::stepJson))))
        }

        private fun stepJson(s: BarStep): JSONObject = when (s) {
            is BarStep.Text -> JSONObject().put("texto", JSONObject().put("_0", s.text))
            is BarStep.Special -> JSONObject().put("especial", JSONObject().put("_0", s.key.raw))
            is BarStep.Ctrl -> JSONObject().put("ctrl", JSONObject().put("_0", s.char))
            is BarStep.Alt -> JSONObject().put("alt", JSONObject().put("_0", s.char))
        }

        private fun keyOf(o: JSONObject): BarKey = BarKey(
            id = o.getString("id"),
            label = o.getString("etiqueta"),
            icon = o.optString("icono").takeIf { it.isNotEmpty() && !o.isNull("icono") },
            action = actionOf(o.getJSONObject("accion")),
            repeats = o.optBoolean("repetir", false),
        )

        private fun actionOf(o: JSONObject): BarAction = when {
            o.has("modificador") -> BarAction.Modifier(ctrl = o.getJSONObject("modificador").getString("_0") == "ctrl")
            o.has("pegar") -> BarAction.Paste
            o.has("pasos") -> o.getJSONObject("pasos").getJSONArray("_0").let { a ->
                BarAction.Steps((0 until a.length()).map { stepOf(a.getJSONObject(it)) })
            }
            else -> throw IllegalArgumentException("unknown key action")
        }

        private fun stepOf(o: JSONObject): BarStep = when {
            o.has("texto") -> BarStep.Text(o.getJSONObject("texto").getString("_0"))
            o.has("especial") -> BarStep.Special(
                BarSpecial.ofRaw(o.getJSONObject("especial").getString("_0")) ?: throw IllegalArgumentException("unknown key"),
            )
            o.has("ctrl") -> BarStep.Ctrl(o.getJSONObject("ctrl").getString("_0"))
            o.has("alt") -> BarStep.Alt(o.getJSONObject("alt").getString("_0"))
            else -> throw IllegalArgumentException("unknown key step")
        }
    }
}

/**
 * What a key's steps type, as terminal input (encoded by each terminal with
 * its own modes, and broadcast like typing). The key bar's sticky [ctrl] and
 * [alt] apply to the first step only, like on iOS.
 */
fun keyInputs(steps: List<BarStep>, ctrl: Boolean, alt: Boolean): List<TermInput> {
    val none = KeyModifiers(shift = false, alt = false, ctrl = false)
    val out = mutableListOf<TermInput>()
    steps.forEachIndexed { i, step ->
        val c = i == 0 && ctrl
        val a = i == 0 && alt
        when (step) {
            is BarStep.Special -> out += TermInput.Key(
                step.key.terminalKey(),
                KeyModifiers(shift = step.key == BarSpecial.SHIFT_TAB, alt = a, ctrl = c),
            )
            is BarStep.Ctrl -> out += TermInput.Text(step.char, KeyModifiers(shift = false, alt = a, ctrl = true))
            is BarStep.Alt -> out += TermInput.Text(step.char, KeyModifiers(shift = false, alt = true, ctrl = c))
            is BarStep.Text -> {
                if (step.text.isEmpty()) return@forEachIndexed
                if (c || a) {
                    val first = step.text.offsetByCodePoints(0, 1)
                    out += TermInput.Text(step.text.substring(0, first), KeyModifiers(shift = false, alt = a, ctrl = c))
                    if (first < step.text.length) out += TermInput.Text(step.text.substring(first), none)
                } else {
                    out += TermInput.Text(step.text, none)
                }
            }
        }
    }
    return out
}

/** The engine's key for a bar key. */
fun BarSpecial.terminalKey(): TerminalKey = when (this) {
    BarSpecial.ESC -> TerminalKey.Escape
    BarSpecial.TAB, BarSpecial.SHIFT_TAB -> TerminalKey.Tab
    BarSpecial.ENTER -> TerminalKey.Enter
    BarSpecial.BACKSPACE -> TerminalKey.Backspace
    BarSpecial.INS -> TerminalKey.Insert
    BarSpecial.DEL -> TerminalKey.Delete
    BarSpecial.HOME -> TerminalKey.Home
    BarSpecial.END -> TerminalKey.End
    BarSpecial.PAGE_UP -> TerminalKey.PageUp
    BarSpecial.PAGE_DOWN -> TerminalKey.PageDown
    BarSpecial.UP -> TerminalKey.Up
    BarSpecial.DOWN -> TerminalKey.Down
    BarSpecial.LEFT -> TerminalKey.Left
    BarSpecial.RIGHT -> TerminalKey.Right
    else -> TerminalKey.Function(raw.removePrefix("f").toUByte())
}

/** Custom keys: what is typed in the editor's "Combination" field, turned into steps. */
object KeyCombination {
    /** The steps, or the part that isn't understood. */
    sealed class Result {
        data class Ok(val steps: List<BarStep>) : Result()
        data class Error(val part: String) : Result()
    }

    /** Parts separated by spaces or commas such as `ctrl+b`, `alt+x`, `^C`, `esc`, `enter`, `up`, `f5`... */
    fun parse(text: String): Result {
        val steps = mutableListOf<BarStep>()
        for (part in text.replace(',', ' ').split(' ').filter { it.isNotEmpty() }) {
            val p = part.lowercase()
            val special = BarSpecial.entries.firstOrNull { it.short == p || it.raw.lowercase() == p }
            steps += when {
                special != null -> BarStep.Special(special)
                p in setOf("return", "intro", "cr") -> BarStep.Special(BarSpecial.ENTER)
                p == "escape" -> BarStep.Special(BarSpecial.ESC)
                p.startsWith("ctrl+") && p.length == 6 -> BarStep.Ctrl(p.last().toString())
                p.startsWith("alt+") && p.length == 5 -> BarStep.Alt(part.last().toString())
                p.startsWith("^") && p.length == 2 -> BarStep.Ctrl(p.last().toString())
                else -> return Result.Error(part)
            }
        }
        return Result.Ok(steps)
    }

    /** Readable text of a key's steps (`ctrl+b, “c”`), for the editor's lists. */
    fun describe(steps: List<BarStep>): String = steps.joinToString(", ") { s ->
        when (s) {
            is BarStep.Special -> s.key.short
            is BarStep.Ctrl -> "ctrl+${s.char}"
            is BarStep.Alt -> "alt+${s.char}"
            is BarStep.Text -> "“${s.text.replace("\r", "⏎")}”"
        }
    }
}
