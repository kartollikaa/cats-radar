package dev.catsradar.presentation.viewer

sealed interface PhotoViewerState {
    data object Loading : PhotoViewerState

    /** [photoPath] is the absolute path of the app's full copy. */
    data class Showing(
        val photoPath: String,
        val timeLabel: String,
        val dayLabel: String,
        val opensInGallery: Boolean = false,
    ) : PhotoViewerState
}
