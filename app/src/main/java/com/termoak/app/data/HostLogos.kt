package com.termoak.app.data

import com.termoak.ffi.SshHost

/**
 * Host logos (the desktop's logos.rs): a host's avatar shows its chosen logo
 * (`SshHost.icon`), else the logo of the system detected when connecting
 * (`SshHost.os`), else its colored initial.
 *
 * The ids are stored in the host and synced: they are the desktop's and must
 * never be renamed. An id this version doesn't know (a later app's) falls
 * back to the automatic logo, and stays in the host when it is saved.
 */
object HostLogos {
    enum class Kind { SYSTEM, GENERIC }

    /** A logo: [id] is what `SshHost.icon` stores; [color] the avatar's background (0xRRGGBB). */
    data class Logo(val id: String, val name: String, val color: Long, val kind: Kind)

    private fun system(id: String, name: String, color: Long) = Logo(id, name, color, Kind.SYSTEM)

    /** Generic logos have no fixed name: the app translates it (`logo_<id>`). */
    private fun generic(id: String, color: Long) = Logo(id, "", color, Kind.GENERIC)

    /** Every logo, in the order of the picker (the desktop's). */
    val all: List<Logo> = listOf(
        system("ubuntu", "Ubuntu", 0xe95420),
        system("debian", "Debian", 0xd70a53),
        system("fedora", "Fedora", 0x3c6eb4),
        system("rhel", "Red Hat", 0xee0000),
        system("centos", "CentOS", 0x9c27b0),
        system("rocky", "Rocky Linux", 0x10b981),
        system("alma", "AlmaLinux", 0x0f4266),
        system("arch", "Arch Linux", 0x1793d1),
        system("manjaro", "Manjaro", 0x35bf5c),
        system("endeavouros", "EndeavourOS", 0x7f3fbf),
        system("alpine", "Alpine", 0x0d597f),
        system("opensuse", "openSUSE", 0x73ba25),
        system("suse", "SUSE", 0x30ba78),
        system("mint", "Linux Mint", 0x87cf3e),
        system("popos", "Pop!_OS", 0x48b9c7),
        system("elementary", "elementary OS", 0x64baff),
        system("zorin", "Zorin OS", 0x15a6f0),
        system("kali", "Kali", 0x367bf0),
        system("gentoo", "Gentoo", 0x54487a),
        system("nixos", "NixOS", 0x5277c3),
        system("void", "Void Linux", 0x478061),
        system("raspberrypi", "Raspberry Pi", 0xc51a4a),
        system("linux", "Linux", 0xf5a524),
        system("freebsd", "FreeBSD", 0xab2b28),
        system("macos", "macOS", 0x8e8e93),
        system("windows", "Windows", 0x0078d4),
        generic("server", 0x4f7cff),
        generic("database", 0x12a594),
        generic("router", 0x0ea5e9),
        generic("firewall", 0xe5484d),
        generic("cloud", 0x3b82f6),
        generic("container", 0x1d63ed),
        generic("kubernetes", 0x326ce5),
        generic("web", 0x30a46c),
        generic("mail", 0xd6409f),
        generic("storage", 0x8e4ec6),
        generic("terminal", 0x475569),
        generic("iot", 0xf5a524),
        generic("security", 0xbf8700),
    )

    /** The logo with this id (as stored in `SshHost.icon`). */
    fun byId(id: String?): Logo? {
        val key = id?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return all.firstOrNull { it.id.equals(key, ignoreCase = true) }
    }

    /** The logo of a detected system (the `ID` of `/etc/os-release`, or `macos`, `windows`...). */
    fun forOs(os: String?): Logo? {
        val key = os?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
        val id = when (key) {
            "devuan" -> "debian"
            "raspbian" -> "raspberrypi"
            "linuxmint" -> "mint"
            "pop" -> "popos"
            "nobara" -> "fedora"
            "redhat" -> "rhel"
            "almalinux" -> "alma"
            "artix", "garuda", "archarm" -> "arch"
            "postmarketos" -> "alpine"
            "opensuse-leap", "opensuse-tumbleweed", "opensuse-microos" -> "opensuse"
            "sles", "sled" -> "suse"
            "darwin", "osx" -> "macos"
            else -> key
        }
        // A generic id would make "server" look detected.
        return byId(id)?.takeIf { it.kind == Kind.SYSTEM }
    }

    /** What a host shows: its chosen logo, else its system's, else none (the initial). */
    fun resolve(icon: String?, os: String?): Logo? = byId(icon) ?: forOs(os)

    fun of(host: SshHost): Logo? = resolve(host.icon, host.os)
}
