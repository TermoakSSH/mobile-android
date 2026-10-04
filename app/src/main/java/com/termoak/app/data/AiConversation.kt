package com.termoak.app.data

import org.json.JSONArray
import org.json.JSONObject

/** A piece of the conversation with the AI. */
sealed class Turn {
    data class User(val text: String) : Turn()
    data class Assistant(val text: String) : Turn()
    /** The model's reasoning (summary). */
    data class Reasoning(val text: String) : Turn()
    data class Tool(val id: String, val name: String, val input: String, val output: String?, val error: Boolean) : Turn()
}

/**
 * The conversation comes from the task messages (`rawJson` from `getAiTask`):
 * each message has a `role` and `content`, a list of `text`, `reasoning`,
 * `tool_call` and `tool_result` parts.
 */
fun parseConversation(rawJson: String): List<Turn> {
    val messages = runCatching { JSONObject(rawJson).optJSONArray("messages") }.getOrNull() ?: return emptyList()
    val out = mutableListOf<Turn>()
    // Tool call → its position in `out`, to attach its result.
    val tools = mutableMapOf<String, Int>()
    for (i in 0 until messages.length()) {
        val m = messages.optJSONObject(i) ?: continue
        val parts: JSONArray = m.optJSONArray("content") ?: continue
        val role = m.optString("role")
        val text = StringBuilder()
        fun flush() {
            val t = text.toString().trim()
            text.clear()
            if (t.isEmpty()) return
            if (role == "user") stripContext(t).takeIf { it.isNotEmpty() }?.let { out += Turn.User(it) }
            else out += Turn.Assistant(t)
        }
        for (j in 0 until parts.length()) {
            val p = parts.optJSONObject(j) ?: continue
            when (p.optString("type")) {
                "text" -> text.append(p.optString("text"))
                "reasoning" -> {
                    flush()
                    p.optString("text").trim().takeIf { it.isNotEmpty() }?.let { out += Turn.Reasoning(it) }
                }
                "tool_call" -> {
                    flush()
                    val id = p.optString("id")
                    tools[id] = out.size
                    out += Turn.Tool(id, p.optString("name"), toolSummary(p.opt("input")), null, false)
                }
                "tool_result" -> tools[p.optString("id")]?.let { idx ->
                    (out.getOrNull(idx) as? Turn.Tool)?.let { t ->
                        out[idx] = t.copy(output = p.optString("content"), error = p.optBoolean("is_error"))
                    }
                }
            }
        }
        flush()
    }
    return out
}

/** Summary of a tool's arguments: the command if there is one; otherwise the trimmed JSON. */
fun toolSummary(input: Any?): String = when (input) {
    null, JSONObject.NULL -> ""
    is JSONObject -> input.optString("command").ifBlank { input.toString().take(300) }
    is String -> runCatching { toolSummary(JSONObject(input)) }.getOrDefault(input.take(300))
    else -> input.toString().take(300)
}

/** Removes the leading `<context>…</context>` blocks (the server's and the copilot's). */
fun stripContext(text: String): String {
    var t = text.trimStart()
    while (t.startsWith("<context>")) {
        val end = t.indexOf("</context>")
        if (end < 0) return ""
        t = t.substring(end + "</context>".length).trimStart()
    }
    return t.trimEnd()
}
