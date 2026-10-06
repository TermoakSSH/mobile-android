package com.termoak.app.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.termoak.app.R
import com.termoak.app.userMessage
import com.termoak.app.TermoakApp
import com.termoak.app.data.Turn
import com.termoak.app.data.parseConversation
import com.termoak.app.data.stripContext
import com.termoak.app.data.toolSummary
import com.termoak.ffi.AiApproval
import com.termoak.ffi.AiPermissionMode
import com.termoak.ffi.AiTask
import com.termoak.ffi.AiTaskRequest
import com.termoak.ffi.AiTaskStatus
import com.termoak.ffi.TermoakException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
private fun statusStyle(status: AiTaskStatus): Pair<String, Color> = when (status) {
    AiTaskStatus.QUEUED -> stringResource(R.string.ai_status_queued) to MaterialTheme.colorScheme.onSurfaceVariant
    AiTaskStatus.RUNNING -> stringResource(R.string.ai_status_running) to Brand.Blue
    AiTaskStatus.WAITING_APPROVAL -> stringResource(R.string.ai_status_waiting) to Brand.Amber
    AiTaskStatus.COMPLETED -> stringResource(R.string.ai_status_completed) to Brand.Green
    AiTaskStatus.FAILED -> stringResource(R.string.ai_status_failed) to Brand.Red
    AiTaskStatus.CANCELLED -> stringResource(R.string.ai_status_cancelled) to MaterialTheme.colorScheme.onSurfaceVariant
    AiTaskStatus.UNKNOWN -> "—" to MaterialTheme.colorScheme.onSurfaceVariant
}

private val AiTask.active get() =
    status == AiTaskStatus.QUEUED || status == AiTaskStatus.RUNNING || status == AiTaskStatus.WAITING_APPROVAL

@Composable
fun AiScreen(app: TermoakApp, nav: NavHostController) {
    val resources = LocalResources.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val loggedIn by app.accounts.loggedIn.collectAsState()
    val accountList by app.accounts.list.collectAsState()
    // The AI works on one account at a time (its tasks and approvals).
    var accountId by remember { mutableStateOf(app.accounts.aiAccount()?.id) }
    var tasks by remember { mutableStateOf<List<AiTask>>(emptyList()) }
    var approvals by remember { mutableStateOf<List<AiApproval>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }

    fun reload() {
        if (loggedIn != true) return
        val handle = app.accounts.handleOrCurrent(accountId) ?: return
        scope.launch {
            loading = true
            try {
                tasks = handle.listAiTasks(50u)
                approvals = handle.listPendingApprovals()
                app.accounts.refreshApprovals()
            } catch (e: TermoakException) {
                snackbar.showSnackbar(e.userMessage(resources, R.string.ai_load_tasks_failed))
            } finally {
                loading = false
            }
        }
    }
    LaunchedEffect(loggedIn, accountId) {
        if (app.accounts.account(accountId)?.status != com.termoak.ffi.AccountStatus.ACTIVE) accountId = app.accounts.aiAccount()?.id
        reload()
    }
    LaunchedEffect(Unit) { app.accounts.changes.collect { if (it == "ai" || it == "lagged") reload() } }

    if (loggedIn != true) {
        ScreenScaffold(title = stringResource(R.string.section_ai), large = true) { padding ->
            EmptyState(
                Icons.Outlined.CloudOff, stringResource(R.string.ai_signed_out_title),
                stringResource(R.string.ai_signed_out_text),
                Modifier.padding(padding), action = stringResource(R.string.common_sign_in), onAction = { nav.navigate(Routes.login()) },
            )
        }
        return
    }

    val active = accountList.filter { it.status == com.termoak.ffi.AccountStatus.ACTIVE }
    ScreenScaffold(
        title = stringResource(R.string.section_ai),
        large = true,
        header = if (active.size > 1) {
            {
                AiAccountPicker(active, accountId) {
                    accountId = it
                    app.accounts.aiAccountId = it
                    tasks = emptyList()
                    approvals = emptyList()
                }
            }
        } else null,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { nav.navigate(Routes.AI_NEW) },
                icon = { Icon(Icons.Outlined.Add, null) },
                text = { Text(stringResource(R.string.ai_new_task)) },
            )
        },
    ) { padding ->
        PullToRefreshBox(loading, { reload() }, Modifier.fillMaxSize().padding(padding)) {
            if (tasks.isEmpty() && approvals.isEmpty()) {
                EmptyState(
                    Icons.Outlined.AutoAwesome, stringResource(R.string.ai_empty_title),
                    stringResource(R.string.ai_empty_text),
                    action = stringResource(R.string.ai_new_task), onAction = { nav.navigate(Routes.AI_NEW) },
                )
                return@PullToRefreshBox
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                if (approvals.isNotEmpty()) {
                    item { SectionLabel(stringResource(R.string.ai_waiting_approval)) }
                    items(approvals, key = { "ap" + it.id }) { a ->
                        ApprovalCard(a, taskTitle = tasks.firstOrNull { it.id == a.taskId }?.title) { approve, always ->
                            scope.launch {
                                runCatching { app.accounts.handleOrCurrent(accountId)?.decideApproval(a.taskId, a.id, approve, always) }
                                    .onFailure { snackbar.showSnackbar(it.userMessage(resources, R.string.error_decide_failed)) }
                                reload()
                            }
                        }
                    }
                }
                item { SectionLabel(stringResource(R.string.ai_tasks)) }
                items(tasks, key = { it.id }) { t ->
                    val (label, color) = statusStyle(t.status)
                    CardBox(Modifier.clickable { nav.navigate(Routes.aiTask(t.id, accountId)) }) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    t.title.ifBlank { t.prompt }, Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                )
                                if (t.active) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = color)
                            }
                            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Pill(label, color)
                                Text(
                                    relativeTime(t.updatedAt) + (t.usedProvider ?: t.provider).let { " · $it" },
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            (t.error ?: t.result)?.takeIf { it.isNotBlank() }?.let {
                                Text(
                                    it, Modifier.padding(top = 8.dp), maxLines = 3, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Action the AI wants to take that needs your permission. */
@Composable
fun ApprovalCard(a: AiApproval, taskTitle: String?, onDecide: (approve: Boolean, always: Boolean) -> Unit) {
    ApprovalCard(taskTitle ?: stringResource(R.string.ai_asks_permission), a.summary, toolSummary(a.inputJson), onDecide)
}

/** Approval card (amber border): summary, command and Approve · Deny · Always approve. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ApprovalCard(
    title: String,
    summary: String,
    command: String,
    onDecide: (approve: Boolean, always: Boolean) -> Unit,
    modifier: Modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
) {
    Surface(
        modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, Brand.Amber.copy(alpha = 0.7f)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Build, null, Modifier.size(18.dp), tint = Brand.Amber)
                Text(
                    title, Modifier.padding(start = 8.dp).weight(1f),
                    style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (summary.isNotBlank() && summary != command) {
                Text(summary, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium)
            }
            if (command.isNotBlank()) {
                Surface(
                    Modifier.padding(top = 10.dp).fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = RoundedCornerShape(8.dp),
                ) {
                    SelectionContainer {
                        Text(command, Modifier.padding(10.dp), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                    }
                }
            }
            FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onDecide(true, false) }) {
                    Icon(Icons.Outlined.Check, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.ai_approve))
                }
                OutlinedButton(onClick = { onDecide(false, false) }) {
                    Icon(Icons.Outlined.Close, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.ai_deny))
                }
                TextButton(onClick = { onDecide(true, true) }) { Text(stringResource(R.string.ai_approve_always)) }
            }
        }
    }
}

/** Permission modes, in the order they are offered. */
val PermissionModes = listOf(
    AiPermissionMode.ASK,
    AiPermissionMode.CONFIRM,
    AiPermissionMode.AUTO,
    AiPermissionMode.READ_ONLY,
)

@StringRes
fun permissionLabel(mode: AiPermissionMode): Int = when (mode) {
    AiPermissionMode.ASK -> R.string.ai_mode_ask
    AiPermissionMode.CONFIRM -> R.string.ai_mode_confirm
    AiPermissionMode.AUTO -> R.string.ai_mode_auto
    AiPermissionMode.READ_ONLY -> R.string.ai_mode_read_only
}

/** What each permission mode means. */
@StringRes
fun permissionHint(mode: AiPermissionMode): Int = when (mode) {
    AiPermissionMode.READ_ONLY -> R.string.ai_mode_read_only_hint
    AiPermissionMode.ASK -> R.string.ai_mode_ask_hint
    AiPermissionMode.CONFIRM -> R.string.ai_mode_confirm_hint
    AiPermissionMode.AUTO -> R.string.ai_mode_auto_hint
}

/** AI permissions, in two rows: Ask · Always ask / Autonomous · Read only. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionModeSelector(mode: AiPermissionMode, onChange: (AiPermissionMode) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PermissionModes.chunked(2).forEach { row ->
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                row.forEachIndexed { i, m ->
                    SegmentedButton(mode == m, { onChange(m) }, SegmentedButtonDefaults.itemShape(i, row.size), icon = {}) {
                        Text(stringResource(permissionLabel(m)), maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun NewAiTaskScreen(app: TermoakApp, nav: NavHostController) {
    val resources = LocalResources.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    // The task runs on the AI section's account: only its hosts.
    val account = remember { app.accounts.aiAccount() }
    val hosts = remember {
        account?.let { a ->
            runCatching { app.core.listHosts(app.accounts.scopeFilter(a.id)) }.getOrDefault(emptyList()).filter { it.accountId == a.id }
        }.orEmpty().sortedBy { it.label.lowercase() }
    }
    var prompt by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var mode by remember { mutableStateOf(AiPermissionMode.ASK) }
    var busy by remember { mutableStateOf(false) }

    ScreenScaffold(
        title = stringResource(R.string.ai_new_task),
        navigationIcon = {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().navigationBarsPadding()
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                prompt, { prompt = it }, Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.ai_prompt_label)) },
                placeholder = { Text(stringResource(R.string.ai_prompt_placeholder)) },
                minLines = 4,
            )
            Text(stringResource(R.string.section_hosts), style = MaterialTheme.typography.titleSmall)
            if (hosts.isEmpty()) {
                Text(stringResource(R.string.ai_no_hosts), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    hosts.forEach { h ->
                        FilterChip(
                            selected = h.id in selected,
                            onClick = { selected = if (h.id in selected) selected - h.id else selected + h.id },
                            label = { Text(h.label) },
                        )
                    }
                }
            }
            Text(stringResource(R.string.common_permissions), style = MaterialTheme.typography.titleSmall)
            PermissionModeSelector(mode, { mode = it })
            Text(
                stringResource(permissionHint(mode)),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = {
                    busy = true
                    scope.launch {
                        try {
                            val handle = account?.let { app.accounts.handle(it.id) } ?: throw TermoakException.NotLoggedIn("")
                            val task = handle.createAiTask(
                                AiTaskRequest(prompt = prompt.trim(), title = null, mode = mode, provider = null,
                                    hostIds = selected.toList(), sessionId = null, effort = null),
                            )
                            nav.popBackStack()
                            nav.navigate(Routes.aiTask(task.id, account.id))
                        } catch (e: TermoakException) {
                            busy = false
                            snackbar.showAiError(e, R.string.ai_create_failed, resources) { nav.navigate(Routes.AI_KEYS) }
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = prompt.isNotBlank() && !busy,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) { Text(stringResource(if (busy) R.string.ai_creating else R.string.ai_start)) }
        }
    }
}

@Composable
fun AiTaskScreen(app: TermoakApp, nav: NavHostController, taskId: String, accountId: String? = null) {
    // The task's account (from the route), or the AI section's.
    val api = remember(accountId) { accountId?.let { app.accounts.handle(it) } ?: app.accounts.aiAccount()?.let { app.accounts.handle(it.id) } }
    val resources = LocalResources.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var task by remember { mutableStateOf<AiTask?>(null) }
    var message by remember { mutableStateOf("") }
    val list = rememberLazyListState()

    suspend fun load() {
        try {
            task = (api ?: throw TermoakException.NotLoggedIn("")).getAiTask(taskId)
        } catch (e: TermoakException) {
            snackbar.showSnackbar(e.userMessage(resources, R.string.ai_load_task_failed))
        }
    }
    LaunchedEffect(taskId) {
        // While it works, it refreshes by itself (and with the server events).
        while (true) {
            load()
            delay(if (task?.active == true) 3_000 else 15_000)
        }
    }
    LaunchedEffect(Unit) { app.accounts.changes.collect { if (it == "ai") load() } }

    val t = task
    val conversation = remember(t?.rawJson) { t?.let { parseConversation(it.rawJson) }.orEmpty() }
    LaunchedEffect(conversation.size) { if (conversation.isNotEmpty()) list.animateScrollToItem(conversation.size + 1) }
    FollowKeyboard(list)

    ScreenScaffold(
        title = t?.title?.ifBlank { null } ?: stringResource(R.string.ai_task),
        subtitle = t?.let { statusStyle(it.status).first },
        navigationIcon = {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) }
        },
        actions = {
            if (t?.active == true) {
                IconButton(onClick = { scope.launch { runCatching { api?.cancelAiTask(taskId) }; load() } }) {
                    Icon(Icons.Outlined.Cancel, stringResource(R.string.common_cancel))
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().navigationBarsPadding()) {
            if (t == null) {
                CircularProgressIndicator(Modifier.padding(32.dp).align(Alignment.CenterHorizontally))
                return@Column
            }
            LazyColumn(Modifier.weight(1f), state = list, contentPadding = PaddingValues(vertical = 8.dp)) {
                if (conversation.isEmpty()) item { TurnView(Turn.User(stripContext(t.prompt))) }
                items(conversation.size) { i -> TurnView(conversation[i], running = t.active) }
                items(t.pendingApprovals, key = { it.id }) { a ->
                    ApprovalCard(a, null) { approve, always ->
                        scope.launch {
                            runCatching { api?.decideApproval(a.taskId, a.id, approve, always) }
                                .onFailure { snackbar.showSnackbar(it.userMessage(resources, R.string.error_decide_failed)) }
                            app.accounts.refreshApprovals()
                            load()
                        }
                    }
                }
                item {
                    if (t.active) {
                        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text(
                                "${statusStyle(t.status).first}…", Modifier.padding(start = 8.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    t.error?.let {
                        Text(it, Modifier.padding(20.dp), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    message, { message = it }, Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.ai_reply_placeholder)) }, maxLines = 4,
                    shape = RoundedCornerShape(24.dp),
                )
                IconButton(
                    onClick = {
                        val text = message.trim()
                        message = ""
                        scope.launch {
                            try {
                                task = (api ?: throw TermoakException.NotLoggedIn("")).sendAiMessage(taskId, text)
                            } catch (e: TermoakException) {
                                // Don't lose what was typed.
                                if (message.isEmpty()) message = text
                                snackbar.showAiError(e, R.string.error_send_failed, resources) { nav.navigate(Routes.AI_KEYS) }
                            }
                        }
                    },
                    enabled = message.isNotBlank(),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.common_send), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

/** A piece of the conversation (task screen and copilot). */
@Composable
fun TurnView(turn: Turn, running: Boolean = false) {
    when (turn) {
        is Turn.User -> Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.End) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)) {
                SelectionContainer { Text(turn.text, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onPrimaryContainer) }
            }
        }
        is Turn.Assistant -> Surface(
            Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
        ) {
            MarkdownText(turn.text, Modifier.padding(12.dp))
        }
        is Turn.Reasoning -> ReasoningText(turn.text)
        is Turn.Tool -> ToolRow(turn.name, turn.input, turn.output, turn.error, running = running && turn.output == null)
    }
}

/** The model's reasoning: gray, italic and trimmed. */
@Composable
fun ReasoningText(text: String) {
    Text(
        if (text.length > 600) text.take(600).trimEnd() + "…" else text,
        Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Tool used by the AI: name, summary and its output (or a spinner while it runs). */
@Composable
fun ToolRow(name: String, summary: String, output: String?, error: Boolean, running: Boolean) {
    var open by remember { mutableStateOf(false) }
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp).clickable { open = !open },
        color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(10.dp),
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (running) {
                    CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp)
                } else {
                    Icon(Icons.Outlined.Build, null, Modifier.size(14.dp),
                        tint = if (error) Brand.Red else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(" $name", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (summary.isNotBlank()) {
                Text(summary, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                    maxLines = if (open) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis)
            }
            if (!output.isNullOrBlank()) {
                // Up to 12 lines; on tap, all of it.
                Surface(
                    Modifier.padding(top = 6.dp).fillMaxWidth(),
                    color = if (error) Brand.Red.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text(
                        output.trimEnd(), Modifier.padding(8.dp), fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                        maxLines = if (open) Int.MAX_VALUE else 12, overflow = TextOverflow.Ellipsis,
                        color = if (error) Brand.Red else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Scrolls [this] conversation to its very end (also the end of a long last message). */
suspend fun LazyListState.scrollToEnd() {
    val n = layoutInfo.totalItemsCount
    if (n > 0) {
        scrollToItem(n - 1)
        scrollBy(100_000f)
    }
}

/**
 * While the keyboard opens, keeps the end of the conversation [list] in view:
 * the list loses its bottom to the keyboard (it shrinks from the bottom) and
 * the last message would end up hidden under the box to write in.
 */
@Composable
fun FollowKeyboard(list: LazyListState) {
    val ime = WindowInsets.ime.getBottom(LocalDensity.current)
    var previous by remember { mutableIntStateOf(ime) }
    LaunchedEffect(ime) {
        // Every step of the keyboard's animation, only while it opens.
        val opening = ime > previous
        previous = ime
        if (opening) list.scrollToEnd()
    }
}

/** Which account the AI section shows (with several signed in). */
@Composable
private fun AiAccountPicker(accounts: List<com.termoak.ffi.AccountInfo>, selected: String?, onSelect: (String) -> Unit) {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        accounts.forEach { a ->
            FilterChip(
                selected = a.id == selected, onClick = { onSelect(a.id) },
                label = { Text(a.email, maxLines = 1) },
                leadingIcon = { AccountAvatar(a, 18.dp) },
            )
        }
    }
}
