package com.termoak.app.data

import com.termoak.app.ui.logoVector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Which logo a host shows, with the desktop's ids (logos.rs), so a choice syncs both ways. */
class HostLogosTest {
    @Test
    fun theIdsAreTheDesktops() {
        assertEquals(
            listOf(
                "ubuntu", "debian", "fedora", "rhel", "centos", "rocky", "alma", "arch", "manjaro", "endeavouros",
                "alpine", "opensuse", "suse", "mint", "popos", "elementary", "zorin", "kali", "gentoo", "nixos", "void",
                "raspberrypi", "linux", "freebsd", "macos", "windows",
                "server", "database", "router", "firewall", "cloud", "container", "kubernetes", "web", "mail",
                "storage", "terminal", "iot", "security",
            ),
            HostLogos.all.map { it.id },
        )
        assertEquals(0xe95420L, HostLogos.byId("ubuntu")?.color)
        assertEquals(0x4f7cffL, HostLogos.byId("server")?.color)
    }

    @Test
    fun everyLogoHasADrawing() {
        HostLogos.all.forEach { assertNotNull(it.id, logoVector(it)) }
    }

    @Test
    fun theChosenLogoComesFirst() {
        assertEquals("router", HostLogos.resolve("router", "ubuntu")?.id)
        assertEquals("debian", HostLogos.resolve(" Debian ", null)?.id)
    }

    @Test
    fun thenTheDetectedSystem() {
        assertEquals("ubuntu", HostLogos.resolve(null, "ubuntu")?.id)
        assertEquals("mint", HostLogos.resolve(null, "linuxmint")?.id)
        assertEquals("popos", HostLogos.resolve(null, "pop")?.id)
        assertEquals("alma", HostLogos.resolve(null, "almalinux")?.id)
        assertEquals("raspberrypi", HostLogos.resolve(null, "raspbian")?.id)
        assertEquals("opensuse", HostLogos.resolve(null, "opensuse-tumbleweed")?.id)
        assertEquals("suse", HostLogos.resolve(null, "sles")?.id)
        assertEquals("macos", HostLogos.resolve(null, "darwin")?.id)
        assertEquals("arch", HostLogos.resolve(null, "garuda")?.id)
        // A later app's logo falls back to the automatic one.
        assertEquals("debian", HostLogos.resolve("quantum-os", "devuan")?.id)
    }

    @Test
    fun elseNoneTheInitial() {
        assertNull(HostLogos.resolve(null, null))
        assertNull(HostLogos.resolve("", " "))
        assertNull(HostLogos.resolve(null, "solaris"))
        // A generic id is never "detected".
        assertNull(HostLogos.resolve(null, "server"))
    }
}
