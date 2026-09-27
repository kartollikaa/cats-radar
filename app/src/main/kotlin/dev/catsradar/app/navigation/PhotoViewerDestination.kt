package dev.catsradar.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.catsradar.presentation.viewer.PhotoViewerEffect
import dev.catsradar.presentation.viewer.PhotoViewerIntent
import dev.catsradar.presentation.viewer.PhotoViewerStore
import dev.catsradar.ui.R
import dev.catsradar.ui.viewer.PhotoViewerScreen
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

internal fun handlePhotoViewerEffect(
    effect: PhotoViewerEffect,
    onClose: () -> Unit,
    galleryOpener: GalleryOpener,
    reporters: PhotoViewerReporters,
) {
    when (effect) {
        PhotoViewerEffect.Close -> onClose()
        is PhotoViewerEffect.OpenInGallery -> if (!galleryOpener.open(effect.uri)) reporters.noGalleryApp.report()
        PhotoViewerEffect.GalleryItemGone -> reporters.galleryGone.report()
        PhotoViewerEffect.RemovePhotoFailed -> reporters.removePhotoFailed.report()
    }
}

internal data class PhotoViewerReporters(
    val galleryGone: MessageReporter,
    val noGalleryApp: MessageReporter,
    val removePhotoFailed: MessageReporter,
)

@Composable
internal fun PhotoViewerDestination(key: PhotoViewer, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val store = koinViewModel<PhotoViewerStore> { parametersOf(key.encounterId, key.photoId) }
    val state by store.state.collectAsStateWithLifecycle()
    val close by rememberUpdatedState(onClose)
    val galleryOpener = rememberGalleryOpener()
    val galleryGoneReporter = rememberMessageReporter(R.string.viewer_gallery_gone)
    val noGalleryAppReporter = rememberMessageReporter(R.string.viewer_no_gallery_app)
    val removePhotoFailedReporter = rememberMessageReporter(R.string.viewer_remove_failed)
    val reporters = PhotoViewerReporters(galleryGoneReporter, noGalleryAppReporter, removePhotoFailedReporter)
    LaunchedEffect(store, galleryOpener, galleryGoneReporter, noGalleryAppReporter, removePhotoFailedReporter) {
        store.effects.collect { effect ->
            handlePhotoViewerEffect(
                effect,
                onClose = { close() },
                galleryOpener = galleryOpener,
                reporters = reporters,
            )
        }
    }
    PhotoViewerScreen(
        state = state,
        modifier = modifier,
        onBackClick = { store.dispatch(PhotoViewerIntent.BackClicked) },
        onOpenInGalleryClick = { photoId -> store.dispatch(PhotoViewerIntent.OpenInGalleryClicked(photoId)) },
        onRemovePhotoClick = { photoId -> store.dispatch(PhotoViewerIntent.RemovePhotoClicked(photoId)) },
        onCancelPhotoRemoval = { store.dispatch(PhotoViewerIntent.RemovePhotoCancelled) },
        onConfirmPhotoRemoval = { store.dispatch(PhotoViewerIntent.RemovePhotoConfirmed) },
    )
}
