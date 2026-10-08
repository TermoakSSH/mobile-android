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
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material.icons.outlined.Pin
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termoak.app.R
import com.termoak.app.TermoakApp
import com.termoak.app.data.RESEND_INTERVAL_MS
import com.termoak.app.userMessage
import com.termoak.ffi.TermoakException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Length of the code in the verification email. */
private const val CODE_LENGTH = 6

/**
 * "Check your email": the account is signed in on a server that requires a
 * verified email, and the six-digit code from the email finishes the
 * sign-in. [onDone] runs once verified; [onDifferentEmail] after signing out
 * to use another account.
 */
@Composable
fun VerifyEmailScreen(app: TermoakApp, onDone: () -> Unit, onDifferentEmail: () -> Unit) {
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val pending by app.accounts.verification.collectAsState()
    var code by rememberSaveable { mutableStateOf("") }
    var totp by rememberSaveable { mutableStateOf("") }
    var needsTotp by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var resending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    // The "resend" button waits a minute after each email.
    var resendAt by rememberSaveable { mutableLongStateOf(pending?.sentAt?.plus(RESEND_INTERVAL_MS) ?: 0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(resendAt) {
        now = System.currentTimeMillis()
        while (now < resendAt) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }
    val waitSeconds = ((resendAt - now + 999) / 1_000).coerceAtLeast(0)

    // Kept while leaving the screen (verified or signed out), when it is gone from the account.
    var lastEmail by rememberSaveable { mutableStateOf(pending?.email) }
    pending?.email?.let { lastEmail = it }
    val email = lastEmail
    if (email == null) {
        // Nothing to verify (e.g. restored after the app was killed).
        LaunchedEffect(Unit) { onDone() }
        return
    }

    fun verify() {
        if (busy || code.length != CODE_LENGTH || (needsTotp && totp.isBlank())) return
        busy = true
        error = null
        notice = null
        scope.launch {
            try {
                app.accounts.verifyCode(code, totp.ifBlank { null })
                onDone()
            } catch (e: TermoakException.TotpRequired) {
                needsTotp = true
            } catch (e: TermoakException.TotpInvalid) {
                error = resources.getString(R.string.login_code_invalid)
            } catch (e: TermoakException.Invalid) {
                error = resources.getString(R.string.verify_code_invalid)
            } catch (e: TermoakException) {
                error = e.userMessage(resources, R.string.verify_code_invalid)
            } finally {
                busy = false
            }
        }
    }

    fun resend() {
        if (resending || waitSeconds > 0) return
        resending = true
        error = null
        notice = null
        scope.launch {
            try {
                app.accounts.resendCode()
                code = ""
                notice = resources.getString(R.string.verify_resent, email)
            } catch (e: TermoakException) {
                // Asked too often (HTTP 429) or no connection.
                error = e.message
            } finally {
                // Also after a "too many attempts": wait before trying again.
                resendAt = System.currentTimeMillis() + RESEND_INTERVAL_MS
                resending = false
            }
        }
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Image(
            painterResource(R.drawable.logo),
            null,
            Modifier.size(88.dp).clip(RoundedCornerShape(22.dp)),
        )
        Text(
            stringResource(R.string.verify_title),
            Modifier.padding(top = 20.dp),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            if (needsTotp) stringResource(R.string.login_code_prompt) else stringResource(R.string.verify_prompt, email),
            Modifier.padding(top = 8.dp, bottom = 28.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                code,
                { value ->
                    // Pasted codes often come as "123 456" or "123-456": keep the digits.
                    code = value.filter { it.isDigit() }.take(CODE_LENGTH)
                    error = null
                    if (code.length == CODE_LENGTH && !needsTotp) verify()
                },
                Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.verify_code)) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.MarkEmailUnread, null) },
                textStyle = TextStyle(fontSize = 22.sp, letterSpacing = 6.sp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { verify() }),
            )
            if (needsTotp) {
                OutlinedTextField(
                    totp, { totp = it.filter { c -> c.isLetterOrDigit() || c == '-' } }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.verify_totp)) }, singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Pin, null) },
                    supportingText = { Text(stringResource(R.string.login_code_hint)) },
                    keyboardOptions = TwoFactorKeyboard,
                    keyboardActions = KeyboardActions(onGo = { verify() }),
                )
            }
            error?.let { Banner(it, isError = true) }
            notice?.let { Banner(it, isError = false) }
            Button(
                onClick = { verify() },
                enabled = !busy && code.length == CODE_LENGTH && (!needsTotp || totp.isNotBlank()),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text(stringResource(R.string.login_verify))
            }
            OutlinedButton(
                onClick = { resend() },
                enabled = !resending && waitSeconds == 0L,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (waitSeconds > 0) stringResource(R.string.verify_resend_in, waitSeconds.toInt())
                    else stringResource(R.string.verify_resend),
                )
            }
            TextButton(
                onClick = {
                    app.accounts.useDifferentEmail()
                    onDifferentEmail()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.verify_different_email)) }
            // The account waits in Manage accounts until the code is entered.
            TextButton(
                onClick = {
                    app.accounts.postponeVerification()
                    onDone()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.verify_later)) }
        }
        Spacer(Modifier.weight(1f))
        Text(
            stringResource(R.string.verify_spam_hint),
            Modifier.padding(vertical = 24.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Banner(text: String, isError: Boolean) {
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
