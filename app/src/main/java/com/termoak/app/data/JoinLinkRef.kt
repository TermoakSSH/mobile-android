package com.termoak.app.data

import java.net.URI
import java.net.URLDecoder
import java.util.Locale

/**
 * An invitation link: `termoak://join?server=…&token=…` or
 * `https://<server>/join/<token>` (also `/api/v1/join/<token>` and the
 * website's `/<lang>/join/<token>`). A server that lives under a path
 * (`https://example.com/termoak/join/<token>`) keeps it, like on iOS.
 */
data class JoinLinkRef(val server: String, val token: String) {
    companion object {
        private val TOKEN = Regex("^[A-Za-z0-9_-]+$")

        fun parse(text: String?): JoinLinkRef? {
            val trimmed = text?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
            val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
            val parts = uri.rawPath.orEmpty().split('/').filter { it.isNotEmpty() }
            if (scheme == "termoak") {
                if (uri.host?.lowercase(Locale.ROOT) != "join") return null
                val query = queryOf(uri.rawQuery)
                val server = query["server"]?.trim()?.takeIf { it.isNotEmpty() } ?: return null
                val serverScheme = runCatching { URI(server).scheme?.lowercase(Locale.ROOT) }.getOrNull()
                if (serverScheme != "https" && serverScheme != "http") return null
                val token = query["token"]?.trim() ?: parts.lastOrNull() ?: return null
                return if (TOKEN.matches(token)) JoinLinkRef(normalize(server), token) else null
            }
            if (scheme != "https" && scheme != "http") return null
            val host = uri.host ?: return null
            val i = parts.lastIndexOf("join")
            val token = parts.getOrNull(i + 1)
            if (i < 0 || token == null || !TOKEN.matches(token)) return null
            // What comes before `/join` is the server's own path, without `/api/v1`
            // nor the language of the website (`/es`).
            var prefix = parts.subList(0, i)
            if (prefix.takeLast(2) == listOf("api", "v1")) prefix = prefix.dropLast(2)
            if (prefix.size == 1 && prefix[0].length == 2) prefix = emptyList()
            val port = if (uri.port > 0) ":${uri.port}" else ""
            val path = if (prefix.isEmpty()) "" else prefix.joinToString("/", prefix = "/")
            return JoinLinkRef("$scheme://$host$port$path", token)
        }

        /** Same server, whatever the trailing slash or letter case. */
        fun sameServer(a: String?, b: String?): Boolean {
            fun norm(s: String?) = s?.let(::normalize)?.lowercase(Locale.ROOT)
            return a != null && b != null && norm(a) == norm(b)
        }

        private fun normalize(server: String) = server.trim().trimEnd('/')

        private fun queryOf(raw: String?): Map<String, String> =
            raw.orEmpty().split('&').filter { it.isNotEmpty() }.associate { pair ->
                val k = pair.substringBefore('=')
                val v = pair.substringAfter('=', "")
                decode(k) to decode(v)
            }

        private fun decode(s: String) = runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)
    }
}

/**
 * An invitation to create an account on a server (and maybe join a team):
 * `termoak://invite?server=…&token=…` or `https://<server>[/path]/invite/<code>`,
 * like the desktop's and iOS's. Signing up with it fills its code.
 */
data class InviteLinkRef(val server: String, val token: String) {
    companion object {
        private val TOKEN = Regex("^[A-Za-z0-9_-]+$")

        fun parse(text: String?): InviteLinkRef? {
            val trimmed = text?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
            val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
            val parts = uri.rawPath.orEmpty().split('/').filter { it.isNotEmpty() }
            if (scheme == "termoak") {
                if (uri.host?.lowercase(Locale.ROOT) != "invite") return null
                val query = uri.rawQuery.orEmpty().split('&').filter { it.isNotEmpty() }.associate { pair ->
                    decode(pair.substringBefore('=')) to decode(pair.substringAfter('=', ""))
                }
                val server = query["server"]?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() } ?: return null
                val serverScheme = runCatching { URI(server).scheme?.lowercase(Locale.ROOT) }.getOrNull()
                if (serverScheme != "https" && serverScheme != "http") return null
                val token = query["token"]?.trim() ?: parts.lastOrNull() ?: return null
                return if (TOKEN.matches(token)) InviteLinkRef(server, token) else null
            }
            if (scheme != "https" && scheme != "http") return null
            val host = uri.host ?: return null
            val i = parts.lastIndexOf("invite")
            val token = parts.getOrNull(i + 1)
            if (i < 0 || token == null || !TOKEN.matches(token)) return null
            var prefix = parts.subList(0, i)
            if (prefix.takeLast(2) == listOf("api", "v1")) prefix = prefix.dropLast(2)
            if (prefix.size == 1 && prefix[0].length == 2) prefix = emptyList()
            val port = if (uri.port > 0) ":${uri.port}" else ""
            val path = if (prefix.isEmpty()) "" else prefix.joinToString("/", prefix = "/")
            return InviteLinkRef("$scheme://$host$port$path", token)
        }

        private fun decode(s: String) = runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)
    }
}
