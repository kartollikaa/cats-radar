package dev.catsradar.app.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.catsradar.presentation.detail.EncounterDetailEffect
import dev.catsradar.presentation.detail.EncounterDetailIntent
import dev.catsradar.presentation.detail.EncounterDetailStore
import dev.catsradar.ui.R
import dev.catsradar.ui.detail.EncounterDetailScreen
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Suppress("LongParameterList") // one collaborator per effect the screen has to carry out
internal fun handleEncounterDetailEffect(
    effect: EncounterDetailEffect,
    onNavigateBack: () -> Unit,
    onOpenPhoto: (PhotoViewer) -> Unit,
    onOpenMap: (catId: String) -> Unit,
    cameraLauncher: CameraLauncher,
    photoPickerLauncher: PhotoPickerLauncher,
    photoFailureReporter: PhotoFailureReporter,
    alreadyThereReporter: PhotoFailureReporter,
    captureDiscarder: CaptureDiscarder,
) {
    when (effect) {
        EncounterDetailEffect.NavigateBack -> onNavigateBack()
        is EncounterDetailEffect.OpenCamera -> cameraLauncher.launch()
        is EncounterDetailEffect.OpenPhotoPicker -> photoPickerLauncher.launch()
        is EncounterDetailEffect.OpenPhoto -> onOpenPhoto(PhotoViewer(effect.catId, effect.photoId))
        is EncounterDetailEffect.OpenMap -> onOpenMap(effect.catId)
        EncounterDetailEffect.PhotoNotAttached -> photoFailureReporter.report()
        EncounterDetailEffect.PhotoAlreadyThere -> alreadyThereReporter.report()
        is EncounterDetailEffect.DiscardCapture -> captureDiscarder.discard(effect.uri)
    }
}

@Composable
internal fun EncounterDetailDestination(
    key: EncounterDetail,
    contentPadding: PaddingValues,
    onOpenPhoto: (PhotoViewer) -> Unit,
    onOpenMap: (catId: String) -> Unit,
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {},
) {
    val store = koinViewModel<EncounterDetailStore> { parametersOf(key.id) }
    val state by store.state.collectAsStateWithLifecycle()
    val currentOnNavigateBack by rememberUpdatedState(onNavigateBack)
    val currentOnOpenPhoto by rememberUpdatedState(onOpenPhoto)
    val currentOnOpenMap by rememberUpdatedState(onOpenMap)
    val cameraLauncher = rememberCameraLauncher { uri -> store.dispatch(EncounterDetailIntent.PhotoTaken(key.id, uri)) }
    val photoPicker =
        rememberSinglePhotoPicker { uri -> store.dispatch(EncounterDetailIntent.PhotoPicked(key.id, uri)) }
    val photoFailureReporter = rememberPhotoFailureReporter(R.string.detail_photo_not_attached)
    val alreadyThereReporter = rememberPhotoFailureReporter(R.string.detail_photo_already_there)
    val captureDiscarder = rememberCaptureDiscarder()
    LaunchedEffect(store, cameraLauncher, photoPicker, photoFailureReporter, alreadyThereReporter, captureDiscarder) {
        store.effects.collect { effect ->
            handleEncounterDetailEffect(
                effect,
                onNavigateBack = { currentOnNavigateBack() },
                onOpenPhoto = { viewer -> currentOnOpenPhoto(viewer) },
                onOpenMap = { catId -> currentOnOpenMap(catId) },
                cameraLauncher = cameraLauncher,
                photoPickerLauncher = photoPicker,
                photoFailureReporter = photoFailureReporter,
                alreadyThereReporter = alreadyThereReporter,
                captureDiscarder = captureDiscarder,
            )
        }
    }
    EncounterDetailScreen(
        state = state,
        modifier = modifier,
        contentPadding = contentPadding,
        onBackClick = { store.dispatch(EncounterDetailIntent.BackClicked) },
        onDeleteClick = { store.dispatch(EncounterDetailIntent.DeleteClicked) },
        onUndoClick = { store.dispatch(EncounterDetailIntent.UndoClicked) },
        onCoatClick = { coat -> store.dispatch(EncounterDetailIntent.CoatPicked(key.id, coat)) },
        onTakePhotoClick = { store.dispatch(EncounterDetailIntent.TakePhotoClicked(key.id)) },
        onPickPhotoClick = { store.dispatch(EncounterDetailIntent.PickPhotoClicked(key.id)) },
        onPhotoClick = { photoId -> store.dispatch(EncounterDetailIntent.PhotoClicked(key.id, photoId)) },
        onCoordinatesClick = { store.dispatch(EncounterDetailIntent.CoordinatesClicked(key.id)) },
    )
}
