package dev.catsradar.presentation.viewer

sealed interface PhotoViewerEffect {
    data object Close : PhotoViewerEffect
    data class OpenInGallery(val uri: String) : PhotoViewerEffect
    data object GalleryItemGone : PhotoViewerEffect
}
