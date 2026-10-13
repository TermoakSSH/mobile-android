package com.termoak.app.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.termoak.app.R
import com.termoak.app.UiText
import com.termoak.app.data.AccountDeletion
import com.termoak.ffi.HttpMethod
import com.termoak.ffi.TermoakException
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings → Accounts → Delete my account: the dialog's button, and the
 * server's refusals (password, two-step code, shared vaults confirmed and
 * sent again) through a stand-in for the account's API.
 */
@RunWith(RobolectricTestRunner::class)
// The plain Application: TermoakApp loads the engine (a native library for Android only).
@Config(application = Application::class, sdk = [34])
class DeleteAccountTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun string(id: Int, vararg args: Any) = rule.activity.getString(id, *args)

    /** A server that answers DELETE with [replies] in turn (`null`: deleted) and records the bodies. */
    private class FakeServer(totp: Boolean, vararg replies: Throwable?) {
        val bodies = mutableListOf<JSONObject>()
        private val pending = replies.toMutableList()
        val deletion = AccountDeletion { method, path, body ->
            assertEquals("/api/v1/me", path)
            when (method) {
                HttpMethod.GET -> """{"user":{"totp_enabled":$totp}}"""
                else -> {
                    bodies += JSONObject(body!!)
                    pending.removeFirstOrNull()?.let { throw it }
                    """{"ok":true}"""
                }
            }
        }
    }

    private fun refusal(status: Int, code: String, message: String) = "the server responded $status ($code): $message"

    @Test
    fun buttonNeedsPasswordWordAndCode() {
        val server = FakeServer(totp = true)
        val form = DeleteAccountForm(server.deletion, string(R.string.account_delete_confirm_word))
        rule.setContent { MaterialTheme { DeleteAccountDialog(form, "termoak.com", onSubmit = {}, onDismiss = {}) } }
        rule.onNodeWithText(string(R.string.account_delete_text, "termoak.com")).assertExists()
        rule.onNodeWithText(string(R.string.account_delete_item_teams)).assertExists()
        val submit = rule.onNodeWithTag("delete-account-submit")
        submit.assertIsNotEnabled()
        rule.onNodeWithText(string(R.string.common_password)).performTextInput("secret")
        rule.onNodeWithTag("delete-account-confirm").performTextInput("delete")
        submit.assertIsNotEnabled() // Capitals only.
        form.typed = " DELETE "
        rule.waitForIdle()
        submit.assertIsEnabled()
        // Two-step verification: the code field appears and is needed.
        runBlocking { form.loadTotp() }
        rule.waitForIdle()
        rule.onNodeWithText(string(R.string.account_delete_code_hint)).assertExists()
        submit.assertIsNotEnabled()
        rule.onNodeWithTag("delete-account-code").performTextInput("123456")
        submit.assertIsEnabled()
    }

    @Test
    fun sharedVaultsAskThenResendWithTheSameCode() = runBlocking {
        val server = FakeServer(
            totp = true,
            TermoakException.Conflict(refusal(409, "shared_vaults", "your shared vaults with members will be deleted")),
        )
        val form = DeleteAccountForm(server.deletion, "DELETE").apply {
            password = "pw"; typed = "DELETE"; needsCode = true; code = "123456"
        }
        var removed = 0
        assertFalse(form.submit { removed++ })
        assertTrue(form.askVaults)
        assertNull(form.error)
        assertFalse(server.bodies[0].has("delete_shared_vaults"))
        assertTrue(form.confirmVaults { removed++ })
        assertFalse(form.askVaults)
        assertEquals(1, removed)
        assertTrue(server.bodies[1].getBoolean("delete_shared_vaults"))
        assertEquals("123456", server.bodies[1].getString("totp_code"))
        assertEquals("pw", server.bodies[1].getString("password"))
    }

    @Test
    fun sharedVaultsCancelled() = runBlocking {
        val server = FakeServer(false, TermoakException.Conflict(refusal(409, "shared_vaults", "confirm")))
        val form = DeleteAccountForm(server.deletion, "DELETE").apply { password = "pw"; typed = "DELETE" }
        assertFalse(form.submit())
        form.cancelVaults()
        assertFalse(form.askVaults)
        assertEquals(1, server.bodies.size)
    }

    @Test
    fun refusals() = runBlocking {
        val server = FakeServer(
            false,
            TermoakException.Forbidden(refusal(403, "invalid_password", "the password is not correct")),
            TermoakException.TotpRequired(refusal(401, "totp_required", "enter the two-step verification code")),
            TermoakException.TotpInvalid(refusal(401, "totp_invalid", "the verification code is not correct")),
            TermoakException.Conflict(refusal(409, "last_admin", "you are the only administrator")),
            TermoakException.Conflict(refusal(409, "last_team_owner", "you are the only owner of \"Ops\": appoint another owner")),
            TermoakException.Server(refusal(405, "error", "")),
        )
        val form = DeleteAccountForm(server.deletion, "DELETE").apply { password = "wrong"; typed = "DELETE" }
        assertFalse(form.submit())
        assertEquals(UiText.Res(R.string.account_delete_wrong_password), form.error)
        // The account has two-step verification: the field appears and the button waits for it.
        assertFalse(form.submit())
        assertTrue(form.needsCode)
        assertNull(form.error)
        assertEquals(UiText.Res(R.string.account_delete_code_required), form.codeError)
        assertFalse(form.canSubmit)
        form.code = "000000"
        assertFalse(form.submit())
        assertEquals(UiText.Res(R.string.login_code_invalid), form.codeError)
        assertFalse(form.submit())
        assertEquals(UiText.Res(R.string.account_delete_last_admin), form.error)
        assertNull(form.codeError)
        assertFalse(form.submit())
        assertEquals(UiText.Res(R.string.account_delete_last_owner, listOf("“Ops”")), form.error)
        assertFalse(form.submit())
        assertEquals(UiText.Res(R.string.account_delete_unsupported), form.error)
        assertFalse(form.busy)
        assertEquals(6, server.bodies.size)
    }
}
