package com.termoak.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Pin
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.termoak.app.BuildConfig
import com.termoak.app.R
import com.termoak.app.TermoakApp
import com.termoak.ffi.TermoakException
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(app: TermoakApp, welcome: Boolean, onDone: () -> Unit, onBack: (() -> Unit)?) {
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var server by remember { mutableStateOf(app.prefs.lastServer ?: BuildConfig.DEFAULT_SERVER) }
    var email by remember { mutableStateOf(app.prefs.lastEmail ?: "") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var needsCode by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        if (busy || server.isBlank() || email.isBlank() || password.isEmpty()) return
        busy = true
        error = null
        scope.launch {
            try {
                val signedIn = app.account.login(server.trim().trimEnd('/'), email.trim(), password, code.trim().ifEmpty { null })
                app.prefs.lastServer = server.trim().trimEnd('/')
                app.prefs.lastEmail = email.trim()
                // Otherwise the email must be verified first: AppRoot opens the code screen.
                if (signedIn) onDone()
            } catch (e: TermoakException.TotpRequired) {
                needsCode = true
                error = null
            } catch (e: TermoakException.TotpInvalid) {
                error = resources.getString(R.string.login_code_invalid)
            } catch (e: TermoakException) {
                error = e.message
            } finally {
                busy = false
            }
        }
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.Start)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back))
            }
        } else {
            Spacer(Modifier.height(48.dp))
        }
        Image(
            painterResource(R.drawable.logo),
            null,
            Modifier.size(88.dp).clip(RoundedCornerShape(22.dp)),
        )
        Text(stringResource(R.string.app_name), Modifier.padding(top = 20.dp), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(if (needsCode) R.string.login_code_prompt else R.string.login_tagline),
            Modifier.padding(top = 8.dp, bottom = 28.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!needsCode) {
                OutlinedTextField(
                    server, { server = it }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.login_server)) }, singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Dns, null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                )
                OutlinedTextField(
                    email, { email = it }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.login_email)) }, singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Email, null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                )
                OutlinedTextField(
                    password, { password = it }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.common_password)) }, singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Lock, null) },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                stringResource(R.string.common_show),
                            )
                        }
                    },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { submit() }),
                )
            } else {
                OutlinedTextField(
                    code, { code = it.filter { c -> c.isLetterOrDigit() || c == '-' } }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.login_code)) }, singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Pin, null) },
                    supportingText = { Text(stringResource(R.string.login_code_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { submit() }),
                )
            }
            error?.let {
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
                    Text(
                        it, Modifier.fillMaxWidth().padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Button(
                onClick = { submit() },
                enabled = !busy && server.isNotBlank() && email.isNotBlank() && password.isNotEmpty() && (!needsCode || code.isNotBlank()),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text(stringResource(if (needsCode) R.string.login_verify else R.string.common_sign_in))
            }
            if (needsCode) {
                TextButton(onClick = { needsCode = false; code = "" }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.common_back))
                }
            }
        }
        Spacer(Modifier.weight(1f))
        if (welcome) {
            TextButton(
                onClick = { app.prefs.skippedLogin = true; onDone() },
                modifier = Modifier.padding(vertical = 16.dp),
            ) { Text(stringResource(R.string.login_skip)) }
        }
        Text(
            stringResource(R.string.login_no_account),
            Modifier.padding(bottom = 24.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
