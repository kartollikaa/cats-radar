package dev.catsradar.presentation.viewer

sealed interface PhotoViewerEffect {
    data object Close : PhotoViewerEffect
}
