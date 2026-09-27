package dev.catsradar.presentation.viewer

sealed interface PhotoViewerIntent {
    data object BackClicked : PhotoViewerIntent
    data class OpenInGalleryClicked(val photoId: String) : PhotoViewerIntent
    data class RemovePhotoClicked(val photoId: String) : PhotoViewerIntent
    data object RemovePhotoCancelled : PhotoViewerIntent
    data object RemovePhotoConfirmed : PhotoViewerIntent
}
