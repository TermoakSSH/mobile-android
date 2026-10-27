package com.termoak.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.print.PrintAttributes
import android.print.PrintManager
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.termoak.app.R
import com.termoak.app.TermoakApp
import com.termoak.app.asString
import com.termoak.app.data.E2eeBook
import com.termoak.app.data.E2eeRules
import com.termoak.app.data.E2eeTexts
import com.termoak.app.data.displayEmail
import com.termoak.app.userMessage
import com.termoak.ffi.E2eePairingRequest
import com.termoak.ffi.E2eeRecoveryKeyCheck
import com.termoak.ffi.TermoakException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ----- The engine's recovery phrase helpers (pure functions of the library) -----

/** The core's BIP39 English word list (2048 words, sorted), read once. */
object RecoveryWords {
    @Volatile var list: List<String>? = null

    /** The engine's own check; tests set a stand-in. */
    @Volatile var check: (String) -> E2eeRecoveryKeyCheck = { com.termoak.ffi.e2eeRecoveryKeyCheck(it) }

    fun words(): List<String> = list ?: runCatching { com.termoak.ffi.e2eeRecoveryWordlist() }.getOrDefault(emptyList()).also { list = it }
}

// ----- FLAG_SECURE while a recovery phrase is on screen -----

private fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/**
 * Keeps the activity's window out of screenshots, screen recordings and the
 * recent apps' thumbnail while composed (a recovery phrase is on screen; the
 * dialog that shows it is secure too).
 */
@Composable
fun SecureWindow() {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = context.activity()?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}

/** A full-screen dialog that never appears in screenshots or the app switcher. */
@Composable
fun SecureFullDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false, dismissOnClickOutside = false, securePolicy = SecureFlagPolicy.SecureOn,
            decorFitsSystemWindows = false,
        ),
    ) {
        SecureWindow()
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) { content() }
        }
    }
}

// ----- Badges -----

/** "End-to-end encrypted" (· locked) for a vault or an item. */
@Composable
fun E2eeBadge(badge: E2eeRules.Badge, modifier: Modifier = Modifier) {
    when (badge) {
        E2eeRules.Badge.NONE -> Unit
        E2eeRules.Badge.E2EE -> Pill(stringResource(R.string.vault_e2ee_badge), Brand.Green, modifier)
        E2eeRules.Badge.LOCKED -> Pill(stringResource(R.string.vault_e2ee_badge_locked), Brand.Amber, modifier)
    }
}

/** A small lock on an item whose secret this device can't open yet. */
@Composable
fun E2eeLockedChip(modifier: Modifier = Modifier) {
    Pill(stringResource(R.string.vault_e2ee_item_locked), Brand.Amber, modifier)
}

// ----- The phrase -----

/** The 24 words in two numbered columns (monospace). */
@Composable
fun PhraseGrid(phrase: String, modifier: Modifier = Modifier) {
    val words = E2eeRules.numberedWords(phrase)
    val half = (words.size + 1) / 2
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium) {
        Row(Modifier.padding(16.dp).testTag("phrase-grid")) {
            listOf(words.take(half), words.drop(half)).forEach { col ->
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    col.forEach { (n, w) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "$n", Modifier.width(28.dp), textAlign = TextAlign.End, fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(w, Modifier.padding(start = 10.dp), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}

/** The phrase shown once: the grid, copy / save / print and "I have stored it". */
@Composable
fun PhraseShowContent(
    title: String,
    phrase: String,
    stored: Boolean,
    onStored: (Boolean) -> Unit,
    onCopy: () -> Unit,
    onSave: () -> Unit,
    onPrint: () -> Unit,
    onNext: () -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.e2ee_recovery_show_message), style = MaterialTheme.typography.bodyMedium)
        PhraseGrid(phrase)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = onCopy, label = { Text(stringResource(R.string.common_copy)) }, leadingIcon = { Icon(Icons.Outlined.ContentCopy, null, Modifier.size(18.dp)) })
            AssistChip(onClick = onSave, label = { Text(stringResource(R.string.e2ee_recovery_save)) }, leadingIcon = { Icon(Icons.Outlined.Save, null, Modifier.size(18.dp)) })
            AssistChip(onClick = onPrint, label = { Text(stringResource(R.string.e2ee_recovery_print)) }, leadingIcon = { Icon(Icons.Outlined.Print, null, Modifier.size(18.dp)) })
        }
        Text(stringResource(R.string.e2ee_recovery_keep_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().clickable { onStored(!stored) }, verticalAlignment = Alignment.CenterVertically) {
            Checkbox(stored, onStored, Modifier.testTag("phrase-stored"))
            Text(stringResource(R.string.e2ee_recovery_stored), style = MaterialTheme.typography.bodyMedium)
        }
        Button(onClick = onNext, enabled = stored, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text(stringResource(R.string.common_continue)) }
    }
}

/** Three words of the phrase typed again. */
@Composable
fun PhraseConfirmContent(
    positions: List<Int>,
    typed: List<String>,
    onTyped: (Int, String) -> Unit,
    wrong: Boolean,
    busy: Boolean,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(stringResource(R.string.e2ee_confirm_done), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.e2ee_confirm_message), style = MaterialTheme.typography.bodyMedium)
        positions.forEachIndexed { i, p ->
            OutlinedTextField(
                typed.getOrElse(i) { "" }, { onTyped(i, it) }, Modifier.fillMaxWidth().testTag("confirm-word-$i"),
                label = { Text(stringResource(R.string.e2ee_confirm_word, (p + 1).toString())) }, singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
            )
        }
        if (wrong) MessageBox(stringResource(R.string.e2ee_confirm_wrong), isError = true)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onBack) { Text(stringResource(R.string.e2ee_confirm_back)) }
            Button(onClick = onDone, enabled = !busy && typed.all { it.isNotBlank() }) { Text(stringResource(R.string.e2ee_confirm_done)) }
        }
    }
}

/** The optional passphrase (twice) with its strength. */
@Composable
fun PassphraseFields(p1: String, p2: String, onP1: (String) -> Unit, onP2: (String) -> Unit, label: String = stringResource(R.string.e2ee_passphrase_optional)) {
    var show by remember { mutableStateOf(false) }
    val vt = if (show) VisualTransformation.None else PasswordVisualTransformation()
    val kb = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false)
    OutlinedTextField(
        p1, onP1, Modifier.fillMaxWidth().testTag("passphrase"), label = { Text(label) }, singleLine = true, visualTransformation = vt,
        keyboardOptions = kb,
        trailingIcon = { IconButton(onClick = { show = !show }) { Icon(if (show) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, stringResource(R.string.common_show)) } },
    )
    if (p1.isNotEmpty()) {
        val s = E2eeRules.strength(p1)
        val (text, color, fraction) = when (s) {
            E2eeRules.Strength.TOO_SHORT -> Triple(R.string.e2ee_passphrase_strength_too_short, Brand.Red, 0.1f)
            E2eeRules.Strength.WEAK -> Triple(R.string.e2ee_passphrase_strength_weak, Brand.Red, 0.3f)
            E2eeRules.Strength.FAIR -> Triple(R.string.e2ee_passphrase_strength_fair, Brand.Amber, 0.55f)
            E2eeRules.Strength.GOOD -> Triple(R.string.e2ee_passphrase_strength_good, Brand.Green, 0.8f)
            E2eeRules.Strength.STRONG -> Triple(R.string.e2ee_passphrase_strength_strong, Brand.Green, 1f)
        }
        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth(), color = color)
        Text(stringResource(text), style = MaterialTheme.typography.bodySmall, color = color)
        OutlinedTextField(
            p2, onP2, Modifier.fillMaxWidth().testTag("passphrase2"), label = { Text(stringResource(R.string.e2ee_passphrase_repeat)) },
            singleLine = true, visualTransformation = vt, keyboardOptions = kb,
            isError = p2.isNotEmpty() && p2 != p1,
            supportingText = if (p2.isNotEmpty() && p2 != p1) ({ Text(stringResource(R.string.e2ee_passphrase_mismatch)) }) else null,
        )
    }
    Text(stringResource(R.string.e2ee_passphrase_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** The passphrase is acceptable: empty (optional) or 8+ characters typed twice the same. */
fun passphraseOk(p1: String, p2: String, required: Boolean): Boolean =
    if (p1.isEmpty()) !required else p1.codePointCount(0, p1.length) >= E2eeRules.MIN_PASSPHRASE && p1 == p2

/** What made the phrase. */
enum class PhraseKind { SETUP, NEW_RECOVERY, ROTATE }

private enum class PhraseStep { PASSPHRASE, SHOW, CONFIRM }

/**
 * Set up, new recovery phrase, replace the key: the optional passphrase
 * (setup, replace), the 24 words shown once (copy, save, print; the screen
 * is kept out of screenshots and the app switcher), then three of them
 * typed again. The engine publishes the key when it makes it (as the
 * desktop), so closing before confirming warns that the phrase is not
 * shown again.
 */
@Composable
fun PhraseFlowDialog(app: TermoakApp, accountId: String, kind: PhraseKind, onClose: (done: Boolean) -> Unit) {
    val res = LocalResources.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    val handle = remember(accountId) { app.accounts.handle(accountId) }
    val account = app.accounts.account(accountId)
    var step by remember { mutableStateOf(if (kind == PhraseKind.NEW_RECOVERY) PhraseStep.SHOW else PhraseStep.PASSPHRASE) }
    var phrase by remember { mutableStateOf<String?>(null) }
    var p1 by remember { mutableStateOf("") }
    var p2 by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var stored by remember { mutableStateOf(false) }
    val positions = remember { E2eeRules.confirmPositions(java.security.SecureRandom().nextLong()) }
    var typed by remember { mutableStateOf(List(3) { "" }) }
    var wrong by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }

    // A new recovery phrase: made at once (the old one stops working).
    LaunchedEffect(kind) {
        if (kind != PhraseKind.NEW_RECOVERY || phrase != null) return@LaunchedEffect
        busy = true
        try {
            phrase = handle?.e2eeNewRecoveryKey()
        } catch (e: TermoakException) {
            error = e.userMessage(res, R.string.e2ee_title)
        } finally {
            busy = false
        }
    }
    fun create() {
        if (busy || !passphraseOk(p1, p2, required = false)) return
        busy = true
        error = null
        scope.launch {
            try {
                val pass = p1.ifEmpty { null }
                val r = if (kind == PhraseKind.ROTATE) handle?.e2eeRotateUserKey(pass) else handle?.e2eeSetup(pass)
                phrase = r?.recoveryKey
                p1 = ""; p2 = ""
                step = PhraseStep.SHOW
                E2eeBook.refresh(accountId)
                app.accounts.sync(accountId)
            } catch (e: TermoakException) {
                error = e.userMessage(res, R.string.e2ee_title)
            } finally {
                busy = false
            }
        }
    }
    val title = stringResource(
        when (kind) {
            PhraseKind.SETUP -> R.string.e2ee_setup_title
            PhraseKind.NEW_RECOVERY -> R.string.e2ee_recovery_new_title
            PhraseKind.ROTATE -> R.string.e2ee_rotate_title
        },
    )
    val who = res.getString(R.string.e2ee_recovery_doc_account, account?.email.orEmpty(), account?.serverUrl.orEmpty())
    val date = res.getString(R.string.e2ee_recovery_doc_date, java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).format(java.util.Date()))
    val saveAs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val p = phrase ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use {
                it.write(E2eeRules.document(p, res.getString(R.string.e2ee_recovery_doc_title), who, date, res.getString(R.string.e2ee_recovery_doc_keep)).toByteArray())
            }
        }.onSuccess { scope.launch { snackbar.showSnackbar(res.getString(R.string.e2ee_recovery_saved_here)) } }
    }
    fun finish() {
        val p = phrase ?: return
        if (!E2eeRules.confirmMatches(p, positions, typed)) {
            wrong = true
            return
        }
        phrase = null
        onClose(true)
        scope.launch {
            snackbar.showSnackbar(
                res.getString(
                    when (kind) {
                        PhraseKind.SETUP -> R.string.e2ee_setup_done
                        PhraseKind.NEW_RECOVERY -> R.string.e2ee_recovery_new_done
                        PhraseKind.ROTATE -> R.string.e2ee_rotate_done
                    },
                ),
            )
        }
    }
    fun tryClose() {
        if (phrase != null) closing = true else onClose(false)
    }
    SecureFullDialog(onDismiss = { tryClose() }) {
        BackHandler { if (step == PhraseStep.CONFIRM) step = PhraseStep.SHOW else tryClose() }
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { tryClose() }) { Text(stringResource(R.string.common_close)) }
            }
            when {
                step == PhraseStep.PASSPHRASE -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(title, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        stringResource(if (kind == PhraseKind.ROTATE) R.string.e2ee_rotate_message else R.string.e2ee_setup_message),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    PassphraseFields(p1, p2, { p1 = it }, { p2 = it })
                    error?.let { MessageBox(it, isError = true) }
                    Button(
                        onClick = { create() }, enabled = !busy && passphraseOk(p1, p2, required = false),
                        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("e2ee-create"),
                    ) {
                        if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Text(stringResource(if (kind == PhraseKind.ROTATE) R.string.e2ee_rotate_confirm else R.string.e2ee_setup_create))
                    }
                }
                phrase == null -> Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                    if (busy) CircularProgressIndicator() else error?.let { MessageBox(it, isError = true) }
                }
                step == PhraseStep.SHOW -> PhraseShowContent(
                    title, phrase!!, stored, { stored = it },
                    onCopy = {
                        copySecret(context, res.getString(R.string.e2ee_recovery_doc_title), phrase!!)
                        scope.launch { snackbar.showSnackbar(res.getString(R.string.e2ee_recovery_copied)) }
                    },
                    onSave = { saveAs.launch("termoak-recovery-phrase.txt") },
                    onPrint = {
                        printHtml(
                            context, res.getString(R.string.e2ee_recovery_doc_title),
                            E2eeRules.printPage(phrase!!, res.getString(R.string.e2ee_recovery_doc_title), who, date, res.getString(R.string.e2ee_recovery_doc_keep)),
                        )
                    },
                    onNext = { step = PhraseStep.CONFIRM; wrong = false },
                )
                else -> PhraseConfirmContent(
                    positions, typed, { i, v -> typed = typed.toMutableList().also { it[i] = v }; wrong = false }, wrong, busy,
                    onBack = { step = PhraseStep.SHOW }, onDone = { finish() },
                )
            }
        }
        if (closing) {
            AlertDialog(
                onDismissRequest = { closing = false },
                title = { Text(stringResource(R.string.e2ee_confirm_close_title)) },
                text = { Text(stringResource(R.string.e2ee_confirm_close_message)) },
                confirmButton = { TextButton(onClick = { closing = false; phrase = null; onClose(true) }) { Text(stringResource(R.string.e2ee_confirm_close_anyway)) } },
                dismissButton = { TextButton(onClick = { closing = false }) { Text(stringResource(R.string.e2ee_confirm_back)) } },
                properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
            )
        }
    }
}

/** Prints an HTML page with Android's print service (a WebView made for it). */
fun printHtml(context: Context, title: String, html: String) {
    val activity = context.activity() ?: return
    val web = WebView(activity)
    web.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String?) {
            val pm = activity.getSystemService(PrintManager::class.java) ?: return
            pm.print(title, view.createPrintDocumentAdapter(title), PrintAttributes.Builder().build())
        }
    }
    web.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
}

// ----- Unlock -----

/** The ways to unlock this device. */
enum class UnlockWay { PASSPHRASE, RECOVERY, DEVICE }

/** The recovery phrase field with live feedback and word suggestions. */
@Composable
fun RecoveryPhraseField(text: String, onText: (String) -> Unit, words: List<String>, feedback: E2eeRules.Feedback) {
    OutlinedTextField(
        text, onText, Modifier.fillMaxWidth().height(140.dp).testTag("recovery-phrase"),
        placeholder = { Text(stringResource(R.string.e2ee_unlock_recovery_placeholder)) },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
        visualTransformation = VisualTransformation.None,
        isError = feedback is E2eeRules.Feedback.Problem,
    )
    val suggestions = E2eeRules.suggestions(text, words)
    if (suggestions.isNotEmpty()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            suggestions.forEach { w -> AssistChip(onClick = { onText(E2eeRules.complete(text, w)) }, label = { Text(w, fontFamily = FontFamily.Monospace) }) }
        }
    }
    when (feedback) {
        E2eeRules.Feedback.Empty -> Unit
        E2eeRules.Feedback.Valid -> Text(stringResource(R.string.e2ee_unlock_recovery_valid), color = Brand.Green, style = MaterialTheme.typography.bodySmall)
        is E2eeRules.Feedback.Typing -> Text(
            stringResource(R.string.e2ee_unlock_recovery_typing, feedback.words.toString()),
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
        )
        is E2eeRules.Feedback.Problem -> Text(
            E2eeTexts.recoveryReason(feedback.reason, feedback.word, feedback.words).asString(),
            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** The pairing code, big. */
@Composable
fun PairingCode(code: String, modifier: Modifier = Modifier) {
    val shown = if (code.length == 6) code.substring(0, 3) + " " + code.substring(3) else code
    Text(
        shown, modifier.fillMaxWidth().testTag("pairing-code"), textAlign = TextAlign.Center, fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold, fontSize = 40.sp, letterSpacing = 4.sp,
    )
}

/** The unlock dialog's body (stateless: the screenshots draw it). */
@Composable
fun UnlockContent(
    way: UnlockWay,
    onWay: (UnlockWay) -> Unit,
    hasPassphrase: Boolean,
    hasRecovery: Boolean,
    passphrase: String,
    onPassphrase: (String) -> Unit,
    recovery: String,
    onRecovery: (String) -> Unit,
    words: List<String>,
    feedback: E2eeRules.Feedback,
    pairingCode: String?,
    pairingLeft: String?,
    error: String?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (hasPassphrase) FilterChip(way == UnlockWay.PASSPHRASE, { onWay(UnlockWay.PASSPHRASE) }, { Text(stringResource(R.string.e2ee_unlock_way_passphrase)) })
            if (hasRecovery) FilterChip(way == UnlockWay.RECOVERY, { onWay(UnlockWay.RECOVERY) }, { Text(stringResource(R.string.e2ee_unlock_way_recovery)) })
            FilterChip(way == UnlockWay.DEVICE, { onWay(UnlockWay.DEVICE) }, { Text(stringResource(R.string.e2ee_unlock_way_device)) }, Modifier.testTag("way-device"))
        }
        when (way) {
            UnlockWay.PASSPHRASE -> {
                Text(stringResource(R.string.e2ee_unlock_passphrase_message), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    passphrase, onPassphrase, Modifier.fillMaxWidth().testTag("unlock-passphrase"), singleLine = true,
                    label = { Text(stringResource(R.string.e2ee_passphrase_placeholder)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                )
            }
            UnlockWay.RECOVERY -> {
                Text(stringResource(R.string.e2ee_unlock_recovery_message), style = MaterialTheme.typography.bodyMedium)
                RecoveryPhraseField(recovery, onRecovery, words, feedback)
            }
            UnlockWay.DEVICE -> {
                Text(stringResource(R.string.e2ee_unlock_device_message), style = MaterialTheme.typography.bodyMedium)
                if (pairingCode != null) {
                    Text(stringResource(R.string.e2ee_unlock_device_code), style = MaterialTheme.typography.labelMedium)
                    PairingCode(pairingCode)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(
                            stringResource(R.string.e2ee_unlock_device_waiting) + (pairingLeft?.let { " · " + stringResource(R.string.e2ee_pairing_expires, it) } ?: ""),
                            Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
    }
}

/**
 * Unlock end-to-end encryption on this device: the E2EE passphrase, the
 * recovery phrase (live feedback from the core and word suggestions), or
 * another device of yours (a six-digit code to compare there; this one
 * asks every few seconds and on the `e2ee` event).
 */
@Composable
fun UnlockDialog(app: TermoakApp, accountId: String, onDone: (unlocked: Boolean) -> Unit) {
    val res = LocalResources.current
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    val handle = remember(accountId) { app.accounts.handle(accountId) }
    val st = E2eeBook.status(accountId)
    val hasPassphrase = st?.hasPassphrase ?: true
    val hasRecovery = st?.hasRecoveryKey ?: true
    var way by remember { mutableStateOf(if (hasPassphrase) UnlockWay.PASSPHRASE else if (hasRecovery) UnlockWay.RECOVERY else UnlockWay.DEVICE) }
    var passphrase by remember { mutableStateOf("") }
    var recovery by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pairing by remember { mutableStateOf<com.termoak.ffi.E2eePairingStart?>(null) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    val words = remember { RecoveryWords.words() }
    val feedback = remember(recovery) { if (recovery.isBlank()) E2eeRules.Feedback.Empty else runCatching { E2eeRules.feedback(recovery, RecoveryWords.check, words) }.getOrDefault(E2eeRules.Feedback.Empty) }

    suspend fun unlocked(n: ULong) {
        E2eeBook.refresh(accountId)
        app.accounts.sync(accountId)
        onDone(true)
        snackbar.showSnackbar(res.getQuantityString(R.plurals.e2ee_unlock_done, n.toInt(), n.toInt()))
    }
    fun submit() {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try {
                val n = when (way) {
                    UnlockWay.PASSPHRASE -> handle?.e2eeUnlockWithPassphrase(passphrase)
                    UnlockWay.RECOVERY -> handle?.e2eeUnlockWithRecoveryKey(recovery)
                    UnlockWay.DEVICE -> {
                        pairing = handle?.e2eePairingStart()
                        null
                    }
                }
                passphrase = ""
                if (n != null) {
                    recovery = ""
                    unlocked(n)
                }
            } catch (e: TermoakException) {
                error = e.userMessage(res, R.string.error_e2ee_decrypt_failed)
            } finally {
                busy = false
            }
        }
    }
    // Another device: wait for the approval (every 3 s, and right after an e2ee event).
    LaunchedEffect(pairing?.id) {
        val p = pairing ?: return@LaunchedEffect
        while (true) {
            now = System.currentTimeMillis()
            if (now > p.expiresAt) {
                error = res.getString(R.string.error_e2ee_pairing_closed)
                pairing = null
                return@LaunchedEffect
            }
            try {
                val n = handle?.e2eePairingFinish(p.id)
                if (n != null) {
                    pairing = null
                    unlocked(n)
                    return@LaunchedEffect
                }
            } catch (e: TermoakException) {
                error = e.userMessage(res, R.string.error_e2ee_pairing_closed)
                pairing = null
                return@LaunchedEffect
            }
            delay(3_000)
        }
    }
    AlertDialog(
        onDismissRequest = { onDone(false) },
        icon = { Icon(Icons.Outlined.LockOpen, null) },
        title = { Text(stringResource(R.string.e2ee_unlock_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                UnlockContent(
                    way, { way = it; error = null }, hasPassphrase, hasRecovery, passphrase, { passphrase = it }, recovery, { recovery = it },
                    words, feedback, pairing?.code, pairing?.let { E2eeRules.pairingLeft(it.expiresAt, now) }, error,
                )
            }
        },
        confirmButton = {
            val enabled = !busy && when (way) {
                UnlockWay.PASSPHRASE -> passphrase.isNotEmpty()
                UnlockWay.RECOVERY -> feedback == E2eeRules.Feedback.Valid
                UnlockWay.DEVICE -> pairing == null
            }
            TextButton(onClick = { submit() }, enabled = enabled, modifier = Modifier.testTag("unlock-go")) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(stringResource(if (way == UnlockWay.DEVICE) R.string.e2ee_unlock_device_start else R.string.e2ee_unlock_button))
            }
        },
        dismissButton = { TextButton(onClick = { onDone(false) }) { Text(stringResource(R.string.common_cancel)) } },
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
    )
}

// ----- Pairing (this device approves another one) -----

@Composable
fun PairingContent(p: E2eePairingRequest, left: String) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.e2ee_pairing_message, p.deviceName), style = MaterialTheme.typography.bodyMedium)
        PairingCode(p.code)
        Text(stringResource(R.string.e2ee_pairing_compare), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.e2ee_pairing_expires, left), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** "Unlock another device?": the same six-digit code as on that device, Approve or Deny. */
@Composable
fun PairingDialog(app: TermoakApp, accountId: String, p: E2eePairingRequest, onDone: () -> Unit) {
    val res = LocalResources.current
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(p.id) {
        while (now < p.expiresAt) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
        onDone()
    }
    fun decide(approve: Boolean) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                app.accounts.handle(accountId)?.e2eePairingDecide(p.id, approve)
                E2eeBook.decided(accountId, p.id)
                onDone()
                snackbar.showSnackbar(res.getString(if (approve) R.string.e2ee_pairing_approved else R.string.e2ee_pairing_denied))
            } catch (e: TermoakException) {
                onDone()
                snackbar.showSnackbar(e.userMessage(res, R.string.error_e2ee_pairing_closed))
            } finally {
                busy = false
            }
            E2eeBook.refresh(accountId)
        }
    }
    AlertDialog(
        onDismissRequest = onDone,
        icon = { Icon(Icons.Outlined.Lock, null) },
        title = { Text(stringResource(R.string.e2ee_pairing_title)) },
        text = { PairingContent(p, E2eeRules.pairingLeft(p.expiresAt, now)) },
        confirmButton = { TextButton(onClick = { decide(true) }, enabled = !busy) { Text(stringResource(R.string.e2ee_pairing_approve)) } },
        dismissButton = {
            TextButton(onClick = { decide(false) }, enabled = !busy) {
                Text(stringResource(R.string.e2ee_pairing_deny), color = MaterialTheme.colorScheme.error)
            }
        },
    )
}

// ----- Passphrase, reset -----

/** Set or change the E2EE passphrase (an unlocked device). */
@Composable
fun PassphraseDialog(app: TermoakApp, accountId: String, change: Boolean, onDone: () -> Unit) {
    val res = LocalResources.current
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    var p1 by remember { mutableStateOf("") }
    var p2 by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(stringResource(if (change) R.string.e2ee_passphrase_change else R.string.e2ee_passphrase_set)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PassphraseFields(p1, p2, { p1 = it }, { p2 = it }, stringResource(R.string.e2ee_passphrase_title))
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && passphraseOk(p1, p2, required = true), onClick = {
                busy = true
                scope.launch {
                    try {
                        app.accounts.handle(accountId)?.e2eeSetPassphrase(p1)
                        E2eeBook.refresh(accountId)
                        onDone()
                        snackbar.showSnackbar(res.getString(R.string.e2ee_passphrase_saved))
                    } catch (e: TermoakException) {
                        error = e.userMessage(res, R.string.e2ee_title)
                    } finally {
                        busy = false
                    }
                }
            }) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = { TextButton(onClick = onDone) { Text(stringResource(R.string.common_cancel)) } },
    )
}

/** The reset's body: the vaults that would be lost and the typed email. */
@Composable
fun ResetContent(lost: List<String>?, email: String, typed: String, onTyped: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.e2ee_reset_message), style = MaterialTheme.typography.bodyMedium)
        when {
            lost == null -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            lost.isEmpty() -> Text(stringResource(R.string.e2ee_reset_no_vaults), style = MaterialTheme.typography.bodyMedium)
            else -> {
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.small) {
                    Column(Modifier.fillMaxWidth().padding(10.dp)) {
                        Text(
                            pluralStringResource(R.plurals.e2ee_reset_vaults_lost, lost.size, lost.size),
                            color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium,
                        )
                        lost.forEach { Text("• $it", color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
        OutlinedTextField(
            typed, onTyped, Modifier.fillMaxWidth().testTag("reset-email"), singleLine = true,
            label = { Text(stringResource(R.string.e2ee_reset_type_email, email)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrectEnabled = false),
            isError = typed.isNotEmpty() && !typed.trim().equals(email, ignoreCase = true),
        )
    }
}

/** Everything lost: the vaults nobody else could open first (`e2ee_reset(false)`), then the reset with the typed email. */
@Composable
fun ResetDialog(app: TermoakApp, accountId: String, onDone: () -> Unit) {
    val res = LocalResources.current
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    val handle = remember(accountId) { app.accounts.handle(accountId) }
    val email = app.accounts.account(accountId)?.email.orEmpty()
    var lost by remember { mutableStateOf<List<String>?>(null) }
    var typed by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(accountId) {
        try {
            val ids = handle?.e2eeReset(false).orEmpty()
            val vaults = app.accounts.vaults.value
            lost = ids.map { id -> vaults.firstOrNull { it.id == id }?.name ?: id }
        } catch (e: TermoakException) {
            error = e.userMessage(res, R.string.e2ee_title)
            lost = emptyList()
        }
    }
    AlertDialog(
        onDismissRequest = onDone,
        icon = { Icon(Icons.Outlined.WarningAmber, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(stringResource(R.string.e2ee_reset_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ResetContent(lost, email, typed) { typed = it }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && lost != null && typed.trim().equals(email, ignoreCase = true), onClick = {
                busy = true
                scope.launch {
                    try {
                        handle?.e2eeReset(true)
                        E2eeBook.refresh(accountId)
                        app.accounts.sync(accountId)
                        onDone()
                        snackbar.showSnackbar(res.getString(R.string.e2ee_reset_done))
                    } catch (e: TermoakException) {
                        error = e.userMessage(res, R.string.e2ee_title)
                    } finally {
                        busy = false
                    }
                }
            }) { Text(stringResource(R.string.e2ee_reset_confirm), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDone) { Text(stringResource(R.string.common_cancel)) } },
    )
}

/** A banner for Home (locked, a pairing, members waiting): title, detail and one action. */
@Composable
fun E2eeBanner(title: String, detail: String, action: String, color: Color = Brand.Amber, tag: String = "e2ee-banner", onAction: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).testTag(tag),
        color = color.copy(alpha = 0.14f), shape = MaterialTheme.shapes.medium,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Lock, null, tint = color)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

/** A plain dot used before list rows. */
@Composable
internal fun Dot(color: Color) {
    Box(Modifier.size(8.dp).background(color, RoundedCornerShape(50)))
}

/** Keeps a full-width spacer. */
@Composable
internal fun Gap(h: Int = 8) = Spacer(Modifier.height(h.dp))

/** The account's email for a screen header. */
internal fun accountEmail(app: TermoakApp, accountId: String): String = app.accounts.account(accountId)?.displayEmail.orEmpty()

/**
 * Home's E2EE banners (0.7 phase 5c), per account: end-to-end encrypted
 * vaults locked on this device (Unlock), another device asking for the key
 * (Review: the same six-digit code), members waiting for a vault's key
 * (this device holds it: their fingerprints on the vault's Encryption
 * page), and a vault you could hold the key of before you set E2EE up.
 */
@Composable
fun E2eeHomeBanners(app: TermoakApp, nav: androidx.navigation.NavHostController) {
    val vaults by app.accounts.vaults.collectAsState()
    var unlocking by remember { mutableStateOf<String?>(null) }
    var reviewing by remember { mutableStateOf<Pair<String, E2eePairingRequest>?>(null) }
    Column {
        E2eeBook.supportedAccounts().forEach { acc ->
            val mine = vaults.filter { it.accountId == acc }
            val st = E2eeBook.status(acc) ?: return@forEach
            val e2eeVaults = mine.filter { it.e2ee?.mayHoldKey == true }
            if (!st.setUp) {
                e2eeVaults.firstOrNull()?.let { v ->
                    E2eeBanner(
                        stringResource(R.string.e2ee_banner_setup_title, v.name), stringResource(R.string.e2ee_banner_setup_detail),
                        stringResource(R.string.e2ee_setup_button), tag = "banner-setup",
                    ) { nav.navigate(Routes.security(acc)) }
                }
                return@forEach
            }
            if (!st.unlocked && e2eeVaults.isNotEmpty()) {
                E2eeBanner(
                    stringResource(R.string.e2ee_banner_locked_title), stringResource(R.string.e2ee_banner_locked_detail),
                    stringResource(R.string.e2ee_unlock_button), tag = "banner-locked",
                ) { unlocking = acc }
            }
            E2eeBook.pairings(acc).forEach { p ->
                E2eeBanner(
                    stringResource(R.string.e2ee_banner_pairing_title, p.deviceName), stringResource(R.string.e2ee_banner_pairing_detail),
                    stringResource(R.string.e2ee_pairing_review), color = Brand.Blue, tag = "banner-pairing",
                ) { reviewing = acc to p }
            }
            E2eeRules.pendingVaults(mine, st.unlocked).forEach { v ->
                val n = v.e2ee?.pendingMembers?.toInt() ?: 0
                E2eeBanner(
                    pluralStringResource(R.plurals.e2ee_banner_pending_title, n, n, v.name), stringResource(R.string.e2ee_banner_pending_detail),
                    stringResource(R.string.e2ee_pairing_review), color = Brand.Blue, tag = "banner-pending",
                ) { nav.navigate(Routes.vaultE2ee(acc, v.id)) }
            }
        }
    }
    unlocking?.let { acc -> UnlockDialog(app, acc) { unlocking = null } }
    reviewing?.let { (acc, p) -> PairingDialog(app, acc, p) { reviewing = null } }
}
