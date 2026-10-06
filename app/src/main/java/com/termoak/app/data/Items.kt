package com.termoak.app.data

import com.termoak.ffi.HostGroup
import com.termoak.ffi.ItemAccess
import com.termoak.ffi.KnownHost
import com.termoak.ffi.PortForward
import com.termoak.ffi.Snippet
import com.termoak.ffi.SshHost
import com.termoak.ffi.SshIdentity
import com.termoak.ffi.SshKey

/**
 * Key of an item in lists: its account and id. In "All accounts" the same
 * item can appear twice (seen through two accounts of the same server).
 */
fun uidOf(accountId: String?, id: String): String = "${accountId ?: DEVICE_VAULT}/$id"

val SshHost.uid get() = uidOf(accountId, id)
val HostGroup.uid get() = uidOf(accountId, id)
val SshKey.uid get() = uidOf(accountId, id)
val SshIdentity.uid get() = uidOf(accountId, id)
val Snippet.uid get() = uidOf(accountId, id)
val PortForward.uid get() = uidOf(accountId, id)
val KnownHost.uid get() = uidOf(accountId, id)

/** Use-only item: it can be used (connect, run) but its secrets are never shown and it can't be changed. */
fun ItemAccess?.useOnly(): Boolean = this == ItemAccess.USE_ONLY
fun ItemAccess?.canWrite(): Boolean = this != ItemAccess.USE_ONLY && this != ItemAccess.UNKNOWN

/** Where an item lives: This device (`account == null`) or a vault of an account. */
data class Place(val account: String?, val vault: String?) {
    val device get() = account == null

    /** Whether an item at [account]/[vault] can be referenced from here (same vault, or This device). */
    fun reaches(account: String?, vault: String?): Boolean =
        account == null || (account == this.account && (vault == this.vault || this.vault == null || vault == null))

    /** `<account>/<vault>` or [DEVICE_VAULT], for the preferences. */
    fun key(): String = if (account == null) DEVICE_VAULT else "$account/${vault.orEmpty()}"

    companion object {
        val DEVICE = Place(null, null)
        fun parse(key: String?): Place? = when {
            key == null -> null
            key == DEVICE_VAULT -> DEVICE
            else -> key.split('/', limit = 2).let { Place(it[0], it.getOrNull(1)?.ifEmpty { null }) }
        }
    }
}

val SshHost.place get() = Place(accountId, vaultId)
