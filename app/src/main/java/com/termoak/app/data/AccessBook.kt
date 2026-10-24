package com.termoak.app.data

import androidx.compose.runtime.mutableStateMapOf
import com.termoak.ffi.AccessRequestInfo
import com.termoak.ffi.AccessRequestScope
import com.termoak.ffi.AccountHandle
import com.termoak.ffi.HostAccessInfo
import com.termoak.ffi.HttpMethod
import com.termoak.ffi.Team2faRequirement
import org.json.JSONObject

/** The calls the access state needs of an account (the engine's handle; tests give a stand-in). */
interface AccessApi {
    /** `GET /api/v1/info`. */
    suspend fun info(): String
    suspend fun approvals(): List<AccessRequestInfo>
    suspend fun mine(): List<AccessRequestInfo>
    suspend fun hostAccess(hostId: String): HostAccessInfo
    suspend fun team2fa(): List<Team2faRequirement>
    /** The engine forgets its cached host answers and team certificates (core v0.7.0-next.9). */
    fun forgetAccess()
}

class HandleAccessApi(private val h: AccountHandle) : AccessApi {
    override suspend fun info(): String = h.apiRequest(HttpMethod.GET, "/api/v1/info", null)
    override suspend fun approvals(): List<AccessRequestInfo> = h.accessRequests(AccessRequestScope.Approvals, null)
    override suspend fun mine(): List<AccessRequestInfo> = h.accessRequests(AccessRequestScope.Mine, null)
    override suspend fun hostAccess(hostId: String): HostAccessInfo = h.hostAccess(hostId)
    override suspend fun team2fa(): List<Team2faRequirement> = h.team2faRequirements()
    override fun forgetAccess() = h.forgetAccess()
}

/**
 * The teams security state of every signed-in account, shared by every
 * screen (Compose state), as the desktop's AccessBook:
 *
 * - which servers have teams security (`/info features.teams_security`,
 *   read when an account starts),
 * - the requests you can decide (the Access screen and its badge) and your
 *   own, read again on **every** `access_request` event (events carry the
 *   bare request, without `can_decide`),
 * - the server's answer about each host (`GET /hosts/{id}/access`: window,
 *   pending request, recording), asked again after every event (the engine
 *   caches its answers for 30 s; a fresh answer replaces its cache),
 * - the teams that require 2FA from you (`team_2fa` of `/me`).
 */
object AccessBook {
    /** The API of an account (set by the app; `null`: signed out). */
    @Volatile var apiOf: (String) -> AccessApi? = { null }

    /** The clock (tests give a stand-in). */
    @Volatile var now: () -> Long = { System.currentTimeMillis() }

    private val supported = mutableStateMapOf<String, Boolean>()
    private val approvals = mutableStateMapOf<String, List<AccessRequestInfo>>()
    private val mine = mutableStateMapOf<String, List<AccessRequestInfo>>()
    private val loaded = mutableStateMapOf<String, Boolean>()
    /** Host uid (`account/id`) → the server's answer. */
    private val hosts = mutableStateMapOf<String, HostAccessInfo>()
    private val hostAt = mutableMapOf<String, Long>()
    private val twoFa = mutableStateMapOf<String, List<Team2faRequirement>>()
    /** Bumped on every change of requests (screens waiting for a window look again). */
    private val revision = mutableStateMapOf<String, Int>()

    /** Answers about hosts younger than this are reused. */
    const val HOST_FRESH_MS = 30_000L

    // ----- Features -----

    /** The account's server has teams security (as last read). Never This device (`null`). */
    fun supported(accountId: String?): Boolean = accountId != null && supported[accountId] == true

    fun anySupported(): Boolean = supported.values.any { it }

    fun supportedAccounts(): List<String> = supported.filterValues { it }.keys.toList()

    /** `features.teams_security` is an object in `/info`. */
    fun hasFeature(infoJson: String): Boolean = runCatching {
        JSONObject(infoJson).optJSONObject("features")?.optJSONObject("teams_security") != null
    }.getOrDefault(false)

    /** Reads `/info` of the account's server; then its requests and 2FA state. Keeps the last answer when it fails. */
    suspend fun start(accountId: String) {
        val api = apiOf(accountId) ?: return
        runCatching { api.info() }.onSuccess { supported[accountId] = hasFeature(it) }
        if (supported(accountId)) {
            refresh(accountId)
            refresh2fa(accountId)
        }
    }

    fun setSupported(accountId: String, on: Boolean) {
        supported[accountId] = on
    }

    /** Signed out: forget it. */
    fun forget(accountId: String) {
        supported.remove(accountId)
        approvals.remove(accountId)
        mine.remove(accountId)
        loaded.remove(accountId)
        twoFa.remove(accountId)
        revision.remove(accountId)
        hosts.keys.filter { it.startsWith("$accountId/") }.forEach { hosts.remove(it); hostAt.remove(it) }
    }

    // ----- Requests -----

    fun approvals(accountId: String): List<AccessRequestInfo> = approvals[accountId].orEmpty()

    fun mine(accountId: String): List<AccessRequestInfo> = mine[accountId].orEmpty()

    fun loaded(accountId: String): Boolean = loaded[accountId] == true

    fun revision(accountId: String): Int = revision[accountId] ?: 0

    /** Requests waiting for your decision, in every account (the badge). */
    fun waitingCount(): Int = approvals.values.sumOf { AccessRules.waiting(it).size }

    fun waitingCount(accountId: String): Int = AccessRules.waiting(approvals(accountId)).size

    /** Reads the account's requests again (yours and the ones you can decide). */
    suspend fun refresh(accountId: String) {
        if (!supported(accountId)) return
        val api = apiOf(accountId) ?: return
        runCatching { api.approvals() }.onSuccess { approvals[accountId] = it.sortedByDescending { r -> r.createdAt } }
        runCatching { api.mine() }.onSuccess { mine[accountId] = it.sortedByDescending { r -> r.createdAt } }
        loaded[accountId] = true
        revision[accountId] = (revision[accountId] ?: 0) + 1
    }

    /**
     * An `access_request` event of an account: the lists are read again and
     * the hosts already asked about are asked again. Returns the event, if
     * it was one.
     */
    suspend fun onEvent(accountId: String, json: JSONObject): AccessRules.AccessEvent? {
        val ev = AccessRules.eventOf(json)
        // A window opened, ended or was revoked: the engine's cached answers and certificates go (as the desktop).
        apiOf(accountId)?.let { runCatching { it.forgetAccess() } }
        refresh(accountId)
        rehost(accountId)
        return ev
    }

    /** Puts a request just made or decided here in the lists until they are read again. */
    fun remember(accountId: String, r: AccessRequestInfo, mineToo: Boolean) {
        if (mineToo) mine[accountId] = listOf(r) + mine(accountId).filterNot { it.id == r.id }
        val a = approvals(accountId)
        if (a.any { it.id == r.id }) approvals[accountId] = a.map { if (it.id == r.id) r.copy(canDecide = it.canDecide && r.canDecide) else it }
        revision[accountId] = (revision[accountId] ?: 0) + 1
    }

    // ----- Hosts -----

    private fun key(accountId: String, hostId: String) = "$accountId/$hostId"

    /** The last answer about a host (`null`: not asked, or not an account's host). */
    fun host(accountId: String?, hostId: String?): HostAccessInfo? =
        if (accountId == null || hostId == null) null else hosts[key(accountId, hostId)]

    /** Asks the server about a host when the last answer is old (or [force]). */
    suspend fun ask(accountId: String, hostId: String, force: Boolean = false): HostAccessInfo? {
        if (!supported(accountId)) return null
        val k = key(accountId, hostId)
        val at = hostAt[k]
        if (!force && at != null && now() - at < HOST_FRESH_MS) return hosts[k]
        val api = apiOf(accountId) ?: return hosts[k]
        hostAt[k] = now()
        return runCatching { api.hostAccess(hostId) }.onSuccess { hosts[k] = it }.getOrElse { hosts[k] }
    }

    /** Every host of the account asked about before, asked again (after an event). */
    suspend fun rehost(accountId: String) {
        hosts.keys.filter { it.startsWith("$accountId/") }.map { it.substringAfter('/') }.forEach { ask(accountId, it, force = true) }
    }

    /** The window of a host as the rows and the terminal bar show it. */
    sealed interface HostWindow {
        /** Open until (ms). */
        data class Open(val endsAt: Long) : HostWindow
        /** A request of yours waits. */
        data object Pending : HostWindow
        /** It needs an approved window (you have none). */
        data object Needed : HostWindow
    }

    /** The window of a host from the last answer (`null`: nothing to show, it is direct or exempt). */
    fun windowOf(accountId: String?, hostId: String?, at: Long = now()): HostWindow? {
        val a = host(accountId, hostId) ?: return null
        return windowOf(a, at)
    }

    fun windowOf(a: HostAccessInfo, at: Long): HostWindow? {
        if (a.exempt || a.mode == com.termoak.ffi.AccessMode.DIRECT) return null
        val end = a.grant?.endsAt
        return when {
            end != null && end > at -> HostWindow.Open(end)
            a.pending != null -> HostWindow.Pending
            else -> HostWindow.Needed
        }
    }

    /** Sets an answer (the Request access sheet after sending; tests). */
    fun putHost(accountId: String, a: HostAccessInfo) {
        val k = key(accountId, a.hostId)
        hosts[k] = a
        hostAt[k] = now()
    }

    // ----- 2FA -----

    fun team2fa(accountId: String): List<Team2faRequirement> = twoFa[accountId].orEmpty()

    /** Every account's teams that require 2FA from you: `(account, requirement)`. */
    fun team2faAll(): List<Pair<String, Team2faRequirement>> = twoFa.flatMap { (a, l) -> l.map { a to it } }

    suspend fun refresh2fa(accountId: String) {
        if (!supported(accountId)) return
        val api = apiOf(accountId) ?: return
        runCatching { api.team2fa() }.onSuccess { twoFa[accountId] = it }
    }

    /** Tests: everything forgotten. */
    fun clear() {
        supported.clear(); approvals.clear(); mine.clear(); loaded.clear(); hosts.clear(); hostAt.clear(); twoFa.clear(); revision.clear()
    }
}
