package com.termoak.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Install on a host": finding and adding a public key in authorized_keys. */
class AuthorizedKeysTest {
    private val key = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIK0 phone (Termoak)"

    @Test
    fun identities() {
        assertEquals("ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIK0", AuthorizedKeys.identity(key))
        assertEquals("ecdsa-sha2-nistp256 AAAA", AuthorizedKeys.identity("from=\"10.0.0.0/8\",no-pty ecdsa-sha2-nistp256 AAAA me@x"))
        assertNull(AuthorizedKeys.identity("# a comment"))
        assertNull(AuthorizedKeys.identity("ssh-rsa"))
    }

    @Test
    fun alreadyThere() {
        assertTrue(AuthorizedKeys.contains("ssh-rsa AAAB other\nno-pty ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIK0 laptop\n", key))
        assertFalse(AuthorizedKeys.contains("# ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIK0\nssh-rsa AAAB other", key))
        assertFalse(AuthorizedKeys.contains("", key))
    }

    @Test
    fun adding() {
        assertEquals("$key\n", AuthorizedKeys.appending("", " $key \n"))
        assertEquals("ssh-rsa AAAB other\n$key\n", AuthorizedKeys.appending("ssh-rsa AAAB other", key))
        assertEquals("ssh-rsa AAAB other\n$key\n", AuthorizedKeys.appending("ssh-rsa AAAB other\n", key))
    }
}
