package com.termoak.app.data

import android.app.Application
import com.termoak.ffi.TermoakException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The per-account AI keys: paths, bodies and replies, as the engine sends and reads them. */
@RunWith(RobolectricTestRunner::class) // org.json is the Android one.
@Config(sdk = [34], application = Application::class)
class AccountAiKeysTest {
    @Test
    fun paths() {
        assertEquals("/api/v1/me/ai/keys/claude", AccountAiKeys.path(" claude "))
        assertEquals("/api/v1/me/ai/keys/opencode-api", AccountAiKeys.path("opencode-api"))
        listOf("", "a/b", "a b", "ñ", "x".repeat(65)).forEach { bad ->
            assertTrue(runCatching { AccountAiKeys.path(bad) }.exceptionOrNull() is TermoakException.Invalid)
        }
    }

    @Test
    fun bodies() {
        val onlyModel = JSONObject(AccountAiKeys.setBody(null, " gpt-5 "))
        assertEquals("gpt-5", onlyModel.getString("model"))
        assertFalse(onlyModel.has("key"))
        val keyAndDefault = JSONObject(AccountAiKeys.setBody(" sk-1 ", " "))
        assertEquals("sk-1", keyAndDefault.getString("key"))
        assertTrue(keyAndDefault.isNull("model"))
        assertTrue(JSONObject(AccountAiKeys.testBody(" ")).isNull("key"))
        assertEquals("sk", JSONObject(AccountAiKeys.testBody("sk")).getString("key"))
    }

    @Test
    fun replies() {
        val keys = AccountAiKeys.parseKeys(
            """[{"provider":"claude","label":"Claude","model":null,"hint":"abcd","created_at":1,"updated_at":2},
               {"provider":"gpt","label":"GPT","model":"gpt-5","hint":"wxyz"}]""",
        )
        assertEquals(listOf("claude", "gpt"), keys.map { it.provider })
        assertNull(keys[0].model)
        assertEquals("gpt-5", keys[1].model)
        assertEquals(2L, keys[0].updatedAt)
        assertEquals(1, AccountAiKeys.parseKeys("""{"keys":[{"provider":"claude"}]}""").size)

        val ok = AccountAiKeys.parseTest("""{"ok":true}""")
        assertTrue(ok.ok)
        assertNull(ok.status)
        val rejected = AccountAiKeys.parseTest("""{"ok":false,"error":"bad key","status":401}""")
        assertFalse(rejected.ok)
        assertEquals("bad key", rejected.error)
        assertEquals(401.toUShort(), rejected.status)
    }
}
