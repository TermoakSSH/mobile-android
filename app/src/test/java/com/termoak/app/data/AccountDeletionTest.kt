package com.termoak.app.data

import android.app.Application
import com.termoak.ffi.HttpMethod
import com.termoak.ffi.TermoakException
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Deleting the account: the request, the confirmation rules and the server's refusals as the engine reports them. */
@RunWith(RobolectricTestRunner::class) // org.json is the Android one.
@Config(sdk = [34], application = Application::class)
class AccountDeletionTest {
    /** The engine's text of a server error (`ClientError::Api`). */
    private fun api(status: Int, code: String, message: String) = "the server responded $status ($code): $message"

    @Test
    fun body() {
        val plain = JSONObject(AccountDeletion.body("p@ss ", null, false))
        assertEquals("p@ss ", plain.getString("password"))
        assertFalse(plain.has("totp_code"))
        assertFalse(plain.has("delete_shared_vaults"))
        val all = JSONObject(AccountDeletion.body("x", " 123456 ", true))
        assertEquals("123456", all.getString("totp_code"))
        assertTrue(all.getBoolean("delete_shared_vaults"))
        assertFalse(JSONObject(AccountDeletion.body("x", "  ", false)).has("totp_code"))
    }

    @Test
    fun confirmation() {
        assertTrue(AccountDeletion.confirmed(" DELETE ", "DELETE"))
        assertFalse(AccountDeletion.confirmed("delete", "DELETE"))
        assertFalse(AccountDeletion.confirmed("DELETE!", "DELETE"))
        assertTrue(AccountDeletion.canSubmit("pw", "BORRAR", "BORRAR", needsCode = false, code = ""))
        assertFalse(AccountDeletion.canSubmit("", "BORRAR", "BORRAR", needsCode = false, code = ""))
        assertFalse(AccountDeletion.canSubmit("pw", "BORRA", "BORRAR", needsCode = false, code = ""))
        assertFalse(AccountDeletion.canSubmit("pw", "BORRAR", "BORRAR", needsCode = true, code = " "))
        assertTrue(AccountDeletion.canSubmit("pw", "BORRAR", "BORRAR", needsCode = true, code = "123456"))
    }

    @Test
    fun totpOfMe() {
        assertEquals(true, AccountDeletion.totpOf("""{"user":{"id":"u","totp_enabled":true},"plan":null}"""))
        assertEquals(false, AccountDeletion.totpOf("""{"user":{"totp_enabled":false}}"""))
        assertNull(AccountDeletion.totpOf("""{"user":{}}"""))
        assertNull(AccountDeletion.totpOf("not json"))
    }

    @Test
    fun errors() {
        fun outcome(e: Throwable) = AccountDeletion.outcomeOf(e)
        assertEquals(DeleteOutcome.InvalidPassword, outcome(TermoakException.Forbidden(api(403, "invalid_password", "the password is not correct"))))
        assertEquals(DeleteOutcome.TotpRequired, outcome(TermoakException.TotpRequired(api(401, "totp_required", "enter the code"))))
        assertEquals(DeleteOutcome.TotpInvalid, outcome(TermoakException.TotpInvalid(api(401, "totp_invalid", "not correct"))))
        assertEquals(DeleteOutcome.LastAdmin, outcome(TermoakException.Conflict(api(409, "last_admin", "you are the only administrator"))))
        val owner = "you are the only owner of \"Ops\", \"Web team\": appoint another owner or delete the team first"
        assertEquals(
            DeleteOutcome.LastTeamOwner(listOf("Ops", "Web team"), owner),
            outcome(TermoakException.Conflict(api(409, "last_team_owner", owner))),
        )
        assertEquals(DeleteOutcome.SharedVaults, outcome(TermoakException.Conflict(api(409, "shared_vaults", "confirm with delete_shared_vaults"))))
        // Older servers: no DELETE on /api/v1/me.
        assertEquals(DeleteOutcome.Unsupported, outcome(TermoakException.Server(api(405, "error", ""))))
        assertEquals(DeleteOutcome.Unsupported, outcome(TermoakException.NotFound(api(404, "not_found", "no route"))))
        val offline = TermoakException.Network("network: offline")
        assertEquals(DeleteOutcome.Failed(offline), outcome(offline))
    }

    @Test
    fun requests() = runBlocking {
        val sent = mutableListOf<Triple<HttpMethod, String, String?>>()
        val deletion = AccountDeletion { method, path, body ->
            sent += Triple(method, path, body)
            if (method == HttpMethod.GET) """{"user":{"totp_enabled":true}}""" else """{"ok":true}"""
        }
        assertEquals(true, deletion.totpEnabled())
        assertEquals(DeleteOutcome.Deleted, deletion.delete("pw", "123456", false))
        assertEquals(HttpMethod.GET to "/api/v1/me", sent[0].first to sent[0].second)
        assertEquals(HttpMethod.DELETE to "/api/v1/me", sent[1].first to sent[1].second)
        assertEquals("123456", JSONObject(sent[1].third!!).getString("totp_code"))
        val failing = AccountDeletion { _, _, _ -> throw TermoakException.Network("offline") }
        assertNull(failing.totpEnabled())
    }
}
