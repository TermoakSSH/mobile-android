package com.termoak.app.sk

import com.termoak.ffi.SecurityKeyAlgorithm
import com.termoak.ffi.SecurityKeyAssertion
import com.termoak.ffi.SecurityKeyCredential
import com.termoak.ffi.SecurityKeyCredentialRequest
import com.termoak.ffi.SecurityKeyException
import com.termoak.ffi.SecurityKeyHandler
import com.termoak.ffi.SecurityKeyResident
import com.termoak.ffi.SecurityKeyResidentRequest
import com.termoak.ffi.SecurityKeySignRequest
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/*
 * FIDO2 security keys on this phone (YubiKey, SoloKey, Nitrokey...): signing
 * for SSH, creating keys on the device and loading the keys stored on it
 * (docs/SSH-PRO.md in TermoakSSH/core). Three layers, like the desktop's
 * src/security_keys, so everything above the hardware is tested on the JVM:
 * - [SkHardware] / [SkKey]: the hardware (YubiKit over USB and NFC,
 *   [YubiHardware]; a pretend authenticator in the tests).
 * - [SkUi]: what the user sees: "Tap or plug in your security key", the PIN,
 *   "Touch your security key" with the host, user, key and purpose, Cancel
 *   (the sheet of [SecurityKeys]; scripted in the tests).
 * - [SecurityKeyFlow]: the flow between them (one operation at a time,
 *   waiting for a key, PIN retries, cancel, the server's requests). It is
 *   the engine's [SecurityKeyHandler].
 */

/** Why an operation stopped before it ended. */
enum class SkStop {
    /** Cancel on the sheet: the request ends (for the server too). */
    CANCELLED,

    /** "Not on this phone": another of your devices may answer. */
    NOT_HERE,

    /** The server decided the request (another device answered, or it timed out). */
    DONE,
}

/**
 * The stop switch of one operation: the sheet's buttons and the server's
 * `security_key_done` flip it; the hardware listens to it ([onStop]) to stop
 * the key (YubiKit's CTAP cancel, closing the connection).
 */
class SkCancel {
    @Volatile var reason: SkStop? = null
        private set
    private val latch = CountDownLatch(1)
    private val hooks = CopyOnWriteArrayList<() -> Unit>()

    val stopped: Boolean get() = reason != null

    fun stop(why: SkStop) {
        synchronized(this) {
            if (reason != null) return
            reason = why
        }
        latch.countDown()
        hooks.forEach { runCatching { it() } }
    }

    /** Runs [hook] when it stops (at once if it already has). Returns a way to forget it. */
    fun onStop(hook: () -> Unit): () -> Unit {
        hooks += hook
        if (stopped) runCatching { hook() }
        return { hooks.remove(hook) }
    }

    /** Waits up to [ms] for a stop; true if it stopped. */
    fun await(ms: Long): Boolean = latch.await(ms, TimeUnit.MILLISECONDS)

    /** What the engine gets when it stopped: "not here" lets another device answer. */
    fun error(): SecurityKeyException = when (reason) {
        SkStop.NOT_HERE -> SecurityKeyException.NoDevice("the security key is not on this phone")
        else -> SecurityKeyException.Cancelled("the security key request was cancelled")
    }
}

/** A security key reached over USB or NFC, open for one operation (closed after it). */
interface SkKey : java.io.Closeable {
    /** What the device calls itself ("YubiKey 5C NFC"), or "" when unknown. */
    val label: String

    /** Tapped on the phone: it answers at once (no waiting for a touch on it) and leaves with the hand. */
    val nfc: Boolean

    /** CTAP2 get assertion: rp id `application`, the allow list `[key_handle]`, the client data hash; the PIN only when there is one. */
    fun sign(req: SecurityKeySignRequest, pin: String?, cancel: SkCancel): SecurityKeyAssertion

    /** CTAP2 make credential. */
    fun makeCredential(req: SecurityKeyCredentialRequest, pin: String?, cancel: SkCancel): SecurityKeyCredential

    /** The resident credentials whose relying party starts with [prefix] (CTAP 2.1 credential management, with the PIN). */
    fun listResident(prefix: String, pin: String, cancel: SkCancel): List<SecurityKeyResident>

    /** PIN attempts left before it blocks, if the key says. */
    fun pinRetries(): Int?
}

/** The phone's side: where keys come from. */
interface SkHardware {
    /** Termoak is in front (the sheet can be seen and NFC can read). */
    val foreground: Boolean

    /** NFC is on this phone and switched on. */
    val nfcOn: Boolean

    /** A security key is plugged in over USB now. */
    fun usbKeyPresent(): Boolean

    /**
     * Waits up to [timeoutMs] for a key: one plugged in (Android asks for
     * the permission to use it) or tapped on the phone. `null`: none in
     * time, or [cancel] stopped.
     */
    fun waitForKey(timeoutMs: Long, cancel: SkCancel): SkKey?

    /** The operation ended: stop looking for keys. */
    fun release() {}
}

/** What an operation is for (what the sheet shows). */
sealed interface SkPurpose {
    /** Signing an SSH login: `purpose` is `connect` from this phone; `server_session`, `test` and `server` when the server asks. */
    data class Sign(val request: SecurityKeySignRequest) : SkPurpose

    /** Creating a key on the device. */
    data class Create(val application: String, val algorithm: SecurityKeyAlgorithm) : SkPurpose

    /** Reading the keys stored on the device. */
    data object LoadResident : SkPurpose
}

/** One request to the security key, as the sheet shows it. */
class SkOperation(val purpose: SkPurpose, val cancel: SkCancel = SkCancel()) {
    /** The server asked (forwarding): "not on this phone" lets another device answer. */
    val forwarded: Boolean
        get() = purpose is SkPurpose.Sign && purpose.request.purpose.let { it.isNotEmpty() && it != "connect" }
}

/** What the key is doing. */
sealed interface SkStage {
    /** No key yet: tap it on the phone ([nfc]: NFC is on) or plug it in. */
    data class Insert(val nfc: Boolean) : SkStage

    /** Waiting for a touch on the key. */
    data object Touch : SkStage

    /** Working without a touch (a no-touch key, or a key held on the phone). */
    data object Working : SkStage
}

/** What the user sees and answers. */
interface SkUi {
    /** Shows [stage] for [op] (replacing what was shown). */
    fun show(op: SkOperation, stage: SkStage)

    /** Asks for the key's PIN and blocks until it is entered. [retry]: the last one was wrong. `null`: cancelled. */
    fun pin(op: SkOperation, retry: Boolean, retriesLeft: Int?): String?

    /** The operation ended: close its sheet. */
    fun hide(op: SkOperation)
}

/**
 * The flow between the hardware and the sheet, as the engine's
 * [SecurityKeyHandler] (its calls come on background threads and block).
 * One operation at a time: the others wait their turn (and stop if they
 * are cancelled or decided meanwhile).
 */
class SecurityKeyFlow(
    private val hw: SkHardware,
    private val ui: SkUi,
    /** How long a connection from this phone waits for a key. */
    private val localWaitMs: Long = 60_000,
    /** How long a request of the server waits for a key here (the server waits 60 s for all your devices). */
    private val forwardedWaitMs: Long = 30_000,
) : SecurityKeyHandler {
    private val turn = Semaphore(1, true)

    /** The server's requests (`request_id` → their message, base64), to stop them on `security_key_done`. */
    private val serverRequests = ConcurrentHashMap<String, String>()

    /** Operations running or waiting, by their message (base64). */
    private val byMessage = ConcurrentHashMap<String, SkCancel>()

    /** Messages already decided by the server (a `done` that arrived before its operation). */
    private val decided = ConcurrentHashMap<String, Long>()

    /** The label of the device of the last operation that reached one (to name new keys). */
    @Volatile var lastDevice: String? = null
        private set

    override fun sign(request: SecurityKeySignRequest): SecurityKeyAssertion {
        val op = SkOperation(SkPurpose.Sign(request))
        if (op.forwarded) {
            // Nobody can see the request or hold a key to the phone: let another device answer.
            if (!hw.foreground) throw SecurityKeyException.NoDevice("Termoak is not open on this phone")
            if (!hw.nfcOn && !hw.usbKeyPresent()) throw SecurityKeyException.NoDevice("no security key is connected to this phone")
        }
        val message = Base64.getEncoder().encodeToString(request.message)
        byMessage[message] = op.cancel
        val now = System.currentTimeMillis()
        decided.entries.removeIf { now - it.value > 120_000 }
        if (decided.containsKey(message)) op.cancel.stop(SkStop.DONE)
        try {
            return exclusive(op) {
                run(op, request.requireVerification, request.requirePresence, if (op.forwarded) forwardedWaitMs else localWaitMs) { key, pin ->
                    key.sign(request, pin, op.cancel)
                }
            }
        } finally {
            byMessage.remove(message, op.cancel)
        }
    }

    override fun makeCredential(request: SecurityKeyCredentialRequest): SecurityKeyCredential {
        val op = SkOperation(SkPurpose.Create(request.application, request.algorithm))
        return exclusive(op) {
            run(op, request.requireVerification, touch = true, waitMs = localWaitMs) { key, pin -> key.makeCredential(request, pin, op.cancel) }
        }
    }

    override fun listResident(request: SecurityKeyResidentRequest): List<SecurityKeyResident> {
        val op = SkOperation(SkPurpose.LoadResident)
        return exclusive(op) {
            run(op, needsPin = true, touch = false, waitMs = localWaitMs) { key, pin -> key.listResident(request.applicationPrefix, pin!!, op.cancel) }
        }
    }

    /** A `security_key_request` of the events socket (the engine answers it through [sign]). */
    fun onServerRequest(requestId: String, messageBase64: String) {
        val m = normalize(messageBase64) ?: return
        serverRequests[requestId] = m
    }

    /** A `security_key_done`: the server decided it, so its sheet goes away. */
    fun onServerDone(requestId: String) {
        val m = serverRequests.remove(requestId) ?: return
        decided[m] = System.currentTimeMillis()
        byMessage[m]?.stop(SkStop.DONE)
    }

    private fun <T> exclusive(op: SkOperation, block: () -> T): T {
        // Wait for the operation in front, unless this one stops meanwhile.
        while (!turn.tryAcquire(100, TimeUnit.MILLISECONDS)) {
            if (op.cancel.stopped) throw op.cancel.error()
        }
        try {
            if (op.cancel.stopped) throw op.cancel.error()
            return block()
        } finally {
            runCatching { hw.release() }
            ui.hide(op)
            turn.release()
        }
    }

    /**
     * Runs [call] on a key: waits for one (showing "tap or plug in"), asks
     * for the PIN when needed, shows "touch your key" while it works and
     * asks again on a wrong PIN. Every try reaches the key again (a key on
     * NFC is tapped again after the PIN).
     */
    private fun <T> run(op: SkOperation, needsPin: Boolean, touch: Boolean, waitMs: Long, call: (SkKey, String?) -> T): T {
        var pin: String? = if (needsPin) askPin(op, false, null) else null
        var wrong = 0
        while (true) {
            val key = reach(op, waitMs)
            var retries: Int? = null
            val result = try {
                key.use { k ->
                    lastDevice = k.label.trim().take(64).ifEmpty { null }
                    ui.show(op, if (touch && !k.nfc) SkStage.Touch else SkStage.Working)
                    try {
                        Result.success(call(k, pin))
                    } catch (e: SecurityKeyException) {
                        if (e is SecurityKeyException.PinInvalid) retries = runCatching { k.pinRetries() }.getOrNull()
                        Result.failure(e)
                    }
                }
            } catch (e: java.io.IOException) {
                Result.failure(SecurityKeyException.Failed(e.message ?: "the security key failed"))
            }
            if (op.cancel.stopped) throw op.cancel.error()
            val e = result.exceptionOrNull() ?: return result.getOrThrow()
            when {
                e is SecurityKeyException.PinRequired && pin == null -> pin = askPin(op, false, null)
                e is SecurityKeyException.PinInvalid && !pinBlocked(e) && retries != 0 && wrong + 1 < PIN_TRIES -> {
                    wrong++
                    pin = askPin(op, true, retries)
                }
                else -> throw e
            }
        }
    }

    /** A key: one plugged in, or the first one plugged in or tapped while "tap or plug in" shows. */
    private fun reach(op: SkOperation, waitMs: Long): SkKey {
        if (op.cancel.stopped) throw op.cancel.error()
        // A key plugged in: connecting to it (Android may ask for the permission first).
        ui.show(op, if (hw.usbKeyPresent()) SkStage.Working else SkStage.Insert(hw.nfcOn))
        val key = hw.waitForKey(waitMs, op.cancel)
        if (op.cancel.stopped) {
            runCatching { key?.close() }
            throw op.cancel.error()
        }
        return key ?: throw SecurityKeyException.NoDevice("no security key was connected to this phone in time")
    }

    private fun askPin(op: SkOperation, retry: Boolean, retriesLeft: Int?): String {
        val pin = ui.pin(op, retry, retriesLeft)
        if (op.cancel.stopped) throw op.cancel.error()
        return when {
            pin == null -> throw SecurityKeyException.Cancelled("the security key request was cancelled")
            pin.isEmpty() -> throw SecurityKeyException.PinRequired("the security key needs its PIN")
            else -> pin
        }
    }

    companion object {
        /** Wrong PINs in a row before giving up (the key blocks its PIN after a few more). */
        const val PIN_TRIES = 3

        /** A wrong PIN that blocked the PIN: asking again is useless. */
        fun pinBlocked(e: SecurityKeyException): Boolean = SkErrors.messageOf(e).contains("blocked")

        private fun normalize(b64: String): String? =
            runCatching { Base64.getEncoder().encodeToString(Base64.getDecoder().decode(b64.trim())) }.getOrNull()
    }
}
