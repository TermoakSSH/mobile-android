package com.termoak.app.data

import com.termoak.ffi.TeamMember
import com.termoak.ffi.TeamRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Teams of any account: the engine's members and roles, and who manages what. */
class AccountTeamsTest {
    @Test
    fun members() {
        assertEquals(TeamPerson("u1", "ana@x.com", "Ana", "owner"), TeamPerson.of(TeamMember("u1", "ana@x.com", "Ana", TeamRole.OWNER, 1)))
        assertEquals("member", AccountTeams.roleKey(TeamRole.MEMBER))
        assertEquals(TeamRole.ADMIN, AccountTeams.roleOf("admin"))
        assertEquals(TeamRole.MEMBER, AccountTeams.roleOf("whatever"))
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
