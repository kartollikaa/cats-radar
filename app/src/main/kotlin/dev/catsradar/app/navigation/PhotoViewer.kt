package dev.catsradar.app.navigation

import androidx.compose.ui.window.DialogProperties
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.DialogSceneStrategy
import kotlinx.serialization.Serializable

/** Opens on [photoId], or on the cat's cover when it is null: a key saved before cats had several photos has none. */
@Serializable
data class PhotoViewer(val encounterId: String, val photoId: String? = null) : NavKey

internal fun photoViewerMetadata(): Map<String, Any> = DialogSceneStrategy.dialog(
    DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
)
