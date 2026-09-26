package dev.catsradar.app.navigation

import android.Manifest
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.catsradar.app.permission.LocationPermissionRequester
import dev.catsradar.app.permission.rememberNotificationPermissionRequest
import dev.catsradar.app.permission.rememberWalkingModeRequest
import dev.catsradar.app.photo.CameraRequest
import dev.catsradar.app.worker.ImportScheduler
import dev.catsradar.app.worker.LocationAttachScheduler
import dev.catsradar.app.worker.toCounterIntent
import dev.catsradar.domain.platform.Haptics
import dev.catsradar.presentation.counter.CounterIntent
import dev.catsradar.presentation.counter.CounterStore
import dev.catsradar.ui.R
import dev.catsradar.ui.counter.CounterScreen
import dev.catsradar.ui.counter.LocationHintAction
import kotlinx.collections.immutable.toImmutableList
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun CounterDestination(
    contentPadding: PaddingValues,
    cameraRequest: CameraRequest,
    modifier: Modifier = Modifier,
) {
    val store = koinViewModel<CounterStore>()
    val state by store.state.collectAsStateWithLifecycle()
    val haptics = koinInject<Haptics>()
    val locationAttachScheduler = koinInject<LocationAttachScheduler>()
    val locationPermissionRequester = rememberLocationPermissionRequester(store)
    val cameraLauncher = rememberCameraLauncher { shot -> store.dispatch(CounterIntent.PhotoCaptured(shot.uri)) }
    val photoFailureReporter = rememberPhotoFailureReporter(R.string.counter_photo_not_saved)
    val captureDiscarder = rememberCaptureDiscarder()
    val milestoneAnnouncer = rememberMilestoneAnnouncer()
    val importScheduler = koinInject<ImportScheduler>()
    val photoPickerLauncher = rememberPhotoPickerLauncher(store)
    val onWalkingModeChange = rememberWalkingModeRequest { enabled ->
        store.dispatch(CounterIntent.WalkingModeToggled(enabled))
    }
    ObserveImportWork(store, importScheduler)
    LaunchedEffect(store, cameraRequest.isPending) {
        if (cameraRequest.consume()) store.dispatch(CounterIntent.CameraClicked)
    }
    LaunchedEffect(
        store,
        haptics,
        locationAttachScheduler,
        locationPermissionRequester,
        cameraLauncher,
        photoPickerLauncher,
    ) {
        store.effects.collect { effect ->
            handleCounterEffect(
                effect,
                haptics,
                locationAttachScheduler,
                locationPermissionRequester,
                cameraLauncher,
                photoFailureReporter,
                captureDiscarder,
                milestoneAnnouncer,
                photoPickerLauncher,
                importScheduler,
            )
        }
    }
    CounterScreen(
        state = state,
        modifier = modifier.padding(contentPadding),
        onTallyClick = { store.dispatch(CounterIntent.TallyClicked) },
        onUndoClick = { store.dispatch(CounterIntent.UndoClicked) },
        onLocationHintAction = { action -> store.dispatch(action.toCounterIntent()) },
        onCameraClick = { store.dispatch(CounterIntent.CameraClicked) },
        onCoatTallyClick = { coat -> store.dispatch(CounterIntent.CoatTallyClicked(coat)) },
        onImportClick = { store.dispatch(CounterIntent.Import.Requested) },
        onUndoImportClick = { store.dispatch(CounterIntent.Import.UndoClicked) },
        onImportSummaryDismiss = { store.dispatch(CounterIntent.Import.SummaryDismissed) },
        onWalkingModeChange = onWalkingModeChange,
        onCoatPromptPick = { coat -> store.dispatch(CounterIntent.CoatPromptPicked(coat)) },
        onCoatPromptDismiss = { store.dispatch(CounterIntent.CoatPromptDismissed) },
    )
}

// The worker outlives this screen, so its state is read back rather than remembered: coming
// back mid-import shows the progress it has actually reached.
@Composable
private fun ObserveImportWork(store: CounterStore, importScheduler: ImportScheduler) {
    LaunchedEffect(store, importScheduler) {
        importScheduler.observe().collect { info ->
            info?.toCounterIntent()?.let(store::dispatch)
        }
    }
}

@Composable
private fun rememberLocationPermissionRequester(store: CounterStore): LocationPermissionRequester {
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val granted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        store.dispatch(CounterIntent.LocationPermissionResult(granted))
    }
    // A fresh lambda's identity would change every recomposition (every tap), which would restart
    // the effect collector and could drop an in-flight effect.
    return remember(permissionLauncher) {
        LocationPermissionRequester {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            )
        }
    }
}

@Composable
private fun rememberPhotoPickerLauncher(store: CounterStore): PhotoPickerLauncher {
    val notificationPermission = rememberNotificationPermissionRequest()
    return rememberGalleryImportPicker { uris ->
        // Asked for after the pick, not before it: a run the user has actually started is the only
        // moment a progress notification is worth a dialog, and a refusal still imports.
        if (uris.isNotEmpty()) notificationPermission()
        store.dispatch(CounterIntent.Import.PhotosPicked(uris.map(Uri::toString).toImmutableList()))
    }
}

@Composable
private fun rememberMilestoneAnnouncer(): MilestoneAnnouncer {
    val context = LocalContext.current
    val resources = LocalResources.current
    return remember(context, resources) {
        MilestoneAnnouncer { value ->
            val text = resources.getQuantityString(R.plurals.counter_milestone, value, value)
            Toast.makeText(context, text, Toast.LENGTH_LONG).show()
        }
    }
}

private fun LocationHintAction.toCounterIntent(): CounterIntent = when (this) {
    LocationHintAction.GRANT -> CounterIntent.GrantLocationClicked
    LocationHintAction.DISMISS -> CounterIntent.LocationPermissionHintDismissed
}
