package com.termoak.app.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.termoak.app.R
import com.termoak.app.data.HostLogos

/**
 * The host editor's logo picker (the desktop's): Automatic (the detected
 * system's logo, or the initial), the systems and the generic icons. The
 * choice is stored in `SshHost.icon` and synced, so the desktop shows it too.
 */
@Composable
fun LogoPicker(
    label: String,
    os: String?,
    color: String?,
    selected: String?,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
) {
    val systems = HostLogos.all.filter { it.kind == HostLogos.Kind.SYSTEM }
    val generic = HostLogos.all.filter { it.kind == HostLogos.Kind.GENERIC }
    val chosen = HostLogos.byId(selected)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editor_logo)) },
        text = {
            LazyVerticalGrid(
                GridCells.Adaptive(minSize = 76.dp),
                Modifier.fillMaxWidth().heightIn(max = 460.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item(key = "auto", span = { GridItemSpan(maxLineSpan) }) {
                    Choice(
                        stringResource(R.string.editor_logo_automatic_hint), chosen == null && selected == null, wide = true,
                        onClick = { onSelect(null) },
                    ) { HostTile(label, os, color, size = 40.dp) }
                }
                item(key = "systems", span = { GridItemSpan(maxLineSpan) }) { PickerTitle(stringResource(R.string.editor_logo_systems)) }
                items(systems, key = { it.id }) { logo ->
                    Choice(logoName(logo), chosen?.id == logo.id, onClick = { onSelect(logo.id) }) {
                        HostTile(label, null, null, size = 40.dp, icon = logo.id)
                    }
                }
                item(key = "generic", span = { GridItemSpan(maxLineSpan) }) { PickerTitle(stringResource(R.string.editor_logo_generic)) }
                items(generic, key = { it.id }) { logo ->
                    Choice(logoName(logo), chosen?.id == logo.id, onClick = { onSelect(logo.id) }) {
                        HostTile(label, null, null, size = 40.dp, icon = logo.id)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
private fun PickerTitle(text: String) {
    Text(
        text, Modifier.padding(top = 10.dp, bottom = 2.dp), style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Choice(name: String, selected: Boolean, wide: Boolean = false, onClick: () -> Unit, tile: @Composable () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    val content: @Composable () -> Unit = {
        tile()
        Text(
            name, if (wide) Modifier.padding(start = 12.dp) else Modifier.padding(top = 4.dp),
            style = if (wide) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.labelSmall,
            textAlign = if (wide) TextAlign.Start else TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
    }
    val modifier = Modifier.fillMaxWidth().clip(shape)
        .border(if (selected) 2.dp else 0.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, shape)
        .clickable(onClick = onClick).padding(6.dp)
    if (wide) {
        androidx.compose.foundation.layout.Row(modifier, verticalAlignment = Alignment.CenterVertically) { content() }
    } else {
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) { content() }
    }
}
