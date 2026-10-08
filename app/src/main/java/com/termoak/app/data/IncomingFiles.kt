package com.termoak.app.data

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Files shared into the app from another one (the system's Share → Termoak):
 * first a host is picked, then its Files open with them waiting for "Upload
 * here" in the folder chosen.
 */
class IncomingFiles {
    private val _waiting = MutableStateFlow<List<Uri>>(emptyList())
    /** Shared, waiting for a host to be picked. */
    val waiting: StateFlow<List<Uri>> = _waiting
    @Volatile private var forFiles: List<Uri>? = null

    fun offer(uris: List<Uri>) {
        _waiting.value = uris
    }

    fun clear() {
        _waiting.value = emptyList()
    }

    /** A host was picked: the next Files screen takes them. */
    fun hostPicked() {
        forFiles = _waiting.value.takeIf { it.isNotEmpty() }
        _waiting.value = emptyList()
    }

    /** For the Files screen that opens: the files to upload (once). */
    fun take(): List<Uri>? = forFiles.also { forFiles = null }

    companion object {
        /** The files of a share (`ACTION_SEND` / `ACTION_SEND_MULTIPLE`); empty for anything else (shared text...). */
        fun urisOf(intent: Intent): List<Uri> {
            val out = mutableListOf<Uri>()
            when (intent.action) {
                Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { out += it }
                Intent.ACTION_SEND_MULTIPLE ->
                    IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { out += it }
                else -> return emptyList()
            }
            if (out.isEmpty()) {
                intent.clipData?.let { clip -> for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let { out += it } }
            }
            // Only documents shared by other apps (never our own files by a path).
            return out.filter { it.scheme == "content" }.distinct()
        }
    }
}
