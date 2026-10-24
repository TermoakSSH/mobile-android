package com.termoak.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.termoak.app.R
import com.termoak.app.TermoakApp
import com.termoak.app.asString
import com.termoak.app.data.SnippetEngine
import com.termoak.app.data.SnippetIssueView
import com.termoak.app.data.SnippetVars
import com.termoak.ffi.Snippet
import com.termoak.ffi.SnippetVariable
import com.termoak.ffi.SnippetVariableKind

/**
 * Under the editor's script: its variables ("Branch · asked (main)") and
 * its issues ("Line 2, column 6: {{select:Env}} — a list needs at least one
 * option"), as you type.
 */
@Composable
fun SnippetVariablesPanel(variables: List<SnippetVariable>, issues: List<SnippetIssueView>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (issues.isNotEmpty()) {
            Text(
                pluralStringResource(R.plurals.snippets_issue_title, issues.size, issues.size),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error,
            )
            issues.forEach { i ->
                Text(
                    stringResource(R.string.snippets_issue_line, i.line, i.column, i.tag, i.text.asString()),
                    Modifier.testTag("snippet-issue"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                )
            }
        }
        if (variables.isNotEmpty()) {
            Text(
                stringResource(R.string.snippets_vars_title) + " · " + pluralStringResource(R.plurals.snippets_vars_count, variables.size, variables.size),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            variables.forEach { v ->
                Text(SnippetVars.variableText(v).asString(), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * A snippet waiting for its variables before it goes to [targets] (each a
 * host id and its account; a `null` host is a terminal without one).
 * [then] gets the values (empty when nothing is asked).
 */
class SnippetAsk(
    val snippet: Snippet,
    val variables: List<SnippetVariable>,
    val remembered: Map<String, String>,
    /** The one host the run goes to (values are remembered per snippet and host), or `null`. */
    val hostId: String?,
    val run: Boolean,
    val then: (Map<String, String>) -> Unit,
)

/**
 * Prepares [snippet] for [targets] (host id + account of each): when it asks
 * nothing (no prompts, or only built-ins every target fills), [then] runs
 * at once and `null` is returned; otherwise the [SnippetAsk] to show with
 * [SnippetAskDialog].
 */
fun askSnippet(
    app: TermoakApp,
    snippet: Snippet,
    targets: List<Pair<String?, String?>>,
    run: Boolean,
    then: (Map<String, String>) -> Unit,
): SnippetAsk? {
    val withHosts = targets.isNotEmpty() && targets.all { (h, a) -> SnippetVars.context(app.core, h, a) != null }
    val vars = runCatching { SnippetVars.prompts(snippet.script, withHosts) }.getOrDefault(emptyList())
    if (vars.isEmpty()) {
        then(emptyMap())
        return null
    }
    val host = targets.singleOrNull()?.first
    val remembered = if (snippet.id.isEmpty()) emptyMap() else runCatching { app.core.snippetValues(snippet.id, host) }.getOrDefault(emptyMap())
    return SnippetAsk(snippet, vars, remembered, host, run) { values ->
        if (snippet.id.isNotEmpty()) {
            runCatching { app.core.rememberSnippetValues(snippet.id, host, snippet.script, SnippetVars.rememberable(vars, values)) }
        }
        then(values)
    }
}

/** The run dialog of [ask]: one field per variable, a picker for a list, a masked field for a secret. */
@Composable
fun SnippetAskDialog(ask: SnippetAsk, onDismiss: () -> Unit) {
    val values = remember(ask) { mutableStateMapOf<String, String>().apply { putAll(SnippetVars.initial(ask.variables, ask.remembered)) } }
    var missing by remember(ask) { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(ask.snippet.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SnippetFields(ask.variables, values, missing) { k, v -> values[k] = v; missing = null }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val m = SnippetVars.missing(ask.variables, values)
                if (m != null) {
                    missing = m
                } else {
                    onDismiss()
                    ask.then(values.toMap())
                }
            }, modifier = Modifier.testTag("snippet-run")) {
                Text(stringResource(if (ask.run) R.string.snippets_run else R.string.common_paste))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

/** The fields of the variables (also drawn by the screenshot tests). */
@Composable
fun SnippetFields(
    variables: List<SnippetVariable>,
    values: Map<String, String>,
    missing: String?,
    onValue: (String, String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        variables.forEach { v ->
            val value = values[v.key].orEmpty()
            when (v.kind) {
                SnippetVariableKind.SELECT -> Picker(v.label, value, null, v.options.map { it to it }) { onValue(v.key, it) }
                SnippetVariableKind.SECRET -> {
                    var shown by remember(v.key) { mutableStateOf(false) }
                    OutlinedTextField(
                        value, { onValue(v.key, it) }, Modifier.fillMaxWidth().testTag("snippet-secret"),
                        label = { Text(stringResource(R.string.snippets_vars_secret_label, v.label)) }, singleLine = true,
                        visualTransformation = if (shown) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                        trailingIcon = {
                            IconButton(onClick = { shown = !shown }) {
                                Icon(if (shown) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, stringResource(R.string.snippets_vars_show_secret))
                            }
                        },
                        supportingText = { Text(stringResource(R.string.snippets_vars_secret_hint)) },
                        isError = missing == v.label,
                    )
                }
                SnippetVariableKind.BUILTIN -> OutlinedTextField(
                    value, { onValue(v.key, it) }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.snippets_vars_builtin_label, v.label)) }, singleLine = true,
                    supportingText = { Text(stringResource(R.string.snippets_vars_builtin_hint)) },
                    isError = missing == v.label,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                )
                SnippetVariableKind.PROMPT -> OutlinedTextField(
                    value, { onValue(v.key, it) }, Modifier.fillMaxWidth(),
                    label = { Text(v.label) }, singleLine = true, isError = missing == v.label,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                )
            }
        }
        missing?.let {
            Text(stringResource(R.string.snippets_missing_value, it), color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** The editor's state of a script: its variables and issues, parsed by the core as you type. */
@Composable
fun rememberSnippetCheck(script: String): Pair<List<SnippetVariable>, List<SnippetIssueView>> = remember(script) {
    runCatching {
        val t = SnippetEngine.parse(script)
        t.variables to SnippetVars.issuesOf(script, t)
    }.getOrDefault(emptyList<SnippetVariable>() to emptyList())
}
