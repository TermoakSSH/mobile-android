package com.termoak.app.ui

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
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.termoak.app.R

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
    ScreenScaffold(
        title = stringResource(R.string.nav_vault),
        large = true,
        actions = actions,
        floatingActionButton = floatingActionButton,
        header = { VaultTabs(section) { if (it != section) nav.goVault(it.route) } },
        content = content,
    )
}

@Composable
private fun VaultTabs(selected: VaultSection, onSelect: (VaultSection) -> Unit) {
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
