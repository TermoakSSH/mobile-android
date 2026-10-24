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
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.termoak.app.data.AccessBook
import com.termoak.app.data.AccessRules
import com.termoak.ffi.AccessMode
import com.termoak.ffi.AccessPolicyInfo
import com.termoak.ffi.AccessRequestInfo
import com.termoak.ffi.AccessRequestStatus
import com.termoak.ffi.AccessTargetKind
import com.termoak.ffi.AiApprovalLevel
import com.termoak.ffi.CaKeyInfo
import com.termoak.ffi.CaOverview
import com.termoak.ffi.CertificatePolicy
import com.termoak.ffi.TeamComplianceMember
import com.termoak.ffi.TeamRole
import com.termoak.ffi.TeamSecurityPolicy
import com.termoak.ffi.VaultMember
import com.termoak.ffi.VaultMemberKind
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
 * Teams security on the phone (0.7 phase 4d) drawn on the JVM in English
 * and Spanish, light and dark: the Access screen (requests to decide, open
 * windows, recent decisions, my requests), Request access, Approve, the
 * terminal's "Approval needed" / "Waiting for approval" card, the CA panel
 * with its install command, the team policy and 2FA compliance, an access
 * policy, the role picker, host chips with the countdown, the 2FA banner
 * and the Authentication field. PNGs in app/build/screenshots/access/.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class AccessScreenshotTest(private val lang: String, private val dark: Boolean) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-{1}")
        fun params() = listOf(arrayOf<Any>("en", false), arrayOf<Any>("en", true), arrayOf<Any>("es", false), arrayOf<Any>("es", true))
    }

    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val now = 1_800_000_000_000L
    private val suffix get() = "$lang-${if (dark) "dark" else "light"}"

    @Before
    fun clock() {
        AccessBook.clear()
        AccessBook.now = { now }
        Locale.setDefault(Locale.forLanguageTag(lang))
    }

    @After
    fun reset() {
        AccessBook.clear()
        AccessBook.now = { System.currentTimeMillis() }
        Locale.setDefault(Locale.ENGLISH)
    }

    private fun save(name: String, node: SemanticsNodeInteraction = rule.onRoot()) {
        rule.waitForIdle()
        val bitmap = node.captureToImage().asAndroidBitmap()
        File("build/screenshots/access/$name-$suffix.png").apply { parentFile!!.mkdirs() }
            .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** What is on screen (the activity's content is set once; later screens replace it). */
    private var screen by androidx.compose.runtime.mutableStateOf<(@Composable () -> Unit)?>(null)

    /** The content in the app's theme and in [lang] (the app's own resources, configured for it). */
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

    private fun req(
        id: String, label: String, who: String, reason: String, minutes: Int, status: AccessRequestStatus,
        kind: AccessTargetKind = AccessTargetKind.HOST, canDecide: Boolean = true, ends: Long? = null, by: String? = null, note: String? = null,
    ) = AccessRequestInfo(
        id, "v1", kind, "t-$id", label, "u-$who", who, "$who@example.com".lowercase(), reason, minutes.toUInt(), status,
        now - 5 * 60_000, now + 3 * 3_600_000, by?.let { "u-$it" }, by, by?.let { now - 60_000 }, note,
        ends?.let { now - 60_000 }, ends, false, ends != null && ends > now, canDecide,
    )

    private val lists get() = AccessLists(
        waiting = listOf(
            AccountRequest("a", null, req("r1", "db-prod-1", "Ana", "Restart the replica after the kernel update", 60, AccessRequestStatus.PENDING)),
            AccountRequest("a", null, req("r2", "Production", "Luis", "Deploy hotfix 4.2.1", 120, AccessRequestStatus.PENDING, AccessTargetKind.GROUP)),
        ),
        active = listOf(AccountRequest("a", null, req("r3", "web-1", "Marta", "Check the nginx logs", 30, AccessRequestStatus.APPROVED, ends = now + 95_000, by = "Oihana"))),
        recent = listOf(AccountRequest("a", null, req("r4", "bastion", "Ana", "Rotate keys", 60, AccessRequestStatus.DENIED, canDecide = false, by = "Oihana", note = "Tomorrow, please"))),
        mine = listOf(
            AccountRequest("a", null, req("m1", "db-prod-1", "Me", "Run the migration", 60, AccessRequestStatus.APPROVED, canDecide = false, ends = now + 42 * 60_000, by = "Oihana")),
            AccountRequest("a", null, req("m2", "Production", "Me", "On-call check", 240, AccessRequestStatus.PENDING, AccessTargetKind.GROUP, canDecide = false)),
            AccountRequest("a", null, req("m3", "bastion", "Me", "Audit", 30, AccessRequestStatus.EXPIRED, canDecide = false)),
        ),
    )

    @Test
    fun accessScreen() {
        show(scroll = false) { AccessContent(lists, now, 0, onTab = {}, onApprove = {}, onDeny = {}, onRevoke = {}, onCancel = {}) }
        rule.onNodeWithTag("approve-r1").assertExists()
        save("access-approvals")
        show(scroll = false) { AccessContent(lists, now, 1, onTab = {}, onApprove = {}, onDeny = {}, onRevoke = {}, onCancel = {}) }
        save("access-mine")
    }

    @Test
    fun requestAndApproveDialogs() {
        val need = AccessRules.AccessNeed("h1", "v1", AccessTargetKind.GROUP, "g1", 240, null, true)
        show(scroll = false) {
            RequestAccessDialog("db-prod-1", need, requestTargets(need, "db-prod-1", "Production", "Ops"), onDismiss = {}, inline = true) { _, _, _ -> }
        }
        save("access-request-dialog")
        show(scroll = false) { ApproveDialog(lists.waiting.first().r, onDismiss = {}, inline = true) { _, _ -> } }
        save("access-approve-dialog")
    }

    @Test
    fun terminalCards() {
        show(scroll = false) {
            Box(Modifier.fillMaxSize()) {
                TerminalAccessCardContent(pending = false, maxText = "4 h", canRequest = true, onRequest = {}, onCancel = {}, onClose = {})
            }
        }
        save("terminal-approval-needed")
        show(scroll = false) {
            Box(Modifier.fillMaxSize()) {
                TerminalAccessCardContent(pending = true, maxText = "4 h", canRequest = true, onRequest = {}, onCancel = {}, onClose = {})
            }
        }
        save("terminal-waiting")
    }

    private val ca = CaOverview(
        CaKeyInfo("c1", "ssh-ed25519", "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIOMqqnkVzrm0SdG6UOoqKLsabgH5C9okWi0dh2l9GKJl termoak-ca team=Ops 2026-10-24",
            "SHA256:Xc1dLqHVpWp0n5c6dJfK9P4m2pQq9Yd3w4L2VpXhJ0k", "active", now - 86_400_000, null),
        listOf(CaKeyInfo("c0", "ssh-ed25519", "ssh-ed25519 AAAA…", "SHA256:old0dLqHVpWp0n5c6dJfK9P4m2pQq9Yd3w4L2Vp", "retiring", now - 9 * 86_400_000, now + 20 * 3_600_000)),
        "ssh-ed25519 AAAA…\n", listOf("TrustedUserCAKeys /etc/ssh/termoak_user_ca.pub"),
        listOf("RevokedKeys /etc/ssh/termoak_revoked.krl", "AuthorizedPrincipalsFile /etc/ssh/termoak_principals/%u"),
        "https://next.termoak.com/api/v1/ssh-ca/0190a3a4/keys", "https://next.termoak.com/api/v1/ssh-ca/0190a3a4/krl",
        "https://next.termoak.com/api/v1/teams/0190a3a4/ca/install.sh", CertificatePolicy(60u, 480u, listOf("{login}"), listOf("permit-pty"), null), true,
    )

    @Test
    fun teamSecurity() {
        val policy = TeamSecurityPolicy(
            true, now - 3 * 86_400_000, 7u, false, true, AiApprovalLevel.ALWAYS, false, true,
            CertificatePolicy(60u, 480u, listOf("{login}", "prod-{member}"), listOf("permit-pty", "permit-port-forwarding"), null),
        )
        val members = listOf(
            TeamComplianceMember("u1", "oihana@example.com", "Oihana", TeamRole.OWNER, true, null, false, null),
            TeamComplianceMember("u2", "ana@example.com", "Ana", TeamRole.MEMBER, false, now + 4 * 86_400_000, false, "ana"),
            TeamComplianceMember("u3", "luis@example.com", "Luis", TeamRole.MEMBER, false, now - 86_400_000, true, null),
        )
        show {
            CaPanelContent(ca, null, personal = false, busy = false, onCreate = {}, onRotate = {}, onDisable = {}, onCertificates = {}, onRevokeKey = {}) { _, _, _ -> "" }
        }
        rule.onNodeWithTag("ca-copy-install").assertExists()
        save("team-ca")
        show {
            TeamPolicyForm(policy, manage = true, saving = false) {}
            ComplianceSection(members) {}
        }
        save("team-policy")
        show {
            CaPanelContent(ca.copy(ca = null, previous = emptyList()), null, personal = true, busy = false, onCreate = {}, onRotate = {}, onDisable = {}, onCertificates = {}, onRevokeKey = {}) { _, _, _ -> "" }
        }
        save("personal-ca-create")
    }

    @Test
    fun policyRolesAndHosts() {
        show {
            AccessPolicyForm(
                AccessPolicyInfo(mode = AccessMode.APPROVAL, approvers = listOf("u1"), maxMinutes = 240u, requestTtlMinutes = 240u, recordSessions = true),
                listOf(Approver("u1", "Oihana"), Approver("u2", "Ana")), saving = false, hasOwn = true, onSave = {}, onRemove = {},
            )
        }
        save("access-policy")
        show {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RolePicker(AccessRules.grantableRoles(true), VaultRole.CONNECT_ONLY) {}
            }
            MemberRow(VaultMember("m1", VaultMemberKind.USER, "u1", "oihana@example.com", "Oihana", null, VaultRole.MANAGER, true, 0), canManage = false, onRole = {}, onRemove = {})
            MemberRow(VaultMember("m2", VaultMemberKind.USER, "u2", "ana@example.com", "Ana", null, VaultRole.CONNECT_ONLY, false, 0), canManage = false, onRole = {}, onRemove = {})
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    AccessWindowChip(AccessBook.HostWindow.Open(now + 42 * 60_000))
                    RecordedChip()
                    ConnectOnlyBadge()
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    AccessWindowChip(AccessBook.HostWindow.Open(now + 95_000))
                    AccessWindowChip(AccessBook.HostWindow.Pending)
                    AccessWindowChip(AccessBook.HostWindow.Needed)
                }
            }
            Team2faCard(
                androidx.compose.ui.res.stringResource(com.termoak.app.R.string.team_2fa_title, "Ops"),
                androidx.compose.ui.res.stringResource(com.termoak.app.R.string.team_2fa_detail, com.termoak.app.data.AccessTexts.date(now + 6 * 86_400_000)),
                blocked = false,
            ) {}
            Team2faCard(
                androidx.compose.ui.res.stringResource(com.termoak.app.R.string.team_2fa_blocked_title, "Ops"),
                androidx.compose.ui.res.stringResource(com.termoak.app.R.string.team_2fa_blocked_detail), blocked = true,
            ) {}
            FormSection(androidx.compose.ui.res.stringResource(com.termoak.app.R.string.auth_field_label)) {
                AuthField(null, AccessRules.EffectiveAuth(AccessRules.TEAM_CERTIFICATE, "Production"), certificates = false, enabled = true, outlined = false) {}
            }
            Text(" ", Modifier.height(24.dp))
        }
        save("roles-hosts-auth")
    }
}
