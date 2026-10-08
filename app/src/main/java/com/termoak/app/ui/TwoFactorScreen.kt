package com.termoak.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.navigation.NavHostController
import com.termoak.app.R
import com.termoak.app.TermoakApp
import com.termoak.app.userMessage
import com.termoak.ffi.TermoakException
import com.termoak.ffi.TwoFactorSetup
import com.termoak.ffi.TwoFactorStatus
import kotlinx.coroutines.launch

/**
 * Two-step verification in the app, as on iOS: its state and recovery codes
 * left; turn it on (QR code or secret for the authenticator app, a code to
 * confirm, then the recovery codes shown once) or off (password and a
 * code). The engine does it for the current account.
 */
@Composable
fun TwoFactorScreen(app: TermoakApp, nav: NavHostController) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val current by app.accounts.current.collectAsState()
    val accountList by app.accounts.list.collectAsState()
    var status by remember { mutableStateOf<TwoFactorStatus?>(null) }
    var setup by remember { mutableStateOf<TwoFactorSetup?>(null) }
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var recovery by remember { mutableStateOf<List<String>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var copied by remember { mutableStateOf(false) }
    var disabling by remember { mutableStateOf(false) }

    suspend fun load() {
        status = runCatching { app.core.twoFactorStatus() }.onFailure { error = it.userMessage(resources, R.string.error_save_failed) }.getOrNull()
    }
    LaunchedEffect(current?.id) { load() }
    fun run(action: suspend () -> Unit) {
        busy = true
        error = null
        scope.launch {
            try {
                action()
            } catch (_: TermoakException.TotpInvalid) {
                error = resources.getString(R.string.login_code_invalid)
            } catch (e: TermoakException) {
                error = e.userMessage(resources, R.string.error_save_failed)
            } finally {
                busy = false
            }
        }
    }
    fun copy(text: String) = context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("2fa", text))

    ScreenScaffold(
        title = stringResource(R.string.two_factor_title),
        navigationIcon = {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val st = status
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_two_factor), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                when {
                    st == null -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    st.enabled -> Pill(stringResource(R.string.settings_two_factor_active), Brand.Green)
                    else -> Pill(stringResource(R.string.settings_two_factor_inactive), Brand.Amber)
                }
            }
            if (st?.enabled == true) {
                val left = st.recoveryCodesLeft.toInt()
                Text(pluralStringResource(R.plurals.settings_two_factor_on, left, left), style = MaterialTheme.typography.bodySmall)
            }
            Text(stringResource(R.string.two_factor_explain), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            current?.let { a ->
                if (accountList.size > 1) {
                    Text(stringResource(R.string.two_factor_current_only, a.email), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val s = setup
            when {
                recovery.isNotEmpty() -> {
                    SectionLabel(stringResource(R.string.two_factor_recovery_title))
                    SelectionContainer {
                        Column { recovery.forEach { Text(it, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyLarge) } }
                    }
                    Text(stringResource(R.string.two_factor_recovery_footer), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { copySecret(context, "2fa", recovery.joinToString("\n")); copied = true }) {
                            Text(stringResource(if (copied) R.string.two_factor_copied else R.string.two_factor_copy_codes))
                        }
                        OutlinedButton(onClick = {
                            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, recovery.joinToString("\n"))
                            runCatching { context.startActivity(Intent.createChooser(send, null)) }
                        }) { Text(stringResource(R.string.two_factor_share_codes)) }
                    }
                    Button(onClick = { recovery = emptyList(); copied = false }) { Text(stringResource(R.string.common_done)) }
                }
                s != null -> {
                    SectionLabel(stringResource(R.string.two_factor_step_scan))
                    QrCodeImage(s.otpauthUrl, stringResource(R.string.two_factor_qr), Modifier.width(220.dp).align(Alignment.CenterHorizontally))
                    TextButton(onClick = {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, s.otpauthUrl.toUri())) }
                    }) {
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.size(18.dp))
                        Text(stringResource(R.string.two_factor_open_app), Modifier.padding(start = 8.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SelectionContainer(Modifier.weight(1f)) { Text(s.secret, fontFamily = FontFamily.Monospace) }
                        IconButton(onClick = { copy(s.secret) }) { Icon(Icons.Outlined.ContentCopy, stringResource(R.string.two_factor_copy_secret)) }
                    }
                    Text(stringResource(R.string.two_factor_scan_footer), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SectionLabel(stringResource(R.string.two_factor_step_code))
                    OutlinedTextField(
                        code, { code = it.filter { c -> c.isDigit() }.take(6) }, Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text(stringResource(R.string.login_code)) },
                        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            run {
                                recovery = app.core.enableTwoFactor(code.trim())
                                setup = null
                                code = ""
                                load()
                            }
                        }, enabled = !busy && code.length == 6) { Text(stringResource(R.string.two_factor_confirm)) }
                        TextButton(onClick = { setup = null; code = "" }) { Text(stringResource(R.string.common_cancel)) }
                    }
                }
                st?.enabled == true -> {
                    SectionLabel(stringResource(R.string.two_factor_turn_off))
                    OutlinedTextField(
                        password, { password = it }, Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text(stringResource(R.string.common_password)) }, visualTransformation = PasswordVisualTransformation(),
                    )
                    OutlinedTextField(
                        code, { code = it.filter { c -> c.isLetterOrDigit() || c == '-' } }, Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text(stringResource(R.string.login_code)) }, keyboardOptions = TwoFactorKeyboard,
                    )
                    Text(stringResource(R.string.two_factor_turn_off_footer), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = { disabling = true }, enabled = !busy && password.isNotEmpty() && code.isNotBlank()) {
                        Text(stringResource(R.string.two_factor_turn_off), color = MaterialTheme.colorScheme.error)
                    }
                }
                st != null -> Button(onClick = {
                    run {
                        setup = app.core.setupTwoFactor()
                        code = ""
                    }
                }, enabled = !busy) { Text(stringResource(R.string.two_factor_turn_on)) }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            current?.serverUrl?.let { server ->
                TextButton(onClick = { openUrl(context, "$server/app/account") }) {
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.size(18.dp))
                    Text(stringResource(R.string.settings_my_account), Modifier.padding(start = 8.dp))
                }
            }
        }
    }
    if (disabling) {
        ConfirmDialog(
            stringResource(R.string.two_factor_turn_off), stringResource(R.string.two_factor_turn_off_confirm),
            stringResource(R.string.two_factor_turn_off), destructive = true, onDismiss = { disabling = false },
        ) {
            run {
                app.core.disableTwoFactor(password, code.trim())
                password = ""
                code = ""
                load()
            }
        }
    }
}
