package com.termoak.app.data

import com.termoak.ffi.LinkTarget
import com.termoak.ffi.parseLink
import java.util.Locale

/**
 * An invitation link to a shared session: `termoak://join?server=…&token=…`
 * or `https://<server>/join/<token>` (also `/api/v1/join/<token>` and the
 * website's `/<lang>/join/<token>`). Read by the engine's one parser
 * ([parseLink]), the same in every app: a server under a path
 * (`https://example.com/termoak/join/<token>`) keeps it.
 */
data class JoinLinkRef(val server: String, val token: String) {
    companion object {
        fun parse(text: String?): JoinLinkRef? =
            (link(text) as? LinkTarget.Join)?.let { JoinLinkRef(it.server, it.token) }

        /** Same server, whatever the trailing slash or letter case. */
        fun sameServer(a: String?, b: String?): Boolean {
            fun norm(s: String?) = s?.trim()?.trimEnd('/')?.lowercase(Locale.ROOT)
            return a != null && b != null && norm(a) == norm(b)
        }
    }
}

/**
 * An invitation to create an account on a server (and maybe join a team):
 * `termoak://invite?server=…&token=…` or `https://<server>[/path]/invite/<code>`,
 * read by the engine's [parseLink]. Signing up with it fills its code.
 */
data class InviteLinkRef(val server: String, val token: String) {
    companion object {
        fun parse(text: String?): InviteLinkRef? =
            (link(text) as? LinkTarget.Invite)?.let { InviteLinkRef(it.server, it.code) }
    }
}

/** What a link or pasted text asks for, by the engine's rules (`null`: none). */
private fun link(text: String?): LinkTarget? {
    val t = text?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return runCatching { parseLink(t) }.getOrNull()
}
