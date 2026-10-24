package com.termoak.app.store

import com.termoak.ffi.AccountInfo
import com.termoak.ffi.AccountStatus
import com.termoak.ffi.AiAccessInfo
import com.termoak.ffi.AiApproval
import com.termoak.ffi.AiApprovalPreview
import com.termoak.ffi.AiKeyProvider
import com.termoak.ffi.AiPermissionMode
import com.termoak.ffi.AiProvider
import com.termoak.ffi.AiProviders
import com.termoak.ffi.AiRiskLevel
import com.termoak.ffi.AiRiskReason
import com.termoak.ffi.AiTask
import com.termoak.ffi.AiTaskStatus
import com.termoak.ffi.HostGroup
import com.termoak.ffi.HostSettings
import com.termoak.ffi.ItemAccess
import com.termoak.ffi.RemoteFile
import com.termoak.ffi.RemoteFileKind
import com.termoak.ffi.Snippet
import com.termoak.ffi.SshHost
import com.termoak.ffi.SshKey
import com.termoak.ffi.SyncMode
import com.termoak.ffi.Team
import com.termoak.ffi.TeamRole
import com.termoak.ffi.VaultInfo
import com.termoak.ffi.VaultKind
import com.termoak.ffi.VaultMember
import com.termoak.ffi.VaultMemberKind
import com.termoak.ffi.VaultRole
import org.json.JSONArray
import org.json.JSONObject

/**
 * The demo world of the store screenshots: two accounts (one on termoak.com,
 * one on a self-hosted server), a team vault with Production and Staging
 * hosts, terminals with colourful sessions, an AI task waiting for approval
 * and some files. Only example.com names and 10.0.x.x addresses. Texts that
 * a person would type (or the AI would write) follow the screenshot's
 * language ([es]).
 */
object Demo {
    /** Spanish screenshots (the app's own texts come from its resources). */
    @Volatile var es = false

    fun tr(en: String, es: String): String = if (this.es) es else en

    /** Now (the times shown are relative to the clock). */
    val NOW = System.currentTimeMillis()
    private const val MIN = 60_000L
    private const val HOUR = 60 * MIN
    private const val DAY = 24 * HOUR

    const val A1 = "0192f0c0-5a1e-7c3d-8e21-4b6f0a9d1c01"
    const val A2 = "0192f0c0-5a1e-7c3d-8e21-4b6f0a9d1c02"
    const val V_PERSONAL = "0192f0c1-1111-7000-8000-00000000a001"
    const val V_INFRA = "0192f0c1-1111-7000-8000-00000000a002"
    const val V_WORK = "0192f0c1-1111-7000-8000-00000000a003"
    const val G_PROD = "0192f0c2-2222-7000-8000-00000000b001"
    const val G_STAGING = "0192f0c2-2222-7000-8000-00000000b002"

    val accounts: List<AccountInfo>
        get() = listOf(
            AccountInfo(
                A1, "https://termoak.com", "termoak.com", true, false, "ane@example.com", "Ane Etxeberria", "u-1",
                AccountStatus.ACTIVE, "#3FB27F", true, true, NOW - 2 * MIN,
            ),
            AccountInfo(
                A2, "https://termoak.ops.example.com", "Acme Ops", false, false, "ane@ops.example.com", "Ane Etxeberria", "u-7",
                AccountStatus.ACTIVE, "#4F7CFF", false, true, NOW - 9 * MIN,
            ),
        )

    /** The names given to the accounts on this device. */
    val aliases: Map<String, String> get() = mapOf(A1 to tr("Personal", "Personal"), A2 to tr("Work", "Trabajo"))

    val vaults: List<VaultInfo>
        get() = listOf(
            VaultInfo(V_PERSONAL, A1, "", "", VaultKind.PERSONAL, VaultRole.MANAGER, null, null, null, null, null, 1, 2, false, null, null),
            VaultInfo(
                V_INFRA, A1, "Infra", tr("Production and staging servers", "Servidores de producción y staging"), VaultKind.TEAM,
                VaultRole.MANAGER, "t-platform", "Platform", null, "#30a46c", null, 6, 13, false, VaultRole.MANAGER, null,
            ),
            VaultInfo(V_WORK, A2, "", "", VaultKind.PERSONAL, VaultRole.MANAGER, null, null, null, "#4f7cff", null, 1, 2, false, null, null),
        )

    val teams = listOf(Team("t-platform", "Platform", TeamRole.OWNER, 6u, NOW - 120 * DAY))

    fun members(vaultId: String): List<VaultMember> = if (vaultId != V_INFRA) {
        listOf(VaultMember("m0", VaultMemberKind.USER, "u-1", "ane@example.com", "Ane Etxeberria", null, VaultRole.MANAGER, false, NOW - 200 * DAY))
    } else {
        listOf(
            VaultMember("m0", VaultMemberKind.USER, "u-1", "ane@example.com", "Ane Etxeberria", null, VaultRole.MANAGER, false, NOW - 120 * DAY),
            VaultMember("m1", VaultMemberKind.USER, "u-2", "jon@example.com", "Jon Ugarte", null, VaultRole.EDITOR, false, NOW - 96 * DAY),
            VaultMember("m2", VaultMemberKind.USER, "u-3", "maite@example.com", "Maite Arrieta", null, VaultRole.EDITOR, false, NOW - 64 * DAY),
            VaultMember("m3", VaultMemberKind.USER, "u-4", "lucia@example.com", "Lucía Romero", null, VaultRole.USE_ONLY, false, NOW - 21 * DAY),
            VaultMember("m4", VaultMemberKind.USER, "u-5", "contractor@example.com", "Sam Patel", null, VaultRole.USE_ONLY, false, NOW - 5 * DAY),
            VaultMember("m5", VaultMemberKind.TEAM, null, null, "Platform", "t-platform", VaultRole.EDITOR, false, NOW - 120 * DAY),
        )
    }

    private fun host(
        id: String,
        label: String,
        address: String,
        os: String,
        osVersion: String,
        vault: String = V_INFRA,
        account: String = A1,
        group: String? = null,
        tags: List<String> = emptyList(),
        user: String = "ane",
        favorite: Boolean = false,
        port: UInt? = null,
        notes: String = "",
        updated: Long = NOW - 3 * DAY,
    ) = SshHost(
        id = id, label = label, address = address, groupId = group, tags = tags,
        settings = HostSettings(port = port, username = user, keyId = "k-ed25519"),
        notes = notes, os = os, osVersion = osVersion, favorite = favorite, protocol = "ssh",
        syncMode = SyncMode.SYNCED, updatedAt = updated, accountId = account, vaultId = vault, access = ItemAccess.MANAGER,
    )

    val hosts: List<SshHost>
        get() = listOf(
            host(
                "h-web-1", "web-1", "10.0.1.11", "ubuntu", "24.04", tags = listOf("nginx", "prod"), favorite = true,
                notes = tr("Shop frontend behind the load balancer.", "Frontend de la tienda detrás del balanceador."),
            ),
            host("h-web-2", "web-2", "10.0.1.12", "ubuntu", "24.04", tags = listOf("nginx", "prod")),
            host("h-db-primary", "db-primary", "10.0.2.10", "debian", "12", tags = listOf("postgres", "prod"), user = "postgres", favorite = true),
            host("h-monitoring", "monitoring", "10.0.3.5", "alpine", "3.20", tags = listOf("grafana")),
            host("h-staging-web", "staging-web", "10.0.8.21", "rocky", "9.4", tags = listOf("staging")),
            host("h-backup", "backup", "10.0.4.20", "rocky", "9.4", tags = listOf("restic"), port = 2222u),
            host("h-api-1", "api-1", "10.0.1.21", "debian", "12", group = G_PROD, tags = listOf("api")),
            host("h-api-2", "api-2", "10.0.1.22", "debian", "12", group = G_PROD, tags = listOf("api")),
            host("h-worker-1", "worker-1", "10.0.1.31", "ubuntu", "24.04", group = G_PROD),
            host("h-lb-1", "lb-1", "10.0.0.2", "alpine", "3.20", group = G_PROD, tags = listOf("haproxy")),
            host("h-stg-api", "staging-api", "10.0.8.22", "rocky", "9.4", group = G_STAGING),
            host("h-stg-db", "staging-db", "10.0.8.23", "debian", "12", group = G_STAGING),
            host("h-homelab", "homelab", "10.0.10.2", "debian", "12", vault = V_PERSONAL, user = "ane"),
            host("h-vpn", "vpn", "10.0.10.1", "alpine", "3.20", vault = V_PERSONAL, user = "root"),
            host("h-ci", "ci-runner", "10.0.20.4", "ubuntu", "22.04", vault = V_WORK, account = A2),
            host("h-build", "build", "10.0.20.5", "rocky", "9.4", vault = V_WORK, account = A2),
        )

    val groups: List<HostGroup>
        get() = listOf(
            HostGroup(G_PROD, "Production", color = "#E5484D", syncMode = SyncMode.SYNCED, accountId = A1, vaultId = V_INFRA, access = ItemAccess.MANAGER),
            HostGroup(G_STAGING, "Staging", color = "#E5C07B", syncMode = SyncMode.SYNCED, accountId = A1, vaultId = V_INFRA, access = ItemAccess.MANAGER),
        )

    val keys = listOf(
        SshKey(
            "k-ed25519", "ane@laptop", "ssh-ed25519", "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIMv3x0Qk ane@laptop",
            "SHA256:Zt4yJ8o1q3bq0wJmP5kX9Vh2cQx7nL6sAe1rT0uYw2E", "ane@laptop", false, null, SyncMode.SYNCED, true,
            NOW - 30 * DAY, A1, V_INFRA, ItemAccess.MANAGER,
        ),
    )

    val snippets: List<Snippet>
        get() = listOf(
            Snippet("s-1", tr("Disk usage", "Uso de disco"), "df -h -x tmpfs", tags = listOf("ops"), syncMode = SyncMode.SYNCED, accountId = A1, vaultId = V_INFRA, access = ItemAccess.MANAGER),
            Snippet("s-2", tr("Reload nginx", "Recargar nginx"), "sudo nginx -t && sudo systemctl reload nginx", tags = listOf("web"), syncMode = SyncMode.SYNCED, accountId = A1, vaultId = V_INFRA, access = ItemAccess.MANAGER),
            Snippet("s-3", tr("Follow logs", "Seguir logs"), "journalctl -fu {{service}}", tags = listOf("ops"), syncMode = SyncMode.SYNCED, accountId = A1, vaultId = V_INFRA, access = ItemAccess.MANAGER),
        )

    /** Round trip of each host, in ms (the status dots and the terminal's badge). */
    val latency = mapOf(
        "web-1" to 18, "web-2" to 21, "db-primary" to 9, "monitoring" to 34, "staging-web" to 46, "backup" to 72,
        "api-1" to 17, "api-2" to 19, "worker-1" to 23, "lb-1" to 6, "staging-api" to 41, "staging-db" to 44,
        "homelab" to 12, "vpn" to 8, "ci-runner" to 28, "build" to 31,
    )

    // ----- AI -----

    val aiAccess = AiAccessInfo(listOf("anthropic"), true, 5.0, 1.42, 3.58, listOf(AiKeyProvider("anthropic", "Anthropic", "claude-sonnet-4-5", listOf("claude-sonnet-4-5"))))

    val aiProviders = AiProviders(
        "claude", emptyList(), AiPermissionMode.ASK,
        listOf(AiProvider("claude", "Claude", "anthropic", true, false, "claude-sonnet-4-5", listOf("claude-sonnet-4-5"), false, null, null, true, true)),
    )

    const val TASK_DISK = "0192f0c3-3333-7000-8000-00000000c001"

    private fun msg(role: String, vararg parts: JSONObject) = JSONObject().put("role", role).put("content", JSONArray(parts.toList()))
    private fun text(t: String) = JSONObject().put("type", "text").put("text", t)
    private fun reasoning(t: String) = JSONObject().put("type", "reasoning").put("text", t)
    private fun call(id: String, command: String) =
        JSONObject().put("type", "tool_call").put("id", id).put("name", "run_command").put("input", JSONObject().put("host", "backup").put("command", command))
    private fun result(id: String, out: String) = JSONObject().put("type", "tool_result").put("id", id).put("content", out)

    val diskCommand = "sudo journalctl --vacuum-time=14d && sudo dnf clean all"

    fun aiTasks(): List<AiTask> {
        val raw = JSONObject().put(
            "messages",
            JSONArray(
                listOf(
                    msg("user", text(tr("backup is almost out of disk space. Find out why and free some up safely.", "backup se está quedando sin disco. Averigua por qué y libera espacio de forma segura."))),
                    msg(
                        "assistant",
                        reasoning(tr("Check which filesystem is full first, then the biggest directories.", "Primero miro qué sistema de ficheros está lleno y luego los directorios más grandes.")),
                        call("c1", "df -h /"),
                    ),
                    msg("tool", result("c1", "Filesystem      Size  Used Avail Use% Mounted on\n/dev/sda2        80G   74G  6.1G  93% /")),
                    msg("assistant", call("c2", "sudo du -xh --max-depth=2 /var | sort -h | tail -4")),
                    msg("tool", result("c2", "1.2G\t/var/cache/dnf\n5.8G\t/var/log/journal\n41G\t/var/lib/restic\n49G\t/var")),
                    msg(
                        "assistant",
                        text(
                            tr(
                                "The disk is 93% full. The restic repository (41 GB) is your backups, so I'll leave it alone. The systemd journal keeps 5.8 GB of logs and the dnf cache 1.2 GB: trimming the journal to 14 days and cleaning the cache frees about 6 GB.",
                                "El disco está al 93 %. El repositorio de restic (41 GB) son tus copias, así que no lo toco. El journal de systemd guarda 5,8 GB de logs y la caché de dnf 1,2 GB: recortar el journal a 14 días y limpiar la caché libera unos 6 GB.",
                            ),
                        ),
                    ),
                ),
            ),
        ).toString()
        val approval = AiApproval(
            "ap-1", TASK_DISK, "run_command", JSONObject().put("host", "backup").put("command", diskCommand).toString(),
            tr("Trim the journal to 14 days and clean the dnf cache", "Recortar el journal a 14 días y limpiar la caché de dnf"),
            "pending", null, NOW - 1 * MIN, null,
            AiApprovalPreview(
                "command", diskCommand, "backup", AiRiskLevel.MEDIUM,
                listOf(AiRiskReason("sudo", "Runs as root"), AiRiskReason("delete", "Deletes files"), AiRiskReason("chain", "Runs several commands")),
                tr("Frees about 6 GB; keeps the last 14 days of logs and every backup.", "Libera unos 6 GB; conserva los logs de los últimos 14 días y todas las copias."),
                null, null, null, null, false, false, null, null, true,
            ),
        )
        return listOf(
            AiTask(
                TASK_DISK, tr("Free space on backup", "Liberar disco en backup"),
                tr("backup is almost out of disk space. Find out why and free some up safely.", "backup se está quedando sin disco. Averigua por qué y libera espacio de forma segura."),
                AiTaskStatus.WAITING_APPROVAL, AiPermissionMode.ASK, "claude", "claude", listOf("h-backup"),
                NOW - 4 * MIN, NOW - 1 * MIN, null, null, null, 31_000, listOf(approval), raw,
            ),
            AiTask(
                "0192f0c3-3333-7000-8000-00000000c002", tr("Why does nginx return 502 on /api?", "¿Por qué nginx devuelve 502 en /api?"),
                "", AiTaskStatus.COMPLETED, AiPermissionMode.ASK, "claude", "claude", listOf("h-web-1"),
                NOW - 3 * HOUR, NOW - 3 * HOUR + 2 * MIN, NOW - 3 * HOUR + 2 * MIN,
                tr("The upstream on 10.0.1.21:8080 was down; restarted shop-api.", "El upstream en 10.0.1.21:8080 estaba caído; reiniciado shop-api."),
                null, 54_000, emptyList(), "{}",
            ),
            AiTask(
                "0192f0c3-3333-7000-8000-00000000c003", tr("Check certificates that expire this month", "Revisar certificados que caducan este mes"),
                "", AiTaskStatus.COMPLETED, AiPermissionMode.READ_ONLY, "claude", "claude", listOf("h-web-1", "h-web-2", "h-lb-1"),
                NOW - DAY, NOW - DAY + 3 * MIN, NOW - DAY + 3 * MIN,
                tr("All certificates are valid for 60+ days.", "Todos los certificados son válidos durante más de 60 días."),
                null, 22_000, emptyList(), "{}",
            ),
        )
    }

    // ----- Files -----

    /** How far each demo download is. */
    fun progress(name: String): Double = if (name.endsWith(".tar.gz")) 0.62 else 0.35

    const val FILES_HOME = "/home/ane"

    fun dir(path: String) = RemoteFile(path.substringAfterLast('/'), path, RemoteFileKind.DIR, 4096u, 0x41EDu, "drwxr-xr-x", NOW / 1000 - 86_400, "ane", "ane")

    private fun file(dir: String, name: String, size: Long, ageMin: Long, mode: String = "-rw-r--r--", kind: RemoteFileKind = RemoteFileKind.FILE) =
        RemoteFile(name, "$dir/$name", kind, size.toULong(), if (kind == RemoteFileKind.DIR) 0x41EDu else 0x81A4u, mode, (NOW - ageMin * MIN) / 1000, "ane", "ane")

    fun files(path: String): List<RemoteFile> {
        val p = path.trimEnd('/').ifEmpty { "/" }
        return if (p == "/srv/shop" || p == FILES_HOME) {
            listOf(
                file(p, "config", 4096, 3 * 24 * 60, "drwxr-xr-x", RemoteFileKind.DIR),
                file(p, "deploy", 4096, 26 * 60, "drwxr-xr-x", RemoteFileKind.DIR),
                file(p, "logs", 4096, 12, "drwxr-xr-x", RemoteFileKind.DIR),
                file(p, "public", 4096, 5 * 24 * 60, "drwxr-xr-x", RemoteFileKind.DIR),
                file(p, ".env", 612, 9 * 24 * 60, "-rw-------"),
                file(p, "docker-compose.yml", 1_874, 2 * 24 * 60),
                file(p, "nginx.conf", 3_412, 26 * 60),
                file(p, "backup-2026-10-11.tar.gz", 734_003_200, 13 * 60),
                file(p, "release-notes.md", 8_960, 4 * 60),
                file(p, "deploy.sh", 2_315, 26 * 60, "-rwxr-xr-x"),
                file(p, "error.log", 18_874_368, 3),
                file(p, "screenshot-checkout.png", 482_133, 50),
            )
        } else {
            emptyList()
        }
    }
}
