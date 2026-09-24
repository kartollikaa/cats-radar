package dev.catsradar.app.navigation

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri

internal fun interface GalleryOpener {
    /** False when no app on the phone can show an image. */
    fun open(uri: String): Boolean
}

// Granting read access the app does not hold (any longer) throws SecurityException; the gallery can
// still open the item with its own access, so the view goes out again without the grant.
internal fun Context.openInGallery(uri: String): Boolean {
    val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri.toUri(), IMAGE_TYPE)
    return try {
        try {
            startActivity(Intent(view).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        } catch (_: SecurityException) {
            startActivity(view)
        }
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

@Composable
internal fun rememberGalleryOpener(): GalleryOpener {
    val context = LocalContext.current
    return remember(context) { GalleryOpener(context::openInGallery) }
}

private const val IMAGE_TYPE = "image/*"
