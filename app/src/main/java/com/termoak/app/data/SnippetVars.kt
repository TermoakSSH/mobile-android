package com.termoak.app.data

import com.termoak.app.R
import com.termoak.app.UiText
import com.termoak.app.uiText
import com.termoak.ffi.SnippetContext
import com.termoak.ffi.SnippetTemplate
import com.termoak.ffi.SnippetVariable
import com.termoak.ffi.SnippetVariableKind
import com.termoak.ffi.TermoakCore
import com.termoak.ffi.parseSnippet
import com.termoak.ffi.renderSnippetWith
import com.termoak.ffi.snippetPrompts

/**
 * The core's template engine (docs/SSH-PRO.md §7). The JVM tests, where
 * the engine's native library can't load, give stand-ins.
 */
object SnippetEngine {
    @Volatile var parse: (String) -> SnippetTemplate = { parseSnippet(it) }
    @Volatile var prompts: (String, SnippetContext?) -> List<SnippetVariable> = { s, c -> snippetPrompts(s, c) }
    @Volatile var render: (String, Map<String, String>, SnippetContext?) -> String = { s, v, c -> renderSnippetWith(s, v, c) }
}

/** An issue of the editor: where (line and column, from 1), the tag as written and what is wrong. */
data class SnippetIssueView(val line: Int, val column: Int, val tag: String, val text: UiText)

/**
 * Snippet variables, as the desktop's views/snippet_vars.rs: the fields of
 * the run dialog (prompts with their default, selects as a picker, secrets
 * masked and never remembered; the host built-ins only when a target has no
 * host), the values remembered on this phone per snippet and host (the
 * core's, never secrets), the issues of the editor and the text of each
 * target with its own host's built-ins.
 */
object SnippetVars {
    /** The variables to ask for [script]: the host built-ins too when some target has no host. */
    fun prompts(script: String, everyTargetHasHost: Boolean): List<SnippetVariable> =
        SnippetEngine.prompts(script, if (everyTargetHasHost) SnippetContext(host = "") else null)

    /** The values the fields start with: the remembered ones (never secrets), else the defaults; a select's is one of its options. */
    fun initial(vars: List<SnippetVariable>, remembered: Map<String, String>): Map<String, String> = vars.associate { v ->
        val r = remembered[v.key]?.takeIf { v.kind != SnippetVariableKind.SECRET }
        v.key to when (v.kind) {
            SnippetVariableKind.SELECT -> (r ?: v.defaultValue)?.takeIf { it in v.options } ?: v.options.firstOrNull().orEmpty()
            SnippetVariableKind.SECRET -> ""
            else -> r ?: v.defaultValue.orEmpty()
        }
    }

    /** The label of the first variable without a value, or `null` when all have one. */
    fun missing(vars: List<SnippetVariable>, values: Map<String, String>): String? =
        vars.firstOrNull { values[it.key].isNullOrBlank() }?.label

    /** What may be remembered: never a secret (the core drops them too). */
    fun rememberable(vars: List<SnippetVariable>, values: Map<String, String>): Map<String, String> {
        val secrets = vars.filter { it.kind == SnippetVariableKind.SECRET }.map { it.key }.toSet()
        return values.filterKeys { it !in secrets }
    }

    /**
     * The text for each target ([contexts], `null`: a terminal without a
     * host) with the same values: each its own built-ins; values asked for
     * the host built-ins only go to the targets without a host.
     */
    fun renderTargets(script: String, values: Map<String, String>, contexts: List<SnippetContext?>): List<String> {
        val hostKeys = SnippetEngine.parse(script).variables.filter { it.needsHost }.map { it.key }.toSet()
        return contexts.map { ctx ->
            val v = if (ctx?.host != null) values - hostKeys else values
            SnippetEngine.render(script, v, ctx)
        }
    }

    /** A snippet as it is typed in one target; the script as written if it can't be rendered. */
    fun textFor(script: String, values: Map<String, String>, context: SnippetContext?): String =
        runCatching { renderTargets(script, values, listOf(context)).first() }.getOrDefault(script)

    /** The built-ins of a host (`null`: no host, or it is gone). */
    fun context(core: TermoakCore, hostId: String?, accountId: String?): SnippetContext? =
        hostId?.let { runCatching { core.snippetContext(it, accountId) }.getOrNull() }

    /** The issues of a script, with their place (offsets are in Unicode scalar values) and text. */
    fun issues(script: String): List<SnippetIssueView> = issuesOf(script, SnippetEngine.parse(script))

    fun issuesOf(script: String, t: SnippetTemplate): List<SnippetIssueView> = t.issues.map { i ->
        val before = prefix(script, i.start.toInt())
        val line = before.count { it == '\n' } + 1
        val lastLine = before.substringAfterLast('\n')
        SnippetIssueView(line, lastLine.codePointCount(0, lastLine.length) + 1, i.tag, issueText(i.message))
    }

    /** The first [n] Unicode scalar values of [s]. */
    private fun prefix(s: String, n: Int): String {
        if (n <= 0) return ""
        val count = s.codePointCount(0, s.length)
        return if (n >= count) s else s.substring(0, s.offsetByCodePoints(0, n))
    }

    /** The text of a core issue message, translated when it is a known one. */
    fun issueText(message: String): UiText {
        fun number(default: Int) = Regex("\\d+").find(message)?.value?.toIntOrNull() ?: default
        return when {
            message == "the variable has no label" -> uiText(R.string.snippets_issue_no_label)
            message.startsWith("the label is longer than") -> uiText(R.string.snippets_issue_label_too_long, number(64))
            message.startsWith("a select needs at least one option") -> uiText(R.string.snippets_issue_select_options)
            message == "a secret cannot have a default value" -> uiText(R.string.snippets_issue_secret_default)
            message.startsWith("a snippet can have at most") -> uiText(R.string.snippets_issue_too_many, number(50))
            else -> {
                // "\"Env\" is already a select variable"
                val m = Regex("^\"(.*)\" is already a (.+) variable$").find(message)
                if (m != null) {
                    val kind = when (m.groupValues[2]) {
                        "built-in" -> uiText(R.string.snippets_kind_builtin)
                        "prompt" -> uiText(R.string.snippets_kind_prompt)
                        "select" -> uiText(R.string.snippets_kind_select)
                        "secret" -> uiText(R.string.snippets_kind_secret)
                        else -> UiText.Raw(m.groupValues[2])
                    }
                    uiText(R.string.snippets_issue_kind_conflict, m.groupValues[1], kind)
                } else {
                    UiText.Raw(message)
                }
            }
        }
    }

    /** "Env · one of: dev, prod" of a variable, for the editor's list. */
    fun variableText(v: SnippetVariable): UiText = when (v.kind) {
        SnippetVariableKind.BUILTIN -> uiText(R.string.snippets_vars_kind_builtin, v.label)
        SnippetVariableKind.PROMPT -> v.defaultValue?.takeIf { it.isNotEmpty() }
            ?.let { uiText(R.string.snippets_vars_kind_prompt_default, v.label, it) }
            ?: uiText(R.string.snippets_vars_kind_prompt, v.label)
        SnippetVariableKind.SELECT -> uiText(R.string.snippets_vars_kind_select, v.label, v.options.joinToString(", "))
        SnippetVariableKind.SECRET -> uiText(R.string.snippets_vars_kind_secret, v.label)
    }

    /** A startup snippet that asks for values (they are typed as written at login): warn in the host editor. */
    fun startupAsks(script: String): Boolean = runCatching { prompts(script, true).any { it.defaultValue == null } }.getOrDefault(false)
}
