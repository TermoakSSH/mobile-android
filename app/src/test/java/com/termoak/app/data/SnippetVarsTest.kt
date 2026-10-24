package com.termoak.app.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.termoak.app.R
import com.termoak.ffi.SnippetContext
import com.termoak.ffi.SnippetIssue
import com.termoak.ffi.SnippetTemplate
import com.termoak.ffi.SnippetVariable
import com.termoak.ffi.SnippetVariableKind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Snippet variables in the app (the core's engine is a stand-in here: its
 * native library can't load on the JVM): the run dialog's values
 * (remembered, defaults, selects, secrets never remembered or prefilled),
 * missing values, each target with its own host's built-ins (values asked
 * for host built-ins only where there is no host), the editor's issues
 * with their line and column (Unicode scalar values) and texts, and the
 * startup snippet warning.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SnippetVarsTest {
    private val res get() = ApplicationProvider.getApplicationContext<Application>().resources

    private fun v(key: String, kind: SnippetVariableKind, default: String? = null, options: List<String> = emptyList(), needsHost: Boolean = false) =
        SnippetVariable(key, kind, key, default, options, needsHost)

    private val branch = v("Branch", SnippetVariableKind.PROMPT, "main")
    private val env = v("Env", SnippetVariableKind.SELECT, "dev", listOf("dev", "prod"))
    private val token = v("Token", SnippetVariableKind.SECRET)
    private val host = v("host", SnippetVariableKind.BUILTIN, needsHost = true)
    private val date = v("date", SnippetVariableKind.BUILTIN)

    private val rendered = mutableListOf<Pair<Map<String, String>, SnippetContext?>>()

    @Before
    fun engine() {
        SnippetEngine.parse = { SnippetTemplate(listOf(host, branch, env, token, date), emptyList()) }
        SnippetEngine.prompts = { _, ctx -> listOfNotNull(if (ctx?.host == null) host else null, branch, env, token) }
        SnippetEngine.render = { script, values, ctx ->
            rendered += values to ctx
            values.entries.fold(script) { s, (k, value) -> s.replace("{{$k}}", value) }.replace("{{host}}", ctx?.host ?: "?")
        }
    }

    @After
    fun reset() {
        SnippetEngine.parse = { com.termoak.ffi.parseSnippet(it) }
        SnippetEngine.prompts = { s, c -> com.termoak.ffi.snippetPrompts(s, c) }
        SnippetEngine.render = { s, v, c -> com.termoak.ffi.renderSnippetWith(s, v, c) }
    }

    @Test
    fun theDialogsValues() {
        val vars = SnippetVars.prompts("x", everyTargetHasHost = true)
        assertEquals(listOf("Branch", "Env", "Token"), vars.map { it.key })
        // Nothing remembered: the defaults (a select's first option); a secret starts empty.
        assertEquals(mapOf("Branch" to "main", "Env" to "dev", "Token" to ""), SnippetVars.initial(vars, emptyMap()))
        // Remembered values win, except a secret's and an option that is gone.
        val remembered = mapOf("Branch" to "release", "Env" to "staging", "Token" to "s3cr3t")
        assertEquals(mapOf("Branch" to "release", "Env" to "dev", "Token" to ""), SnippetVars.initial(vars, remembered))
        assertEquals("Token", SnippetVars.missing(vars, mapOf("Branch" to "main", "Env" to "dev", "Token" to " ")))
        assertNull(SnippetVars.missing(vars, mapOf("Branch" to "main", "Env" to "dev", "Token" to "x")))
        // The secret is never remembered.
        assertEquals(mapOf("Branch" to "main", "Env" to "dev"), SnippetVars.rememberable(vars, mapOf("Branch" to "main", "Env" to "dev", "Token" to "x")))
        // A terminal without a host also asks {{host}}.
        assertEquals("host", SnippetVars.prompts("x", everyTargetHasHost = false).first().key)
    }

    @Test
    fun eachTargetWithItsHost() {
        val values = mapOf("Branch" to "main", "host" to "typed.example")
        val web = SnippetContext(host = "10.0.0.5", label = "web1", user = "deploy", port = 22u, group = null)
        val out = SnippetVars.renderTargets("deploy {{Branch}} on {{host}}", values, listOf(web, null))
        assertEquals(listOf("deploy main on 10.0.0.5", "deploy main on typed.example"), out)
        // The value typed for {{host}} only goes to the target without a host.
        assertFalse(rendered[0].first.containsKey("host"))
        assertEquals("typed.example", rendered[1].first["host"])
        // Rendering can't fail the send: the script as written.
        SnippetEngine.render = { _, _, _ -> throw IllegalArgumentException("missing variable") }
        assertEquals("echo {{x}}", SnippetVars.textFor("echo {{x}}", emptyMap(), null))
    }

    @Test
    fun issuesWithTheirPlace() {
        // "a😀\n  {{select:Env}}": the tag starts at scalar value 5 (the emoji is one), line 2 column 3.
        val script = "a😀\n  {{select:Env}} {{prompt:}}"
        val t = SnippetTemplate(
            emptyList(),
            listOf(
                SnippetIssue(5u, 19u, "{{select:Env}}", "a select needs at least one option ({{select:Name|a|b}})"),
                SnippetIssue(20u, 31u, "{{prompt:}}", "the variable has no label"),
            ),
        )
        val issues = SnippetVars.issuesOf(script, t)
        assertEquals(listOf(2 to 3, 2 to 18), issues.map { it.line to it.column })
        assertEquals(res.getString(R.string.snippets_issue_select_options), issues[0].text.resolve(res))
        assertEquals(res.getString(R.string.snippets_issue_no_label), issues[1].text.resolve(res))
    }

    @Test
    fun issueTexts() {
        fun text(m: String) = SnippetVars.issueText(m).resolve(res)
        assertEquals(res.getString(R.string.snippets_issue_label_too_long, 64), text("the label is longer than 64 characters"))
        assertEquals(res.getString(R.string.snippets_issue_secret_default), text("a secret cannot have a default value"))
        assertEquals(res.getString(R.string.snippets_issue_too_many, 50), text("a snippet can have at most 50 variables"))
        assertEquals(
            res.getString(R.string.snippets_issue_kind_conflict, "Env", res.getString(R.string.snippets_kind_select)),
            text("\"Env\" is already a select variable"),
        )
        assertEquals("something new", text("something new"))
        assertEquals(res.getString(R.string.snippets_vars_kind_prompt_default, "Branch", "main"), SnippetVars.variableText(branch).resolve(res))
        assertEquals(res.getString(R.string.snippets_vars_kind_select, "Env", "dev, prod"), SnippetVars.variableText(env).resolve(res))
        assertEquals(res.getString(R.string.snippets_vars_kind_secret, "Token"), SnippetVars.variableText(token).resolve(res))
    }

    @Test
    fun startupSnippetsThatAsk() {
        // A secret has no default: it would be typed as written at login.
        assertTrue(SnippetVars.startupAsks("x"))
        SnippetEngine.prompts = { _, _ -> listOf(branch, env) }
        assertFalse(SnippetVars.startupAsks("x"))
    }
}
