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
    fun open(uri: String, grantRead: Boolean): Boolean
}

// Granting access the app does not hold throws SecurityException, so the grant is only ever asked for.
internal fun Context.openInGallery(uri: String, grantRead: Boolean): Boolean {
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri.toUri(), IMAGE_TYPE)
    if (grantRead) intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    return try {
        startActivity(intent)
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
