package com.termoak.app.sk

import com.termoak.ffi.SecurityKeyAssertion
import com.termoak.ffi.SecurityKeyException
import com.termoak.ffi.SecurityKeyResidentRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * The flow between the hardware and the sheet ([SecurityKeyFlow]) with a
 * pretend key ([SoftFido] through the real YubiKit layer) and a scripted
 * sheet: signing over USB and NFC, the PIN and its retries, Cancel stopping
 * the key, the server's requests (in the background, with no key here,
 * "Not on this phone", decided elsewhere) and one request at a time; and
 * the app's sheet state ([SecurityKeys]).
 */
class SecurityKeyFlowTest {
    private fun flow(hw: SkHardware, ui: SkUi) = SecurityKeyFlow(hw, ui, localWaitMs = 1500, forwardedWaitMs = 600)

    /** A key on [hw]'s device (made directly on it). */
    private fun credential(hw: FakeHardware, resident: Boolean = false): ByteArray {
        val d = hw.device!!
        val c = d.connection()
        CtapKey("x", false, c) { com.yubico.yubikit.fido.ctap.Ctap2Session(c) }.use {
            it.makeCredential(SkFixtures.createRequest(resident = resident), d.pin, SkCancel())
        }
        return d.credentials.last().id
    }

    private fun <T> expect(block: () -> T): Throwable = try {
        block()
        fail("expected an error")
        throw IllegalStateException()
    } catch (e: SecurityKeyException) {
        e
    }

    @Test
    fun signsWithAKeyPluggedIn() {
        val hw = FakeHardware()
        val ui = ScriptUi()
        val id = credential(hw)
        val a = flow(hw, ui).sign(SkFixtures.signRequest(id))
        assertEquals(0x01, a.authenticatorData!![32].toInt())
        // Connecting to the key, then "Touch your security key"; then the sheet closes.
        assertEquals(listOf(SkStage.Working, SkStage.Touch), ui.stages)
        assertEquals(1, ui.hidden.size)
        assertEquals(1, hw.released.get())
        assertTrue(hw.opened.all { it.closed })
    }

    @Test
    fun waitsForAKeyTappedOverNfc() {
        val hw = FakeHardware().apply { usb = false; nfcTapAfterMs = 200 }
        val ui = ScriptUi()
        val id = credential(hw)
        flow(hw, ui).sign(SkFixtures.signRequest(id))
        // "Tap or plug in your security key", then it works at once (no touch on NFC).
        assertEquals(listOf(SkStage.Insert(nfc = true), SkStage.Working), ui.stages)
    }

    @Test
    fun noKeyInTimeIsNoDevice() {
        val hw = FakeHardware().apply { usb = false; nfcOn = false }
        val ui = ScriptUi()
        val e = expect { flow(hw, ui).sign(SkFixtures.signRequest(SkFixtures.random(48))) }
        assertTrue(e is SecurityKeyException.NoDevice)
        assertEquals(listOf<SkStage>(SkStage.Insert(nfc = false)), ui.stages)
    }

    @Test
    fun asksThePinAndAgainWhenWrong() {
        val hw = FakeHardware(SoftFido(pin = "1234"))
        val ui = ScriptUi()
        val id = credential(hw)
        ui.pins += listOf("0000", "1234")
        val a = flow(hw, ui).sign(SkFixtures.signRequest(id, verify = true))
        assertEquals("verified", 0x05, a.authenticatorData!![32].toInt())
        // First the PIN, then again after the wrong one, with the tries left.
        assertEquals(listOf(false to null, true to 7), ui.pinAsks.toList())
    }

    @Test
    fun asksThePinWhenTheKeyWantsIt() {
        val hw = FakeHardware(SoftFido(pin = "1234"))
        val ui = ScriptUi()
        ui.pins += "1234"
        // A resident key on a key with a PIN: the key says PIN required, then it is asked.
        val cred = flow(hw, ui).makeCredential(SkFixtures.createRequest(resident = true))
        assertEquals(0x45, cred.authenticatorData[32].toInt())
        assertEquals(listOf(false to null), ui.pinAsks.toList())
        assertTrue(hw.device!!.credentials.single().resident)
    }

    @Test
    fun givesUpAfterThreeWrongPinsOrABlockedOne() {
        val hw = FakeHardware(SoftFido(pin = "1234"))
        val id = credential(hw)
        val ui = ScriptUi()
        ui.pins += listOf("1111", "2222", "3333", "1234")
        assertTrue(expect { flow(hw, ui).sign(SkFixtures.signRequest(id, verify = true)) } is SecurityKeyException.PinInvalid)
        assertEquals(SecurityKeyFlow.PIN_TRIES, ui.pinAsks.size)
        // Blocked: no point asking again.
        hw.device!!.pinRetries = 1
        val ui2 = ScriptUi()
        ui2.pins += listOf("9999", "1234")
        val e = expect { flow(hw, ui2).sign(SkFixtures.signRequest(id, verify = true)) }
        assertTrue(SkErrors.messageOf(e as SecurityKeyException), SecurityKeyFlow.pinBlocked(e))
        assertEquals(1, ui2.pinAsks.size)
    }

    @Test
    fun cancelOnThePinOrWhileTouching() {
        val hw = FakeHardware(SoftFido(pin = "1234"))
        val id = credential(hw)
        val ui = ScriptUi()
        ui.pins += ScriptUi.CANCEL
        assertTrue(expect { flow(hw, ui).sign(SkFixtures.signRequest(id, verify = true)) } is SecurityKeyException.Cancelled)
        assertTrue("the key was not reached", hw.opened.isEmpty())

        // Cancel while the key blinks: CTAPHID_CANCEL reaches it and the connection closes.
        hw.device!!.autoTouch = false
        val ui2 = ScriptUi()
        ui2.onShow = { op, stage -> if (stage == SkStage.Touch) thread { Thread.sleep(150); op.cancel.stop(SkStop.CANCELLED) } }
        assertTrue(expect { flow(hw, ui2).sign(SkFixtures.signRequest(id)) } is SecurityKeyException.Cancelled)
        assertTrue(hw.device!!.cancelsReceived >= 1)
        assertTrue(hw.opened.all { it.closed })
        assertEquals(1, ui2.hidden.size)
    }

    @Test
    fun serverRequestsNobodyCanSeeOrReachAreNoDeviceAtOnce() {
        val hw = FakeHardware().apply { foreground = false }
        val ui = ScriptUi()
        val id = credential(hw)
        val started = System.currentTimeMillis()
        assertTrue(expect { flow(hw, ui).sign(SkFixtures.signRequest(id, purpose = "server_session")) } is SecurityKeyException.NoDevice)
        // No NFC and no key plugged in: another device answers.
        hw.foreground = true
        hw.usb = false
        hw.nfcOn = false
        assertTrue(expect { flow(hw, ui).sign(SkFixtures.signRequest(id, purpose = "test")) } is SecurityKeyException.NoDevice)
        assertTrue("at once", System.currentTimeMillis() - started < 500)
        assertTrue("nothing shown", ui.shown.isEmpty())
        // A connection from this phone still waits (and shows) without a key.
        assertTrue(expect { flow(hw, ui).sign(SkFixtures.signRequest(id)) } is SecurityKeyException.NoDevice)
        assertEquals(1, ui.shown.size)
    }

    @Test
    fun notOnThisPhoneAndTimeoutLetOtherDevicesAnswer() {
        val hw = FakeHardware().apply { usb = false; nfcOn = true }
        val ui = ScriptUi()
        val id = credential(hw)
        ui.onShow = { op, _ -> assertTrue(op.forwarded); op.cancel.stop(SkStop.NOT_HERE) }
        assertTrue(expect { flow(hw, ui).sign(SkFixtures.signRequest(id, purpose = "server")) } is SecurityKeyException.NoDevice)
        // Nobody taps it in time (shorter than a connection from this phone).
        val ui2 = ScriptUi()
        val started = System.currentTimeMillis()
        assertTrue(expect { flow(hw, ui2).sign(SkFixtures.signRequest(id, purpose = "server_session")) } is SecurityKeyException.NoDevice)
        assertTrue(System.currentTimeMillis() - started < 1400)
    }

    @Test
    fun theServerDecidingClosesTheSheet() {
        val hw = FakeHardware()
        val ui = ScriptUi()
        val id = credential(hw)
        hw.device!!.autoTouch = false
        val f = flow(hw, ui)
        val message = SkFixtures.random(80)
        f.onServerRequest("r1", Base64.getEncoder().encodeToString(message))
        ui.onShow = { _, stage -> if (stage == SkStage.Touch) thread { Thread.sleep(100); f.onServerDone("r1") } }
        assertTrue(expect { f.sign(SkFixtures.signRequest(id, purpose = "server_session", message = message)) } is SecurityKeyException.Cancelled)
        assertEquals(1, ui.hidden.size)
        assertTrue(hw.device!!.cancelsReceived >= 1)

        // Decided before its turn came: never shown.
        val message2 = SkFixtures.random(80)
        f.onServerRequest("r2", Base64.getEncoder().encodeToString(message2))
        f.onServerDone("r2")
        val ui2Count = ui.shown.size
        assertTrue(expect { f.sign(SkFixtures.signRequest(id, purpose = "server_session", message = message2)) } is SecurityKeyException.Cancelled)
        assertEquals(ui2Count, ui.shown.size)
    }

    @Test
    fun oneRequestAtATime() {
        val hw = FakeHardware()
        val d = hw.device!!
        val ui = ScriptUi()
        val id = credential(hw)
        d.autoTouch = false
        val f = flow(hw, ui)
        val results = CopyOnWriteArrayList<SecurityKeyAssertion>()
        val first = thread { results += f.sign(SkFixtures.signRequest(id)) }
        val deadline = System.currentTimeMillis() + 3000
        while (!d.waitingForTouch && System.currentTimeMillis() < deadline) Thread.sleep(10)
        // A server request while the first one waits: queued (not shown) until it ends.
        val message = SkFixtures.random(80)
        f.onServerRequest("q", Base64.getEncoder().encodeToString(message))
        val queued = AtomicReference<Throwable?>()
        val second = thread {
            try {
                f.sign(SkFixtures.signRequest(id, purpose = "server_session", message = message))
            } catch (e: Throwable) {
                queued.set(e)
            }
        }
        Thread.sleep(300)
        assertEquals("only the first one is shown", 1, ui.shown.map { it.first }.distinct().size)
        // The server gives it to another device meanwhile: it is dropped from the queue.
        f.onServerDone("q")
        second.join(2000)
        assertTrue(queued.get() is SecurityKeyException.Cancelled)
        d.touch()
        first.join(3000)
        assertEquals(1, results.size)
        // Then a next one runs.
        d.autoTouch = true
        f.sign(SkFixtures.signRequest(id))
        assertEquals(2, ui.shown.map { it.first }.distinct().size)
    }

    @Test
    fun loadsResidentKeysWithThePin() {
        val hw = FakeHardware(SoftFido(pin = "1234"))
        credential(hw, resident = true)
        val ui = ScriptUi()
        ui.pins += "1234"
        val list = flow(hw, ui).listResident(SecurityKeyResidentRequest("ssh:"))
        assertEquals(1, list.size)
        assertEquals("openssh", list.single().userName)
        assertEquals(listOf(false to null), ui.pinAsks.toList())
    }

    @Test
    fun theAppSheetFollowsTheFlow() {
        val hw = FakeHardware(SoftFido(pin = "1234"))
        val id = credential(hw)
        val keys = SecurityKeys(hw)
        val seen = CopyOnWriteArrayList<String>()
        // The "user": answers the PIN on the sheet, wrong first.
        val user = thread {
            var answered = 0
            val deadline = System.currentTimeMillis() + 4000
            while (answered < 2 && System.currentTimeMillis() < deadline) {
                val s = keys.sheet.value
                val p = s?.pin
                if (p != null) {
                    seen += "pin retry=${p.retry} left=${p.retriesLeft}"
                    keys.answerPin(if (answered == 0) "4321" else "1234")
                    answered++
                    Thread.sleep(50)
                }
                Thread.sleep(5)
            }
        }
        val a = keys.flow.sign(SkFixtures.signRequest(id, verify = true))
        user.join(2000)
        assertEquals(0x05, a.authenticatorData!![32].toInt())
        assertEquals(listOf("pin retry=false left=null", "pin retry=true left=7"), seen.toList())
        assertNull("closed", keys.sheet.value)

        // Cancel on the sheet while it waits for a key.
        hw.usb = false
        hw.nfcTapAfterMs = null
        val canceller = thread {
            val deadline = System.currentTimeMillis() + 2000
            while (keys.sheet.value == null && System.currentTimeMillis() < deadline) Thread.sleep(5)
            assertTrue(keys.sheet.value!!.stage is SkStage.Insert)
            keys.cancel()
        }
        assertTrue(expect { keys.flow.sign(SkFixtures.signRequest(id)) } is SecurityKeyException.Cancelled)
        canceller.join()
        assertFalse(keys.sheet.value != null)
    }
}
