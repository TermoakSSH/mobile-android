package com.termoak.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.termoak.app.R
import com.termoak.app.TermoakApp

/**
 * Over the whole app while it is locked (the iOS app's lock screen): the
 * logo, why, and Unlock, which asks for the fingerprint, face or screen
 * lock (it asks by itself when it appears). The terminals keep running.
 */
@Composable
fun AppLockScreen(app: TermoakApp) {
    val locked by app.appLock.locked.collectAsState()
    if (!locked) return
    val resources = LocalResources.current
    val auth = rememberDeviceAuth()
    var error by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    fun unlock() {
        if (checking) return
        checking = true
        error = null
        auth.unlock(resources.getString(R.string.lock_reason_open)) { ok ->
            checking = false
            if (ok) app.appLock.unlocked() else error = resources.getString(R.string.lock_canceled)
        }
    }
    LaunchedEffect(Unit) { unlock() }
    // Nothing underneath gets the touches or Back.
    BackHandler { }
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .clickable(remember { MutableInteractionSource() }, indication = null) { },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = 420.dp).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Image(painterResource(R.drawable.logo), null, Modifier.size(72.dp).clip(RoundedCornerShape(16.dp)))
            Text(stringResource(R.string.lock_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.lock_detail), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center) }
            Button(onClick = { unlock() }, enabled = !checking) {
                Icon(Icons.Outlined.LockOpen, null, Modifier.size(18.dp))
                Text(stringResource(R.string.lock_unlock), Modifier.padding(start = 8.dp))
            }
        }
    }
}
