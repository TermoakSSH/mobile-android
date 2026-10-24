package com.termoak.app.sk

import com.termoak.ffi.SecurityKeyAlgorithm
import com.termoak.ffi.SecurityKeyCredentialRequest
import com.termoak.ffi.SecurityKeySignRequest
import com.yubico.yubikit.fido.ctap.Ctap2Session
import java.security.SecureRandom
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Requests as the engine makes them (its native library can't load on the JVM). */
object SkFixtures {
    fun random(n: Int) = ByteArray(n).also { SecureRandom().nextBytes(it) }

    /** OpenSSH's user id: the user name zero-padded to 32 bytes. */
    fun userId(name: String) = name.toByteArray().copyOf(32)

    fun createRequest(
        algorithm: SecurityKeyAlgorithm = SecurityKeyAlgorithm.ED25519,
        resident: Boolean = false,
        verify: Boolean = false,
        application: String = "ssh:",
        user: String = "openssh",
    ) = SecurityKeyCredentialRequest(
        algorithm = algorithm,
        coseAlgorithm = if (algorithm == SecurityKeyAlgorithm.ED25519) -8L else -7L,
        application = application,
        userId = userId(user),
        userName = user,
        clientDataHash = random(32),
        resident = resident,
        requireVerification = verify,
        flags = ((if (resident) 0x20 else 0) or (if (verify) 0x04 else 0) or 0x01).toUByte(),
        device = null,
    )

    fun signRequest(
        keyHandle: ByteArray,
        purpose: String = "connect",
        presence: Boolean = true,
        verify: Boolean = false,
        message: ByteArray = random(64),
        application: String = "ssh:",
        algorithm: SecurityKeyAlgorithm = SecurityKeyAlgorithm.ED25519,
    ) = SecurityKeySignRequest(
        algorithm = algorithm,
        application = application,
        keyHandle = keyHandle,
        clientDataHash = SoftFido.sha256(message),
        message = message,
        requirePresence = presence,
        requireVerification = verify,
        publicKey = "sk-ssh-ed25519@openssh.com AAAA test",
        fingerprint = "SHA256:test",
        host = "web1",
        address = "10.0.0.5",
        username = "deploy",
        keyLabel = "YubiKey",
        keyId = "k1",
        device = "YubiKey 5C NFC",
        purpose = purpose,
        sessionId = if (purpose == "server_session") "s1" else null,
    )
}

/**
 * The phone's side in the tests: a [SoftFido] plugged in over USB, or
 * tapped over NFC after [nfcTapAfterMs]; whether Termoak is in front and
 * NFC is on.
 */
class FakeHardware(var device: SoftFido? = SoftFido()) : SkHardware {
    @Volatile override var foreground = true
    @Volatile override var nfcOn = true
    @Volatile var usb = true
    @Volatile var nfcTapAfterMs: Long? = null
    val released = AtomicInteger()
    val opened = CopyOnWriteArrayList<SoftFido.Connection>()

    override fun usbKeyPresent() = usb && device != null

    override fun waitForKey(timeoutMs: Long, cancel: SkCancel): SkKey? {
        val d = device
        if (usb && d != null) return key(d, nfc = false)
        val tap = nfcTapAfterMs
        if (d == null || tap == null || tap > timeoutMs) {
            cancel.await(timeoutMs)
            return null
        }
        if (cancel.await(tap)) return null
        return key(d, nfc = true)
    }

    override fun release() {
        released.incrementAndGet()
    }

    private fun key(d: SoftFido, nfc: Boolean): SkKey {
        val c = d.connection().also { opened += it }
        return CtapKey(if (nfc) "" else "Soft FIDO2 key", nfc, c) { Ctap2Session(c) }
    }
}

/**
 * The sheet in the tests: records what is shown; PINs come from [pins]
 * (`null`: Cancel); [onShow] acts like the user.
 */
class ScriptUi : SkUi {
    val shown = CopyOnWriteArrayList<Pair<SkOperation, SkStage>>()
    val pinAsks = CopyOnWriteArrayList<Pair<Boolean, Int?>>()
    val hidden = CopyOnWriteArrayList<SkOperation>()
    val pins = LinkedBlockingQueue<String>()
    @Volatile var onShow: (SkOperation, SkStage) -> Unit = { _, _ -> }

    override fun show(op: SkOperation, stage: SkStage) {
        shown += op to stage
        onShow(op, stage)
    }

    override fun pin(op: SkOperation, retry: Boolean, retriesLeft: Int?): String? {
        pinAsks += retry to retriesLeft
        val p = pins.poll(2, TimeUnit.SECONDS) ?: return null
        return p.takeIf { it != CANCEL }
    }

    override fun hide(op: SkOperation) {
        hidden += op
    }

    val stages: List<SkStage> get() = shown.map { it.second }

    companion object {
        /** A PIN "answer" that is Cancel. */
        const val CANCEL = "\u0000cancel"
    }
}
