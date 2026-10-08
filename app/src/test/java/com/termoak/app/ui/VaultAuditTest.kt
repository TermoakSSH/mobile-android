package com.termoak.app.ui

import com.termoak.app.R
import com.termoak.ffi.VaultMember
import com.termoak.ffi.VaultMemberKind
import com.termoak.ffi.VaultRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VaultAuditTest {
    private fun member(id: String, userId: String?, email: String?, name: String) =
        VaultMember(id, VaultMemberKind.USER, userId, email, name, null, VaultRole.EDITOR, false, 0)

    @Test
    fun knownActionsAreTranslatedWithHyphensOrUnderscores() {
        assertEquals(R.string.vault_audit_secret_reveal, VaultAudit.actionText("secret.reveal"))
        assertEquals(R.string.vault_audit_member_add, VaultAudit.actionText("vault.member-add"))
        assertEquals(R.string.vault_audit_role_changed, VaultAudit.actionText("vault.role_changed"))
        assertNull(VaultAudit.actionText("host.something_new"))
    }

    @Test
    fun actorsAreShownByEmailOrName() {
        val members = listOf(member("m1", "u1", "ana@example.com", "Ana"), member("m2", "u2", null, "Bea"), member("m3", "u3", null, ""))
        assertEquals("ana@example.com", VaultAudit.who("u1", members))
        assertEquals("ana@example.com", VaultAudit.who("user:u1", members))
        assertEquals("Bea", VaultAudit.who("m2", members))
        assertEquals("u3", VaultAudit.who("u3", members))
        assertEquals("ai:task-9", VaultAudit.who("ai:task-9", members))
    }
}
