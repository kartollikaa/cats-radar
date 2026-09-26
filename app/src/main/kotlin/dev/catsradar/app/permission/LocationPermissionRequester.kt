package dev.catsradar.app.permission

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

fun interface LocationPermissionRequester {
    fun request()
}

/** Asks for either location permission; [onResult] hears whether one of them is granted. */
@Composable
fun rememberLocationPermissionRequester(onResult: (granted: Boolean) -> Unit): LocationPermissionRequester {
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        onResult(
            results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                results[Manifest.permission.ACCESS_COARSE_LOCATION] == true,
        )
    }
    // A fresh lambda's identity would change every recomposition (every tap), which would restart
    // the effect collector and could drop an in-flight effect.
    return remember(permissionLauncher) {
        LocationPermissionRequester {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            )
        }
    }
}
