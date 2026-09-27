package dev.catsradar.data.platform

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import java.io.FileInputStream
import java.io.InputStream

internal fun Context.openPhotoStream(uri: String): InputStream = when {
    uri.startsWith("content://") || uri.startsWith("file://") -> openContentStream(Uri.parse(uri))
    else -> FileInputStream(uri)
}

private fun Context.openContentStream(uri: Uri): InputStream =
    openOriginalOrNull(uri)
        ?: contentResolver.openInputStream(uri)
        ?: error("no stream for $uri")

// MediaStore strips a photo's GPS tags unless the reader has access to photo locations and asks for the
// original, which some of its URIs refuse outright.
private fun Context.openOriginalOrNull(uri: Uri): InputStream? {
    val mayAskForOriginal = uri.authority == MediaStore.AUTHORITY && mayReadPhotoLocations()
    if (!mayAskForOriginal) return null
    return runCatching { contentResolver.openInputStream(MediaStore.setRequireOriginal(uri)) }.getOrNull()
}
