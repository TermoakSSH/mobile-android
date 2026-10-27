package com.termoak.app.data

import androidx.compose.runtime.mutableStateMapOf
import com.termoak.ffi.AccountHandle
import com.termoak.ffi.E2eePairingRequest
import com.termoak.ffi.E2eeStatus
import com.termoak.ffi.HttpMethod
import com.termoak.ffi.SyncReport
import com.termoak.ffi.VaultInfo
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import org.json.JSONObject

/** The calls the E2EE state needs of an account (the engine's handle; tests give a stand-in). */
interface E2eeApi {
    /** `GET /api/v1/info`. */
    suspend fun info(): String
    suspend fun status(): E2eeStatus
    suspend fun pairings(): List<E2eePairingRequest>
    suspend fun rotateVault(vaultId: String): UInt
}

class HandleE2eeApi(private val h: AccountHandle) : E2eeApi {
    override suspend fun info(): String = h.apiRequest(HttpMethod.GET, "/api/v1/info", null)
    override suspend fun status(): E2eeStatus = h.e2eeStatus()
    override suspend fun pairings(): List<E2eePairingRequest> = h.e2eePairings()
    override suspend fun rotateVault(vaultId: String): UInt = h.e2eeRotateVault(vaultId)
}

/**
 * The E2EE, SSO and audit state of every signed-in account (0.7 phase 5c),
 * shared by every screen (Compose state), as the desktop's E2eeBook:
 *
 * - which servers have E2EE vaults, team SSO and the extended audit
 *   (`/info features.e2ee|sso|audit_v2`, read when an account starts),
 * - the account's key and whether this device holds it (`e2ee_status`;
 *   the engine keeps the key in the account store sealed with the device
 *   key, i.e. behind the Android Keystore),
 * - the pairing requests of your other devices this unlocked device can
 *   approve (read again on every `{"type":"e2ee"}` event),
 * - the edits waiting for an unlock after a sync,
 * - the vault keys this device already rotated by itself (once per vault
 *   and key version, when the server flags `rekey_needed`).
 */
object E2eeBook {
    /** The API of an account (set by the app; `null`: signed out). */
    @Volatile var apiOf: (String) -> E2eeApi? = { null }

    /** The clock (tests give a stand-in). */
    @Volatile var now: () -> Long = { System.currentTimeMillis() }

    /** What the server of an account has. */
    data class Features(val e2ee: Boolean, val sso: Boolean, val audit: AuditRules.Features?)

    private val features = mutableStateMapOf<String, Features>()
    private val status = mutableStateMapOf<String, E2eeStatus>()
    private val pairings = mutableStateMapOf<String, List<E2eePairingRequest>>()
    private val waiting = mutableStateMapOf<String, Long>()
    private val errors = mutableStateMapOf<String, String>()
    /** `vault:version` keys this device tried to rotate. */
    private val rotations = mutableSetOf<String>()

    /** A pairing request that was not there before (account id, request): a dialog or a notification. */
    private val _newPairings = MutableSharedFlow<Pair<String, E2eePairingRequest>>(extraBufferCapacity = 8)
    val newPairings: SharedFlow<Pair<String, E2eePairingRequest>> = _newPairings

    /** Something to say after a sync (edits waiting, a key rotated by itself). */
    sealed interface Notice {
        data class Waiting(val accountId: String, val count: Int) : Notice
        data class Rotated(val accountId: String, val vault: String, val version: UInt) : Notice
    }

    private val _notices = MutableSharedFlow<Notice>(extraBufferCapacity = 8)
    val notices: SharedFlow<Notice> = _notices

    // ----- Features -----

    fun parseFeatures(infoJson: String): Features = runCatching {
        val f = JSONObject(infoJson).optJSONObject("features") ?: JSONObject()
        Features(e2ee = f.optBoolean("e2ee", false), sso = f.optBoolean("sso", false), audit = AuditRules.features(infoJson))
    }.getOrDefault(Features(false, false, null))

    fun features(accountId: String?): Features? = accountId?.let { features[it] }

    /** The account's server has E2EE vaults (as last read). Never This device (`null`). */
    fun supported(accountId: String?): Boolean = features(accountId)?.e2ee == true

    fun ssoSupported(accountId: String?): Boolean = features(accountId)?.sso == true

    fun audit(accountId: String?): AuditRules.Features? = features(accountId)?.audit

    fun supportedAccounts(): List<String> = features.filterValues { it.e2ee }.keys.toList()

    fun anySupported(): Boolean = features.values.any { it.e2ee || it.sso || it.audit != null }

    fun setFeatures(accountId: String, f: Features) {
        features[accountId] = f
    }

    /** Reads `/info` of the account's server, then its E2EE state. Keeps the last answer when it fails. */
    suspend fun start(accountId: String) {
        val api = apiOf(accountId) ?: return
        runCatching { api.info() }.onSuccess { features[accountId] = parseFeatures(it) }
        if (supported(accountId)) refresh(accountId)
    }

    /** Signed out: forget it. */
    fun forget(accountId: String) {
        features.remove(accountId)
        status.remove(accountId)
        pairings.remove(accountId)
        waiting.remove(accountId)
        errors.remove(accountId)
    }

    // ----- State -----

    fun status(accountId: String?): E2eeStatus? = accountId?.let { status[it] }

    fun setUp(accountId: String?): Boolean = status(accountId)?.setUp == true

    fun unlocked(accountId: String?): Boolean = status(accountId)?.unlocked == true

    /** Set up, but this device does not hold the key. */
    fun locked(accountId: String?): Boolean = status(accountId)?.let { it.setUp && !it.unlocked } == true

    fun pairings(accountId: String): List<E2eePairingRequest> = pairings[accountId].orEmpty()

    /** Pairing requests waiting in every account (the badge). */
    fun pairingCount(): Int = pairings.values.sumOf { it.size }

    fun waiting(accountId: String): Long = waiting[accountId] ?: 0L

    fun error(accountId: String): String? = errors[accountId]

    /** Pairings still open (the server keeps them ten minutes). */
    fun open(list: List<E2eePairingRequest>, now: Long): List<E2eePairingRequest> = list.filter { it.expiresAt > now }

    /**
     * Reads the account's E2EE state again; the pairings are listed only on
     * an unlocked device (the one that can approve them). Returns the
     * pairings that were not there before (also sent to [newPairings]).
     */
    suspend fun refresh(accountId: String): List<E2eePairingRequest> {
        val api = apiOf(accountId) ?: return emptyList()
        val st = try {
            api.status()
        } catch (e: Exception) {
            errors[accountId] = e.message.orEmpty()
            return emptyList()
        }
        errors.remove(accountId)
        status[accountId] = st
        val list = if (st.unlocked && st.pendingPairings > 0u) runCatching { api.pairings() }.getOrDefault(emptyList()) else emptyList()
        val open = open(list, now())
        val before = pairings[accountId].orEmpty().map { it.id }.toSet()
        pairings[accountId] = open
        val fresh = open.filter { it.id !in before }
        fresh.forEach { _newPairings.tryEmit(accountId to it) }
        return fresh
    }

    /** A pairing was approved or denied here: it leaves the list at once. */
    fun decided(accountId: String, pairingId: String) {
        pairings[accountId] = pairings(accountId).filter { it.id != pairingId }
    }

    /** An `{"type":"e2ee","event":"pairing"}` event. */
    fun isPairingEvent(event: JSONObject): Boolean = event.optString("type") == "e2ee" && event.optString("event") == "pairing"

    /**
     * After a sync: the edits waiting for an unlock are said once (when their
     * number grows), and this unlocked device rotates the keys the server
     * flags `rekey_needed` (someone lost access), once per vault and
     * version. Returns whether it rotated one (the caller syncs again).
     */
    suspend fun synced(accountId: String, report: SyncReport?, vaults: List<VaultInfo>): Boolean {
        if (!supported(accountId)) return false
        if (report != null) {
            val before = waiting(accountId)
            val now = report.e2eeWaiting.toLong()
            waiting[accountId] = now
            if (now > before) _notices.tryEmit(Notice.Waiting(accountId, now.toInt()))
        }
        if (status[accountId] == null) refresh(accountId)
        return rotateFlagged(accountId, vaults)
    }

    /** Whether to try this vault's rotation now (the first time it is seen flagged at this version). */
    fun takeRotation(vaultId: String, version: UInt): Boolean = rotations.add("$vaultId:$version")

    private suspend fun rotateFlagged(accountId: String, vaults: List<VaultInfo>): Boolean {
        val api = apiOf(accountId) ?: return false
        var rotated = false
        E2eeRules.toRotate(vaults.filter { it.accountId == accountId }, unlocked(accountId)).forEach { v ->
            val e = v.e2ee ?: return@forEach
            if (!takeRotation(v.id, e.keyVersion)) return@forEach
            // A race with another key holder ends in e2ee_stale: nothing to do.
            runCatching { api.rotateVault(v.id) }.onSuccess { version ->
                rotated = true
                _notices.tryEmit(Notice.Rotated(accountId, v.name, version))
            }
        }
        return rotated
    }

    /** Tests: start again. */
    fun reset() {
        features.clear(); status.clear(); pairings.clear(); waiting.clear(); errors.clear(); rotations.clear()
    }
}
