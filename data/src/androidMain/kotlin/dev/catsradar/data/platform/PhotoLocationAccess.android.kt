package dev.catsradar.data.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

// Taking limited access in ACCESS_MEDIA_LOCATION's dialog grants READ_MEDIA_VISUAL_USER_SELECTED, which
// MediaProvider then counts as access to photo locations.
fun Context.mayReadPhotoLocations(): Boolean =
    isGranted(Manifest.permission.ACCESS_MEDIA_LOCATION) || hasLimitedAccess()

private fun Context.hasLimitedAccess(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
        isGranted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)

private fun Context.isGranted(permission: String): Boolean =
    checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
