package com.termoak.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.termoak.app.R
import com.termoak.ffi.qrCode

/**
 * The QR code of [text] (the engine's encoder), dark modules on white with
 * the four-module quiet zone, so phones can read it from the screen in
 * either theme. Nothing when the text doesn't fit in a QR code.
 */
@Composable
fun QrCodeImage(text: String, description: String, modifier: Modifier = Modifier) {
    val code = remember(text) { runCatching { qrCode(text) }.getOrNull() } ?: return
    val n = code.size.toInt()
    val modules = code.modules
    Canvas(modifier.aspectRatio(1f).semantics { contentDescription = description }) {
        drawRect(Color.White)
        val cell = size.minDimension / (n + 8)
        for (y in 0 until n) {
            for (x in 0 until n) {
                if (modules[y * n + x]) {
                    drawRect(Color.Black, Offset((x + 4) * cell, (y + 4) * cell), Size(cell + 0.5f, cell + 0.5f))
                }
            }
        }
    }
}

/** A QR code to scan from another device, with its text under it. */
@Composable
fun QrCodeDialog(title: String, text: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                QrCodeImage(text, title, Modifier.widthIn(max = 280.dp).fillMaxWidth())
                Text(
                    text, Modifier.padding(top = 12.dp), fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) } },
    )
}
