package com.termoak.app.data

import com.termoak.ffi.HostGroup

/**
 * Pure rules of the AI screens (the iOS app's; JVM tests): the provider and
 * model a task asks for, which hosts a group or a tag reaches, and how long
 * a host took.
 */
object AiTaskForm {
    /** `provider::model`, or the provider alone with its default model (`null`: the server's default). */
    fun provider(key: String?, model: String?): String? {
        if (key.isNullOrBlank()) return null
        return if (model.isNullOrBlank()) key else "$key::$model"
    }

    /** [groupId] and every group inside it, at any depth. */
    fun groupAndSubgroups(groups: List<HostGroup>, groupId: String): Set<String> {
        val out = mutableSetOf(groupId)
        var added = true
        while (added) {
            added = false
            for (g in groups) if (g.parentId in out && out.add(g.id)) added = true
        }
        return out
    }

    /** "1 min 5 s", "800 ms": how long a host took. */
    fun duration(ms: Long): String {
        if (ms < 1000) return "$ms ms"
        val s = ms / 1000
        if (s < 60) return "$s s"
        return if (s % 60 == 0L) "${s / 60} min" else "${s / 60} min ${s % 60} s"
    }
}
