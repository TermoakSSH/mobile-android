package com.termoak.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.createBitmap
import com.termoak.app.R
import com.termoak.app.files.FilePreviewState
import com.termoak.app.files.PreviewKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** What a text preview loaded: the text (or its start), and whether it can be edited. */
private data class LoadedText(val text: String, val whole: Boolean, val binary: Boolean)

/**
 * The in-app preview of a remote file (as iOS's QuickLook): text with its
 * own editor (saved back over the file), an image, or a PDF page by page.
 * "Open with" hands it to another app, as before.
 */
@Composable
internal fun FilePreviewDialog(
    preview: FilePreviewState,
    canEdit: Boolean,
    onOpenWith: () -> Unit,
    onSave: suspend (String) -> Boolean,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var editing by remember(preview) { mutableStateOf(false) }
    var edited by remember(preview) { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var discardAsk by remember { mutableStateOf(false) }
    val text by produceState<LoadedText?>(null, preview) {
        if (preview.kind != PreviewKind.TEXT) return@produceState
        value = withContext(Dispatchers.IO) { loadText(preview.local) }
    }
    val changed = edited != null && edited != text?.text
    fun close() = if (changed) discardAsk = true else onDismiss()
    Dialog(onDismissRequest = { close() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BackHandler { close() }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { close() }) { Icon(Icons.Outlined.Close, stringResource(R.string.common_close)) }
                    Text(
                        preview.file.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    val t = text
                    when {
                        editing -> TextButton(
                            enabled = changed && !saving,
                            onClick = {
                                val value = edited ?: return@TextButton
                                saving = true
                                scope.launch {
                                    if (onSave(value)) {
                                        editing = false
                                        onDismiss()
                                    }
                                    saving = false
                                }
                            },
                        ) { Text(stringResource(if (saving) R.string.files_saving else R.string.common_save)) }
                        preview.kind == PreviewKind.TEXT && canEdit && t != null && t.whole && !t.binary ->
                            IconButton(onClick = { edited = t.text; editing = true }) { Icon(Icons.Outlined.Edit, stringResource(R.string.common_edit)) }
                    }
                    if (!editing) {
                        IconButton(onClick = onOpenWith) { Icon(Icons.AutoMirrored.Outlined.OpenInNew, stringResource(R.string.files_open_with)) }
                    }
                }
                HorizontalDivider()
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (preview.kind) {
                        PreviewKind.TEXT -> {
                            val t = text
                            when {
                                t == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                                t.binary -> Hint(stringResource(R.string.files_preview_binary))
                                editing -> BasicTextField(
                                    edited.orEmpty(), { edited = it },
                                    Modifier.fillMaxSize().padding(12.dp),
                                    textStyle = TextStyle(
                                        fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface,
                                    ),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                                )
                                else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                                    Text(
                                        t.text, Modifier.horizontalScroll(rememberScrollState()).padding(12.dp),
                                        fontFamily = FontFamily.Monospace, fontSize = 13.sp, softWrap = false,
                                    )
                                    if (!t.whole) Hint(stringResource(R.string.files_preview_truncated))
                                }
                            }
                        }
                        PreviewKind.IMAGE -> ImagePreview(preview.local)
                        PreviewKind.PDF -> PdfPreview(preview.local)
                    }
                }
            }
        }
    }
    if (discardAsk) {
        ConfirmDialog(
            stringResource(R.string.files_discard_title), stringResource(R.string.files_discard_text),
            stringResource(R.string.files_discard), destructive = true, onDismiss = { discardAsk = false },
        ) { onDismiss() }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text, Modifier.fillMaxWidth().padding(24.dp), textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun loadText(file: File): LoadedText {
    val size = file.length()
    val bytes = file.inputStream().use { it.readNBytesCompat(PreviewKind.MAX_TEXT_BYTES.toInt()) }
    if (PreviewKind.looksBinary(bytes)) return LoadedText("", whole = false, binary = true)
    return LoadedText(String(bytes, Charsets.UTF_8), whole = size <= PreviewKind.MAX_TEXT_BYTES, binary = false)
}

private fun java.io.InputStream.readNBytesCompat(n: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(8192)
    var left = n
    while (left > 0) {
        val r = read(buf, 0, minOf(buf.size, left))
        if (r < 0) break
        out.write(buf, 0, r)
        left -= r
    }
    return out.toByteArray()
}

/** An image, scaled down to at most ~2048 px on its long side (memory). */
@Composable
private fun ImagePreview(file: File) {
    val bitmap by produceState<Bitmap?>(null, file) {
        value = withContext(Dispatchers.IO) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
            BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        val b = bitmap
        if (b == null) CircularProgressIndicator() else Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
    }
}

/** A PDF, page by page (Android's own renderer). */
@Composable
private fun PdfPreview(file: File) {
    val renderer = remember(file) {
        runCatching { PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)) }.getOrNull()
    }
    androidx.compose.runtime.DisposableEffect(renderer) { onDispose { runCatching { renderer?.close() } } }
    if (renderer == null) {
        Hint(stringResource(R.string.files_preview_failed))
        return
    }
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        items(renderer.pageCount) { index ->
            val page by produceState<Bitmap?>(null, index) {
                value = withContext(Dispatchers.IO) {
                    synchronized(renderer) {
                        runCatching {
                            renderer.openPage(index).use { p ->
                                val width = 1200
                                val height = (width.toFloat() * p.height / p.width).toInt().coerceAtLeast(1)
                                createBitmap(width, height).also { bmp ->
                                    bmp.eraseColor(android.graphics.Color.WHITE)
                                    p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                }
                            }
                        }.getOrNull()
                    }
                }
            }
            val b = page
            Box(Modifier.fillMaxWidth().padding(8.dp)) {
                if (b == null) {
                    Box(Modifier.fillMaxWidth().aspectRatio(0.707f).background(Color.White))
                } else {
                    Image(b.asImageBitmap(), null, Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
                }
            }
        }
    }
}
