package com.termoak.app.data

import com.termoak.ffi.HostGroup
import com.termoak.ffi.HostSettings

/**
 * A group's defaults for the hosts inside it and its subgroups (the
 * desktop's group settings): the engine applies them when connecting, the
 * nearest group winning and a host's own value above all. The app uses
 * these rules to show what a host gets from its groups. Pure (JVM tests).
 */
object GroupDefaults {
    /** [base] with [over]'s values on top (the engine's overlay: each value set in [over] wins; the environment is merged). */
    fun overlay(base: HostSettings, over: HostSettings): HostSettings = base.copy(
        env = base.env + over.env,
        port = over.port ?: base.port,
        username = over.username ?: base.username,
        identityId = over.identityId ?: base.identityId,
        keyId = over.keyId ?: base.keyId,
        jumpHostIds = over.jumpHostIds ?: base.jumpHostIds,
        startupSnippetId = over.startupSnippetId ?: base.startupSnippetId,
        keepaliveSecs = over.keepaliveSecs ?: base.keepaliveSecs,
        agentForwarding = over.agentForwarding ?: base.agentForwarding,
        term = over.term ?: base.term,
        theme = over.theme ?: base.theme,
        recordSessions = over.recordSessions ?: base.recordSessions,
        proxy = over.proxy ?: base.proxy,
    )

    /** The defaults a host in [groupId] gets from that group and the ones above it ([groups]: those of its account and vault). */
    fun inherited(groupId: String?, groups: List<HostGroup>): HostSettings {
        val byId = groups.associateBy { it.id }
        val chain = mutableListOf<HostGroup>()
        var id = groupId
        while (id != null && chain.size < 32) {
            val g = byId[id] ?: break
            if (g in chain) break
            chain += g
            id = g.parentId
        }
        // The top group first, the nearest one last (it wins).
        return chain.asReversed().fold(HostSettings()) { acc, g -> overlay(acc, g.settings) }
    }
}
