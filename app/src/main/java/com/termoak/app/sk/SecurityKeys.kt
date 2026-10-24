package com.termoak.app.sk

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import java.util.concurrent.CompletableFuture

/**
 * The security keys of the app: the flow ([flow], the engine's handler,
 * registered when the engine starts so the events sockets answer the
 * server), the hardware ([YubiHardware]) and what the sheet shows ([sheet],
 * drawn by SecurityKeySheet above every screen).
 */
class SecurityKeys(private val hardware: SkHardware, private val supported: Boolean = true) : SkUi {
    /** What the sheet shows: one request at a time. */
    data class Sheet(val op: SkOperation, val stage: SkStage, val pin: PinAsk? = null)

    /** The PIN asked to the user ([retry]: the last one was wrong). */
    class PinAsk(val retry: Boolean, val retriesLeft: Int?, internal val answer: CompletableFuture<String?>)

    private val _sheet = MutableStateFlow<Sheet?>(null)
    val sheet: StateFlow<Sheet?> = _sheet

    val flow = SecurityKeyFlow(hardware, this)

    /** This phone can reach a key at all (NFC or USB host). */
    val available: Boolean get() = supported

    override fun show(op: SkOperation, stage: SkStage) {
        _sheet.update { s -> if (s?.op === op) s.copy(stage = stage, pin = null) else Sheet(op, stage) }
    }

    override fun pin(op: SkOperation, retry: Boolean, retriesLeft: Int?): String? {
        val answer = CompletableFuture<String?>()
        _sheet.update { s -> Sheet(op, s?.takeIf { it.op === op }?.stage ?: SkStage.Working, PinAsk(retry, retriesLeft, answer)) }
        val forget = op.cancel.onStop { answer.complete(null) }
        try {
            return answer.get()
        } finally {
            forget()
            _sheet.update { s -> if (s?.op === op) s.copy(pin = null) else s }
        }
    }

    override fun hide(op: SkOperation) {
        _sheet.update { s -> if (s?.op === op) null else s }
    }

    /** Cancel on the sheet: the request ends (for the server too). */
    fun cancel() {
        _sheet.value?.let { s ->
            s.pin?.answer?.complete(null)
            s.op.cancel.stop(SkStop.CANCELLED)
        }
    }

    /** "Not on this phone": another of your devices may answer it. */
    fun notHere() {
        _sheet.value?.let { s ->
            s.pin?.answer?.complete(null)
            s.op.cancel.stop(SkStop.NOT_HERE)
        }
    }

    /** The PIN typed on the sheet (`null`: cancelled). */
    fun answerPin(pin: String?) {
        val s = _sheet.value ?: return
        if (pin == null) {
            cancel()
        } else {
            s.pin?.answer?.complete(pin)
        }
    }

    /**
     * An event of an account's events socket: `security_key_request` (the
     * engine signs it through [flow]; it is remembered to close its sheet)
     * and `security_key_done` (the server decided it: another device
     * answered, or it timed out).
     */
    fun onEvent(event: JSONObject) {
        when (event.optString("type")) {
            "security_key_request" -> {
                val id = event.optString("request_id")
                val message = event.optString("message")
                if (id.isNotEmpty() && message.isNotEmpty()) flow.onServerRequest(id, message)
            }
            "security_key_done" -> event.optString("request_id").takeIf { it.isNotEmpty() }?.let { flow.onServerDone(it) }
        }
    }

    companion object {
        /** NFC or a USB port that hosts devices. */
        fun supported(context: Context): Boolean = context.packageManager.let {
            it.hasSystemFeature(PackageManager.FEATURE_NFC) || it.hasSystemFeature(PackageManager.FEATURE_USB_HOST)
        }
    }
}
