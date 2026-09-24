package dev.catsradar.presentation.viewer

sealed interface PhotoViewerEffect {
    data object Close : PhotoViewerEffect

    /** [grantRead] when the gallery may be handed the app's own read access to [uri]. */
    data class OpenInGallery(val uri: String, val grantRead: Boolean) : PhotoViewerEffect

    data object GalleryItemGone : PhotoViewerEffect
}
