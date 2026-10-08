package com.termoak.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import androidx.navigation.NavHostController
import com.termoak.app.R
import com.termoak.app.TermoakApp
import com.termoak.app.asString
import com.termoak.app.data.AccountView
import com.termoak.app.data.Accounts
import com.termoak.app.data.DEVICE_VAULT
import com.termoak.app.data.serverHost
import com.termoak.app.userMessage
import com.termoak.ffi.AccountInfo
import com.termoak.ffi.AccountStatus
import com.termoak.ffi.ItemRef
import com.termoak.ffi.SyncMode
import com.termoak.ffi.TermoakException
import com.termoak.ffi.TransferMode
import com.termoak.ffi.VaultInfo
import com.termoak.ffi.VaultRole
import kotlinx.coroutines.launch

/** Colors of the account avatars without a color of their own (picked by email). */
private val AvatarPalette = listOf(
    Color(0xFF4F7CFF), Color(0xFF3FB27F), Color(0xFFE8A33D), Color(0xFF9B6BFF),
    Color(0xFFE5534B), Color(0xFF2BA6B5), Color(0xFFD9640F), Color(0xFF6B7A99),
)

fun accountColor(a: AccountInfo): Color =
    a.color?.let { runCatching { Color(it.toColorInt()) }.getOrNull() }
        ?: AvatarPalette[Math.floorMod(a.email.lowercase().hashCode(), AvatarPalette.size)]

/** Circle with the initial of the account, in its color. */
@Composable
fun AccountAvatar(a: AccountInfo, size: Dp = 32.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(accountColor(a)), contentAlignment = Alignment.Center) {
        val initial = (a.name.ifBlank { a.email }).trim().firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?"
        Text(initial, color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.45f).sp)
    }
}

/** "termoak.com" for the official server; the host of the others. */
@Composable
fun accountServer(a: AccountInfo): String =
    if (a.official) stringResource(R.string.accounts_official_server, a.serverName) else a.serverName

/** Name of a vault ("Personal" is translated). */
@Composable
fun vaultName(v: VaultInfo): String = Accounts.vaultLabel(LocalContext.current, v)

/** Color of a vault (its own, or the app's). */
@Composable
fun vaultColor(v: VaultInfo?): Color =
    v?.color?.let { runCatching { Color(it.toColorInt()) }.getOrNull() } ?: MaterialTheme.colorScheme.primary

/**
 * The account switcher at the top of the Vault: the account shown (or "All
 * accounts" / "This device only"), and a menu with every account, Add
 * account and Manage accounts. Under it, a banner when that account has to
 * sign in again or verify its email.
 */
@Composable
fun AccountSwitcher(app: TermoakApp, nav: NavHostController, attention: Boolean = true) {
    val accounts by app.accounts.list.collectAsState()
    val view by app.accounts.view.collectAsState()
    if (accounts.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    val shown = (view as? AccountView.One)?.let { v -> accounts.firstOrNull { it.id == v.id } }
    Box(Modifier.padding(horizontal = 12.dp)) {
        AccountSwitcherButton(accounts, view) { open = true }
        DropdownMenu(open, { open = false }) {
            accounts.forEach { a ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(a.email, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val status = statusText(a)
                            Text(
                                listOfNotNull(accountServer(a).takeIf { !a.official }, status).joinToString(" · ")
                                    .ifEmpty { accountServer(a) },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (status != null) Brand.Amber else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    leadingIcon = { AccountAvatar(a, 28.dp) },
                    trailingIcon = { if (shown?.id == a.id) Icon(Icons.Outlined.Check, null) },
                    onClick = { open = false; app.accounts.setView(AccountView.One(a.id)) },
                )
            }
            if (accounts.size > 1) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.accounts_all)) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.ViewList, null) },
                    trailingIcon = { if (view == AccountView.All) Icon(Icons.Outlined.Check, null) },
                    onClick = { open = false; app.accounts.setView(AccountView.All) },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.accounts_device_only)) },
                leadingIcon = { Icon(Icons.Outlined.PhoneAndroid, null) },
                trailingIcon = { if (view == AccountView.Device) Icon(Icons.Outlined.Check, null) },
                onClick = { open = false; app.accounts.setView(AccountView.Device) },
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.accounts_add)) },
                leadingIcon = { Icon(Icons.Outlined.Add, null) },
                onClick = { open = false; nav.navigate(Routes.login()) },
            )
            if (accounts.any { it.vaultsSupported }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.vaults_title)) },
                    leadingIcon = { Icon(Icons.Outlined.Lock, null) },
                    onClick = { open = false; nav.navigate(Routes.VAULTS) },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.accounts_manage)) },
                leadingIcon = { Icon(Icons.Outlined.ManageAccounts, null) },
                onClick = { open = false; nav.navigate(Routes.ACCOUNTS) },
            )
        }
    }
    // The account shown has to sign in again (or verify its email).
    if (attention) attentionAccount(app)?.let { a -> AccountAttention(app, nav, a) }
}

/** The switcher's button: the account shown (or "All accounts" / "This device only") and its server. */
@Composable
internal fun AccountSwitcherButton(accounts: List<AccountInfo>, view: AccountView, onClick: () -> Unit) {
    val shown = (view as? AccountView.One)?.let { v -> accounts.firstOrNull { it.id == v.id } }
    Row(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            shown != null -> AccountAvatar(shown, 28.dp)
            view == AccountView.Device -> Icon(Icons.Outlined.PhoneAndroid, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
            else -> Icon(Icons.AutoMirrored.Outlined.ViewList, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.padding(start = 10.dp).weight(1f, fill = false)) {
            Text(
                when {
                    shown != null -> shown.email
                    view == AccountView.Device -> stringResource(R.string.accounts_device_only)
                    else -> stringResource(R.string.accounts_all)
                },
                style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                when {
                    shown != null -> accountServer(shown)
                    view == AccountView.Device -> stringResource(R.string.accounts_device_only_hint)
                    else -> pluralStringResource(R.plurals.accounts_count, accounts.size, accounts.size)
                },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
            )
        }
        Icon(Icons.Outlined.ArrowDropDown, stringResource(R.string.accounts_switch))
    }
}

/** The account whose sign-in or email needs attention in the Vault: the one shown, or the only one. */
@Composable
fun attentionAccount(app: TermoakApp): AccountInfo? {
    val accounts by app.accounts.list.collectAsState()
    val view by app.accounts.view.collectAsState()
    val shown = (view as? AccountView.One)?.let { v -> accounts.firstOrNull { it.id == v.id } }
    return shown ?: accounts.singleOrNull()?.takeIf { view != AccountView.Device }
}

/** "Signed out", "Confirm your email"... (`null`: active). */
@Composable
private fun statusText(a: AccountInfo): String? = when (a.status) {
    AccountStatus.NEEDS_SIGN_IN -> stringResource(R.string.accounts_status_signed_out)
    AccountStatus.UNVERIFIED -> stringResource(R.string.accounts_status_unverified)
    else -> null
}

/** Banner of an account that can't sync: sign in again, or enter the code from the email. */
@Composable
fun AccountAttention(app: TermoakApp, nav: NavHostController, a: AccountInfo) {
    if (a.status != AccountStatus.NEEDS_SIGN_IN && a.status != AccountStatus.UNVERIFIED) return
    CardBox {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.WarningAmber, null, tint = Brand.Amber)
            Text(
                stringResource(
                    if (a.status == AccountStatus.UNVERIFIED) R.string.accounts_verify_banner else R.string.accounts_sign_in_banner,
                ),
                Modifier.weight(1f).padding(horizontal = 12.dp), style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = { reauthenticate(app, nav, a) }) {
                Text(stringResource(if (a.status == AccountStatus.UNVERIFIED) R.string.verify_enter_code else R.string.common_sign_in))
            }
        }
    }
}

/** Signs an account in again (form prefilled), or opens its code screen. */
fun reauthenticate(app: TermoakApp, nav: NavHostController, a: AccountInfo) {
    if (a.status == AccountStatus.UNVERIFIED) {
        app.accounts.startVerification(a)
        nav.navigate(Routes.VERIFY_EMAIL) { launchSingleTop = true }
    } else {
        nav.navigate(Routes.login(LoginMode.SIGN_IN, a.serverUrl, a.email))
    }
}

/**
 * Vault filter under the switcher: All vaults, each vault of the accounts
 * shown, This device. Hidden when there is only one vault.
 */
@Composable
fun VaultFilterRow(app: TermoakApp) {
    val vaults by app.accounts.vaults.collectAsState()
    val view by app.accounts.view.collectAsState()
    val selected by app.accounts.vaultFilter.collectAsState()
    val accounts by app.accounts.list.collectAsState()
    if (view == AccountView.Device) return
    val shown = remember(vaults, view) { app.accounts.vaultsInView() }
    if (shown.size <= 1) return
    VaultFilterChips(shown, selected, accounts, several = accounts.size > 1 && view == AccountView.All) { app.accounts.setVaultFilter(it) }
}

/** The chips of [VaultFilterRow]: All vaults, the vaults [shown] (with their owner when [several] accounts) and This device. */
@Composable
internal fun VaultFilterChips(
    shown: List<VaultInfo>,
    selected: String?,
    accounts: List<AccountInfo>,
    several: Boolean,
    onSelect: (String?) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(selected == null, { onSelect(null) }, { Text(stringResource(R.string.vaults_all)) })
        shown.forEach { v ->
            val owner = if (several) accounts.firstOrNull { it.id == v.accountId }?.email?.substringBefore('@') else null
            FilterChip(
                selected == v.id, { onSelect(if (selected == v.id) null else v.id) },
                { Text(listOfNotNull(vaultName(v), owner).joinToString(" · "), maxLines = 1) },
                leadingIcon = {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(vaultColor(v)))
                },
                trailingIcon = if (v.role == VaultRole.USE_ONLY) {
                    { Icon(Icons.Outlined.Lock, stringResource(R.string.vault_use_only), Modifier.size(FilterChipDefaults.IconSize)) }
                } else null,
            )
        }
        FilterChip(
            selected == DEVICE_VAULT, { onSelect(if (selected == DEVICE_VAULT) null else DEVICE_VAULT) },
            { Text(stringResource(R.string.vault_this_device)) },
            leadingIcon = { Icon(Icons.Outlined.PhoneAndroid, null, Modifier.size(FilterChipDefaults.IconSize)) },
        )
    }
}

/**
 * The vault filter of the sidebar (desktop layout): the same choices as
 * [VaultFilterRow] in a menu. Hidden when there is only one vault.
 */
@Composable
fun VaultFilterMenu(app: TermoakApp) {
    val vaults by app.accounts.vaults.collectAsState()
    val view by app.accounts.view.collectAsState()
    val selected by app.accounts.vaultFilter.collectAsState()
    val accounts by app.accounts.list.collectAsState()
    if (view == AccountView.Device) return
    val shown = remember(vaults, view) { app.accounts.vaultsInView() }
    if (shown.size <= 1) return
    val several = accounts.size > 1 && view == AccountView.All
    var open by remember { mutableStateOf(false) }
    val current = shown.firstOrNull { it.id == selected }
    Box(Modifier.padding(horizontal = 12.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { open = true }.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                current != null -> Box(Modifier.padding(horizontal = 4.dp).size(10.dp).clip(CircleShape).background(vaultColor(current)))
                selected == DEVICE_VAULT -> Icon(Icons.Outlined.PhoneAndroid, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> Icon(Icons.Outlined.Lock, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                when {
                    current != null -> vaultName(current)
                    selected == DEVICE_VAULT -> stringResource(R.string.vault_this_device)
                    else -> stringResource(R.string.vaults_all)
                },
                Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Icon(Icons.Outlined.ArrowDropDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(open, { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.vaults_all)) },
                trailingIcon = { if (selected == null) Icon(Icons.Outlined.Check, null) },
                onClick = { open = false; app.accounts.setVaultFilter(null) },
            )
            shown.forEach { v ->
                val owner = if (several) accounts.firstOrNull { it.id == v.accountId }?.email?.substringBefore('@') else null
                DropdownMenuItem(
                    text = { Text(listOfNotNull(vaultName(v), owner).joinToString(" · "), maxLines = 1) },
                    leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(vaultColor(v))) },
                    trailingIcon = {
                        when {
                            selected == v.id -> Icon(Icons.Outlined.Check, null)
                            v.role == VaultRole.USE_ONLY -> Icon(Icons.Outlined.Lock, stringResource(R.string.vault_use_only), Modifier.size(16.dp))
                        }
                    },
                    onClick = { open = false; app.accounts.setVaultFilter(v.id) },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.vault_this_device)) },
                leadingIcon = { Icon(Icons.Outlined.PhoneAndroid, null) },
                trailingIcon = { if (selected == DEVICE_VAULT) Icon(Icons.Outlined.Check, null) },
                onClick = { open = false; app.accounts.setVaultFilter(DEVICE_VAULT) },
            )
        }
    }
}

/** Small chip with the vault of an item (or "This device"). */
@Composable
fun VaultChip(text: String, color: Color, useOnly: Boolean = false) {
    Row(
        Modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.14f)).padding(horizontal = 6.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (useOnly) {
            Icon(Icons.Outlined.Lock, stringResource(R.string.vault_use_only), Modifier.size(11.dp).padding(end = 2.dp), tint = color)
        }
        Text(text, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
    }
}

/** "Use only" with a lock: the item can be used but its secrets are never shown. */
@Composable
fun UseOnlyBadge() {
    Pill(stringResource(R.string.vault_use_only), MaterialTheme.colorScheme.onSurfaceVariant)
}

// ----- Manage accounts -----

/**
 * Settings → Accounts: every account with its status, server and last
 * sync; sync now, sign in again, enter the code, sign out (its data on this
 * phone is deleted) and add another one.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ManageAccountsScreen(app: TermoakApp, nav: NavHostController) {
    val accounts by app.accounts.list.collectAsState()
    val syncing by app.accounts.syncingIds.collectAsState()
    val online by app.accounts.onlineIds.collectAsState()
    val errors by app.accounts.syncErrors.collectAsState()
    val vaults by app.accounts.vaults.collectAsState()
    val context = LocalContext.current
    var signingOut by remember { mutableStateOf<AccountInfo?>(null) }
    ScreenScaffold(
        title = stringResource(R.string.accounts_title),
        navigationIcon = {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            if (accounts.isEmpty()) {
                Text(
                    stringResource(R.string.settings_no_server_text), Modifier.padding(20.dp),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            accounts.forEach { a ->
                CardBox {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AccountAvatar(a, 44.dp)
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(a.email, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    listOfNotNull(a.name.ifBlank { null }, accountServer(a)).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            when (a.status) {
                                AccountStatus.ACTIVE -> StatusDot(if (a.id in online) Brand.Green else Brand.Amber)
                                else -> Pill(statusText(a) ?: "", Brand.Amber)
                            }
                        }
                        val status = when {
                            a.id in syncing -> stringResource(R.string.common_syncing)
                            a.lastSyncAt != null -> stringResource(R.string.settings_synced_ago, relativeTime(a.lastSyncAt))
                            else -> stringResource(R.string.settings_never_synced)
                        }
                        Text(
                            listOfNotNull(
                                status,
                                stringResource(R.string.accounts_current).takeIf { a.isCurrent && accounts.size > 1 },
                                stringResource(R.string.accounts_no_vaults).takeIf { !a.vaultsSupported && a.status == AccountStatus.ACTIVE },
                            ).joinToString(" · "),
                            Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (a.insecure) {
                            Text(
                                stringResource(R.string.login_insecure), Modifier.padding(top = 4.dp),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                            )
                        }
                        // Its last sync's error (each account its own, as on iOS).
                        errors[a.id]?.let { why ->
                            Text(
                                why.asString(), Modifier.padding(top = 4.dp),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                            )
                        }
                        // Its vaults, only its items in the Vault, and its page on the web.
                        FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (a.vaultsSupported) {
                                val count = vaults.count { it.accountId == a.id }
                                TextButton(onClick = { nav.navigate(Routes.VAULTS) }) {
                                    Icon(Icons.Outlined.Lock, null, Modifier.size(16.dp))
                                    Text(stringResource(R.string.accounts_vaults_count, count), Modifier.padding(start = 6.dp))
                                }
                            }
                            if (accounts.size > 1) {
                                TextButton(onClick = {
                                    app.accounts.setView(AccountView.One(a.id))
                                    nav.goTab(Routes.HOSTS)
                                }) {
                                    Icon(Icons.Outlined.Visibility, null, Modifier.size(16.dp))
                                    Text(stringResource(R.string.accounts_show_only), Modifier.padding(start = 6.dp))
                                }
                            }
                            if (a.status == AccountStatus.ACTIVE) {
                                TextButton(onClick = { openUrl(context, "${a.serverUrl}/app/account") }) {
                                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.size(16.dp))
                                    Text(stringResource(R.string.settings_my_account), Modifier.padding(start = 6.dp))
                                }
                            }
                        }
                        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            when (a.status) {
                                AccountStatus.ACTIVE -> OutlinedButton(onClick = { app.accounts.sync(a.id) }, enabled = a.id !in syncing) {
                                    Icon(Icons.Outlined.Sync, null, Modifier.size(18.dp))
                                    Text(stringResource(R.string.common_sync), Modifier.padding(start = 6.dp))
                                }
                                AccountStatus.UNVERIFIED -> Button(onClick = { reauthenticate(app, nav, a) }) {
                                    Icon(Icons.Outlined.MarkEmailUnread, null, Modifier.size(18.dp))
                                    Text(stringResource(R.string.verify_enter_code), Modifier.padding(start = 6.dp))
                                }
                                else -> Button(onClick = { reauthenticate(app, nav, a) }) {
                                    Icon(Icons.AutoMirrored.Outlined.Login, null, Modifier.size(18.dp))
                                    Text(stringResource(R.string.accounts_sign_in_again), Modifier.padding(start = 6.dp))
                                }
                            }
                            TextButton(onClick = { signingOut = a }) {
                                Text(
                                    stringResource(if (a.status == AccountStatus.ACTIVE) R.string.settings_sign_out else R.string.accounts_remove),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            ListItem(
                modifier = Modifier.clickable { nav.navigate(Routes.login()) },
                headlineContent = { Text(stringResource(R.string.accounts_add)) },
                supportingContent = { Text(stringResource(R.string.accounts_add_hint)) },
                leadingContent = { Icon(Icons.Outlined.Add, null, tint = MaterialTheme.colorScheme.primary) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            if (accounts.any { it.vaultsSupported }) {
                ListItem(
                    modifier = Modifier.clickable { nav.navigate(Routes.VAULTS) },
                    headlineContent = { Text(stringResource(R.string.vaults_title)) },
                    supportingContent = { Text(stringResource(R.string.vaults_hint)) },
                    leadingContent = { Icon(Icons.Outlined.Lock, null, tint = MaterialTheme.colorScheme.primary) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
            Text(
                stringResource(R.string.accounts_footer), Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    signingOut?.let { a -> SignOutFlow(app, a, onDone = { signingOut = null }) }
}

/**
 * Signing out of one account: confirmation (its data on this phone is
 * deleted), then, with changes not uploaded yet, "Sync now / Discard".
 */
@Composable
fun SignOutFlow(app: TermoakApp, a: AccountInfo, onDone: () -> Unit) {
    val resources = LocalResources.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var unsynced by remember { mutableStateOf<Long?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun signOut(discard: Boolean) {
        busy = true
        error = null
        scope.launch {
            try {
                val r = app.accounts.signOut(a.id, discard)
                if (r.signedOut) {
                    app.sessions.closeAccount(a.id)
                    onDone()
                    snackbar.showSnackbar(resources.getString(R.string.accounts_signed_out, a.email))
                } else {
                    unsynced = r.unsynced.toLong()
                }
            } catch (e: TermoakException) {
                error = e.userMessage(resources, R.string.accounts_sign_out_failed)
            } finally {
                busy = false
            }
        }
    }

    fun syncThenSignOut() {
        busy = true
        error = null
        scope.launch {
            try {
                app.accounts.syncNow(a.id)
                busy = false
                signOut(false)
            } catch (e: TermoakException) {
                busy = false
                error = e.userMessage(resources, R.string.error_sync_failed)
            }
        }
    }

    val pending = unsynced
    if (pending == null) {
        AlertDialog(
            onDismissRequest = { if (!busy) onDone() },
            icon = { Icon(Icons.AutoMirrored.Filled.Logout, null) },
            title = {
                Text(stringResource(if (a.status == AccountStatus.ACTIVE) R.string.accounts_sign_out_title else R.string.accounts_remove_title, a.email))
            },
            text = {
                Column {
                    Text(stringResource(R.string.accounts_sign_out_text, accountServer(a)))
                    error?.let { Text(it, Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = { signOut(false) }, enabled = !busy) {
                    if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text(
                        stringResource(if (a.status == AccountStatus.ACTIVE) R.string.settings_sign_out else R.string.accounts_remove),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = { TextButton(onClick = onDone, enabled = !busy) { Text(stringResource(R.string.common_cancel)) } },
        )
    } else {
        val n = pending.toInt()
        AlertDialog(
            onDismissRequest = { if (!busy) onDone() },
            icon = { Icon(Icons.Outlined.WarningAmber, null) },
            title = { Text(pluralStringResource(R.plurals.accounts_unsynced_title, n, n)) },
            text = {
                Column {
                    Text(stringResource(R.string.accounts_unsynced_text))
                    error?.let { Text(it, Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = { signOut(true) }, enabled = !busy) {
                        Text(stringResource(R.string.accounts_discard), color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { syncThenSignOut() }, enabled = !busy && a.status == AccountStatus.ACTIVE) {
                        if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text(stringResource(R.string.accounts_sync_now))
                    }
                }
            },
            dismissButton = { TextButton(onClick = onDone, enabled = !busy) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

// ----- After the first account: upload This-device items -----

/** An item of This device that can be uploaded to the new account. */
private data class DeviceItem(val id: String, val label: String, val kind: Int, val deviceOnly: Boolean)

/**
 * "Upload N items from this device to your Personal vault?": right after
 * the first account is added, if This device has items. Items marked "This
 * phone only" start unchecked.
 */
@Composable
fun UploadDeviceItemsDialog(app: TermoakApp, accountId: String) {
    val resources = LocalResources.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val items = remember {
        val f = app.accounts.scopeFilter(null)
        fun dev(m: SyncMode?) = m == SyncMode.DEVICE_ONLY
        runCatching {
            app.core.listHosts(f).map { DeviceItem(it.id, it.label, R.string.section_hosts, dev(it.syncMode)) } +
                app.core.listGroups(f).map { DeviceItem(it.id, it.name, R.string.hosts_groups, dev(it.syncMode)) } +
                app.core.listKeys(f).map { DeviceItem(it.id, it.label, R.string.section_keychain, dev(it.syncMode)) } +
                app.core.listIdentities(f).map { DeviceItem(it.id, it.label, R.string.keys_identities, dev(it.syncMode)) } +
                app.core.listSnippets(f).map { DeviceItem(it.id, it.name, R.string.section_snippets, dev(it.syncMode)) } +
                app.core.listForwards(null, f).map { DeviceItem(it.id, it.label, R.string.section_tunnels, dev(it.syncMode)) }
        }.getOrDefault(emptyList())
    }
    var chosen by remember { mutableStateOf(items.filterNot { it.deviceOnly }.map { it.id }.toSet()) }
    var busy by remember { mutableStateOf(false) }
    val vault = app.accounts.personalVault(accountId)?.let { vaultName(it) } ?: stringResource(R.string.vault_personal)
    if (items.isEmpty()) {
        LaunchedEffect(Unit) { app.accounts.dismissUploadOffer() }
        return
    }
    AlertDialog(
        onDismissRequest = { if (!busy) app.accounts.dismissUploadOffer() },
        title = { Text(pluralStringResource(R.plurals.upload_title, chosen.size, chosen.size, vault)) },
        text = {
            Column {
                Text(stringResource(R.string.upload_text), style = MaterialTheme.typography.bodyMedium)
                // Select all / none (as on iOS).
                val all = chosen.size == items.size
                TextButton(onClick = { chosen = if (all) emptySet() else items.map { it.id }.toSet() }) {
                    Text(stringResource(if (all) R.string.upload_select_none else R.string.upload_select_all))
                }
                LazyColumn(Modifier.heightIn(max = 320.dp).padding(top = 8.dp)) {
                    items(items, key = { it.id }) { it ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                .clickable { chosen = if (it.id in chosen) chosen - it.id else chosen + it.id },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(it.id in chosen, null, Modifier.padding(4.dp))
                            Column(Modifier.weight(1f)) {
                                Text(it.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    listOfNotNull(
                                        stringResource(it.kind),
                                        stringResource(R.string.common_device_only).takeIf { _ -> it.deviceOnly },
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && chosen.isNotEmpty(), onClick = {
                busy = true
                scope.launch {
                    try {
                        val r = app.core.transfer(
                            chosen.map { ItemRef(accountId = null, id = it) }, accountId,
                            app.accounts.personalVault(accountId)?.id, TransferMode.MOVE, false,
                        )
                        val n = r.moved.size + r.copied.size
                        app.accounts.dismissUploadOffer()
                        app.accounts.sync(accountId)
                        snackbar.showSnackbar(resources.getQuantityString(R.plurals.upload_done, n, n))
                    } catch (e: TermoakException) {
                        busy = false
                        snackbar.showSnackbar(e.userMessage(resources, R.string.upload_failed))
                    }
                }
            }) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(stringResource(R.string.upload_action))
            }
        },
        dismissButton = {
            TextButton(onClick = { app.accounts.dismissUploadOffer() }, enabled = !busy) { Text(stringResource(R.string.upload_not_now)) }
        },
    )
}

/** Once, after the update: the data now lives in one store per account. */
@Composable
fun LayoutNoticeDialog(app: TermoakApp) {
    AlertDialog(
        onDismissRequest = { app.accounts.dismissLayoutNotice() },
        icon = { Icon(Icons.Outlined.Folder, null) },
        title = { Text(stringResource(R.string.layout_notice_title)) },
        text = { Text(stringResource(R.string.layout_notice_text)) },
        confirmButton = { TextButton(onClick = { app.accounts.dismissLayoutNotice() }) { Text(stringResource(R.string.common_ok)) } },
    )
}

/** Settings: the accounts (the current one, and how many there are) and the vaults. */
@Composable
fun AccountsSettingsSection(app: TermoakApp, nav: NavHostController) {
    val accounts by app.accounts.list.collectAsState()
    val current by app.accounts.current.collectAsState()
    val syncing by app.accounts.syncing.collectAsState()
    val online by app.accounts.online.collectAsState()
    val a = current
    CardBox {
        if (a == null) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.settings_no_server), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.settings_no_server_text),
                    Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = { nav.navigate(Routes.login()) }, modifier = Modifier.padding(top = 12.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.Login, null, Modifier.padding(end = 8.dp))
                    Text(stringResource(R.string.common_sign_in))
                }
            }
        } else {
            Column(Modifier.clickable { nav.navigate(Routes.ACCOUNTS) }.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AccountAvatar(a, 44.dp)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(a.email, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(accountServer(a), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (a.status == AccountStatus.ACTIVE) StatusDot(if (online) Brand.Green else Brand.Amber)
                    else Pill(statusText(a) ?: "", Brand.Amber)
                }
                val status = when {
                    syncing -> stringResource(R.string.common_syncing)
                    a.lastSyncAt != null -> stringResource(R.string.settings_synced_ago, relativeTime(a.lastSyncAt))
                    else -> stringResource(R.string.settings_never_synced)
                }
                Text(
                    if (online) stringResource(R.string.settings_sync_live, status) else status,
                    Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { app.accounts.sync() }, enabled = !syncing) { Text(stringResource(R.string.common_sync)) }
                    OutlinedButton(onClick = { nav.navigate(Routes.ACCOUNTS) }) {
                        Icon(Icons.Outlined.Settings, null, Modifier.size(18.dp))
                        Text(
                            pluralStringResource(R.plurals.accounts_manage_count, accounts.size, accounts.size),
                            Modifier.padding(start = 6.dp),
                        )
                    }
                }
            }
        }
    }
    if (a != null && a.status != AccountStatus.ACTIVE) AccountAttention(app, nav, a)
}

/** Used by lists to say an item can't be changed (Use-only vault). */
@Composable
fun UseOnlyNote() {
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                stringResource(R.string.vault_use_only_note), Modifier.padding(start = 12.dp).weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
