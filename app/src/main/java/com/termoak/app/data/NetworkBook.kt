package com.termoak.app.data

import androidx.compose.runtime.mutableStateMapOf
import com.termoak.ffi.AccountHandle
import com.termoak.ffi.HostGroup
import com.termoak.ffi.ItemFilter
import com.termoak.ffi.SshHost
import com.termoak.ffi.TermoakCore

/**
 * What is known of the private networks, shared by every screen (Compose
 * state): which accounts' servers have them (`features.networks`, read
 * again when an account starts and when the Networks screen opens), their
 * state in the server, the network of each host of the lists and the last
 * host test through them ("via Office · 23 ms"; only when asked, never
 * polled). This device never has networks.
 */
object NetworkBook {
    private val supported = mutableStateMapOf<String, Boolean>()
    private val statuses = mutableStateMapOf<String, NetStatus>()
    private val statusAt = mutableMapOf<String, Long>()
    /** Networks whose state couldn't be read (netd down...): "unknown". */
    private val unknown = mutableStateMapOf<String, Boolean>()
    private val tests = mutableStateMapOf<String, HostTest>()
    private val testing = mutableStateMapOf<String, Boolean>()
    /** Host (uid) → its network (own or its groups'). */
    private val hostNetworks = mutableStateMapOf<String, String>()
    /** Network (`account/id`) → name. */
    private val names = mutableStateMapOf<String, String>()

    /** States younger than this are not asked again (unless forced). */
    private const val STATE_FRESH_MS = 15_000L

    /** The account's server has private networks (as last read). Never This device (`null`). */
    fun supported(accountId: String?): Boolean = accountId != null && supported[accountId] == true

    /** Some account has them. */
    fun anySupported(): Boolean = supported.values.any { it }

    /** Reads `/api/v1/info` of the account's server again; keeps the last answer when it fails. */
    suspend fun refreshFeatures(accountId: String, handle: AccountHandle?) {
        val h = handle ?: return
        runCatching { NetworksApi(h).supported() }.onSuccess { supported[accountId] = it }
    }

    /** Signed out: forget it. */
    fun forget(accountId: String) {
        supported.remove(accountId)
    }

    // ----- State in the server -----

    fun status(networkId: String): NetStatus? = statuses[networkId]

    fun isUnknown(networkId: String): Boolean = unknown[networkId] == true

    fun setStatus(networkId: String, status: NetStatus?, now: Long = System.currentTimeMillis()) {
        if (status == null) {
            unknown[networkId] = true
            statuses.remove(networkId)
        } else {
            unknown.remove(networkId)
            statuses[networkId] = status
        }
        statusAt[networkId] = now
    }

    /** Asks the state of a network when it is not fresh (or [force]). */
    suspend fun refreshStatus(api: NetworksApi, networkId: String, force: Boolean = false) {
        val at = statusAt[networkId]
        if (!force && at != null && System.currentTimeMillis() - at < STATE_FRESH_MS) return
        statusAt[networkId] = System.currentTimeMillis()
        setStatus(networkId, api.status(networkId))
    }

    fun state(networkId: String, kind: String): NetState = Networks.state(statuses[networkId], kind)

    // ----- Hosts -----

    /** The groups of [hostOrGroupAccount]/[vault] as the inheritance sees them. */
    fun links(groups: List<HostGroup>, account: String?, vault: String?): List<GroupLink> =
        groups.filter { it.accountId == account && it.vaultId == vault }.map { GroupLink(it.id, it.name, it.parentId, it.settings.networkId) }

    /**
     * Learns the network of [hosts] (their own, or their groups' in the same
     * vault) and the names of the networks in the local stores.
     */
    fun index(core: TermoakCore, hosts: List<SshHost>, groups: List<HostGroup>) {
        val nets = runCatching { core.listNetworks(ItemFilter()) }.getOrDefault(emptyList())
        nets.forEach { names["${it.accountId}/${it.id}"] = it.name }
        for (h in hosts) {
            if (h.accountId == null) {
                hostNetworks.remove(h.uid)
                continue
            }
            val net = Networks.effective(h.settings.networkId, h.groupId, links(groups, h.accountId, h.vaultId))
            if (net == null) hostNetworks.remove(h.uid) else hostNetworks[h.uid] = net
        }
    }

    /** The network id of a host of the lists (`null`: direct, or not known). */
    fun networkOf(host: SshHost): String? = hostNetworks[host.uid]

    /** Name of a network of an account, or `null` when it is not in the stores (missing). */
    fun name(accountId: String?, networkId: String): String? = names["$accountId/$networkId"]

    /**
     * The network a host connects through, from the stores (for the
     * terminal bar): `(id, name)`, the name `null` when it is missing.
     */
    fun resolve(core: TermoakCore, hostId: String, accountId: String?): Pair<String, String?>? {
        if (accountId == null) return null
        val host = runCatching { core.getHost(hostId, accountId) }.getOrNull() ?: return null
        val groups = runCatching { core.listGroups(ItemFilter(accountIds = listOf(accountId), vaultIds = null, includeDevice = false)) }
            .getOrDefault(emptyList())
        val net = Networks.effective(host.settings.networkId, host.groupId, links(groups, accountId, host.vaultId)) ?: return null
        val name = runCatching { core.getNetwork(net, accountId).name }.getOrNull()
        return net to name
    }

    // ----- Host tests -----

    fun test(host: SshHost): HostTest? = tests[host.uid]

    fun isTesting(host: SshHost): Boolean = testing[host.uid] == true

    /** Tests [host] from the server; the result stays for the lists. */
    suspend fun testHost(api: NetworksApi, host: SshHost, trust: Boolean): HostTest {
        testing[host.uid] = true
        try {
            val t = api.testHost(host.id, trust)
            tests[host.uid] = t
            return t
        } finally {
            testing.remove(host.uid)
        }
    }

    /** For tests. */
    internal fun reset() {
        supported.clear(); statuses.clear(); statusAt.clear(); unknown.clear(); tests.clear(); testing.clear()
        hostNetworks.clear(); names.clear()
    }
}
