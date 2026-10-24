package com.termoak.app.sk

import com.yubico.yubikit.core.fido.FidoConnection
import com.yubico.yubikit.fido.Cbor
import com.yubico.yubikit.fido.ctap.PinUvAuthProtocol
import com.yubico.yubikit.fido.ctap.PinUvAuthProtocolV1
import com.yubico.yubikit.fido.ctap.PinUvAuthProtocolV2
import java.io.IOException
import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import javax.crypto.KeyAgreement

/**
 * A pretend FIDO2 security key behind YubiKit's USB HID connection (test
 * only): CTAPHID framing (INIT, CBOR, KEEPALIVE while it waits for a touch,
 * CANCEL) and CTAP 2.1 commands (getInfo, makeCredential with resident
 * keys, getAssertion with `up` and the PIN's auth param, clientPin with PIN
 * protocols 1 and 2 and its retries, credential management or its 2.1-PRE
 * preview). Ed25519 and ECDSA P-256 with the JDK's crypto. So the app's
 * YubiKit code ([CtapKey]) is tested as it talks to a real key.
 */
class SoftFido(
    /** A PIN set on the key, or `null`. */
    var pin: String? = null,
    /** Only the CTAP 2.1-PRE credential management (YubiKey 5.2–5.4). */
    private val preview: Boolean = false,
) {
    class Credential(
        val rpId: String,
        val id: ByteArray,
        val keys: KeyPair,
        val cose: Map<Int, Any>,
        val user: Map<String, Any>,
        val resident: Boolean,
        var counter: Int = 0,
    )

    val credentials = mutableListOf<Credential>()

    /** How a touch happens: at once ([autoTouch]) or when [touch] is called. */
    @Volatile var autoTouch = true
    private var touched = CountDownLatch(1)
    @Volatile var waitingForTouch = false
        private set
    @Volatile var cancelsReceived = 0
        private set
    @Volatile var pinRetries = 8
    private var wrongInARow = 0
    private var token: ByteArray? = null
    private val ecdh: KeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    private val aaguid = ByteArray(16) { 0x5A }

    /** The user touches the key. */
    fun touch() = touched.countDown()

    /** A new connection to it (a USB HID one, as YubiKit sees it). */
    fun connection(): Connection = Connection()

    inner class Connection : FidoConnection {
        private val out = LinkedBlockingQueue<ByteArray>()
        private var cid = 0
        private var pending: ByteBuffer? = null
        private var pendingCmd: Byte = 0
        @Volatile private var cancel = false
        @Volatile var closed = false
            private set

        override fun send(packet: ByteArray) {
            if (closed) throw IOException("closed")
            val b = ByteBuffer.wrap(packet)
            val channel = b.int
            val first = b.get()
            if (first.toInt() and 0x80 != 0) {
                if (first == CANCEL) {
                    cancel = true
                    cancelsReceived++
                    return
                }
                val len = b.short.toInt() and 0xFFFF
                pendingCmd = first
                pending = ByteBuffer.allocate(len)
                pending!!.put(packet, 7, minOf(len, 57))
            } else {
                pending!!.put(packet, 5, minOf(pending!!.remaining(), 59))
            }
            if (!pending!!.hasRemaining()) {
                val data = pending!!.array()
                pending = null
                handle(channel, pendingCmd, data)
            }
        }

        override fun receive(packet: ByteArray) {
            val p = out.poll(5, TimeUnit.SECONDS) ?: throw IOException("no answer from the pretend key")
            p.copyInto(packet)
        }

        override fun close() {
            closed = true
        }

        private fun handle(channel: Int, cmd: Byte, data: ByteArray) {
            when (cmd) {
                INIT -> {
                    cid = 0x01020304
                    val r = ByteBuffer.allocate(17).put(data, 0, 8).putInt(cid).put(2).put(5).put(4).put(3).put(0x05)
                    reply(channel, cmd, r.array())
                }
                CBOR -> {
                    cancel = false
                    // Answered on its own thread: it may wait for a touch (keepalives meanwhile).
                    Thread {
                        val r = try {
                            byteArrayOf(0) + command(data[0], data.copyOfRange(1, data.size))
                        } catch (e: Ctap) {
                            byteArrayOf(e.status.toByte())
                        }
                        reply(cid, CBOR, r)
                    }.start()
                }
                else -> reply(channel, ERROR, byteArrayOf(1))
            }
        }

        /** Waits for a touch (keepalive "up needed" every 50 ms) unless cancelled. */
        fun presence() {
            if (autoTouch) return
            waitingForTouch = true
            val giveUp = System.currentTimeMillis() + 10_000
            try {
                while (!touched.await(50, TimeUnit.MILLISECONDS)) {
                    if (cancel || closed) throw Ctap(0x2D)
                    // Like a real key: it stops waiting after a while (CTAP2_ERR_USER_ACTION_TIMEOUT).
                    if (System.currentTimeMillis() > giveUp) throw Ctap(0x2F)
                    reply(cid, KEEPALIVE, byteArrayOf(2))
                }
                touched = CountDownLatch(1)
            } finally {
                waitingForTouch = false
            }
        }

        private fun command(c: Byte, body: ByteArray): ByteArray {
            @Suppress("UNCHECKED_CAST")
            val p = (if (body.isEmpty()) emptyMap<Int, Any>() else Cbor.decode(body) as Map<Int, Any?>)
            return when (c.toInt()) {
                0x04 -> Cbor.encode(info())
                0x01 -> Cbor.encode(makeCredential(p, this))
                0x02 -> Cbor.encode(getAssertion(p, this))
                0x06 -> Cbor.encode(clientPin(p))
                0x0A, 0x41 -> {
                    if ((c.toInt() == 0x41) != preview) throw Ctap(0x01)
                    Cbor.encode(credMgmt(p))
                }
                else -> throw Ctap(0x01)
            }
        }

        private fun reply(channel: Int, cmd: Byte, data: ByteArray) {
            val first = ByteBuffer.allocate(64).putInt(channel).put(cmd).putShort(data.size.toShort())
            var i = minOf(57, data.size)
            first.put(data, 0, i)
            out.offer(first.array())
            var seq = 0
            while (i < data.size) {
                val n = minOf(59, data.size - i)
                out.offer(ByteBuffer.allocate(64).putInt(channel).put(seq++.toByte()).put(data, i, n).array())
                i += n
            }
        }
    }

    private class Ctap(val status: Int) : Exception()

    private fun info(): Map<Int, Any> {
        val options = mutableMapOf<String, Any>("rk" to true, "up" to true, "clientPin" to (pin != null))
        if (preview) options["credentialMgmtPreview"] = true else {
            options["credMgmt"] = true
            options["pinUvAuthToken"] = true
        }
        return mapOf(
            1 to if (preview) listOf("U2F_V2", "FIDO_2_0", "FIDO_2_1_PRE") else listOf("U2F_V2", "FIDO_2_0", "FIDO_2_1"),
            3 to aaguid,
            4 to options,
            5 to 1200,
            6 to if (preview) listOf(1) else listOf(2, 1),
        )
    }

    private fun protocol(v: Any?): PinUvAuthProtocol = if ((v as? Number)?.toInt() == 2) PinUvAuthProtocolV2() else PinUvAuthProtocolV1()

    /** The PIN's auth param over [message], or a CTAP error. */
    private fun checkAuth(param: Any?, version: Any?, message: ByteArray): Boolean {
        if (param == null) return false
        val t = token ?: throw Ctap(0x33)
        val expected = protocol(version).authenticate(t, message)
        if (!expected.contentEquals(param as ByteArray)) throw Ctap(0x33)
        return true
    }

    private fun makeCredential(p: Map<Int, Any?>, conn: Connection): Map<Int, Any> {
        val cdh = p[1] as ByteArray
        @Suppress("UNCHECKED_CAST") val rp = p[2] as Map<String, Any>
        @Suppress("UNCHECKED_CAST") val user = p[3] as Map<String, Any>
        @Suppress("UNCHECKED_CAST") val params = p[4] as List<Map<String, Any>>
        @Suppress("UNCHECKED_CAST") val options = (p[7] as? Map<String, Any>).orEmpty()
        val rk = options["rk"] == true
        val uv = checkAuth(p[8], p[9], cdh)
        if (pin != null && rk && !uv) throw Ctap(0x36)
        val alg = (params.first()["alg"] as Number).toInt()
        val (keys, cose) = when (alg) {
            -8 -> {
                val kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
                val raw = kp.public.encoded.takeLast(32).toByteArray()
                kp to mapOf(1 to 1, 3 to -8, -1 to 6, -2 to raw)
            }
            -7 -> {
                val kp = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
                val w = (kp.public as ECPublicKey).w
                kp to mapOf(1 to 2, 3 to -7, -1 to 1, -2 to coord(w.affineX), -3 to coord(w.affineY))
            }
            else -> throw Ctap(0x26)
        }
        conn.presence()
        val id = ByteArray(48).also { SecureRandom().nextBytes(it) }
        val rpId = rp["id"] as String
        val cred = Credential(rpId, id, keys, cose, user, rk)
        if (rk) credentials.removeAll { it.resident && it.rpId == rpId && (it.user["id"] as ByteArray).contentEquals(user["id"] as ByteArray) }
        credentials += cred
        val flags = 0x01 or 0x40 or (if (uv) 0x04 else 0)
        val authData = sha256(rpId.toByteArray()) + byteArrayOf(flags.toByte()) + int32(0) + aaguid +
            byteArrayOf((id.size shr 8).toByte(), id.size.toByte()) + id + Cbor.encode(cose)
        return mapOf(1 to "none", 2 to authData, 3 to emptyMap<String, Any>())
    }

    private fun getAssertion(p: Map<Int, Any?>, conn: Connection): Map<Int, Any> {
        val rpId = p[1] as String
        val cdh = p[2] as ByteArray
        @Suppress("UNCHECKED_CAST") val allow = (p[3] as? List<Map<String, Any>>).orEmpty()
        @Suppress("UNCHECKED_CAST") val options = (p[5] as? Map<String, Any>).orEmpty()
        if (options.containsKey("uv")) throw Ctap(0x2C)
        val uv = checkAuth(p[6], p[7], cdh)
        val cred = credentials.firstOrNull { c -> c.rpId == rpId && allow.any { (it["id"] as ByteArray).contentEquals(c.id) } } ?: throw Ctap(0x2E)
        val up = options["up"] != false
        if (up) conn.presence()
        cred.counter++
        val flags = (if (up) 0x01 else 0) or (if (uv) 0x04 else 0)
        val authData = sha256(rpId.toByteArray()) + byteArrayOf(flags.toByte()) + int32(cred.counter)
        val signature = sign(cred.keys.private, authData + cdh)
        return mapOf(1 to mapOf("type" to "public-key", "id" to cred.id), 2 to authData, 3 to signature)
    }

    private fun clientPin(p: Map<Int, Any?>): Map<Int, Any> {
        val proto = protocol(p[1])
        return when ((p[2] as Number).toInt()) {
            0x01 -> mapOf(3 to pinRetries)
            0x02 -> {
                val w = (ecdh.public as ECPublicKey).w
                mapOf(1 to mapOf(1 to 2, 3 to -25, -1 to 1, -2 to coord(w.affineX), -3 to coord(w.affineY)))
            }
            0x05, 0x09 -> {
                if (pin == null) throw Ctap(0x35)
                if (pinRetries == 0) throw Ctap(0x32)
                if (wrongInARow >= 3) throw Ctap(0x34)
                @Suppress("UNCHECKED_CAST") val peer = p[3] as Map<Int, Any>
                val shared = proto.kdf(z(peer))
                val hash = proto.decrypt(shared, p[6] as ByteArray)
                val want = sha256(pin!!.toByteArray()).copyOf(16)
                if (!hash.contentEquals(want)) {
                    pinRetries--
                    wrongInARow++
                    throw Ctap(if (pinRetries == 0) 0x32 else 0x31)
                }
                pinRetries = 8
                wrongInARow = 0
                val t = ByteArray(32).also { SecureRandom().nextBytes(it) }
                token = t
                mapOf(2 to proto.encrypt(shared, t))
            }
            else -> throw Ctap(0x3E)
        }
    }

    private var rpCursor = 0
    private var credCursor: List<Credential> = emptyList()

    private fun credMgmt(p: Map<Int, Any?>): Map<Int, Any> {
        val sub = (p[1] as Number).toInt()
        @Suppress("UNCHECKED_CAST") val params = p[2] as? Map<Int, Any>
        val resident = credentials.filter { it.resident }
        val rps = resident.map { it.rpId }.distinct()
        fun auth() {
            val message = byteArrayOf(sub.toByte()) + (params?.let { Cbor.encode(it) } ?: ByteArray(0))
            if (!checkAuth(p[4], p[3], message)) throw Ctap(0x36)
        }
        fun rp(i: Int): Map<Int, Any> = mapOf(3 to mapOf("id" to rps[i]), 4 to sha256(rps[i].toByteArray()))
        fun cred(c: Credential): Map<Int, Any> = mapOf(6 to c.user, 7 to mapOf("type" to "public-key", "id" to c.id), 8 to c.cose)
        return when (sub) {
            0x02 -> {
                auth()
                if (rps.isEmpty()) throw Ctap(0x2E)
                rpCursor = 1
                rp(0) + mapOf(5 to rps.size)
            }
            0x03 -> rp(rpCursor++)
            0x04 -> {
                auth()
                val hash = params!![1] as ByteArray
                credCursor = resident.filter { sha256(it.rpId.toByteArray()).contentEquals(hash) }
                if (credCursor.isEmpty()) throw Ctap(0x2E)
                cred(credCursor[0]) + mapOf(9 to credCursor.size)
            }
            0x05 -> cred(credCursor[++rpCursorCred])
            else -> throw Ctap(0x3E)
        }.also { if (sub == 0x04) rpCursorCred = 0 }
    }

    private var rpCursorCred = 0

    private fun z(peer: Map<Int, Any>): ByteArray {
        val w = ECPoint(BigInteger(1, peer[-2] as ByteArray), BigInteger(1, peer[-3] as ByteArray))
        val key = KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(w, (ecdh.public as ECPublicKey).params))
        return KeyAgreement.getInstance("ECDH").apply { init(ecdh.private); doPhase(key, true) }.generateSecret()
    }

    companion object {
        private const val INIT = 0x86.toByte()
        private const val CBOR = 0x90.toByte()
        private const val CANCEL = 0x91.toByte()
        private const val KEEPALIVE = 0xBB.toByte()
        private const val ERROR = 0xBF.toByte()

        fun sha256(b: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(b)

        private fun int32(v: Int) = ByteBuffer.allocate(4).putInt(v).array()

        private fun coord(v: BigInteger): ByteArray {
            val b = v.toByteArray()
            return when {
                b.size == 32 -> b
                b.size > 32 -> b.copyOfRange(b.size - 32, b.size)
                else -> ByteArray(32 - b.size) + b
            }
        }

        fun sign(key: PrivateKey, data: ByteArray): ByteArray =
            Signature.getInstance(if (key.algorithm == "EC") "SHA256withECDSA" else "Ed25519").run {
                initSign(key)
                update(data)
                sign()
            }

        fun verify(key: PublicKey, data: ByteArray, signature: ByteArray): Boolean =
            Signature.getInstance(if (key.algorithm == "EC") "SHA256withECDSA" else "Ed25519").run {
                initVerify(key)
                update(data)
                verify(signature)
            }
    }
}
