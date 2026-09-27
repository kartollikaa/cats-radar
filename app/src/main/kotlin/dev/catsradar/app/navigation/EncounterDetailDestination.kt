package dev.catsradar.app.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.catsradar.presentation.detail.EncounterDetailEffect
import dev.catsradar.presentation.detail.EncounterDetailIntent
import dev.catsradar.presentation.detail.EncounterDetailState
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
    onOpenLocationPicker: (catId: String) -> Unit,
    cameraLauncher: CameraLauncher,
    photoPickerLauncher: CatPhotosPickerLauncher,
    photoFailureReporter: MessageReporter,
    alreadyThereReporter: MessageReporter,
    notAttachedCountReporter: PhotoCountReporter,
    allAlreadyThereReporter: MessageReporter,
    captureDiscarder: CaptureDiscarder,
) {
    when (effect) {
        EncounterDetailEffect.NavigateBack -> onNavigateBack()
        is EncounterDetailEffect.OpenCamera -> cameraLauncher.launch(effect.catId)
        is EncounterDetailEffect.OpenPhotoPicker -> photoPickerLauncher.launch(effect.catId)
        is EncounterDetailEffect.OpenPhoto -> onOpenPhoto(PhotoViewer(effect.catId, effect.photoId))
        is EncounterDetailEffect.OpenMap -> onOpenMap(effect.catId)
        is EncounterDetailEffect.OpenLocationPicker -> onOpenLocationPicker(effect.catId)
        EncounterDetailEffect.PhotoNotAttached -> photoFailureReporter.report()
        is EncounterDetailEffect.PhotosNotAttached -> notAttachedCountReporter.report(effect.count)
        EncounterDetailEffect.PhotoAlreadyThere -> alreadyThereReporter.report()
        EncounterDetailEffect.PhotosAlreadyThere -> allAlreadyThereReporter.report()
        is EncounterDetailEffect.DiscardCapture -> captureDiscarder.discard(effect.uri)
    }
}

// A shot naming no cat is dropped: a photo on a guessed cat can never be removed.
internal fun dispatchCameraShot(shot: CameraShot, dispatch: (EncounterDetailIntent) -> Unit) {
    shot.catId?.let { dispatch(EncounterDetailIntent.PhotoTaken(it, shot.uri)) }
}

/** [key]'s Store; rebuilt after the process died, it starts on the cat on screen, saved with the screen. */
@Composable
private fun rememberEncounterDetailStore(key: EncounterDetail): EncounterDetailStore {
    var shownId by rememberSaveable { mutableStateOf<String?>(null) }
    val store = koinViewModel<EncounterDetailStore> { parametersOf(key.id, shownId) }
    LaunchedEffect(store) {
        store.state.collect { state -> (state as? EncounterDetailState.Loaded)?.let { shownId = it.currentId } }
    }
    return store
}

@Composable
internal fun EncounterDetailDestination(
    key: EncounterDetail,
    contentPadding: PaddingValues,
    onOpenPhoto: (PhotoViewer) -> Unit,
    onOpenMap: (catId: String) -> Unit,
    onOpenLocationPicker: (catId: String) -> Unit,
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {},
) {
    val store = rememberEncounterDetailStore(key)
    val state by store.state.collectAsStateWithLifecycle()
    val currentOnNavigateBack by rememberUpdatedState(onNavigateBack)
    val currentOnOpenPhoto by rememberUpdatedState(onOpenPhoto)
    val currentOnOpenMap by rememberUpdatedState(onOpenMap)
    val currentOnOpenLocationPicker by rememberUpdatedState(onOpenLocationPicker)
    val cameraLauncher = rememberCameraLauncher { shot -> dispatchCameraShot(shot, store::dispatch) }
    val photoPicker = rememberCatPhotosPicker { picked ->
        store.dispatch(EncounterDetailIntent.PhotosPicked(picked.catId, picked.uris))
    }
    val photoFailureReporter = rememberMessageReporter(R.string.detail_photo_not_attached)
    val alreadyThereReporter = rememberMessageReporter(R.string.detail_photo_already_there)
    val notAttachedCountReporter = rememberPhotoCountReporter(R.plurals.detail_photos_not_attached)
    val allAlreadyThereReporter = rememberMessageReporter(R.string.detail_photos_already_there)
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
                onOpenPhoto = { viewer -> currentOnOpenPhoto(viewer) },
                onOpenMap = { catId -> currentOnOpenMap(catId) },
                onOpenLocationPicker = { catId -> currentOnOpenLocationPicker(catId) },
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
    DispatchingDetailScreen(state, store::dispatch, contentPadding, modifier)
}

@Composable
private fun DispatchingDetailScreen(
    state: EncounterDetailState,
    dispatch: (EncounterDetailIntent) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    EncounterDetailScreen(
        state = state,
        modifier = modifier,
        contentPadding = contentPadding,
        onBackClick = { dispatch(EncounterDetailIntent.BackClicked) },
        onPageSettle = { catId -> dispatch(EncounterDetailIntent.PageSettled(catId)) },
        onPhotoCatClick = { catId -> dispatch(EncounterDetailIntent.PhotoCatClicked(catId)) },
        onDeleteClick = { dispatch(EncounterDetailIntent.DeleteClicked) },
        onUndoClick = { dispatch(EncounterDetailIntent.UndoClicked) },
        onCoatClick = { pick -> dispatch(EncounterDetailIntent.CoatPicked(pick.catId, pick.coat)) },
        onTakePhotoClick = { catId -> dispatch(EncounterDetailIntent.TakePhotoClicked(catId)) },
        onPickPhotoClick = { catId -> dispatch(EncounterDetailIntent.PickPhotoClicked(catId)) },
        onPhotoClick = { tap -> dispatch(EncounterDetailIntent.PhotoClicked(tap.catId, tap.photoId)) },
        onCoordinatesClick = { catId -> dispatch(EncounterDetailIntent.CoordinatesClicked(catId)) },
        onSetLocationClick = { catId -> dispatch(EncounterDetailIntent.SetLocationClicked(catId)) },
    )
}
