package dev.catsradar.app.permission

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

/**
 * Asks for `POST_NOTIFICATIONS`, calling [onResult] with the answer. Already granted and already
 * refused both answer without a dialog.
 */
@Composable
internal fun rememberNotificationPermissionRequest(onResult: (Boolean) -> Unit = {}): () -> Unit {
    // A fresh lambda's identity would change every recomposition, and the returned request is held
    // across them.
    val currentOnResult by rememberUpdatedState(onResult)
    // POST_NOTIFICATIONS does not exist before API 33; there is nothing to ask for.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return remember { { currentOnResult(true) } }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        currentOnResult(granted)
    }
    return remember(launcher) { { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) } }
}

/**
 * The walking mode switch. Turning it on is refused without permission to post: the notification is
 * the whole feature, and a control reading "on" over an empty shade would be a lie.
 */
@Composable
internal fun rememberWalkingModeRequest(onChange: (Boolean) -> Unit): (Boolean) -> Unit {
    val currentOnChange by rememberUpdatedState(onChange)
    val request = rememberNotificationPermissionRequest { granted -> if (granted) currentOnChange(true) }
    return remember(request) { { enabled -> if (enabled) request() else currentOnChange(false) } }
}
