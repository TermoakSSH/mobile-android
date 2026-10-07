package com.termoak.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.termoak.app.R
import com.termoak.app.TermoakApp
import com.termoak.app.data.AccountView
import com.termoak.app.data.Prefs
import com.termoak.app.data.ThemeMode
import com.termoak.ffi.AccountInfo
import com.termoak.ffi.AccountStatus
import com.termoak.ffi.VaultInfo
import com.termoak.ffi.VaultKind
import com.termoak.ffi.VaultRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs

/**
 * Screenshots of the Vault (hosts) on a phone and of the welcome screen, on
 * the JVM (Robolectric, Android's own graphics): the first frame (before the
 * window insets arrive), with the status and gesture bars, after scrolling
 * the hosts and after scrolling back. The Vault is the phone layout of
 * VaultScaffold — ScreenScaffold with the real account switcher button,
 * section chips and vault chips — over a list like the hosts'; the engine
 * (accounts, hosts) is not loaded on the JVM. The status bar's icons are
 * drawn on the PNGs as the system would (light or dark, from the window's
 * appearance). PNGs in app/build/screenshots/vault/ (one strip per case).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// TermoakApp: the welcome screen takes it (its engine is not loaded: nothing here calls it).
@Config(application = TermoakApp::class, sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class VaultScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val statusDp = 48
    private val navDp = 24
    private val density get() = rule.activity.resources.displayMetrics.density

    /** What the screen looks like at one moment: the PNG and where the account switcher is. */
    private class Frame(val name: String, val bitmap: Bitmap)

    private fun lightStatusBars(): Boolean = rule.runOnUiThread<Boolean> {
        val w = rule.activity.window
        WindowCompat.getInsetsController(w, w.decorView).isAppearanceLightStatusBars
    }

    /** The screen now, with the status bar's icons and the gesture handle drawn as the system would. */
    private fun frame(name: String, bars: Boolean): Frame {
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, true)
        if (bars) drawSystemBars(bitmap, lightStatusBars())
        return Frame(name, bitmap)
    }

    private fun drawSystemBars(bitmap: Bitmap, light: Boolean) {
        val d = density
        val canvas = Canvas(bitmap)
        // Light bars (isAppearanceLightStatusBars) have dark icons.
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (light) 0xFF1F1F1F.toInt() else 0xFFF2F2F2.toInt()
            textSize = 15 * d
            isFakeBoldText = true
        }
        val mid = statusDp * d / 2
        canvas.drawText("12:30", 20 * d, mid + 5 * d, paint)
        val right = bitmap.width - 20 * d
        canvas.drawRoundRect(RectF(right - 22 * d, mid - 6 * d, right, mid + 6 * d), 3 * d, 3 * d, paint)
        canvas.drawCircle(right - 36 * d, mid, 6 * d, paint)
        canvas.drawRect(right - 58 * d, mid - 6 * d, right - 48 * d, mid + 6 * d, paint)
        val y = bitmap.height - navDp * d / 2
        canvas.drawRoundRect(RectF(bitmap.width / 2f - 54 * d, y - 2 * d, bitmap.width / 2f + 54 * d, y + 2 * d), 2 * d, 2 * d, paint)
    }

    /** The status bar (top) and the gesture bar (bottom), as the system dispatches them once the window is laid out. */
    private fun dispatchInsets() {
        val d = density
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, (statusDp * d).toInt(), 0, 0))
            .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, (navDp * d).toInt()))
            .build()
        rule.runOnUiThread { ViewCompat.dispatchApplyWindowInsets(rule.activity.window.decorView, insets) }
        rule.waitForIdle()
    }

    /** The frames side by side at half size, in build/screenshots/vault/[name].png (and each one alone). */
    private fun save(name: String, frames: List<Frame>) {
        val dir = File("build/screenshots/vault").apply { mkdirs() }
        frames.forEach { f ->
            File(dir, "$name-${f.name}.png").outputStream().use { f.bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        val w = frames.first().bitmap.width / 2
        val h = frames.first().bitmap.height / 2
        val gap = 12
        val strip = Bitmap.createBitmap(frames.size * (w + gap) - gap, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(strip)
        canvas.drawColor(0xFF808080.toInt())
        frames.forEachIndexed { i, f ->
            canvas.drawBitmap(Bitmap.createScaledBitmap(f.bitmap, w, h, true), (i * (w + gap)).toFloat(), 0f, null)
        }
        File(dir, "$name.png").outputStream().use { strip.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** The text of the account switcher's button, to find it. */
    private fun switcherText(accounts: List<AccountInfo>, view: AccountView): String =
        (view as? AccountView.One)?.let { v -> accounts.first { it.id == v.id }.email } ?: rule.activity.getString(R.string.accounts_all)

    private fun topOf(text: String): Float = rule.onNodeWithText(text).getBoundsInRoot().top.value

    private fun setUp(theme: ThemeMode) {
        val activity = rule.activity
        // As MainActivity: edge to edge (system bars from the system's theme), then the app's theme.
        rule.runOnUiThread { activity.enableEdgeToEdge() }
        Prefs(activity).setTheme(theme)
    }

    /**
     * The Vault from launch: first frame, insets, a scroll down the hosts
     * and back up. The header (account switcher, chips) must stay where it
     * is: under a one-row bar with the title, right below the status bar.
     */
    private fun vault(name: String, theme: ThemeMode, appDark: Boolean, accounts: List<AccountInfo>, view: AccountView, vaults: List<VaultInfo>) {
        setUp(theme)
        val prefs = Prefs(rule.activity)
        rule.setContent { TermoakTheme(prefs) { PhoneVault(accounts, view, vaults) } }
        rule.waitForIdle()
        val launch = frame("1-launch", bars = false)
        dispatchInsets()
        val insets = frame("2-insets", bars = true)
        val headerAtLaunch = topOf(switcherText(accounts, view))
        rule.onNodeWithTag("hosts").performTouchInput { swipeUp(startY = centerY + 200f, endY = centerY - 200f) }
        rule.waitForIdle()
        val scrolled = frame("3-scrolled", bars = true)
        val headerScrolled = topOf(switcherText(accounts, view))
        rule.onNodeWithTag("hosts").performTouchInput { swipeDown(startY = centerY - 300f, endY = centerY + 300f) }
        rule.waitForIdle()
        val back = frame("4-back", bars = true)
        save(name, listOf(launch, insets, scrolled, back))

        // System bars: icons that can be seen on the app's background.
        assertEquals("light status bar (dark icons) with the light theme only", !appDark, lightStatusBars())
        // The header doesn't move when scrolling, and sits under one bar row (64 dp) below the status bar.
        assertTrue("header moved from $headerAtLaunch to $headerScrolled dp", abs(headerAtLaunch - headerScrolled) < 1f)
        assertTrue("header at $headerAtLaunch dp: too far down", headerAtLaunch < statusDp + 64 + 16)
    }

    @Test
    fun vaultOneAccountAppDarkSystemLight() =
        vault("vault-one-account-dark-on-light-system", ThemeMode.DARK, true, listOf(account1), AccountView.One(account1.id), listOf(personal1))

    @Test
    @Config(qualifiers = "+night")
    fun vaultOneAccountAppDarkSystemDark() =
        vault("vault-one-account-dark-on-dark-system", ThemeMode.DARK, true, listOf(account1), AccountView.One(account1.id), listOf(personal1))

    @Test
    fun vaultOneAccountWithVaultChips() =
        vault("vault-one-account-chips", ThemeMode.DARK, true, listOf(account1), AccountView.One(account1.id), listOf(personal1, team1))

    @Test
    fun vaultSeveralAccounts() =
        vault(
            "vault-several-accounts", ThemeMode.DARK, true, listOf(account1, account2), AccountView.All,
            listOf(personal1, team1, personal2),
        )

    @Test
    @Config(qualifiers = "+night")
    fun vaultAppLightSystemDark() =
        vault("vault-light-on-dark-system", ThemeMode.LIGHT, false, listOf(account1, account2), AccountView.All, listOf(personal1, team1, personal2))

    @Test
    fun vaultAppSystemThemeLight() =
        vault("vault-system-theme-light", ThemeMode.SYSTEM, false, listOf(account1), AccountView.One(account1.id), listOf(personal1))

    /** The welcome screen (first launch): the real LoginScreen, keeping clear of the status bar. */
    private fun welcome(name: String, theme: ThemeMode, appDark: Boolean) {
        setUp(theme)
        val app = rule.activity.application as TermoakApp
        rule.setContent {
            TermoakTheme(app.prefs) {
                // As in AppRoot: the gesture bar is the Scaffold's (consumed), the screen adds the status bar.
                Scaffold(contentWindowInsets = WindowInsets.navigationBars) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                        LoginScreen(app, welcome = true, onDone = {}, onBack = null)
                    }
                }
            }
        }
        rule.waitForIdle()
        val launch = frame("1-launch", bars = false)
        dispatchInsets()
        val insets = frame("2-insets", bars = true)
        save(name, listOf(launch, insets))
        assertEquals("light status bar (dark icons) with the light theme only", !appDark, lightStatusBars())
        val title = topOf(rule.activity.getString(R.string.app_name))
        assertTrue("title at $title dp, under the status bar", title > statusDp)
    }

    @Test
    fun welcomeAppDarkSystemLight() = welcome("welcome-dark-on-light-system", ThemeMode.DARK, true)

    @Test
    @Config(qualifiers = "+night")
    fun welcomeAppLightSystemDark() = welcome("welcome-light-on-dark-system", ThemeMode.LIGHT, false)
}

private fun account(id: String, email: String, name: String) = AccountInfo(
    id = id, serverUrl = "https://termoak.com", serverName = "termoak.com", official = true, insecure = false,
    email = email, name = name, userId = null, status = AccountStatus.ACTIVE, color = null, isCurrent = id == "a1",
    vaultsSupported = true, lastSyncAt = null,
)

private fun vaultInfo(id: String, accountId: String, name: String, kind: VaultKind, color: String?) = VaultInfo(
    id = id, accountId = accountId, name = name, description = "", kind = kind, role = VaultRole.MANAGER,
    teamId = null, teamName = null, ownerName = null, color = color, icon = null, memberCount = 1, hostCount = 4,
    strict = false, teamMemberRole = null,
)

private val account1 = account("a1", "ane@example.com", "Ane")
private val account2 = account("a2", "jon@work.example", "Jon")
private val personal1 = vaultInfo("v1", "a1", "Personal", VaultKind.PERSONAL, null)
private val team1 = vaultInfo("v2", "a1", "Infra", VaultKind.TEAM, "#3FB27F")
private val personal2 = vaultInfo("v3", "a2", "Personal", VaultKind.PERSONAL, "#E8A33D")

private val hosts = listOf(
    "web-1" to "ubuntu", "web-2" to "ubuntu", "db-primary" to "debian", "db-replica" to "debian",
    "backup" to "fedora", "gateway" to "alpine", "build" to "arch", "monitoring" to "rocky",
    "mail" to "debian", "vpn" to "ubuntu", "nas" to "freebsd", "staging" to "ubuntu",
)

/**
 * The Vault on a phone as AppRoot and VaultScaffold lay it out: bottom bar,
 * ScreenScaffold(large) with the account switcher, the section chips and
 * the vault chips (when there are several vaults) pinned under the title,
 * and the hosts in a pull-to-refresh grid.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhoneVault(accounts: List<AccountInfo>, view: AccountView, vaults: List<VaultInfo>) {
    Scaffold(
        contentWindowInsets = WindowInsets.navigationBars,
        bottomBar = {
            NavigationBar {
                listOf(
                    R.string.nav_vault to Icons.Outlined.Dns, R.string.nav_connections to Icons.Outlined.Terminal,
                    R.string.section_ai to Icons.Outlined.AutoAwesome, R.string.section_settings to Icons.Outlined.Settings,
                ).forEachIndexed { i, (label, icon) ->
                    NavigationBarItem(i == 0, {}, { Icon(icon, null) }, label = { Text(stringResource(label)) })
                }
            }
        },
    ) { outer ->
        Row(Modifier.fillMaxSize().padding(outer).consumeWindowInsets(outer)) {
            Box(Modifier.weight(1f)) {
                ScreenScaffold(
                    title = stringResource(R.string.nav_vault),
                    large = true,
                    actions = { IconButton({}) { Icon(Icons.Outlined.Sync, stringResource(R.string.common_sync)) } },
                    floatingActionButton = {
                        FloatingActionButton({}, shape = RoundedCornerShape(16.dp)) { Icon(Icons.Outlined.Add, null) }
                    },
                    header = {
                        Box(Modifier.padding(horizontal = 12.dp)) { AccountSwitcherButton(accounts, view) {} }
                        VaultTabs(VaultSection.HOSTS) {}
                        val shown = vaults.filter { v -> view == AccountView.All || (view as? AccountView.One)?.id == v.accountId }
                        if (shown.size > 1) VaultFilterChips(shown, null, accounts, several = accounts.size > 1 && view == AccountView.All) {}
                    },
                ) { padding ->
                    PullToRefreshBox(false, {}, Modifier.fillMaxSize().padding(padding)) {
                        LazyVerticalGrid(
                            GridCells.Adaptive(minSize = 340.dp), Modifier.fillMaxSize().testTag("hosts"),
                            contentPadding = PaddingValues(bottom = 96.dp),
                        ) {
                            item(span = { GridItemSpan(maxLineSpan) }) { SearchBox() }
                            item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel(stringResource(R.string.section_hosts)) }
                            items(hosts.size, key = { hosts[it].first }) { i -> HostLine(hosts[i].first, hosts[i].second) }
                        }
                    }
                }
            }
        }
    }
}

/** The hosts' search box (as SearchField). */
@Composable
private fun SearchBox(height: Dp = 56.dp) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(height)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.hosts_search), Modifier.padding(start = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A host row (as HostRow): tile with the system's logo, name, "ssh, user" and "⋮". */
@Composable
private fun HostLine(name: String, os: String) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HostTile(name, os, size = 44.dp)
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            Text("ssh, root", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton({}) { Icon(Icons.Outlined.MoreVert, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
