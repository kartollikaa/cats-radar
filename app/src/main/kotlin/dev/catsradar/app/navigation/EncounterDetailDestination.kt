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
    onOpenPhoto: (photoId: String) -> Unit,
    onOpenMap: () -> Unit,
    cameraLauncher: CameraLauncher,
    photoPickerLauncher: PhotoPickerLauncher,
    photoFailureReporter: PhotoFailureReporter,
    alreadyThereReporter: PhotoFailureReporter,
    notAttachedCountReporter: PhotoCountReporter,
    allAlreadyThereReporter: PhotoFailureReporter,
    captureDiscarder: CaptureDiscarder,
) {
    when (effect) {
        EncounterDetailEffect.NavigateBack -> onNavigateBack()
        EncounterDetailEffect.OpenCamera -> cameraLauncher.launch()
        EncounterDetailEffect.OpenPhotoPicker -> photoPickerLauncher.launch()
        is EncounterDetailEffect.OpenPhoto -> onOpenPhoto(effect.photoId)
        EncounterDetailEffect.OpenMap -> onOpenMap()
        EncounterDetailEffect.PhotoNotAttached -> photoFailureReporter.report()
        is EncounterDetailEffect.PhotosNotAttached -> notAttachedCountReporter.report(effect.count)
        EncounterDetailEffect.PhotoAlreadyThere -> alreadyThereReporter.report()
        EncounterDetailEffect.PhotosAlreadyThere -> allAlreadyThereReporter.report()
        is EncounterDetailEffect.DiscardCapture -> captureDiscarder.discard(effect.uri)
    }
}

@Composable
internal fun EncounterDetailDestination(
    key: EncounterDetail,
    contentPadding: PaddingValues,
    onOpenPhoto: (photoId: String) -> Unit,
    onOpenMap: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {},
) {
    val store = koinViewModel<EncounterDetailStore> { parametersOf(key.id) }
    val state by store.state.collectAsStateWithLifecycle()
    val currentOnNavigateBack by rememberUpdatedState(onNavigateBack)
    val currentOnOpenPhoto by rememberUpdatedState(onOpenPhoto)
    val currentOnOpenMap by rememberUpdatedState(onOpenMap)
    val cameraLauncher = rememberCameraLauncher { uri -> store.dispatch(EncounterDetailIntent.PhotoTaken(uri)) }
    val photoPicker = rememberSeveralPhotosPicker { uris -> store.dispatch(EncounterDetailIntent.PhotosPicked(uris)) }
    val photoFailureReporter = rememberPhotoFailureReporter(R.string.detail_photo_not_attached)
    val alreadyThereReporter = rememberPhotoFailureReporter(R.string.detail_photo_already_there)
    val notAttachedCountReporter = rememberPhotoCountReporter(R.plurals.detail_photos_not_attached)
    val allAlreadyThereReporter = rememberPhotoFailureReporter(R.string.detail_photos_already_there)
    val captureDiscarder = rememberCaptureDiscarder()
    LaunchedEffect(
        store,
        cameraLauncher,
        photoPicker,
        photoFailureReporter,
        alreadyThereReporter,
        notAttachedCountReporter,
        allAlreadyThereReporter,
        captureDiscarder,
    ) {
        store.effects.collect { effect ->
            handleEncounterDetailEffect(
                effect,
                onNavigateBack = { currentOnNavigateBack() },
                onOpenPhoto = { photoId -> currentOnOpenPhoto(photoId) },
                onOpenMap = { currentOnOpenMap() },
                cameraLauncher = cameraLauncher,
                photoPickerLauncher = photoPicker,
                photoFailureReporter = photoFailureReporter,
                alreadyThereReporter = alreadyThereReporter,
                notAttachedCountReporter = notAttachedCountReporter,
                allAlreadyThereReporter = allAlreadyThereReporter,
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
        onCoatClick = { coat -> store.dispatch(EncounterDetailIntent.CoatPicked(coat)) },
        onTakePhotoClick = { store.dispatch(EncounterDetailIntent.TakePhotoClicked) },
        onPickPhotoClick = { store.dispatch(EncounterDetailIntent.PickPhotoClicked) },
        onPhotoClick = { photoId -> store.dispatch(EncounterDetailIntent.PhotoClicked(photoId)) },
        onCoordinatesClick = { store.dispatch(EncounterDetailIntent.CoordinatesClicked) },
    )
}
