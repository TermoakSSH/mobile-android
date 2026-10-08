package com.termoak.app.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Session recordings (asciicast `.cast` files) handed to other apps to
 * share or save them, as on iOS: from a server session's download or from
 * the one a terminal of the phone is writing.
 */
internal object Recordings {
    private const val MIME = "application/x-asciicast"

    /** A new, empty folder for one recording in the shared cache (what the FileProvider hands over). */
    fun folder(context: Context): File {
        val root = File(context.cacheDir, "recordings")
        // The previous ones were already handed over.
        root.listFiles()?.forEach { it.deleteRecursively() }
        return File(root, java.util.UUID.randomUUID().toString()).apply { mkdirs() }
    }

    /** `title.cast` without characters that don't fit a file name (the session id if nothing is left). */
    fun fileName(title: String, fallback: String): String {
        val safe = title.replace(Regex("[/\\\\:*?\"<>|]"), "-").trim()
        return (safe.ifEmpty { fallback }) + ".cast"
    }

    /** Copies the recording at [path] (what has been recorded so far) and shares it; `false` if it can't. */
    fun shareCopy(context: Context, path: String, title: String): Boolean {
        val source = File(path)
        if (!source.isFile) return false
        val target = File(folder(context), fileName(title, source.nameWithoutExtension))
        if (runCatching { source.copyTo(target, overwrite = true) }.isFailure) return false
        return share(context, target)
    }

    /** Hands [file] to another app (share, save to Drive or Files...); `false` if none can. */
    fun share(context: Context, file: File): Boolean {
        val uri = runCatching { FileProvider.getUriForFile(context, "${context.packageName}.files", file) }.getOrNull() ?: return false
        val intent = Intent(Intent.ACTION_SEND).setType(MIME).putExtra(Intent.EXTRA_STREAM, uri)
            .apply { clipData = ClipData.newRawUri(file.name, uri) }
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return try {
            context.startActivity(Intent.createChooser(intent, file.name))
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
}
