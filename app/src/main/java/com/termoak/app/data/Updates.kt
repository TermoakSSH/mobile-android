package com.termoak.app.data

import android.content.Context
import android.os.Build
import androidx.core.content.edit
import com.termoak.app.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** The latest Android release published on a server: its version and its APK. */
data class AppUpdate(val version: String, val url: String) {
    /** Newer than the installed app? */
    val isNewer: Boolean get() = isNewerVersion(version, BuildConfig.VERSION_NAME)
}

/**
 * New versions of the app. It is distributed as an APK, not through a store,
 * so it asks the server (`GET /api/v1/downloads`, no authentication) for the
 * latest Android release: on startup at most once a day if enabled in the
 * settings, and when asked from the settings. A newer version shows a notice
 * in the Vault until it is dismissed (once per version).
 */
class Updates(context: Context, private val prefs: Prefs) {
    private val sp = context.getSharedPreferences("updates", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _latest = MutableStateFlow(
        sp.getString(LATEST_VERSION, null)?.let { v -> sp.getString(LATEST_URL, null)?.let { AppUpdate(v, it) } },
    )
    /** The latest release seen in the last check (`null`: none published, or never checked). */
    val latest: StateFlow<AppUpdate?> = _latest

    private val _dismissed = MutableStateFlow(sp.getString(DISMISSED, null))
    /** Version whose notice was closed. */
    val dismissed: StateFlow<String?> = _dismissed

    /** Startup: checks [server] if enabled and the last check is more than a day old. */
    fun checkIfDue(server: String?) {
        if (!prefs.checkUpdates.value) return
        val last = sp.getLong(LAST_CHECK, 0)
        val now = System.currentTimeMillis()
        if (now - last in 0 until DAY_MS) return
        scope.launch { check(server) }
    }

    /**
     * Asks [server] (or [BuildConfig.DEFAULT_SERVER] when signed out) for the
     * latest release. `false` if it couldn't be reached.
     */
    suspend fun check(server: String?): Boolean {
        val base = server ?: BuildConfig.DEFAULT_SERVER
        val result = runCatching { fetch(base) }.getOrElse { return false }
        sp.edit {
            putLong(LAST_CHECK, System.currentTimeMillis())
            putString(LATEST_VERSION, result?.version)
            putString(LATEST_URL, result?.url)
        }
        _latest.value = result
        return true
    }

    /** Closes the notice of [version] (it comes back for a later one). */
    fun dismiss(version: String) {
        _dismissed.value = version
        sp.edit { putString(DISMISSED, version) }
    }

    /**
     * The Android release of `/api/v1/downloads`, `null` if the server has
     * none (no `android` component or no APK, or no downloads at all: `404`).
     * Throws when the server can't be reached.
     */
    private suspend fun fetch(server: String): AppUpdate? = withContext(Dispatchers.IO) {
        val base = URL("${server.trimEnd('/')}/api/v1/downloads")
        val conn = base.openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        conn.setRequestProperty("Accept", "application/json")
        try {
            when (conn.responseCode) {
                200 -> {}
                404 -> return@withContext null
                else -> throw IOException("HTTP ${conn.responseCode}")
            }
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val version = json.optJSONObject("components")?.optJSONObject("android")
                ?.optString("version")?.removePrefix("v")?.takeIf { it.isNotBlank() }
                ?: return@withContext null
            val files = json.optJSONArray("files") ?: return@withContext null
            val apks = (0 until files.length()).mapNotNull { files.optJSONObject(it) }
                .filter { it.optString("name").endsWith(".apk", ignoreCase = true) && it.optString("url").isNotBlank() }
            val android = apks.filter { it.optString("component") == "android" && it.optString("kind") == "app" }
                .ifEmpty { apks.filter { it.optString("component") == "android" || it.optString("os") == "android" } }
            val apk = pickApk(android.map { it.optString("name") }, Build.SUPPORTED_ABIS.firstOrNull())
                ?.let { name -> android.first { it.optString("name") == name } }
                ?: return@withContext null
            // Absolute in practice; resolved against the server just in case.
            AppUpdate(version, URL(base, apk.optString("url")).toString())
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        const val DAY_MS = 24 * 60 * 60 * 1000L
        const val LAST_CHECK = "last_check"
        const val LATEST_VERSION = "latest_version"
        const val LATEST_URL = "latest_url"
        const val DISMISSED = "dismissed"
    }
}

/**
 * The APK of a release for this device, among the file [names]. Releases
 * have one APK per ABI (`Termoak-android-vX.Y.Z-arm64-v8a.apk`,
 * `…-armeabi-v7a.apk`) plus `…-universal.apk` with both: the one of the
 * device's primary ABI ([abi], `Build.SUPPORTED_ABIS[0]`), else the universal
 * one (e.g. x86_64 Chromebooks, which run ARM code), else the first one
 * (older releases had a single `Termoak-android-vX.Y.Z.apk`).
 */
fun pickApk(names: List<String>, abi: String?): String? {
    fun endsWith(name: String, suffix: String) = name.substringBeforeLast('.').endsWith("-$suffix", ignoreCase = true)
    return abi?.let { a -> names.firstOrNull { endsWith(it, a) } }
        ?: names.firstOrNull { endsWith(it, "universal") }
        ?: names.firstOrNull()
}

/**
 * Is [candidate] newer than [installed]? Semantic versions (`X.Y.Z`, an
 * optional leading `v`, an optional `-pre.release` and `+build`). By
 * precedence, a pre-release (`0.3.9-next`) comes before its release
 * (`0.3.9`) and after every older one, so a pre-release build is only told
 * about a release of its own version or later, never an older stable one.
 */
fun isNewerVersion(candidate: String, installed: String): Boolean {
    fun parse(v: String): Pair<List<Int>, List<String>> {
        val clean = v.trim().removePrefix("v").substringBefore('+')
        val core = clean.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val pre = if ('-' in clean) clean.substringAfter('-').split('.') else emptyList()
        return core to pre
    }
    val (ca, pa) = parse(candidate)
    val (cb, pb) = parse(installed)
    for (i in 0 until maxOf(ca.size, cb.size, 3)) {
        val d = ca.getOrElse(i) { 0 }.compareTo(cb.getOrElse(i) { 0 })
        if (d != 0) return d > 0
    }
    // Same X.Y.Z: a release is newer than its pre-releases.
    if (pa.isEmpty() || pb.isEmpty()) return pa.isEmpty() && pb.isNotEmpty()
    for (i in 0 until maxOf(pa.size, pb.size)) {
        val a = pa.getOrNull(i) ?: return false
        val b = pb.getOrNull(i) ?: return true
        val na = a.toIntOrNull()
        val nb = b.toIntOrNull()
        val d = when {
            na != null && nb != null -> na.compareTo(nb)
            na != null -> -1
            nb != null -> 1
            else -> a.compareTo(b)
        }
        if (d != 0) return d > 0
    }
    return false
}
