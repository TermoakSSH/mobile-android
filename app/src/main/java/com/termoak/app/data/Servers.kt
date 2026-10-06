package com.termoak.app.data

import com.termoak.app.BuildConfig
import com.termoak.ffi.officialServerUrl

/**
 * The official server of this build: the engine's `officialServerUrl()`
 * (`https://termoak.com`, or `TERMOAK_OFFICIAL_SERVER` when the engine was
 * built, e.g. `https://next.termoak.com`).
 */
val officialServer: String by lazy { runCatching { officialServerUrl() }.getOrDefault(BuildConfig.DEFAULT_SERVER) }

/** A server URL without its scheme, to show it ("termoak.com"). */
fun serverHost(url: String): String = url.substringAfter("://").trimEnd('/')
