package dev.catsradar.presentation.viewer

import kotlinx.collections.immutable.ImmutableList

sealed interface PhotoViewerState {
    data object Loading : PhotoViewerState

    /** The cat's photos, oldest first; [firstPage] is the one the viewer opens on. */
    data class Showing(
        val photos: ImmutableList<ViewerPhoto>,
        val firstPage: Int,
        val timeLabel: String,
        val dayLabel: String,
        val removingPhotoId: String? = null,
        val removalInFlight: Boolean = false,
    ) : PhotoViewerState
}

/** [path] is the absolute path of the app's full copy. */
data class ViewerPhoto(val id: String, val path: String, val opensInGallery: Boolean = false)
