package com.termoak.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.termoak.app.R
import com.termoak.app.TermoakApp
import com.termoak.app.data.HostTest
import com.termoak.app.data.NetState
import com.termoak.app.data.NetworkBook
import com.termoak.app.data.NetworkChoice
import com.termoak.app.data.Networks
import com.termoak.app.data.NetworksApi
import com.termoak.app.data.uid
import com.termoak.app.toUiText
import com.termoak.ffi.HostGroup
import com.termoak.ffi.ItemFilter
import com.termoak.ffi.SshHost
import kotlinx.coroutines.launch

/*
 * Private networks in the host and group editors, the host lists and the
 * terminal bar (NETWORKS.md §8.1).
 */

/** Label of a choice of the "Network" picker. */
@Composable
internal fun choiceLabel(c: NetworkChoice): String = when (c) {
    is NetworkChoice.Inherit -> c.inherited?.let {
        stringResource(R.string.networks_picker_inherit, it.group.name, c.inheritedName ?: stringResource(R.string.networks_missing))
    } ?: stringResource(R.string.networks_picker_direct)
    is NetworkChoice.Network -> "${c.name} · ${Networks.kindLabel(c.kind)}"
    is NetworkChoice.Missing -> stringResource(R.string.networks_missing)
}

/**
 * The "Network" field of a host or group of [account]/[vault]: empty
 * (`null`) inherits the group's network ([groupId]: the host's group or the
 * group's parent) or connects directly; then the networks of the same
 * vault; a network that isn't there is kept as "Missing network". Shown
 * for account items whose server has networks, or that already have one
 * (to see and keep it); never on This device. Under it, the chosen
 * network's state in the server.
 */
@Composable
internal fun NetworkField(
    app: TermoakApp,
    account: String?,
    vault: String?,
    groupId: String?,
    groups: List<HostGroup>,
    current: String?,
    outlined: Boolean,
    proxySet: Boolean = false,
    /** A host's address (`null` for a group): a tailnet says what it can be. */
    hostAddress: String? = null,
    onChange: (String?) -> Unit,
) {
    if (account == null) return
    if (!NetworkBook.supported(account) && current == null) return
    val resources = LocalResources.current
    val networks = remember(account, vault) {
        runCatching { app.core.listNetworks(ItemFilter(accountIds = listOf(account), vaultIds = null, includeDevice = false)) }
            .getOrDefault(emptyList()).filter { it.vaultId == vault }
    }
    val links = NetworkBook.links(groups, account, vault)
    val choices = Networks.choices(current, groupId, links, networks.map { Triple(it.id, it.name, it.kind) })
    val chosen = choices.firstOrNull { it.id == current } ?: choices.first()
    val label = stringResource(R.string.networks_picker_label)
    val options = choices.map { it.id to choiceLabel(it) }
    Column(Modifier.testTag("network-field")) {
        if (outlined) Picker(label, choiceLabel(chosen), null, options) { onChange(it) }
        else FormPicker(label, choiceLabel(chosen), options) { onChange(it) }
        // The network it goes through (its own or the group's) and its state.
        val effective = current ?: (chosen as? NetworkChoice.Inherit)?.inherited?.networkId
        val net = networks.firstOrNull { it.id == effective }
        LaunchedEffect(effective) {
            val id = net?.id ?: return@LaunchedEffect
            app.accounts.handle(account)?.let { NetworkBook.refreshStatus(NetworksApi(it), id) }
        }
        val hint = when {
            effective == null -> stringResource(R.string.networks_picker_direct_hint)
            net == null -> stringResource(R.string.networks_picker_missing_hint)
            else -> {
                val state = if (NetworkBook.isUnknown(net.id)) NetState.UNKNOWN else NetworkBook.state(net.id, net.kind)
                val status = NetworkBook.status(net.id)
                val handshake = when {
                    net.kind == Networks.TAILSCALE && state == NetState.UP && status != null ->
                        " · " + Networks.tailnetSummary(status) { resources.getQuantityString(R.plurals.networks_ts_devices, it, it) }
                    else -> status?.lastHandshakeAt?.let { " · " + stringResource(R.string.networks_handshake, relativeTime(it)) } ?: ""
                }
                val from = (chosen as? NetworkChoice.Inherit)?.inherited?.let { stringResource(R.string.networks_picker_inherited_from, it.group.name) + " " } ?: ""
                from + "${Networks.kindLabel(net.kind)} · ${stateLabel(state, kind = net.kind)}$handshake. " + stringResource(R.string.networks_picker_how)
            }
        }
        // A host in a tailnet: what its address can be.
        val tailnet = net?.kind == Networks.TAILSCALE && hostAddress != null
        Text(
            hint, Modifier.padding(horizontal = if (outlined) 0.dp else 16.dp, vertical = 2.dp),
            style = MaterialTheme.typography.bodySmall,
            color = if (effective != null && net == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (tailnet) {
            Text(
                stringResource(R.string.networks_ts_address_hint), Modifier.padding(horizontal = if (outlined) 0.dp else 16.dp).testTag("tailnet-address-hint"),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (effective != null && proxySet) {
            Text(
                stringResource(R.string.networks_picker_proxy_ignored), Modifier.padding(horizontal = if (outlined) 0.dp else 16.dp),
                style = MaterialTheme.typography.bodySmall, color = Brand.Amber,
            )
        }
        if (networks.isEmpty() && current == null && NetworkBook.supported(account)) {
            Text(
                stringResource(R.string.networks_picker_no_networks), Modifier.padding(horizontal = if (outlined) 0.dp else 16.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "via Office" (with the last test's latency) of a host of the lists; nothing for direct hosts. */
internal fun viaText(name: String, test: HostTest?, networkId: String, testing: Boolean, viaLatency: (String, String) -> String, via: (String) -> String): String = when {
    testing -> via(name) + " · …"
    test != null && test.ok && test.latencyMs != null && (test.networkId == null || test.networkId == networkId) ->
        viaLatency(name, "${test.latencyMs} ms")
    else -> via(name)
}

/** The "via Office · 23 ms" chip of a host row or card. */
@Composable
internal fun NetworkVia(host: SshHost, modifier: Modifier = Modifier) {
    val id = NetworkBook.networkOf(host) ?: return
    val name = NetworkBook.name(host.accountId, id)
    if (name == null) {
        Pill(stringResource(R.string.networks_missing), MaterialTheme.colorScheme.error, modifier.testTag("via-missing"))
        return
    }
    val viaLatency = stringResource(R.string.networks_via_latency)
    val via = stringResource(R.string.networks_via)
    val text = viaText(
        name, NetworkBook.test(host), id, NetworkBook.isTesting(host),
        { n, ms -> String.format(viaLatency, n, ms) }, { n -> String.format(via, n) },
    )
    val failed = NetworkBook.test(host)?.ok == false
    Pill(text, if (failed) Brand.Amber else Brand.Blue, modifier.testTag("via-chip"))
}

/**
 * Tests [host] from the server through its network (only when asked: never
 * polled) and says how it went; an unknown host key asks to trust it and
 * test again. Calls [onDone] when finished.
 */
@Composable
internal fun HostNetworkTest(app: TermoakApp, host: SshHost, onDone: () -> Unit) {
    val resources = LocalResources.current
    val snackbar = LocalSnackbar.current
    var trustAsk by remember { mutableStateOf<HostTest?>(null) }
    // An end-to-end encrypted host: send its credentials for this test? (then they go with every retry of it)
    var sendAsk by remember { mutableStateOf<Boolean?>(null) }
    var unlock by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    fun run(trust: Boolean) {
        val handle = host.accountId?.let { app.accounts.handle(it) }
        if (handle == null) {
            onDone()
            return
        }
        scope.launch {
            try {
                val t = NetworkBook.testHost(NetworksApi(handle), host, trust, unlock)
                val name = t.networkName ?: NetworkBook.networkOf(host)?.let { NetworkBook.name(host.accountId, it) } ?: ""
                when {
                    t.ok -> {
                        snackbar.showSnackbar(resources.getString(R.string.networks_test_ok_via, name, (t.latencyMs ?: 0L).toString()))
                        onDone()
                    }
                    t.errorCode == "host_key_unknown" && !trust -> trustAsk = t
                    else -> {
                        snackbar.showSnackbar(resources.getString(R.string.networks_test_failed_via, name, Networks.hostTestError(t).resolve(resources)))
                        onDone()
                    }
                }
            } catch (e: Exception) {
                if (!unlock && com.termoak.app.data.E2eeTexts.needsUnlock(e)) {
                    sendAsk = trust
                    return@launch
                }
                snackbar.showSnackbar(e.toUiText(R.string.networks_test_failed).resolve(resources))
                onDone()
            }
        }
    }
    LaunchedEffect(host.uid) { run(false) }
    sendAsk?.let { trust ->
        SendForTestDialog(host.label, onDismiss = { sendAsk = null; onDone() }) {
            sendAsk = null
            unlock = true
            run(trust)
        }
    }
    trustAsk?.let { t ->
        AlertDialog(
            onDismissRequest = { trustAsk = null; onDone() },
            icon = { Icon(Icons.Outlined.Hub, null) },
            title = { Text(stringResource(R.string.networks_test_trust_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.networks_test_trust_text, host.label))
                    t.fingerprint?.let {
                        SelectionContainer { Text(it, Modifier.padding(top = 8.dp), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { trustAsk = null; run(true) }) { Text(stringResource(R.string.networks_test_trust)) }
            },
            dismissButton = { TextButton(onClick = { trustAsk = null; onDone() }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

/** The network a terminal's host connects through (its name), for the terminal bar; `null`: direct. */
@Composable
internal fun rememberTerminalNetwork(app: TermoakApp, hostId: String?, accountId: String?): String? {
    val missing = stringResource(R.string.networks_missing)
    return remember(hostId, accountId) {
        if (hostId == null || accountId == null) null
        else NetworkBook.resolve(app.core, hostId, accountId)?.let { (_, name) -> name ?: missing }
    }
}

/** "via Office" in the terminal bar's color. */
@Composable
internal fun TerminalVia(name: String, color: Color) {
    Text(
        stringResource(R.string.networks_via, name), color = color, style = MaterialTheme.typography.labelSmall,
        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("terminal-via"),
    )
}
