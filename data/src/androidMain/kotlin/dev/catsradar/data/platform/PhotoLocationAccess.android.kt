package dev.catsradar.data.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

// Limited access taken in ACCESS_MEDIA_LOCATION's dialog grants that permission one-time only, but
// READ_MEDIA_VISUAL_USER_SELECTED for good, and MediaProvider counts the latter as access to photo locations.
fun Context.mayReadPhotoLocations(): Boolean =
    isGranted(Manifest.permission.ACCESS_MEDIA_LOCATION) || hasLimitedAccess()

private fun Context.hasLimitedAccess(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
        isGranted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)

private fun Context.isGranted(permission: String): Boolean =
    checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
