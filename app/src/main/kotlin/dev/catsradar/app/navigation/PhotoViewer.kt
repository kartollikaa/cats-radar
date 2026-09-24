package dev.catsradar.app.navigation

import androidx.compose.ui.window.DialogProperties
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.DialogSceneStrategy
import kotlinx.serialization.Serializable

@Serializable
data class PhotoViewer(val encounterId: String) : NavKey

internal fun photoViewerMetadata(): Map<String, Any> = DialogSceneStrategy.dialog(
    DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
)
