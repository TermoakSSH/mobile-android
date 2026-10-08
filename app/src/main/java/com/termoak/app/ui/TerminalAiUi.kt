package com.termoak.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termoak.app.R
import com.termoak.app.TermoakApp
import com.termoak.app.asString
import com.termoak.app.data.AiProposal
import com.termoak.app.term.AiCommandRisk
import com.termoak.app.term.TermSession
import com.termoak.ffi.AiCommandSuggestion
import com.termoak.ffi.LastCommandInfo
import com.termoak.ffi.typeableCommand

/**
 * The AI over a terminal, in its bottom right corner (the prompt and what is
 * typed stay in view on the left): the "Command failed · Explain · Fix" chip
 * and the card of a command the AI proposes (typed at the prompt, never
 * run). The explanation opens in a sheet.
 */
@Composable
internal fun TerminalAiOverlay(app: TermoakApp, session: TermSession, modifier: Modifier = Modifier) {
    val ai = remember(session.id) { app.terminalAi.state(session) }
    val failed by session.failedCommand.collectAsState()
    var confirming by remember { mutableStateOf<AiCommandSuggestion?>(null) }
    val proposal = ai.proposal
    Column(modifier.padding(10.dp), horizontalAlignment = Alignment.End) {
        if (proposal != null) {
            AiProposalCard(
                proposal,
                onType = { s -> if (AiCommandRisk.of(s.risk).needsConfirmation) confirming = s else app.terminalAi.typeProposal(session) },
                onDismiss = { app.terminalAi.dismissProposal(session) },
            )
        } else {
            failed?.let { last ->
                FailedCommandChip(
                    last,
                    onExplain = { app.terminalAi.explainFailed(session) },
                    onFix = { app.terminalAi.fixFailed(session) },
                    onDismiss = { session.dismissFailed() },
                )
            }
        }
    }
    confirming?.let { s ->
        AlertDialog(
            onDismissRequest = { confirming = null },
            icon = { Icon(Icons.Outlined.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.terminal_ai_dangerous_title)) },
            text = { Text(listOf(s.command, s.explanation).filter { it.isNotBlank() }.joinToString("\n\n")) },
            confirmButton = {
                TextButton(onClick = { confirming = null; app.terminalAi.typeProposal(session) }) {
                    Text(stringResource(R.string.terminal_ai_insert_anyway), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirming = null }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
    ai.explanation?.let { AiExplanationSheet(it) { ai.explanation = null } }
}

/** "Command failed (exit 2) · Explain · Fix ×". */
@Composable
private fun FailedCommandChip(last: LastCommandInfo, onExplain: () -> Unit, onFix: () -> Unit, onDismiss: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.background(TermBarBg, shape).border(1.dp, Brand.Amber.copy(alpha = 0.5f), shape).padding(start = 12.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Warning, null, Modifier.size(16.dp), tint = Brand.Amber)
        Text(
            last.exitCode?.let { stringResource(R.string.terminal_ai_chip_failed_exit, it) } ?: stringResource(R.string.terminal_ai_chip_failed),
            Modifier.padding(start = 6.dp).widthIn(max = 200.dp), color = TermKeyFg, style = MaterialTheme.typography.labelMedium,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        TextButton(onClick = onExplain) {
            Icon(Icons.Outlined.HelpOutline, null, Modifier.size(16.dp))
            Text(stringResource(R.string.terminal_ai_explain), Modifier.padding(start = 4.dp), fontSize = 13.sp)
        }
        TextButton(onClick = onFix) {
            Icon(Icons.Outlined.AutoFixHigh, null, Modifier.size(16.dp))
            Text(stringResource(R.string.terminal_ai_fix), Modifier.padding(start = 4.dp), fontSize = 13.sp)
        }
        IconButton(onClick = onDismiss, Modifier.size(32.dp)) {
            Icon(Icons.Outlined.Close, stringResource(R.string.terminal_ai_dismiss), Modifier.size(16.dp), tint = TermKeyFg.copy(alpha = 0.7f))
        }
    }
}

/** The command the AI proposes: asking, ready ("Type it", Copy) with its risk and explanation, typed, or an error. */
@Composable
private fun AiProposalCard(p: AiProposal, onType: (AiCommandSuggestion) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.widthIn(max = 440.dp).background(TermBarBg, shape).border(1.dp, TermKeyFg.copy(alpha = 0.15f), shape)
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Text(
                stringResource(R.string.terminal_ai_suggested_title), Modifier.weight(1f).padding(start = 6.dp),
                color = TermKeyFg, style = MaterialTheme.typography.labelLarge,
            )
            IconButton(onClick = onDismiss, Modifier.size(36.dp)) {
                Icon(Icons.Outlined.Close, stringResource(R.string.terminal_ai_dismiss), Modifier.size(16.dp), tint = TermKeyFg.copy(alpha = 0.7f))
            }
        }
        Column(Modifier.padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (val st = p.state) {
                AiProposal.State.Asking -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    Text(
                        stringResource(if (p.requestLine == null) R.string.terminal_ai_asking_fix else R.string.terminal_ai_asking_request),
                        Modifier.padding(start = 8.dp), color = TermKeyFg.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall,
                    )
                }
                is AiProposal.State.Failed -> Text(st.message.asString(), color = Brand.Red, style = MaterialTheme.typography.bodySmall)
                is AiProposal.State.Ready -> {
                    Suggestion(st.suggestion)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onType(st.suggestion) }) {
                            Icon(Icons.Outlined.Edit, null, Modifier.size(16.dp))
                            Text(stringResource(R.string.terminal_ai_insert), Modifier.padding(start = 6.dp))
                        }
                        OutlinedButton(onClick = {
                            context.getSystemService(ClipboardManager::class.java)
                                ?.setPrimaryClip(ClipData.newPlainText("command", st.suggestion.command))
                        }) {
                            Icon(Icons.Outlined.ContentCopy, null, Modifier.size(16.dp))
                            Text(stringResource(R.string.term_copy), Modifier.padding(start = 6.dp))
                        }
                    }
                    Text(stringResource(R.string.terminal_ai_review), color = TermKeyFg.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
                }
                is AiProposal.State.Typed -> {
                    Suggestion(st.suggestion)
                    Text(stringResource(R.string.terminal_ai_typed), color = TermKeyFg.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun Suggestion(s: AiCommandSuggestion) {
    val command = remember(s.command) { runCatching { typeableCommand(s.command) }.getOrDefault(s.command) }
    SelectionContainer {
        Text(
            command, Modifier.fillMaxWidth().background(TermKeyFg.copy(alpha = 0.08f), RoundedCornerShape(8.dp)).padding(8.dp),
            color = TermKeyFg, fontFamily = FontFamily.Monospace, fontSize = 13.sp,
        )
    }
    RiskBadge(AiCommandRisk.of(s.risk))
    if (s.explanation.isNotBlank()) {
        Text(s.explanation, color = TermKeyFg.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall, maxLines = 4, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RiskBadge(risk: AiCommandRisk) {
    val (color, text, icon) = when (risk) {
        AiCommandRisk.READ -> Triple(Brand.Green, R.string.terminal_ai_risk_read, Icons.Outlined.Visibility)
        AiCommandRisk.WRITE -> Triple(Brand.Amber, R.string.terminal_ai_risk_write, Icons.Outlined.Edit)
        AiCommandRisk.DANGEROUS -> Triple(Brand.Red, R.string.terminal_ai_risk_dangerous, Icons.Outlined.Warning)
    }
    Row(
        Modifier.background(color.copy(alpha = 0.14f), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(12.dp), tint = color)
        Text(stringResource(text), Modifier.padding(start = 4.dp), color = color, style = MaterialTheme.typography.labelSmall)
    }
}

/** Why a command failed, in the AI's words (Markdown). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiExplanationSheet(item: com.termoak.app.data.AiExplanationItem, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.size(12.dp))
            when {
                item.error != null -> Text(item.error.asString(), color = MaterialTheme.colorScheme.error)
                item.answer != null -> {
                    SelectionContainer { MarkdownText(item.answer) }
                    if (!item.provider.isNullOrBlank()) {
                        Text(
                            stringResource(R.string.terminal_ai_provider, item.provider), Modifier.padding(top = 12.dp),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                else -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.terminal_ai_asking), Modifier.padding(start = 10.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.navigationBarsPadding().size(24.dp))
        }
    }
}
