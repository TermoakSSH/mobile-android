package com.termoak.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.navigation.NavHostController
import com.termoak.app.R
import com.termoak.app.TermoakApp
import com.termoak.app.data.AppUpdate

/** The parts of the Vault, as chips under its title (Termius' vault sections). */
enum class VaultSection(val route: String, @StringRes val label: Int, val icon: ImageVector) {
    HOSTS(Routes.HOSTS, R.string.section_hosts, Icons.Outlined.Dns),
    KEYCHAIN(Routes.keys(), R.string.section_keychain, Icons.Outlined.Key),
    TUNNELS(Routes.FORWARDS, R.string.section_tunnels, Icons.Outlined.SwapHoriz),
    SNIPPETS(Routes.SNIPPETS, R.string.section_snippets, Icons.Outlined.Code),
    KNOWN_HOSTS(Routes.KNOWN_HOSTS, R.string.section_known_hosts, Icons.Outlined.VerifiedUser),
}

/** A Vault section: big "Vault" title, the section chips pinned under it and its content. */
@Composable
fun VaultScaffold(
    section: VaultSection,
    nav: NavHostController,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    // Desktop layout: the sidebar has the sections, the account switcher and the vault filter.
    if (LocalDesktop.current) {
        DesktopScaffold(
            title = stringResource(section.label),
            actions = actions,
            floatingActionButton = floatingActionButton,
            header = {
                val app = LocalContext.current.applicationContext as TermoakApp
                attentionAccount(app)?.let { AccountAttention(app, nav, it) }
                UpdateBanner()
            },
            content = content,
        )
        return
    }
    ScreenScaffold(
        title = stringResource(R.string.nav_vault),
        large = true,
        actions = actions,
        floatingActionButton = floatingActionButton,
        header = {
            val app = LocalContext.current.applicationContext as TermoakApp
            // The account shown (switcher) and its vaults (filter), for every section.
            AccountSwitcher(app, nav)
            VaultTabs(section) { if (it != section) nav.goVault(it.route) }
            VaultFilterRow(app)
            UpdateBanner()
        },
        content = content,
    )
}

@Composable
internal fun VaultTabs(selected: VaultSection, onSelect: (VaultSection) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        VaultSection.entries.forEach { s ->
            FilterChip(
                selected = s == selected,
                onClick = { onSelect(s) },
                label = { Text(stringResource(s.label)) },
                leadingIcon = { Icon(s.icon, null, Modifier.size(FilterChipDefaults.IconSize)) },
            )
        }
    }
}

/** "Termoak X.Y.Z is available · Download", until it is dismissed (Updates). */
@Composable
internal fun UpdateBanner() {
    val context = LocalContext.current
    val app = context.applicationContext as TermoakApp
    val enabled by app.prefs.checkUpdates.collectAsState()
    val latest by app.updates.latest.collectAsState()
    val dismissed by app.updates.dismissed.collectAsState()
    val update = latest?.takeIf { enabled && it.isNewer && it.version != dismissed } ?: return
    CardBox {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.SystemUpdate, null, tint = MaterialTheme.colorScheme.primary)
            Text(
                stringResource(R.string.update_banner, update.version),
                Modifier.weight(1f).padding(horizontal = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = { openUpdate(context, update) }) { Text(stringResource(R.string.settings_download)) }
            IconButton(onClick = { app.updates.dismiss(update.version) }) {
                Icon(Icons.Outlined.Close, stringResource(R.string.common_close))
            }
        }
    }
}

/**
 * Opens the APK of [update] in the browser, which downloads it and offers to
 * install it (the app doesn't install packages itself).
 */
fun openUpdate(context: Context, update: AppUpdate) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, update.url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // No browser: nothing to open it with.
    }
}
