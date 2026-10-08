package com.termoak.app.data

/**
 * The app's actions in the command palette (Ctrl+K, Ctrl+Shift+P: quick
 * connect extended like the desktop's palette): which ones apply right now,
 * and the keys the palette remembers them by. Pure logic (JVM tests).
 */
enum class PaletteCommand(val id: String) {
    HOME("home"), NEXT_TAB("nextTab"), CLOSE_TAB("closeTab"), ADD_TO_SPLIT("addToSplit"), FOCUS_MODE("focusMode"),
    BROADCAST("broadcast"), ZOOM_IN("zoomIn"), ZOOM_OUT("zoomOut"), ZOOM_RESET("zoomReset"), TOGGLE_THEME("toggleTheme");

    /** The palette's stable key (`cmd:<name>`, like the desktop's and iOS's). */
    val key: String get() = "cmd:$id"

    /** What is open and shown now. */
    data class State(val tabs: Int = 0, val terminalShown: Boolean = false, val splitAvailable: Boolean = false, val splitActive: Boolean = false)

    /** English words it is also found by (whatever the app's language). */
    val keywords: List<String>
        get() = when (this) {
            HOME -> listOf("home", "hosts")
            NEXT_TAB -> listOf("next", "tab")
            CLOSE_TAB -> listOf("close", "tab")
            ADD_TO_SPLIT -> listOf("split", "pane")
            FOCUS_MODE -> listOf("focus", "split")
            BROADCAST -> listOf("broadcast", "all panes")
            ZOOM_IN -> listOf("zoom", "bigger", "font")
            ZOOM_OUT -> listOf("zoom", "smaller", "font")
            ZOOM_RESET -> listOf("zoom", "reset", "font")
            TOGGLE_THEME -> listOf("theme", "appearance", "dark", "light")
        }

    companion object {
        /** The actions that do something in [s], in a fixed order. */
        fun available(s: State): List<PaletteCommand> = entries.filter { c ->
            when (c) {
                HOME, CLOSE_TAB -> s.terminalShown
                NEXT_TAB -> s.tabs > 1
                ADD_TO_SPLIT -> s.splitAvailable && s.tabs >= 2
                FOCUS_MODE, BROADCAST -> s.splitActive
                ZOOM_IN, ZOOM_OUT, ZOOM_RESET -> s.tabs > 0
                TOGGLE_THEME -> true
            }
        }
    }
}

object PaletteKey {
    fun tab(id: String) = "tab:$id"
    fun host(uid: String) = "host:$uid"
    fun session(key: String) = "session:$key"
    fun snippet(uid: String) = "snippet:$uid"
    fun go(route: String) = "go:$route"

    /** Tabs are not remembered among the recent entries (they come and go). */
    fun remembered(key: String): Boolean = !key.startsWith("tab:")
}
