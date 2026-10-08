package com.termoak.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.termoak.app.R
import com.termoak.app.TermoakApp
import com.termoak.app.data.HostDot
import com.termoak.app.data.HostStatusPlan
import com.termoak.app.data.HostStatusStore
import com.termoak.app.data.uid
import com.termoak.ffi.SshHost
import kotlinx.coroutines.delay

/**
 * The status dot of a host next to its name: green (with the time a
 * connection took when [latency]) or red; nothing when the setting is off
 * or the host isn't checked.
 */
@Composable
internal fun HostStatusDot(host: SshHost, modifier: Modifier = Modifier, latency: Boolean = false) {
    if (!HostStatusStore.enabled) return
    when (val d = HostStatusStore.dot(host)) {
        is HostDot.Up -> {
            val text = HostStatusPlan.latency(d.ms)
            val description = stringResource(R.string.host_status_up, text)
            Row(modifier.clearAndSetSemantics { contentDescription = description }, verticalAlignment = Alignment.CenterVertically) {
                Dot(Brand.Green)
                if (latency) {
                    Text(text, Modifier.padding(start = 3.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        HostDot.Down -> {
            val description = stringResource(R.string.host_status_down)
            Dot(Brand.Red, modifier.clearAndSetSemantics { contentDescription = description })
        }
        HostDot.Unknown -> Unit
    }
}

@Composable
private fun Dot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(8.dp).clip(CircleShape).background(color))
}

/**
 * Checks the status of [hosts] while they are on screen and the app is in
 * the foreground, every host at most once a minute; nothing at all while
 * the setting is off.
 */
@Composable
internal fun HostStatusChecks(app: TermoakApp, hosts: List<SshHost>) {
    val on by app.prefs.hostStatusChecks.collectAsState()
    val off by app.prefs.hostStatusOff.collectAsState()
    LaunchedEffect(on) { HostStatusStore.enabled = on }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val key = if (!on) "" else hosts.joinToString(",") { it.uid + "@" + HostStatusStore.target(it) } + "|" + off.joinToString(",")
    LaunchedEffect(key) {
        if (key.isEmpty()) return@LaunchedEffect
        val list = hosts
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                HostStatusStore.refresh(list, app.core, off)
                delay(HostStatusPlan.TICK_MS)
            }
        }
    }
}
