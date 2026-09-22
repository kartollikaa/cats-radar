package dev.catsradar.app.photo

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import java.io.File

private const val CAPTURES_DIRECTORY = "captures"

/**
 * Where the camera writes an original before the app has made its own copies of it.
 *
 * The cache directory, because the original is temporary: once [dev.catsradar.domain.usecase.LogPhoto]
 * has stored a copy and, if the user wants it, handed the original to the gallery, nothing needs it.
 */
object CaptureTarget {

    fun newUri(context: Context): Uri {
        val directory = File(context.cacheDir, CAPTURES_DIRECTORY).apply { mkdirs() }
        val file = File(directory, "capture-${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /** Removes originals the camera wrote; a cancelled capture leaves one nothing will ever read. */
    fun clear(context: Context) {
        File(context.cacheDir, CAPTURES_DIRECTORY).listFiles()?.forEach { it.delete() }
    }

    fun discard(context: Context, uri: String) {
        runCatching { context.contentResolver.delete(uri.toUri(), null, null) }
    }
}
