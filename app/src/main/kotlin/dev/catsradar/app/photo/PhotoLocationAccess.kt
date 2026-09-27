package dev.catsradar.app.photo

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore

// Taking limited access in ACCESS_MEDIA_LOCATION's dialog grants READ_MEDIA_VISUAL_USER_SELECTED, which
// MediaProvider then counts as access to photo locations.
internal fun Context.mayReadPhotoLocations(): Boolean =
    isGranted(Manifest.permission.ACCESS_MEDIA_LOCATION) || hasLimitedAccess()

/** The latest photos the user shared through limited access, up to [maxItems], leaving out the ones this app saved. */
internal fun Context.photosSharedThroughLimitedAccess(maxItems: Int): List<Uri> {
    if (!hasLimitedAccess()) return emptyList()
    val images = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.OWNER_PACKAGE_NAME)
    val args = Bundle().apply {
        // Android 14 cannot narrow the query to the latest selection: every photo shared so far comes back.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            putBoolean(MediaStore.QUERY_ARG_LATEST_SELECTION_ONLY, true)
        }
    }
    return runCatching {
        contentResolver.query(images, projection, args, null)?.use { cursor ->
            buildList {
                while (size < maxItems && cursor.moveToNext()) {
                    if (cursor.getString(1) != packageName) add(ContentUris.withAppendedId(images, cursor.getLong(0)))
                }
            }
        }
    }.getOrNull().orEmpty()
}

private fun Context.hasLimitedAccess(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
        isGranted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)

private fun Context.isGranted(permission: String): Boolean =
    checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
