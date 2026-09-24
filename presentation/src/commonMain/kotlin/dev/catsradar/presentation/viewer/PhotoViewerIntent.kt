package dev.catsradar.presentation.viewer

sealed interface PhotoViewerIntent {
    data object BackClicked : PhotoViewerIntent
}
