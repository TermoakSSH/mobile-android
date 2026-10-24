package com.termoak.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.termoak.app.R
import com.termoak.app.asString
import com.termoak.app.sk.SecurityKeys
import com.termoak.app.sk.SkErrors
import com.termoak.app.sk.SkPurpose
import com.termoak.app.sk.SkStage
import com.termoak.ffi.SecurityKeyAlgorithm

/**
 * The security key's sheet, above everything (also above dialogs): "Tap or
 * plug in your security key", its PIN and "Touch your security key" with
 * what the request is for (host, user, key and purpose), Cancel and, for a
 * request of the server, "Not on this phone" (another device may answer).
 * Swiping it away is Cancel, or "Not on this phone" for the server's.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityKeySheet(keys: SecurityKeys) {
    val sheet by keys.sheet.collectAsState()
    val s = sheet ?: return
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { if (s.op.forwarded) keys.notHere() else keys.cancel() },
        sheetState = state,
    ) {
        SecurityKeySheetContent(s, onCancel = { keys.cancel() }, onNotHere = { keys.notHere() }, onPin = { keys.answerPin(it) })
        Spacer(Modifier.navigationBarsPadding().height(12.dp))
    }
}

/** What the sheet shows (also drawn by the screenshot tests). */
@Composable
fun SecurityKeySheetContent(
    sheet: SecurityKeys.Sheet,
    onCancel: () -> Unit,
    onNotHere: () -> Unit,
    onPin: (String) -> Unit,
) {
    val op = sheet.op
    val pin = sheet.pin
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).testTag("sk-sheet"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val (icon, title, text) = when {
            pin != null -> Triple(Icons.Outlined.Password, stringResource(R.string.sk_pin_title), stringResource(R.string.sk_pin_text))
            sheet.stage is SkStage.Insert && sheet.stage.nfc -> Triple(
                Icons.Outlined.Nfc, stringResource(R.string.sk_sheet_insert_title_nfc), stringResource(R.string.sk_sheet_insert_text_nfc),
            )
            sheet.stage is SkStage.Insert -> Triple(Icons.Outlined.Key, stringResource(R.string.sk_sheet_insert_title), stringResource(R.string.sk_sheet_insert_text))
            sheet.stage == SkStage.Touch -> Triple(Icons.Outlined.TouchApp, stringResource(R.string.sk_sheet_touch_title), stringResource(R.string.sk_sheet_touch_text))
            else -> Triple(Icons.Outlined.Key, stringResource(R.string.sk_sheet_working_title), stringResource(R.string.sk_sheet_working_text))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(48.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
            }
            Spacer(Modifier.width(16.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        }
        Text(text, style = MaterialTheme.typography.bodyMedium)
        if (pin == null && op.forwarded && sheet.stage is SkStage.Insert) {
            Text(stringResource(R.string.sk_sheet_insert_forwarded), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (pin == null && sheet.stage !is SkStage.Insert) LinearProgressIndicator(Modifier.fillMaxWidth())
        RequestDetails(op.purpose)
        if (pin != null) {
            var value by remember(pin) { mutableStateOf("") }
            val focus = remember { FocusRequester() }
            if (pin.retry) {
                Text(stringResource(R.string.sk_pin_wrong), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            pin.retriesLeft?.let {
                Text(pluralStringResource(R.plurals.sk_pin_retries, it, it), style = MaterialTheme.typography.bodySmall,
                    color = if (it <= 2) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedTextField(
                value, { value = it }, Modifier.fillMaxWidth().focusRequester(focus).testTag("sk-pin"),
                label = { Text(stringResource(R.string.sk_pin_label)) }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onDone = { if (value.length >= MIN_PIN) onPin(value) }),
            )
            Text(stringResource(R.string.sk_pin_tap_again), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            LaunchedEffect(pin) { runCatching { focus.requestFocus() } }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                TextButton(onClick = onCancel) { Text(stringResource(R.string.common_cancel)) }
                Button(onClick = { onPin(value) }, enabled = value.length >= MIN_PIN, modifier = Modifier.testTag("sk-pin-ok")) {
                    Text(stringResource(R.string.sk_pin_ok))
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                if (op.forwarded) {
                    OutlinedButton(onClick = onNotHere, modifier = Modifier.testTag("sk-not-here")) { Text(stringResource(R.string.sk_sheet_not_here)) }
                }
                TextButton(onClick = onCancel, modifier = Modifier.testTag("sk-cancel")) { Text(stringResource(R.string.common_cancel)) }
            }
        }
    }
}

/** FIDO2 PINs have at least 4 characters. */
private const val MIN_PIN = 4

/** For / Host / Key of a sign request; Application / Algorithm when creating. */
@Composable
private fun RequestDetails(purpose: SkPurpose) {
    Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Detail(stringResource(R.string.sk_sheet_purpose), SkErrors.purposeText(purpose).asString())
            when (purpose) {
                is SkPurpose.Sign -> {
                    Detail(stringResource(R.string.sk_sheet_host), SkErrors.targetText(purpose.request))
                    Detail(stringResource(R.string.sk_sheet_key), SkErrors.keyText(purpose.request))
                }
                is SkPurpose.Create -> {
                    Detail(stringResource(R.string.sk_sheet_application), purpose.application, mono = true)
                    Detail(
                        stringResource(R.string.sk_sheet_algorithm),
                        if (purpose.algorithm == SecurityKeyAlgorithm.ED25519) "Ed25519" else "ECDSA P-256",
                    )
                }
                SkPurpose.LoadResident -> {}
            }
        }
    }
}

@Composable
private fun Detail(title: String, value: String, mono: Boolean = false) {
    Row {
        Text(title, Modifier.width(96.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis,
            fontFamily = if (mono) FontFamily.Monospace else null)
    }
}
