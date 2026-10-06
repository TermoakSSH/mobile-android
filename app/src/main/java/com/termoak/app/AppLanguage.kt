package com.termoak.app

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import org.xmlpull.v1.XmlPullParser
import java.util.Locale

/**
 * The app language (core/docs/I18N.md): the one chosen in Settings or, without a
 * choice, the system language when there is a translation for it (English
 * otherwise). It is a per-app language: on Android 13+ the system stores and
 * applies it (it also shows up in the system settings); on older versions
 * AppCompat does it for its activities.
 */
object AppLanguage {
    /** A language the app is translated into, named in that language. */
    data class Language(val tag: String, val name: String)

    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

    /** The chosen language tag, or `null` when following the system. */
    fun chosen(): String? =
        AppCompatDelegate.getApplicationLocales().takeUnless { it.isEmpty }?.get(0)?.toLanguageTag()

    /**
     * The languages with a translation: the locale config the build generates
     * from the `values-<lang>` folders (`generateLocaleConfig`), so a new
     * translation shows up here without code changes.
     */
    fun available(context: Context): List<Language> {
        val tags = mutableListOf<String>()
        val parser = context.resources.getXml(R.xml._generated_res_locale_config)
        try {
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "locale") {
                    parser.getAttributeValue(ANDROID_NS, "name")?.let(tags::add)
                }
            }
        } finally {
            parser.close()
        }
        return tags.map { Language(it, nameOf(context, it)) }.sortedBy { it.name.lowercase() }
    }

    /** `language_name` of [tag], in that language. */
    private fun nameOf(context: Context, tag: String): String {
        val config = Configuration(context.resources.configuration)
        config.setLocale(Locale.forLanguageTag(tag))
        return context.createConfigurationContext(config).getString(R.string.language_name)
    }

    /** Switches to [tag] (`null`: the system language). Open screens are recreated in the new language. */
    fun choose(app: TermoakApp, tag: String?) {
        AppCompatDelegate.setApplicationLocales(
            if (tag == null) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag),
        )
        app.accounts.saveLocale(tag ?: systemLanguage(app))
    }

    /** The translation that matches the system languages best (English if none does). */
    fun systemLanguage(context: Context): String {
        val tags = available(context).map { it.tag }
        val system = Resources.getSystem().configuration.locales
        for (i in 0 until system.size()) {
            val locale = system[i]
            tags.firstOrNull { Locale.forLanguageTag(it) == locale }?.let { return it }
            tags.firstOrNull { Locale.forLanguageTag(it).language == locale.language }?.let { return it }
        }
        return "en"
    }
}

/**
 * This context in the app language. Activities already have it; this is for
 * the service and other non-UI contexts on Android 12 and older, where
 * AppCompat only applies the language to its activities.
 */
fun Context.localized(): Context {
    val locales = AppCompatDelegate.getApplicationLocales()
    if (Build.VERSION.SDK_INT >= 33 || locales.isEmpty) return this
    val config = Configuration(resources.configuration)
    config.setLocales(LocaleList.forLanguageTags(locales.toLanguageTags()))
    return createConfigurationContext(config)
}
