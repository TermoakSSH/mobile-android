package com.termoak.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.NavHostController
import com.termoak.app.MainActivity
import com.termoak.app.R
import com.termoak.app.TermoakApp
import com.termoak.app.data.displayName
import com.termoak.app.push.AccountPush
import com.termoak.ffi.PushPreferences
import com.termoak.ffi.PushStatus
import com.termoak.ffi.TermoakException
import kotlinx.coroutines.launch

/**
 * Settings → Notifications: push without Google (UnifiedPush). Which
 * distributor delivers them (the ntfy app, NextPush...), and per account the
 * state, the kinds this phone wants, "also while the app is open" and a test.
 * Without a distributor the app only notifies while it runs.
 */
@Composable
fun NotificationsScreen(app: TermoakApp, nav: NavHostController) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val activity = LocalActivity.current
    val accounts by app.accounts.list.collectAsState()
    val active = accounts.filter { it.status == com.termoak.ffi.AccountStatus.ACTIVE }
    val states by app.push.states.collectAsState()
    // Re-read when coming back (a distributor installed, the permission given in the system's settings).
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    val installed = remember(refresh) { app.push.installed() }
    var current by remember(refresh) { mutableStateOf(app.push.current()) }
    val canNotify = remember(refresh) { app.pushNotifier.canNotify() }
    val statuses = remember { mutableStateMapOf<String, PushStatus?>() }
    LaunchedEffect(refresh, current, active.map { it.id }) {
        active.forEach { a -> statuses[a.id] = app.push.status(a.id) }
    }
    val desktop = LocalDesktop.current

    ScreenScaffold(
        title = stringResource(R.string.push_settings_title),
        large = desktop,
        navigationIcon = {
            if (!desktop) {
                IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            if (!canNotify) {
                Hint(stringResource(R.string.push_permission_needed))
                Row(Modifier.padding(horizontal = 20.dp)) {
                    OutlinedButton(onClick = { (activity as? MainActivity)?.askNotificationPermission(); refresh++ }) {
                        Text(stringResource(R.string.push_permission_allow))
                    }
                }
            }

            // ----- Distributor -----
            SectionLabel(stringResource(R.string.push_distributor))
            if (installed.isEmpty()) {
                Hint(stringResource(R.string.push_no_distributor))
                Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(NTFY_FDROID)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }) { Text(stringResource(R.string.push_get_ntfy)) }
                    TextButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(UNIFIEDPUSH_APPS)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }) { Text(stringResource(R.string.push_other_distributors)) }
                }
            } else {
                Hint(stringResource(R.string.push_distributor_hint))
                installed.forEach { pkg ->
                    ChoiceRow(app.push.label(pkg), pkg, current == pkg) {
                        app.push.choose(pkg)
                        current = pkg
                    }
                }
                ChoiceRow(stringResource(R.string.push_off), stringResource(R.string.push_off_hint), current == null) {
                    app.push.turnOff()
                    current = null
                }
            }

            // ----- Accounts -----
            active.forEach { a ->
                SectionLabel(a.displayName + " · " + a.serverUrl.removePrefix("https://").removePrefix("http://"))
                val status = statuses[a.id]
                val state = states[a.id]
                Hint(stateText(current != null, status, state, resources))
                if (current != null && status?.serverWebpush == true) {
                    var prefs by remember(status) { mutableStateOf(status.preferences) }
                    fun save(p: PushPreferences) {
                        val before = prefs
                        prefs = p
                        scope.launch {
                            try {
                                app.push.savePreferences(a.id, p)?.let { prefs = it }
                            } catch (e: TermoakException) {
                                prefs = before
                                snackbar.showSnackbar(e.message ?: resources.getString(R.string.push_save_failed))
                            }
                        }
                    }
                    status.kinds.forEach { kind ->
                        val on = prefs.kinds[kind] ?: true
                        Toggle(kindLabel(kind, resources), on) { save(prefs.copy(kinds = prefs.kinds + (kind to it))) }
                    }
                    Toggle(stringResource(R.string.push_when_online), prefs.whenOnline, stringResource(R.string.push_when_online_hint)) {
                        save(prefs.copy(whenOnline = it))
                    }
                    Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                        OutlinedButton(onClick = {
                            scope.launch {
                                val text = try {
                                    app.push.sendTest(a.id)
                                    resources.getString(R.string.push_test_sent)
                                } catch (e: TermoakException) {
                                    resources.getString(R.string.push_test_failed, e.message.orEmpty())
                                }
                                snackbar.showSnackbar(text)
                            }
                        }) { Text(stringResource(R.string.push_send_test)) }
                    }
                }
            }
        }
    }
}

private const val NTFY_FDROID = "https://f-droid.org/packages/io.heckel.ntfy/"
private const val UNIFIEDPUSH_APPS = "https://unifiedpush.org/users/distributors/"

/** An account's state in words. */
private fun stateText(hasDistributor: Boolean, status: PushStatus?, state: AccountPush?, res: android.content.res.Resources): String = when {
    !hasDistributor -> res.getString(R.string.push_state_off)
    status != null && !status.serverWebpush -> res.getString(R.string.push_state_server_cannot)
    state == AccountPush.ServerCannot -> res.getString(R.string.push_state_server_cannot)
    state is AccountPush.Failed -> res.getString(R.string.push_state_failed, state.reason)
    status?.registered == true && status.platform == "webpush" -> res.getString(R.string.push_state_on)
    state == AccountPush.Registered -> res.getString(R.string.push_state_on)
    else -> res.getString(R.string.push_state_registering)
}

/** A kind's name ("AI approvals"...); an unknown one shows as it is. */
private fun kindLabel(kind: String, res: android.content.res.Resources): String {
    val id = when (kind) {
        "ai_approval" -> R.string.push_kind_ai_approval
        "ai_finished" -> R.string.push_kind_ai_finished
        "session_shared" -> R.string.push_kind_session_shared
        "session_prompt" -> R.string.push_kind_session_prompt
        "join_request" -> R.string.push_kind_join_request
        "control_request" -> R.string.push_kind_control_request
        "team_added" -> R.string.push_kind_team_added
        "security_key_request" -> R.string.push_kind_security_key_request
        "access_request" -> R.string.push_kind_access_request
        "access_decided" -> R.string.push_kind_access_decided
        else -> return kind
    }
    return res.getString(id)
}

@Composable
private fun Hint(text: String) {
    Text(
        text, Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ChoiceRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { RadioButton(selected, onClick) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun Toggle(title: String, checked: Boolean, subtitle: String? = null, onChange: (Boolean) -> Unit) {
    ListItem(
        modifier = Modifier.clickable { onChange(!checked) },
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = { Switch(checked, onChange) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
