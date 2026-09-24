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
    galleryGoneReporter: PhotoFailureReporter,
    noGalleryAppReporter: PhotoFailureReporter,
) {
    when (effect) {
        PhotoViewerEffect.Close -> onClose()
        is PhotoViewerEffect.OpenInGallery ->
            if (!galleryOpener.open(effect.uri, effect.grantRead)) noGalleryAppReporter.report()
        PhotoViewerEffect.GalleryItemGone -> galleryGoneReporter.report()
    }
}

@Composable
internal fun PhotoViewerDestination(key: PhotoViewer, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val store = koinViewModel<PhotoViewerStore> { parametersOf(key.encounterId) }
    val state by store.state.collectAsStateWithLifecycle()
    val close by rememberUpdatedState(onClose)
    val galleryOpener = rememberGalleryOpener()
    val galleryGoneReporter = rememberPhotoFailureReporter(R.string.viewer_gallery_gone)
    val noGalleryAppReporter = rememberPhotoFailureReporter(R.string.viewer_no_gallery_app)
    LaunchedEffect(store, galleryOpener, galleryGoneReporter, noGalleryAppReporter) {
        store.effects.collect { effect ->
            handlePhotoViewerEffect(
                effect,
                onClose = { close() },
                galleryOpener = galleryOpener,
                galleryGoneReporter = galleryGoneReporter,
                noGalleryAppReporter = noGalleryAppReporter,
            )
        }
    }
    PhotoViewerScreen(
        state = state,
        modifier = modifier,
        onBackClick = { store.dispatch(PhotoViewerIntent.BackClicked) },
        onOpenInGalleryClick = { store.dispatch(PhotoViewerIntent.OpenInGalleryClicked) },
    )
}
