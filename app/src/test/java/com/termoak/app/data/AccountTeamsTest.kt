package com.termoak.app.data

import android.app.Application
import com.termoak.ffi.TermoakException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Teams of any account: paths, replies and who manages what. */
@RunWith(RobolectricTestRunner::class) // org.json is the Android one.
@Config(sdk = [34], application = Application::class)
class AccountTeamsTest {
    @Test
    fun paths() {
        assertEquals("/api/v1/teams/0193b1c2-aaaa", AccountTeams.teamPath("0193b1c2-aaaa"))
        assertTrue(runCatching { AccountTeams.teamPath("../me") }.exceptionOrNull() is TermoakException.Invalid)
    }

    @Test
    fun members() {
        val list = AccountTeams.parseMembers(
            """[{"user_id":"u1","email":"ana@x.com","name":"Ana","role":"owner","added_at":1},{"user_id":"u2","email":"bo@x.com","name":"","role":"member"}]""",
        )
        assertEquals(listOf(TeamPerson("u1", "ana@x.com", "Ana", "owner"), TeamPerson("u2", "bo@x.com", "", "member")), list)
    }

    @Test
    fun roles() {
        assertTrue(AccountTeams.canManage("admin"))
        assertFalse(AccountTeams.canManage("member"))
        assertFalse(AccountTeams.canManage(null))
        assertEquals(listOf("member", "admin", "owner"), AccountTeams.assignable("owner"))
        assertEquals(listOf("member", "admin"), AccountTeams.assignable("admin"))
        assertEquals(emptyList<String>(), AccountTeams.assignable("member"))
    }
}
