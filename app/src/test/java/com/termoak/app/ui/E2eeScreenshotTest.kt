package com.termoak.app.ui

import android.app.Application
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.termoak.app.R
import com.termoak.app.data.AuditRules
import com.termoak.app.data.E2eeRules
import com.termoak.app.data.SsoRules
import com.termoak.app.term.TermSession
import com.termoak.ffi.AuditEvent
import com.termoak.ffi.AuditSinkInfo
import com.termoak.ffi.E2eeKeyInfo
import com.termoak.ffi.E2eeMember
import com.termoak.ffi.E2eePairingRequest
import com.termoak.ffi.E2eeStatus
import com.termoak.ffi.E2eeVaultState
import com.termoak.ffi.SsoHint
import com.termoak.ffi.SsoSettings
import com.termoak.ffi.TeamDomainInfo
import com.termoak.ffi.VaultE2eeInfo
import com.termoak.ffi.VaultInfo
import com.termoak.ffi.VaultKind
import com.termoak.ffi.VaultRole
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Locale

/**
 * E2EE vaults, SSO and the extended audit on the phone (0.7 phase 5c) drawn
 * on the JVM in English and Spanish, light and dark: Settings → Security
 * (not set up, unlocked with a pairing request, locked), the recovery
 * phrase shown and confirmed, the passphrase, unlock (recovery phrase with
 * a refusal explained, another device's code), a pairing request, reset,
 * a vault's Encryption page (off with fingerprints, on with a member
 * waiting and a rotation needed), the terminal's E2EE cards, the sign-in's
 * SSO card and its 2FA step, a team's SSO with a domain, the audit and its
 * sinks, Home's banners and the badges. Every phrase and fingerprint is
 * made up. PNGs in app/build/screenshots/e2ee/.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class E2eeScreenshotTest(private val lang: String, private val dark: Boolean) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-{1}")
        fun params() = listOf(arrayOf<Any>("en", false), arrayOf<Any>("en", true), arrayOf<Any>("es", false), arrayOf<Any>("es", true))

        /** Made up for the screenshots: Trezor's all-zero BIP39 vector, never an account's phrase. */
        const val PHRASE = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon " +
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon art"
        const val FP = "1a2b 3c4d 5e6f 7081 92a3 b4c5 d6e7 f809"
    }

    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val now = 1_800_000_000_000L
    private val suffix get() = "$lang-${if (dark) "dark" else "light"}"

    @Before
    fun locale() = Locale.setDefault(Locale.forLanguageTag(lang))

    @After
    fun reset() = Locale.setDefault(Locale.ENGLISH)

    private fun save(name: String, node: SemanticsNodeInteraction = rule.onRoot()) {
        rule.waitForIdle()
        val bitmap = node.captureToImage().asAndroidBitmap()
        File("build/screenshots/e2ee/$name-$suffix.png").apply { parentFile!!.mkdirs() }
            .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private var screen by androidx.compose.runtime.mutableStateOf<(@Composable () -> Unit)?>(null)

    private fun show(scroll: Boolean = true, content: @Composable () -> Unit) {
        val first = screen == null
        screen = {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                if (scroll) Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) { content() } else content()
            }
        }
        if (!first) return
        rule.setContent {
            val base = LocalContext.current
            val config = Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag(lang)) }
            val ctx = base.createConfigurationContext(config)
            CompositionLocalProvider(LocalContext provides ctx, LocalConfiguration provides config, LocalResources provides ctx.resources) {
                MaterialTheme(colorScheme = appColorScheme(dark), typography = appTypography) {
                    screen?.let { androidx.compose.runtime.key(it) { it() } }
                }
            }
        }
    }

    private val key = E2eeKeyInfo("k1", FP, now - 86_400_000)
    private val pairing = E2eePairingRequest("p1", "Pixel 9 Pro", "482913", now - 30_000, now + 552_000)
    private val noop: () -> Unit = {}

    @Composable
    private fun section(st: E2eeStatus?, pairings: List<E2eePairingRequest> = emptyList(), waiting: Long = 0) =
        E2eeSectionContent(st, pairings, waiting, now, noop, noop, noop, noop, noop, noop, noop, noop) {}

    @Test
    fun security() {
        show { section(E2eeStatus(false, false, null, false, false, 0u, 0uL)); IdentitiesContent(emptyList(), listOf(LinkableTeam("t1", "Acme")), null, {}, {}) }
        rule.onNodeWithTag("e2ee-setup").assertExists()
        save("security-not-set-up")
        show {
            section(E2eeStatus(true, true, key, true, true, 1u, 0uL), listOf(pairing))
            IdentitiesContent(listOf(LinkedIdentity("t1", "Acme", "https://login.acme.example", "ana@acme.example", now - 3_600_000)), emptyList(), null, {}, {})
        }
        rule.onNodeWithTag("pairing-p1").assertExists()
        save("security-unlocked")
        show { section(E2eeStatus(true, false, key, false, true, 0u, 4uL), waiting = 2) }
        save("security-locked")
    }

    @Test
    fun phrase() {
        show(scroll = false) { PhraseShowContent(stringResource(R.string.e2ee_setup_title), PHRASE, true, {}, noop, noop, noop, noop) }
        rule.onNodeWithTag("phrase-grid").assertExists()
        save("phrase-show")
        show(scroll = false) { PhraseConfirmContent(listOf(2, 11, 23), listOf("aban", "", ""), { _, _ -> }, wrong = true, busy = false, onBack = noop, onDone = noop) }
        save("phrase-confirm")
        show { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { PassphraseFields("correct horse battery", "correct horse battery", {}, {}) } }
        save("passphrase")
    }

    @Test
    fun unlockAndPairing() {
        show {
            Column(Modifier.padding(16.dp)) {
                UnlockContent(
                    UnlockWay.RECOVERY, {}, true, true, "", {}, "abandon abandon qwerty aban", {},
                    listOf("abandon", "ability", "able"), E2eeRules.Feedback.Problem("unknown_word", 3, 4), null, null, null,
                )
            }
        }
        save("unlock-recovery")
        show { Column(Modifier.padding(16.dp)) { UnlockContent(UnlockWay.DEVICE, {}, true, true, "", {}, "", {}, emptyList(), E2eeRules.Feedback.Empty, "482913", "9:12", null) } }
        rule.onNodeWithTag("pairing-code").assertExists()
        save("unlock-device")
        show { Column(Modifier.padding(16.dp)) { PairingContent(pairing, "9:12") } }
        save("pairing-approve")
        show { Column(Modifier.padding(16.dp)) { ResetContent(listOf("Ops", "Billing"), "ana@example.com", "ana@", {}) } }
        save("reset")
    }

    private fun vault(e: VaultE2eeInfo?, role: VaultRole = VaultRole.MANAGER) = VaultInfo(
        "v1", "a1", "Ops", "", VaultKind.TEAM, role, "t1", "Acme", "Acme", null, null, 3L, 12L, true, null, null, e,
    )

    @Test
    fun vaultEncryption() {
        val members = listOf(
            E2eeMember("u1", "ana@example.com", "Ana", FP, false),
            E2eeMember("u2", "luis@example.com", "Luis", "9f8e 7d6c 5b4a 3928 1706 f5e4 d3c2 b1a0", false),
            E2eeMember("u3", "marta@example.com", "Marta", "0a1b 2c3d 4e5f 6071 8293 a4b5 c6d7 e8f9", false),
        )
        show {
            VaultE2eeContent(
                vault(null), E2eeRules.KeyState.OFF, FP, "u1", E2eeVaultState(false, 1u, members, listOf(members[2].copy(userId = "u4")), 14u, false),
                emptyList(), networks = 1, selected = setOf("u2"), busy = false, onToggle = { _, _ -> }, onTurnOn = noop, onTurnOff = noop,
                onRotate = noop, onConfirm = {}, onSetUp = noop, onUnlock = noop,
            )
        }
        rule.onNodeWithTag("e2ee-turn-on").assertExists()
        save("vault-e2ee-off")
        show {
            VaultE2eeContent(
                vault(VaultE2eeInfo(3u, true, true, true, 1u, 0L)), E2eeRules.KeyState.HOLDER, FP, "u1", null,
                listOf(members[2].copy(keyChanged = true)), networks = 0, selected = emptySet(), busy = false, onToggle = { _, _ -> },
                onTurnOn = noop, onTurnOff = noop, onRotate = noop, onConfirm = {}, onSetUp = noop, onUnlock = noop,
            )
        }
        rule.onNodeWithTag("confirm-u3").assertExists()
        save("vault-e2ee-on")
    }

    @Test
    fun terminalCards() {
        show(scroll = false) { Box(Modifier.fillMaxSize()) { TerminalE2eeCardContent(TermSession.E2eeNeed.SEND, noop, noop, noop) } }
        rule.onNodeWithTag("e2ee-send").assertExists()
        save("terminal-send")
        show(scroll = false) { Box(Modifier.fillMaxSize()) { TerminalE2eeCardContent(TermSession.E2eeNeed.UNLOCK, noop, noop, noop) } }
        save("terminal-locked")
    }

    @Test
    fun ssoSignInAndTeam() {
        show {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SsoCard(SsoHint(true, true, "t1", "Acme"), busy = false, onContinue = noop, onOwnerPassword = noop)
                SsoCard(SsoHint(true, false, "t1", "Acme"), busy = false, onContinue = noop, onOwnerPassword = null)
                SsoWaitingCard(totp = false, code = "", onCode = {}, busy = false, onSubmit = noop, onCancel = noop, onAgain = noop)
                SsoWaitingCard(totp = true, code = "123", onCode = {}, busy = false, onSubmit = noop, onCancel = noop, onAgain = noop)
            }
        }
        rule.onAllNodesWithTag("sso-card").assertCountEquals(2)
        save("sso-sign-in")
        val s = SsoSettings(true, "keycloak", "https://sso.acme.example/realms/acme", null, "termoak", listOf("openid", "email", "profile"), null, true, false, true, true, true, false, true, "https://next.termoak.com/api/v1/auth/sso/callback")
        show {
            SsoFormContent(SsoRules.Form.of(s), {}, true, s.redirectUri, canEnforce = true, error = null, busy = false, configured = true, onCopy = {}, onSave = noop, onRemove = noop)
        }
        save("team-sso-form")
        show {
            DomainsContent(
                listOf(
                    TeamDomainInfo("acme.example", "_termoak-challenge.acme.example", "termoak-verify=Qm9vbmRvZ2dsZQ", false, null, "no TXT record yet"),
                    TeamDomainInfo("acme.dev", "_termoak-challenge.acme.dev", "termoak-verify=x", true, now - 86_400_000, null),
                ),
                "", {}, busy = false, onAdd = noop, onVerify = {}, onRemove = {}, onCopy = {}, onShare = {},
            )
        }
        rule.onNodeWithTag("verify-acme.example").assertExists()
        save("team-sso-domains")
    }

    @Test
    fun audit() {
        val entries = listOf(
            AuditEvent(5, "u1", "user:u1", "e2ee.enable", "Ops", """{"items":14}""", now - 60_000, "v1", "t1"),
            AuditEvent(4, "u1", "user:u2", "e2ee.unlock", "db-prod-1", """{"session":"s9"}""", now - 3_600_000, "v1", "t1"),
            AuditEvent(3, "u1", "user:u3", "sso.login", null, """{"device":"Pixel 9"}""", now - 7_200_000, null, "t1"),
            AuditEvent(2, "u1", "user:u1", "sso.domain_verify", "acme.example", "{}", now - 86_400_000, null, "t1"),
            AuditEvent(1, "u1", "user:u2", "access.approve", "web-1", """{"minutes":60}""", now - 2 * 86_400_000, "v1", "t1"),
        )
        val names = mapOf("u1" to "ana@example.com", "u2" to "luis@example.com", "u3" to "marta@example.com")
        show {
            AuditFilterBar(AuditRules.Filters(AuditRules.Preset.E2EE, range = AuditRules.Range.WEEK), showActor = true, onChange = {}, onSearch = noop)
            entries.forEach { AuditRow(it, AuditRules.actor(it.actor, names)) }
        }
        save("audit-team")
        show {
            AuditSettingsContent(
                AuditRules.RetentionView(180, 365, 3650), "180", {}, noop,
                listOf(
                    AuditSinkInfo("s1", "webhook", "SIEM", "https://siem.acme.example/termoak", null, null, null, emptyList(), true, 0, null, now - 120_000),
                    AuditSinkInfo("s2", "syslog", "Graylog", null, "logs.acme.example", null, "tls", listOf("e2ee.*"), true, 3, "connection refused", null),
                    AuditSinkInfo("s3", "webhook", "Old", "https://old.acme.example", null, null, null, emptyList(), false, 0, null, null),
                ),
                busy = false, onTest = {}, onToggle = {}, onDelete = {},
            )
        }
        save("audit-sinks")
    }

    @Test
    fun bannersAndBadges() {
        show {
            E2eeBanner(stringResource(R.string.e2ee_banner_locked_title), stringResource(R.string.e2ee_banner_locked_detail), stringResource(R.string.e2ee_unlock_button)) {}
            E2eeBanner(stringResource(R.string.e2ee_banner_pairing_title, "Pixel 9 Pro"), stringResource(R.string.e2ee_banner_pairing_detail), stringResource(R.string.e2ee_pairing_review), Brand.Blue) {}
            E2eeBanner(pluralStringResource(R.plurals.e2ee_banner_pending_title, 2, 2, "Ops"), stringResource(R.string.e2ee_banner_pending_detail), stringResource(R.string.e2ee_pairing_review), Brand.Blue) {}
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                E2eeBadge(E2eeRules.Badge.E2EE)
                E2eeBadge(E2eeRules.Badge.LOCKED)
                E2eeLockedChip()
            }
            NetworksE2eeWarning(2)
            Text(stringResource(R.string.vault_e2ee_network_form_warning), Modifier.padding(16.dp))
        }
        save("banners-badges")
    }
}
