package com.termoak.app.term

import android.content.Context
import android.graphics.Typeface

/**
 * Fonts of the terminal (Settings → Terminal → Font), like the iOS app's:
 * the system's monospace one and the three open ones it bundles (SIL Open
 * Font License, with their licenses in `assets/fonts`). The iOS app's Apple
 * fonts (SF Mono, Menlo, Courier New) can't be shipped here: the system one
 * takes their place.
 */
enum class TerminalFont(val id: String, val displayName: String, private val asset: String?) {
    SYSTEM("system", "", null),
    JETBRAINS_MONO("jetbrains-mono", "JetBrains Mono", "fonts/JetBrainsMono.ttf"),
    SOURCE_CODE_PRO("source-code-pro", "Source Code Pro", "fonts/SourceCodePro.ttf"),
    FIRA_CODE("fira-code", "Fira Code", "fonts/FiraCode.ttf"),
    ;

    /** The typeface (the bundled ones are variable fonts: at their Regular weight). */
    fun typeface(context: Context): Typeface {
        val path = asset ?: return Typeface.MONOSPACE
        synchronized(cache) {
            cache[this]?.let { return it }
            val tf = runCatching {
                Typeface.Builder(context.assets, path).setFontVariationSettings("'wght' 400").build()
            }.getOrNull() ?: Typeface.MONOSPACE
            cache[this] = tf
            return tf
        }
    }

    companion object {
        private val cache = mutableMapOf<TerminalFont, Typeface>()

        /** The font with [id]; the iOS app's Apple fonts and unknown ids are the system one. */
        fun of(id: String?): TerminalFont = entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}
