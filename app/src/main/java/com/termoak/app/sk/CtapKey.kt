package com.termoak.app.sk

import com.termoak.ffi.SecurityKeyAssertion
import com.termoak.ffi.SecurityKeyCredential
import com.termoak.ffi.SecurityKeyCredentialRequest
import com.termoak.ffi.SecurityKeyException
import com.termoak.ffi.SecurityKeyResident
import com.termoak.ffi.SecurityKeySignRequest
import com.yubico.yubikit.core.application.CommandException
import com.yubico.yubikit.core.application.CommandState
import com.yubico.yubikit.core.fido.CtapException
import com.yubico.yubikit.fido.Cbor
import com.yubico.yubikit.fido.ctap.ClientPin
import com.yubico.yubikit.fido.ctap.CredentialManagement
import com.yubico.yubikit.fido.ctap.Ctap2Session
import com.yubico.yubikit.fido.ctap.PinUvAuthProtocol
import com.yubico.yubikit.fido.ctap.PinUvAuthProtocolV1
import com.yubico.yubikit.fido.ctap.PinUvAuthProtocolV2
import java.io.Closeable
import java.io.IOException

/**
 * A security key through YubiKit's CTAP2 session (yubikit-android `fido`,
 * Apache-2.0), over USB HID or NFC (ISO 7816): any FIDO2 key, not only
 * YubiKeys. The rp id is the key's application (`ssh:`), the client data
 * hash the engine's (YubiKit takes the hash); user verification only
 * through the PIN when there is one (never the `uv` option), `up` off only
 * for no-touch keys; credential management (CTAP 2.1 or 2.1-PRE, YubiKit
 * picks the command) for resident keys. Cancel sends CTAPHID_CANCEL
 * (YubiKit's [CommandState]) and closes the connection.
 */
class CtapKey(
    override val label: String,
    override val nfc: Boolean,
    /** The open connection (closed with the key). */
    private val connection: Closeable,
    private val open: () -> Ctap2Session,
) : SkKey {
    private var session: Ctap2Session? = null

    private fun session(): Ctap2Session = session ?: open().also { session = it }

    override fun sign(req: SecurityKeySignRequest, pin: String?, cancel: SkCancel): SecurityKeyAssertion = guarded(cancel) { state ->
        val s = session()
        val (param, version) = pin?.let { pinAuth(s, it, ClientPin.PIN_PERMISSION_GA, req.application, req.clientDataHash) } ?: (null to null)
        val options: Map<String, Any>? = if (!req.requirePresence) mapOf("up" to false) else null
        val allow = listOf(mapOf("type" to "public-key", "id" to req.keyHandle))
        val a = s.getAssertions(req.application, req.clientDataHash, allow, null, options, param, version, state).first()
        SecurityKeyAssertion(signature = a.signature, authenticatorData = a.authenticatorData)
    }

    override fun makeCredential(req: SecurityKeyCredentialRequest, pin: String?, cancel: SkCancel): SecurityKeyCredential = guarded(cancel) { state ->
        val s = session()
        val (param, version) = pin?.let { pinAuth(s, it, ClientPin.PIN_PERMISSION_MC, req.application, req.clientDataHash) } ?: (null to null)
        val rp = mapOf("id" to req.application, "name" to req.application)
        val user = mapOf("id" to req.userId, "name" to req.userName, "displayName" to req.userName)
        val params = listOf(mapOf("type" to "public-key", "alg" to req.coseAlgorithm.toInt()))
        val options: Map<String, Any>? = if (req.resident) mapOf("rk" to true) else null
        val c = s.makeCredential(req.clientDataHash, rp, user, params, null, null, options, param, version, null, state)
        SecurityKeyCredential(authenticatorData = c.authenticatorData, attestationObject = ByteArray(0))
    }

    override fun listResident(prefix: String, pin: String, cancel: SkCancel): List<SecurityKeyResident> = guarded(cancel) {
        val s = session()
        if (!CredentialManagement.isSupported(s.cachedInfo)) {
            throw SecurityKeyException.Unsupported("this security key cannot list the keys stored on it (CTAP 2.1 credential management)")
        }
        val protocol = protocolOf(s)
        val token = ClientPin(s, protocol).getPinToken(pin.toCharArray(), ClientPin.PIN_PERMISSION_CM, null)
        val cm = CredentialManagement(s, protocol, token)
        cm.enumerateRps().flatMap { rp ->
            val id = rp.rp["id"] as? String ?: return@flatMap emptyList()
            if (!id.startsWith(prefix)) return@flatMap emptyList()
            cm.enumerateCredentials(rp.rpIdHash).mapNotNull { c ->
                val credentialId = c.credentialId["id"] as? ByteArray ?: return@mapNotNull null
                SecurityKeyResident(
                    application = id,
                    credentialId = credentialId,
                    publicKeyCose = Cbor.encode(c.publicKey),
                    userId = c.user["id"] as? ByteArray ?: ByteArray(0),
                    userName = c.user["name"] as? String,
                )
            }
        }
    }

    override fun pinRetries(): Int? = runCatching {
        val s = session()
        if (!ClientPin.isSupported(s.cachedInfo)) null else ClientPin(s, protocolOf(s)).pinRetries.count
    }.getOrNull()

    override fun close() {
        runCatching { session?.close() }
        runCatching { connection.close() }
    }

    /** The PIN's token for [permission] and the auth param over the client data hash. */
    private fun pinAuth(s: Ctap2Session, pin: String, permission: Int, rpId: String, clientDataHash: ByteArray): Pair<ByteArray, Int> {
        if (!ClientPin.isSupported(s.cachedInfo)) {
            throw SecurityKeyException.Unsupported("this security key has no PIN")
        }
        val protocol = protocolOf(s)
        val token = ClientPin(s, protocol).getPinToken(pin.toCharArray(), permission, rpId)
        return protocol.authenticate(token, clientDataHash) to protocol.version
    }

    private fun protocolOf(s: Ctap2Session): PinUvAuthProtocol =
        if (s.cachedInfo.pinUvAuthProtocols.contains(PinUvAuthProtocolV2.VERSION)) PinUvAuthProtocolV2() else PinUvAuthProtocolV1()

    /** Runs [block] with a CTAP command state that [cancel] cancels; device errors as the handler's. */
    private fun <T> guarded(cancel: SkCancel, block: (CommandState) -> T): T {
        val state = CommandState()
        val forget = cancel.onStop {
            state.cancel()
            // A key on NFC answers at once; closing ends the exchange.
            if (nfc) runCatching { connection.close() }
        }
        try {
            if (cancel.stopped) throw cancel.error()
            return block(state)
        } catch (e: SecurityKeyException) {
            throw e
        } catch (e: CtapException) {
            if (cancel.stopped) throw cancel.error()
            throw SkErrors.exceptionOfCtap(e.ctapError.toInt(), e.errorName)
        } catch (e: CommandException) {
            if (cancel.stopped) throw cancel.error()
            throw SecurityKeyException.Failed(e.message ?: e.javaClass.simpleName)
        } catch (e: IOException) {
            if (cancel.stopped) throw cancel.error()
            throw SecurityKeyException.Failed(e.message ?: "the connection to the security key failed")
        } catch (e: IllegalArgumentException) {
            // YubiKit checks the PIN's length (4 to 63 bytes) before sending it.
            if (cancel.stopped) throw cancel.error()
            if (e.message.orEmpty().contains("PIN")) throw SecurityKeyException.PinInvalid(e.message!!)
            throw SecurityKeyException.Failed(e.message ?: e.javaClass.simpleName)
        } catch (e: IllegalStateException) {
            if (cancel.stopped) throw cancel.error()
            throw SecurityKeyException.Unsupported(e.message ?: "not supported by this security key")
        } finally {
            forget()
        }
    }
}
