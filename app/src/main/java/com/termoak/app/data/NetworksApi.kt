package com.termoak.app.data

import com.termoak.ffi.AccountHandle
import com.termoak.ffi.HttpMethod
import kotlinx.coroutines.delay
import org.json.JSONObject

/**
 * The REST calls of the networks screens (the server's docs/API.md,
 * "Private networks"), through an account's handle (the engine's generic
 * API, like the web and the desktop): the server checks the plan's
 * `max_networks` and fills in the WireGuard public key at once, and the
 * changes reach the local stores by sync. [request] is the handle's
 * `apiRequest` (tests give a stand-in).
 */
class NetworksApi(
    private val request: suspend (HttpMethod, String, String?) -> String,
    /** The wait between the polls of [test] (tests give a stand-in). */
    private val pause: suspend (Long) -> Unit = { delay(it) },
    /** The engine's host test with this device's credentials (`test_host_unlocked`, core v0.7.0-next.13). */
    private val unlockedTest: (suspend (String, Boolean) -> String)? = null,
) {
    constructor(handle: AccountHandle) : this(
        { method, path, body -> handle.apiRequest(method, path, body) },
        unlockedTest = { id, trust -> handle.testHostUnlocked(id, trust) },
    )

    /** `features.networks` of the account's server, read again (it can change while the app runs). */
    suspend fun supported(): Boolean = Networks.supported(request(HttpMethod.GET, "$BASE/info", null))

    /** The kinds of network the account's server joins (`features.network_kinds`; empty without networks). */
    suspend fun kinds(): List<String> = Networks.kinds(request(HttpMethod.GET, "$BASE/info", null))

    /** The networks of every vault you can see. */
    suspend fun list(): List<NetworkItem> = Networks.networks(request(HttpMethod.GET, "$BASE/networks", null))

    suspend fun create(body: String): NetworkItem = Networks.network(JSONObject(request(HttpMethod.POST, "$BASE/networks", body)))

    suspend fun update(id: String, body: String): NetworkItem =
        Networks.network(JSONObject(request(HttpMethod.PUT, "$BASE/networks/$id", body)))

    suspend fun delete(id: String) {
        request(HttpMethod.DELETE, "$BASE/networks/$id", null)
    }

    /** Its state in the server, or `null` when it can't be read (netd down...): "unknown", not an error. */
    suspend fun status(id: String): NetStatus? =
        runCatching { Networks.status(request(HttpMethod.GET, "$BASE/networks/$id/status", null)) }.getOrNull()

    suspend fun up(id: String): NetStatus =
        Networks.status(request(HttpMethod.POST, "$BASE/networks/$id/up", "{}")) ?: NetStatus("unknown")

    suspend fun down(id: String) {
        request(HttpMethod.POST, "$BASE/networks/$id/down", "{}")
    }

    /**
     * Test: brings it up, then asks its state about once a second for a few
     * seconds until the WireGuard handshake appears (wireguard-go only
     * shakes hands with traffic or a keep-alive). A Tailscale node: while it
     * is starting, about 30 s (`up` already waited 10 s). [onStatus] sees
     * each one.
     */
    suspend fun test(id: String, kind: String, onStatus: (NetStatus) -> Unit = {}): NetStatus {
        var st = up(id)
        onStatus(st)
        var polls = 0
        val tailnet = kind == Networks.TAILSCALE
        val max = if (tailnet) Networks.TS_TEST_POLLS else Networks.TEST_POLLS
        fun pending(s: NetStatus) = Networks.state(s, kind).let { it == NetState.STARTING || (!tailnet && it == NetState.WAITING) }
        while (polls < max && pending(st)) {
            pause(1000)
            st = status(id) ?: st
            onStatus(st)
            polls++
        }
        return st
    }

    /**
     * A login or an approval of a Tailscale node: its state every 2 s until
     * it is no longer waiting (up, an error, or `down` when the network went
     * idle meanwhile: Test brings it back up already logged in), 5 minutes
     * at most. Returns the last state (`null`: never read). The caller
     * cancels it when the screen closes.
     */
    suspend fun watchLogin(id: String, onStatus: (NetStatus) -> Unit = {}): NetStatus? {
        var last: NetStatus? = null
        var waited = 0L
        while (waited < Networks.LOGIN_WAIT_MS) {
            pause(Networks.LOGIN_POLL_MS)
            waited += Networks.LOGIN_POLL_MS
            val st = status(id) ?: continue
            last = st
            onStatus(st)
            if (st.state != "needs_auth" && st.state != "starting") break
        }
        return last
    }

    /** The hosts of a vault (to mark the devices already added); empty when they can't be read. */
    suspend fun hosts(vaultId: String?): List<HostRef> = runCatching {
        Networks.hostRefs(request(HttpMethod.GET, "$BASE/hosts" + (vaultId?.let { "?vault_id=$it" } ?: ""), null))
    }.getOrDefault(emptyList())

    /** Devices of the network (it brings it up; Editors). */
    suspend fun peers(id: String): List<NetPeer> = Networks.peers(request(HttpMethod.GET, "$BASE/networks/$id/peers", null))

    /**
     * The server generates the network's WireGuard private key (stored as its
     * secret, never shown) and returns the public key. With a key already:
     * [replace], otherwise `409 key_exists`.
     */
    suspend fun generateKey(id: String, replace: Boolean): String {
        val body = JSONObject().put("replace", replace).toString()
        val o = JSONObject(request(HttpMethod.POST, "$BASE/networks/$id/wireguard/key", body))
        return o.optString("public_key", "")
    }

    /** `(networks billed to you, max)` of your plan. */
    suspend fun usage(): Pair<Int, Int?>? = runCatching { Networks.usage(request(HttpMethod.GET, "$BASE/me/plan", null)) }.getOrNull()

    /** The server tests the host (through its network): never polled, only when asked. [trust]: accept a new host key. */
    suspend fun testHost(hostId: String, trust: Boolean): HostTest =
        Networks.hostTest(request(HttpMethod.POST, "$BASE/hosts/$hostId/test" + (if (trust) "?trust=true" else ""), "{}"))
            ?: HostTest(ok = false)

    /**
     * The same test for a host of an end-to-end encrypted vault: the engine
     * resolves its credentials on this device and sends them for this test
     * only (after asking the person; audited `e2ee.unlock`).
     */
    suspend fun testHostUnlocked(hostId: String, trust: Boolean): HostTest =
        Networks.hostTest(unlockedTest?.invoke(hostId, trust) ?: error("no engine")) ?: HostTest(ok = false)

    /** Adds a host at an address of a network (in the network's vault). */
    suspend fun addHost(body: String): String = JSONObject(request(HttpMethod.POST, "$BASE/hosts", body)).optString("label", "")

    companion object {
        const val BASE = "/api/v1"
    }
}
