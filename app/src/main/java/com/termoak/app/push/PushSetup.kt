package com.termoak.app.push

/**
 * The plain rules of the push setup (JVM-tested): which UnifiedPush
 * distributor to use and what each account's state is.
 */
object PushSetup {
    /**
     * The distributor to use: the one the user picked while it is still
     * installed; with none picked and exactly one installed (ntfy, NextPush...),
     * that one, unless the user turned notifications off.
     */
    fun distributor(installed: List<String>, saved: String?, turnedOff: Boolean): String? = when {
        saved != null && saved in installed -> saved
        turnedOff -> null
        installed.size == 1 -> installed.first()
        else -> null
    }

    /** The external distributors (the connector also lists this app when it embeds one). */
    fun external(all: List<String>, ownPackage: String): List<String> = all.filter { it != ownPackage }.distinct()

    /** The text the distributor may show for an account's registration. */
    fun label(email: String, server: String): String = "Termoak · $email · ${server.removePrefix("https://").removePrefix("http://").trimEnd('/')}"

    /** A VAPID key the connector accepts (uncompressed P-256, base64url: 87 characters), else `null`. */
    fun vapid(key: String?): String? = key?.trimEnd('=')?.takeIf { Regex("^[A-Za-z0-9_-]{87}$").matches(it) }
}

/** One account's notifications on this phone. */
sealed interface AccountPush {
    /** No distributor chosen (or notifications off): the app only notifies while it runs. */
    data object Off : AccountPush

    /** Asked the distributor for an endpoint; waiting for it. */
    data object Registering : AccountPush

    /** The server has this phone's endpoint. */
    data object Registered : AccountPush

    /** The server is older than 0.7 or has Web Push turned off. */
    data object ServerCannot : AccountPush

    /** Something failed (no network, the distributor needs attention...). */
    data class Failed(val reason: String) : AccountPush
}
