package com.termoak.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import com.termoak.app.R
import com.termoak.app.data.HostLogos
import com.termoak.ffi.SshHost

/** Short notices ("Copied", errors...) from any screen. */
val LocalSnackbar = staticCompositionLocalOf { SnackbarHostState() }

/** Label and color for a host's detected OS (as on the desktop). */
fun osBadge(os: String?): Pair<String, Color>? = when (os?.trim()?.lowercase()) {
    null, "" -> null
    "ubuntu" -> "Ubuntu" to Color(0xFFE95420)
    "debian", "devuan" -> "Debian" to Color(0xFFD70A53)
    "raspbian" -> "Raspbian" to Color(0xFFC51A4A)
    "linuxmint", "mint" -> "Mint" to Color(0xFF87CF3E)
    "pop" -> "Pop!_OS" to Color(0xFF48B9C7)
    "elementary" -> "elementary" to Color(0xFF64BAFF)
    "zorin" -> "Zorin" to Color(0xFF15A6F0)
    "kali" -> "Kali" to Color(0xFF557C94)
    "fedora", "nobara" -> "Fedora" to Color(0xFF51A2DA)
    "centos" -> "CentOS" to Color(0xFF932279)
    "rocky" -> "Rocky" to Color(0xFF10B981)
    "almalinux" -> "Alma" to Color(0xFF0F4266)
    "rhel" -> "RHEL" to Color(0xFFEE0000)
    "arch", "artix", "garuda" -> "Arch" to Color(0xFF1793D1)
    "manjaro" -> "Manjaro" to Color(0xFF35BF5C)
    "endeavouros" -> "EndeavourOS" to Color(0xFF7F3FBF)
    "alpine", "postmarketos" -> "Alpine" to Color(0xFF0D597F)
    "opensuse", "opensuse-leap", "opensuse-tumbleweed", "sles", "sled" -> "SUSE" to Color(0xFF73BA25)
    "gentoo" -> "Gentoo" to Color(0xFF54487A)
    "nixos" -> "NixOS" to Color(0xFF5277C3)
    "void" -> "Void" to Color(0xFF478061)
    "freebsd" -> "FreeBSD" to Color(0xFFAB2B28)
    "macos", "darwin" -> "macOS" to Color(0xFF8E8E93)
    "windows" -> "Windows" to Color(0xFF0078D4)
    "linux" -> "Linux" to Color(0xFF4A5468)
    else -> os.replaceFirstChar { it.uppercase() } to Color(0xFF6B7A99)
}

/** Tile colors for hosts without a color or a detected system (picked by name). */
private val TilePalette = listOf(
    Color(0xFF4F7CFF), Color(0xFF3FB27F), Color(0xFFE8A33D), Color(0xFF9B6BFF),
    Color(0xFFE5534B), Color(0xFF2BA6B5), Color(0xFFD9640F), Color(0xFF6B7A99),
)

/**
 * Rounded square of a host, like Termius': its logo ([icon], the one chosen
 * in the editor, else the one of its detected system [os], see HostLogos)
 * on the host color or the logo's, or the initial of its name. With
 * [twoInitials], the desktop's avatar without a logo: the initials of the
 * name ("TS").
 */
@Composable
fun HostTile(
    label: String,
    os: String?,
    color: String? = null,
    size: Dp = 42.dp,
    twoInitials: Boolean = false,
    icon: String? = null,
) {
    val logo = HostLogos.resolve(icon, os)
    val vector = logo?.let { logoVector(it) }
    val badge = osBadge(os)
    val bg = color?.let { runCatching { Color(it.toColorInt()) }.getOrNull() }
        ?: logo?.let { Color(0xFF000000 or it.color) }
        ?: badge?.second?.takeIf { !twoInitials }
        ?: TilePalette[Math.floorMod(label.lowercase().hashCode(), TilePalette.size)]
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size / 4.5f)).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        if (vector != null) {
            Icon(vector, logo.takeIf { it.kind == HostLogos.Kind.SYSTEM }?.name ?: badge?.first, Modifier.size(size * 0.56f), tint = Color.White)
        } else {
            // With a detected system without a logo, its initial; otherwise the name's.
            val initial = if (twoInitials) {
                initials(label)
            } else {
                (badge?.first ?: label).trim().firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?"
            }
            Text(initial, color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * (if (initial.length > 1) 0.36f else 0.42f)).sp)
        }
    }
}

/** [HostTile] of a saved host (its logo, system and color). */
@Composable
fun HostTile(host: SshHost, size: Dp = 42.dp, twoInitials: Boolean = false) =
    HostTile(host.label, host.os, host.color, size, twoInitials, host.icon)

/** Name of a logo for the picker: the system's, or the generic one's in the app language. */
@Composable
fun logoName(logo: HostLogos.Logo): String = when (logo.kind) {
    HostLogos.Kind.SYSTEM -> logo.name
    HostLogos.Kind.GENERIC -> genericLogoName(logo.id)?.let { stringResource(it) } ?: logo.id
}

@androidx.annotation.StringRes
private fun genericLogoName(id: String): Int? = when (id) {
    "server" -> R.string.logo_server
    "database" -> R.string.logo_database
    "router" -> R.string.logo_router
    "firewall" -> R.string.logo_firewall
    "cloud" -> R.string.logo_cloud
    "container" -> R.string.logo_container
    "kubernetes" -> R.string.logo_kubernetes
    "web" -> R.string.logo_web
    "mail" -> R.string.logo_mail
    "storage" -> R.string.logo_storage
    "terminal" -> R.string.logo_terminal
    "iot" -> R.string.logo_iot
    "security" -> R.string.logo_security
    else -> null
}

/** The "Telnet" badge of a host (rows, cards, sheets): it is unencrypted. */
@Composable
fun TelnetBadge(modifier: Modifier = Modifier) {
    Text(
        "Telnet",
        modifier.clip(RoundedCornerShape(4.dp)).background(Brand.Amber.copy(alpha = 0.18f)).padding(horizontal = 5.dp, vertical = 1.dp),
        style = MaterialTheme.typography.labelSmall, color = Brand.Amber, maxLines = 1,
    )
}

/** "TS" for "Test server", "W" for "web-1" (the desktop's avatars). */
fun initials(label: String): String {
    val words = label.trim().split(' ', '\t').filter { w -> w.any { it.isLetterOrDigit() } }
    val letters = words.take(2).mapNotNull { w -> w.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar() }
    return letters.joinToString("").ifEmpty { "?" }
}

@Composable
fun StatusDot(color: Color, size: Dp = 8.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

/** Small chip with a status text. */
@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.15f),
        contentColor = color,
        shape = RoundedCornerShape(50),
    ) {
        Text(text, Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(34.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Text(title, Modifier.padding(top = 20.dp), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            text,
            Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Button(onClick = onAction, modifier = Modifier.padding(top = 20.dp)) { Text(action) }
        }
    }
}

/** Title of a group of items in a list. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 18.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text.uppercase(LocalConfiguration.current.locales[0]),
            Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.8.sp,
        )
        trailing?.invoke()
    }
}

/** List card in the app style. */
@Composable
fun CardBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        content = content,
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirm: String,
    destructive: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(confirm, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

/** "5 min ago", "yesterday"... in the app language. [millis] may also be in seconds. */
@Composable
fun relativeTime(millis: Long?): String {
    if (millis == null || millis <= 0) return ""
    val ms = if (millis < 10_000_000_000L) millis * 1000 else millis
    val diff = (System.currentTimeMillis() - ms) / 1000
    return when {
        diff < 45 -> stringResource(R.string.time_now)
        diff < 3600 -> stringResource(R.string.time_minutes_ago, (diff / 60).toInt())
        diff < 86_400 -> stringResource(R.string.time_hours_ago, (diff / 3600).toInt())
        diff < 172_800 -> stringResource(R.string.time_yesterday)
        diff < 30 * 86_400 -> (diff / 86_400).toInt().let { pluralStringResource(R.plurals.time_days_ago, it, it) }
        else -> java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM, LocalConfiguration.current.locales[0])
            .format(java.util.Date(ms))
    }
}
