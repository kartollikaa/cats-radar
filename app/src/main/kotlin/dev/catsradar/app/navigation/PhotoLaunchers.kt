package dev.catsradar.app.navigation

import android.Manifest
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import dev.catsradar.app.photo.CaptureTarget
import dev.catsradar.app.photo.PendingCaptures
import dev.catsradar.app.photo.PickGalleryPhotos
import dev.catsradar.app.photo.holdReadAccess
import dev.catsradar.domain.Tuning

/** Opens the camera; it owns the file the camera writes to. */
internal fun interface CameraLauncher {
    fun launch()
}

internal fun interface MessageReporter {
    fun report()
}

internal fun interface CaptureDiscarder {
    fun discard(uri: String)
}

internal fun interface PhotoPickerLauncher {
    fun launch()
}

@Composable
internal fun rememberCameraLauncher(onResult: (String?) -> Unit): CameraLauncher {
    val context = LocalContext.current
    // Saveable: the process can die while a camera is in front, and its result says nothing about
    // where it wrote.
    val pending = rememberSaveable(saver = PendingCaptures.Saver) { PendingCaptures() }
    val resultLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val target = pending.answered()
        onResult(target.takeIf { saved })
        if (!saved && target != null) CaptureTarget.discard(context, target)
    }
    return remember(resultLauncher, context, pending) {
        CameraLauncher {
            val target = CaptureTarget.newUri(context)
            pending.launched(target.toString())
            resultLauncher.launch(target)
        }
    }
}

@Composable
internal fun rememberSinglePhotoPicker(onResult: (String?) -> Unit): PhotoPickerLauncher {
    val resultLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        onResult(uri?.toString())
    }
    return remember(resultLauncher) {
        PhotoPickerLauncher {
            resultLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }
}

@Composable
internal fun rememberMessageReporter(@StringRes messageRes: Int): MessageReporter {
    val context = LocalContext.current
    return remember(context, messageRes) {
        MessageReporter { Toast.makeText(context, messageRes, Toast.LENGTH_SHORT).show() }
    }
}

@Composable
internal fun rememberCaptureDiscarder(): CaptureDiscarder {
    val context = LocalContext.current
    return remember(context) { CaptureDiscarder { uri -> CaptureTarget.discard(context, uri) } }
}

/** Asks for the location of photos, then opens the gallery whatever the answer. */
@Composable
internal fun rememberGalleryImportPicker(onResult: (List<Uri>) -> Unit): PhotoPickerLauncher {
    val resolver = LocalContext.current.contentResolver
    val galleryLauncher = rememberLauncherForActivityResult(PickGalleryPhotos(Tuning.IMPORT_BATCH_MAX)) { uris ->
        resolver.holdReadAccess(uris)
        onResult(uris)
    }
    // Before the gallery, not after the pick: a photo opened while the app lacks it has already lost its GPS.
    val mediaLocationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        galleryLauncher.launch(Unit)
    }
    return remember(mediaLocationLauncher) {
        PhotoPickerLauncher { mediaLocationLauncher.launch(Manifest.permission.ACCESS_MEDIA_LOCATION) }
    }
}
