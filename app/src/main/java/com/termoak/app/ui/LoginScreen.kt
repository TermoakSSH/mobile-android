package com.termoak.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Pin
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.termoak.app.R
import com.termoak.app.TermoakApp
import com.termoak.app.data.officialServer
import com.termoak.app.data.serverHost
import com.termoak.app.userMessage
import com.termoak.ffi.AccountStatus
import com.termoak.ffi.ServerChoice
import com.termoak.ffi.TermoakException
import com.termoak.ffi.canonicalServerUrl
import com.termoak.ffi.serverInfo
import kotlinx.coroutines.launch
import org.json.JSONObject

/** What `GET /api/v1/info` says about a server, for the sign-in and sign-up forms. */
data class ServerDetails(
    val url: String,
    val official: Boolean,
    val name: String,
    val version: String,
    val registrationOpen: Boolean,
    /** `preprod` on a test server (show a banner), `null` in production. */
    val environment: String?,
    val termsUrl: String?,
    val privacyUrl: String?,
) {
    val insecure get() = url.startsWith("http://")

    companion object {
        fun parse(url: String, json: String): ServerDetails {
            val v = JSONObject(json)
            fun str(key: String) = v.optString(key).takeIf { it.isNotBlank() && !v.isNull(key) }
            return ServerDetails(
                url = url,
                official = url == officialServer,
                name = str("name") ?: serverHost(url),
                version = str("version").orEmpty(),
                registrationOpen = v.optString("registration") == "open",
                environment = str("environment"),
                termsUrl = str("terms_url"),
                privacyUrl = str("privacy_url"),
            )
        }
    }
}

/** Steps of "Add account". */
private enum class Step { CHOICE, CUSTOM, SIGN_IN, SIGN_UP }

/** What the sign-in screen opens with (an account that has to sign in again, Manage accounts...). */
object LoginMode {
    const val SIGN_IN = "signin"
    const val SIGN_UP = "signup"
    const val CUSTOM = "custom"
}

/**
 * Welcome and "Add account": **Sign in to Termoak** (the official server),
 * **Create a Termoak account**, or **Use your own server** (its address,
 * checked with `/info`: name, version, registration and test environment),
 * then the sign-in form (with the two-step code when asked) or the sign-up
 * form (name, email, password and the terms). Accounts that must verify
 * their email continue on the code screen (AppRoot opens it).
 */
@Composable
fun LoginScreen(
    app: TermoakApp,
    welcome: Boolean,
    onDone: () -> Unit,
    onBack: (() -> Unit)?,
    mode: String? = null,
    prefillServer: String? = null,
    prefillEmail: String? = null,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val first = when (mode) {
        LoginMode.SIGN_IN -> Step.SIGN_IN
        LoginMode.SIGN_UP -> Step.SIGN_UP
        LoginMode.CUSTOM -> Step.CUSTOM
        else -> Step.CHOICE
    }
    var step by rememberSaveable { mutableStateOf(first) }
    // The server of the forms: the official one unless another was chosen.
    var serverUrl by rememberSaveable { mutableStateOf(prefillServer ?: officialServer) }
    var details by remember { mutableStateOf<ServerDetails?>(null) }
    var customUrl by rememberSaveable { mutableStateOf(prefillServer?.takeIf { it != officialServer } ?: "") }
    var email by rememberSaveable { mutableStateOf(prefillEmail ?: app.prefs.lastEmail ?: "") }
    var name by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var needsCode by remember { mutableStateOf(false) }
    var acceptTerms by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val official = serverUrl == officialServer

    fun choice(): ServerChoice = if (official) ServerChoice.Official else ServerChoice.Custom(serverUrl)

    // The details of the server of the forms (terms, test environment...).
    LaunchedEffect(serverUrl) {
        if (details?.url == serverUrl) return@LaunchedEffect
        details = runCatching { ServerDetails.parse(serverUrl, serverInfo(serverUrl)) }.getOrNull()
    }

    fun back() {
        error = null
        when {
            needsCode -> { needsCode = false; code = "" }
            step == first -> onBack?.invoke()
            step == Step.SIGN_IN || step == Step.SIGN_UP -> step = if (official) Step.CHOICE else Step.CUSTOM
            else -> step = Step.CHOICE
        }
    }
    BackHandler(enabled = onBack != null || step != first || needsCode) { back() }

    /** Checks the address of your own server with `/info`. */
    fun checkServer() {
        if (busy || customUrl.isBlank()) return
        busy = true
        error = null
        scope.launch {
            try {
                val url = canonicalServerUrl(customUrl.trim())
                val info = try {
                    serverInfo(url)
                } catch (e: TermoakException) {
                    error = resources.getString(R.string.login_server_unreachable, serverHost(url))
                    return@launch
                }
                val d = ServerDetails.parse(url, info)
                details = d
                serverUrl = url
                customUrl = url
            } catch (e: TermoakException) {
                error = resources.getString(R.string.login_server_invalid)
            } finally {
                busy = false
            }
        }
    }

    fun finished(status: AccountStatus) {
        // An account that must verify its email: AppRoot opens the code screen.
        if (status != AccountStatus.UNVERIFIED) onDone()
    }

    fun signIn() {
        if (busy || email.isBlank() || password.isEmpty()) return
        busy = true
        error = null
        scope.launch {
            try {
                val info = app.accounts.signIn(choice(), email, password, code.trim().ifEmpty { null })
                finished(info.status)
            } catch (e: TermoakException.TotpRequired) {
                needsCode = true
            } catch (e: TermoakException.TotpInvalid) {
                error = resources.getString(R.string.login_code_invalid)
            } catch (e: TermoakException) {
                error = e.userMessage(resources, R.string.login_failed)
            } finally {
                busy = false
            }
        }
    }

    fun signUp() {
        val terms = details?.termsUrl != null
        if (busy || email.isBlank() || name.isBlank() || password.isEmpty() || (terms && !acceptTerms)) return
        busy = true
        error = null
        scope.launch {
            try {
                val info = app.accounts.signUp(choice(), email, name, password, null)
                finished(info.status)
            } catch (e: TermoakException) {
                error = e.userMessage(resources, R.string.signup_failed)
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
        if (onBack != null || step != first || needsCode) {
            IconButton(onClick = { back() }, modifier = Modifier.align(Alignment.Start)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back))
            }
        } else {
            Spacer(Modifier.height(48.dp))
        }
        Image(painterResource(R.drawable.logo), null, Modifier.size(80.dp).clip(RoundedCornerShape(20.dp)))
        Text(
            when (step) {
                Step.CHOICE -> stringResource(if (welcome) R.string.app_name else R.string.accounts_add)
                Step.CUSTOM -> stringResource(R.string.login_own_server)
                Step.SIGN_IN -> stringResource(R.string.common_sign_in)
                Step.SIGN_UP -> stringResource(R.string.signup_title)
            },
            Modifier.padding(top = 18.dp), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center,
        )
        Text(
            when {
                needsCode -> stringResource(R.string.login_code_prompt)
                step == Step.CHOICE -> stringResource(R.string.login_tagline)
                step == Step.CUSTOM -> stringResource(R.string.login_own_server_text)
                official -> stringResource(R.string.login_on_official, serverHost(serverUrl))
                else -> stringResource(R.string.login_on_server, details?.name ?: serverHost(serverUrl), serverHost(serverUrl))
            },
            Modifier.padding(top = 8.dp, bottom = 24.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (step) {
                Step.CHOICE -> {
                    Button(
                        onClick = { serverUrl = officialServer; step = Step.SIGN_IN },
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.login_official), fontWeight = FontWeight.SemiBold)
                            Text(serverHost(officialServer), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    OutlinedButton(
                        onClick = { serverUrl = officialServer; step = Step.SIGN_UP },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text(stringResource(R.string.login_create_official)) }
                    TextButton(onClick = { step = Step.CUSTOM }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Dns, null, Modifier.size(18.dp))
                        Text(stringResource(R.string.login_own_server), Modifier.padding(start = 8.dp))
                    }
                }
                Step.CUSTOM -> {
                    OutlinedTextField(
                        customUrl, { customUrl = it; details = null; error = null }, Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.login_server)) }, singleLine = true,
                        placeholder = { Text("ssh.example.com") },
                        leadingIcon = { Icon(Icons.Outlined.Dns, null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
                        keyboardActions = KeyboardActions(onGo = { checkServer() }),
                    )
                    val d = details?.takeIf { it.url == serverUrl && it.url == customUrl }
                    if (d == null) {
                        error?.let { MessageBox(it, isError = true) }
                        Button(
                            onClick = { checkServer() }, enabled = !busy && customUrl.isNotBlank(),
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) {
                            if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            else Text(stringResource(R.string.common_continue))
                        }
                    } else {
                        ServerCard(d)
                        Button(onClick = { step = Step.SIGN_IN }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                            Text(stringResource(R.string.common_sign_in))
                        }
                        if (d.registrationOpen) {
                            OutlinedButton(onClick = { step = Step.SIGN_UP }, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.signup_create))
                            }
                        } else {
                            Text(
                                stringResource(R.string.login_registration_closed), Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
                Step.SIGN_IN -> {
                    details?.environment?.let { EnvironmentBanner(it) }
                    if (!needsCode) {
                        EmailField(email) { email = it }
                        PasswordField(password, showPassword, { password = it }, { showPassword = !showPassword }, ImeAction.Go) { signIn() }
                    } else {
                        OutlinedTextField(
                            code, { code = it.filter { c -> c.isLetterOrDigit() || c == '-' } }, Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.login_code)) }, singleLine = true,
                            leadingIcon = { Icon(Icons.Outlined.Pin, null) },
                            supportingText = { Text(stringResource(R.string.login_code_hint)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = { signIn() }),
                        )
                    }
                    error?.let { MessageBox(it, isError = true) }
                    Button(
                        onClick = { signIn() },
                        enabled = !busy && email.isNotBlank() && password.isNotEmpty() && (!needsCode || code.isNotBlank()),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Text(stringResource(if (needsCode) R.string.login_verify else R.string.common_sign_in))
                    }
                    if (!needsCode) {
                        TextButton(onClick = { openUrl(context, "$serverUrl/forgot-password") }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.login_forgot_password))
                        }
                        if (official || details?.registrationOpen == true) {
                            TextButton(onClick = { error = null; step = Step.SIGN_UP }, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.login_no_account_create))
                            }
                        }
                    }
                }
                Step.SIGN_UP -> {
                    details?.environment?.let { EnvironmentBanner(it) }
                    OutlinedTextField(
                        name, { name = it }, Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.common_name)) }, singleLine = true,
                        leadingIcon = { Icon(Icons.Outlined.Badge, null) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    )
                    EmailField(email) { email = it }
                    PasswordField(password, showPassword, { password = it }, { showPassword = !showPassword }, ImeAction.Done) { signUp() }
                    val terms = details?.termsUrl
                    if (terms != null) {
                        Row(
                            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { acceptTerms = !acceptTerms },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(acceptTerms, { acceptTerms = it })
                            Text(stringResource(R.string.signup_accept_terms), style = MaterialTheme.typography.bodyMedium)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            TextButton(onClick = { openUrl(context, terms) }) { Text(stringResource(R.string.signup_terms)) }
                            details?.privacyUrl?.let { p ->
                                TextButton(onClick = { openUrl(context, p) }) { Text(stringResource(R.string.signup_privacy)) }
                            }
                        }
                    }
                    error?.let { MessageBox(it, isError = true) }
                    Button(
                        onClick = { signUp() },
                        enabled = !busy && name.isNotBlank() && email.isNotBlank() && password.isNotEmpty() && (terms == null || acceptTerms),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Text(stringResource(R.string.signup_create))
                    }
                    Text(
                        stringResource(R.string.signup_code_hint), Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    TextButton(onClick = { error = null; step = Step.SIGN_IN }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.signup_have_account))
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        if (welcome && step == Step.CHOICE) {
            TextButton(
                onClick = { app.prefs.skippedLogin = true; onDone() },
                modifier = Modifier.padding(vertical = 16.dp),
            ) { Text(stringResource(R.string.login_skip)) }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun EmailField(email: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        email, onChange, Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.login_email)) }, singleLine = true,
        leadingIcon = { Icon(Icons.Outlined.Email, null) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next, autoCorrectEnabled = false),
    )
}

@Composable
private fun PasswordField(
    password: String,
    show: Boolean,
    onChange: (String) -> Unit,
    onToggle: () -> Unit,
    imeAction: ImeAction,
    onIme: () -> Unit,
) {
    OutlinedTextField(
        password, onChange, Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.common_password)) }, singleLine = true,
        leadingIcon = { Icon(Icons.Outlined.Lock, null) },
        trailingIcon = {
            IconButton(onClick = onToggle) {
                Icon(if (show) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, stringResource(R.string.common_show))
            }
        },
        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = KeyboardActions(onGo = { onIme() }, onDone = { onIme() }),
    )
}

/** The server checked with `/info`: name, address, version, registration and test environment. */
@Composable
private fun ServerCard(d: ServerDetails) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Dns, null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(d.name, style = MaterialTheme.typography.titleMedium)
                    Text(serverHost(d.url), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (d.official) Pill(stringResource(R.string.login_official_pill), Brand.Green)
            }
            Text(
                listOfNotNull(
                    d.version.takeIf { it.isNotBlank() }?.let { stringResource(R.string.login_server_version, it) },
                    stringResource(if (d.registrationOpen) R.string.login_registration_open else R.string.login_registration_closed_short),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            d.environment?.let { EnvironmentBanner(it) }
            if (d.insecure) MessageBox(stringResource(R.string.login_insecure), isError = true)
        }
    }
}

/** "Test server (preprod)": the server is not a production one. */
@Composable
fun EnvironmentBanner(environment: String) {
    Surface(color = Brand.Amber.copy(alpha = 0.16f), contentColor = Brand.Amber, shape = MaterialTheme.shapes.small) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.WarningAmber, null, Modifier.size(18.dp))
            Text(stringResource(R.string.login_environment, environment), Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun MessageBox(text: String, isError: Boolean) {
    Surface(
        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Text(
            text, Modifier.fillMaxWidth().padding(12.dp),
            color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** Opens a web page in the browser. */
fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // No browser.
    }
}
