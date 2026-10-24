package com.termoak.app.sk

import android.app.Activity
import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.nfc.NfcAdapter
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.yubico.yubikit.android.YubiKitManager
import com.yubico.yubikit.android.transport.nfc.NfcConfiguration
import com.yubico.yubikit.android.transport.nfc.NfcYubiKeyDevice
import com.yubico.yubikit.android.transport.usb.DeviceFilter
import com.yubico.yubikit.android.transport.usb.UsbConfiguration
import com.yubico.yubikit.android.transport.usb.UsbYubiKeyDevice
import com.yubico.yubikit.core.fido.FidoConnection
import com.yubico.yubikit.core.smartcard.SmartCardConnection
import com.yubico.yubikit.fido.ctap.Ctap2Session
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * The phone's security keys through YubiKit (yubikit-android `android`
 * module): USB (Android asks once for the permission to use the key) and
 * NFC (reader mode on the activity in front, only while a request is open).
 * Any FIDO2 key works over NFC; over USB, the known FIDO makers ([FidoUsb]).
 */
class YubiHardware(context: Context) : SkHardware {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val manager by lazy { YubiKitManager(app) }

    /** The activity in front (MainActivity sets it in onResume and clears it in onPause). */
    @Volatile var activity: Activity? = null
        set(value) {
            field = value
            // Reading NFC again when the app comes back while a request waits (the USB permission dialog pauses it).
            if (value != null && discovering) main.post { startNfc(value) }
        }

    @Volatile private var discovering = false
    /** Keys tapped on the phone while a request waits. */
    @Volatile private var found: LinkedBlockingQueue<SkKey>? = null
    /** Keys plugged in (with permission) while a request is open: used again for each try. */
    private val usbFound = java.util.concurrent.CopyOnWriteArrayList<UsbYubiKeyDevice>()
    private var nfcActivity: Activity? = null

    /** The app lock is on: nothing of a request can be seen, as if Termoak were not in front. */
    @Volatile var locked: () -> Boolean = { false }

    override val foreground: Boolean get() = activity != null && !locked()

    override val nfcOn: Boolean get() = runCatching { NfcAdapter.getDefaultAdapter(app)?.isEnabled == true }.getOrDefault(false)

    override fun usbKeyPresent(): Boolean = runCatching {
        app.getSystemService(UsbManager::class.java)?.deviceList?.values?.any { FidoUsb.matches(it) } == true
    }.getOrDefault(false)

    override fun waitForKey(timeoutMs: Long, cancel: SkCancel): SkKey? {
        val queue = found ?: LinkedBlockingQueue<SkKey>().also { found = it }
        if (!discovering) {
            discovering = true
            onMain {
                runCatching {
                    manager.startUsbDiscovery(UsbConfiguration().handlePermissions(true).setDeviceFilter(FidoUsb)) { device ->
                        if (device.supportsConnection(FidoConnection::class.java)) usbFound += device
                    }
                }.onFailure { Log.w("termoak", "security keys: USB: ${it.message}") }
                activity?.let { startNfc(it) }
            }
        }
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            if (cancel.stopped) return null
            val left = deadline - System.currentTimeMillis()
            if (left <= 0) return null
            attachedUsb()?.let { return usbKey(it) }
            queue.poll(minOf(left, 200), TimeUnit.MILLISECONDS)?.let { return it }
        }
    }

    /** Stops looking for keys (after an operation; a key in use keeps its connection until it is closed). */
    override fun release() {
        if (!discovering) return
        discovering = false
        onMain {
            runCatching { manager.stopUsbDiscovery() }
            nfcActivity?.let { a -> runCatching { manager.stopNfcDiscovery(a) } }
            nfcActivity = null
        }
        found?.forEach { runCatching { it.close() } }
        found = null
        usbFound.clear()
    }

    /** A key found by the USB discovery that is still plugged in. */
    private fun attachedUsb(): UsbYubiKeyDevice? {
        val attached = runCatching { app.getSystemService(UsbManager::class.java)?.deviceList?.values?.toSet() }.getOrNull().orEmpty()
        usbFound.removeAll { d -> attached.none { it.deviceName == d.usbDevice.deviceName } }
        return usbFound.firstOrNull()
    }

    private fun startNfc(a: Activity) {
        if (!discovering || nfcActivity === a || !nfcOn) return
        nfcActivity?.let { runCatching { manager.stopNfcDiscovery(it) } }
        nfcActivity = runCatching {
            manager.startNfcDiscovery(NfcConfiguration().skipNdefCheck(true).timeout(5_000), a) { device ->
                found?.offer(nfcKey(device))
            }
            a
        }.onFailure { Log.w("termoak", "security keys: NFC: ${it.message}") }.getOrNull()
    }

    private fun usbKey(device: UsbYubiKeyDevice): SkKey {
        val name = device.usbDevice.productName?.trim().orEmpty()
        return LazyKey(name, nfc = false) {
            val c = device.openConnection(FidoConnection::class.java)
            c to { Ctap2Session(c) }
        }
    }

    private fun nfcKey(device: NfcYubiKeyDevice): SkKey = LazyKey("", nfc = true) {
        val c = device.openConnection(SmartCardConnection::class.java)
        c to { Ctap2Session(c) }
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
            return
        }
        val done = CountDownLatch(1)
        main.post {
            try { block() } finally { done.countDown() }
        }
        done.await(2, TimeUnit.SECONDS)
    }
}

/** A key whose connection opens when it is first used (on the operation's thread). */
private class LazyKey(
    override val label: String,
    override val nfc: Boolean,
    private val connect: () -> Pair<java.io.Closeable, () -> Ctap2Session>,
) : SkKey {
    private var key: CtapKey? = null

    private fun key(): CtapKey = key ?: try {
        val (c, open) = connect()
        CtapKey(label, nfc, c, open).also { key = it }
    } catch (e: java.io.IOException) {
        throw com.termoak.ffi.SecurityKeyException.Failed(e.message ?: "could not open the security key")
    }

    override fun sign(req: com.termoak.ffi.SecurityKeySignRequest, pin: String?, cancel: SkCancel) = key().sign(req, pin, cancel)
    override fun makeCredential(req: com.termoak.ffi.SecurityKeyCredentialRequest, pin: String?, cancel: SkCancel) =
        key().makeCredential(req, pin, cancel)
    override fun listResident(prefix: String, pin: String, cancel: SkCancel) = key().listResident(prefix, pin, cancel)
    override fun pinRetries(): Int? = runCatching { key().pinRetries() }.getOrNull()
    override fun close() {
        key?.close()
    }
}

/**
 * USB devices of the FIDO security key makers (vendor ids): Android asks
 * for the permission to use a USB device, so only these, and only while a
 * request is open. The key must also have a FIDO HID interface.
 */
object FidoUsb : DeviceFilter() {
    private val vendors = setOf(
        0x1050, // Yubico
        0x20A0, // Nitrokey
        0x1209, // SoloKeys (pid.codes)
        0x0483, // SoloKeys v1 (STMicroelectronics)
        0x18D1, // Google Titan
        0x096E, // Feitian
        0x349E, // Token2
        0x1EA8, // Thetis
        0x2CCF, // HyperFIDO
        0x047D, // Kensington
        0x311F, // TrustKey
        0x2581, // Plug-up / Ledger
        0x2C97, // Ledger
        0x1D50, // OnlyKey
    )

    override fun checkVendorProductIds(vendorId: Int, productId: Int): Boolean = vendorId in vendors

    override fun checkUsbDevice(usbManager: UsbManager, usbDevice: UsbDevice): Boolean = hasHid(usbDevice)

    /** A plugged-in device that looks like a security key. */
    fun matches(device: UsbDevice): Boolean = device.vendorId in vendors && hasHid(device)

    private fun hasHid(device: UsbDevice): Boolean =
        (0 until device.interfaceCount).any { device.getInterface(it).interfaceClass == UsbConstants.USB_CLASS_HID }
}
