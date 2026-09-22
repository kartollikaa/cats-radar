package dev.catsradar.app.navigation

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dev.catsradar.app.permission.LocationPermissionRequester
import dev.catsradar.app.photo.CaptureTarget
import dev.catsradar.app.worker.LocationAttachScheduler
import dev.catsradar.domain.platform.Haptics
import dev.catsradar.presentation.counter.CounterIntent
import dev.catsradar.presentation.counter.CounterStore
import dev.catsradar.presentation.detail.EncounterDetailEffect
import dev.catsradar.presentation.detail.EncounterDetailIntent
import dev.catsradar.presentation.detail.EncounterDetailStore
import dev.catsradar.presentation.encounters.EncountersStore
import dev.catsradar.ui.R
import dev.catsradar.ui.counter.CounterScreen
import dev.catsradar.ui.counter.LocationHintAction
import dev.catsradar.ui.detail.EncounterDetailScreen
import dev.catsradar.ui.encounters.EncountersScreen
import dev.catsradar.ui.navigation.CatsRadarBottomBar
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun CatsRadarNavHost(modifier: Modifier = Modifier) {
    val backStack = rememberBottomNavBackStack()

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            CatsRadarBottomBar(
                selectedTab = backStack.selectedTab,
                onTabSelect = { tab -> backStack.selectTab(tab) },
            )
        },
    ) { innerPadding ->
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.popOrNull() },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider {
                entry<Counter> { CounterDestination(contentPadding = innerPadding) }
                entry<Encounters> {
                    EncountersDestination(
                        contentPadding = innerPadding,
                        onRowClick = { id -> backStack.push(EncounterDetail(id)) },
                    )
                }
                entry<EncounterDetail> { key ->
                    EncounterDetailDestination(
                        key = key,
                        contentPadding = innerPadding,
                        onNavigateBack = { backStack.popOrNull() },
                    )
                }
            },
        )
    }
}

@Composable
private fun CounterDestination(contentPadding: PaddingValues, modifier: Modifier = Modifier) {
    val store = koinViewModel<CounterStore>()
    val state by store.state.collectAsStateWithLifecycle()
    val haptics = koinInject<Haptics>()
    val locationAttachScheduler = koinInject<LocationAttachScheduler>()
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val granted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        store.dispatch(CounterIntent.LocationPermissionResult(granted))
    }
    // A fresh lambda's identity would change every recomposition (every tap), which would restart
    // the effect collector below and could drop an in-flight effect.
    val locationPermissionRequester = remember(permissionLauncher) {
        LocationPermissionRequester {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            )
        }
    }
    val context = LocalContext.current
    val cameraLauncher = rememberCameraLauncher(store)
    val photoFailureReporter = remember(context) {
        PhotoFailureReporter {
            Toast.makeText(context, R.string.counter_photo_not_saved, Toast.LENGTH_SHORT).show()
        }
    }
    val captureDiscarder = remember(context) { CaptureDiscarder { uri -> CaptureTarget.discard(context, uri) } }
    LaunchedEffect(
        store,
        haptics,
        locationAttachScheduler,
        locationPermissionRequester,
        cameraLauncher,
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
    )
}

@Composable
private fun rememberCameraLauncher(store: CounterStore): CameraLauncher {
    val context = LocalContext.current
    // rememberSaveable: the process can die while the camera app is in front, and the result
    // arrives with nothing but this URI to say where the original was written.
    var captureUri by rememberSaveable { mutableStateOf<String?>(null) }
    val resultLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        store.dispatch(CounterIntent.PhotoCaptured(captureUri.takeIf { saved }))
        captureUri = null
        if (!saved) CaptureTarget.clear(context)
    }
    return remember(resultLauncher, context) {
        CameraLauncher {
            val target = CaptureTarget.newUri(context)
            captureUri = target.toString()
            resultLauncher.launch(target)
        }
    }
}

private fun LocationHintAction.toCounterIntent(): CounterIntent = when (this) {
    LocationHintAction.GRANT -> CounterIntent.GrantLocationClicked
    LocationHintAction.DISMISS -> CounterIntent.LocationPermissionHintDismissed
}

@Composable
private fun EncountersDestination(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    onRowClick: (String) -> Unit = {},
) {
    val store = koinViewModel<EncountersStore>()
    val state by store.state.collectAsStateWithLifecycle()
    EncountersScreen(state = state, modifier = modifier, contentPadding = contentPadding, onRowClick = onRowClick)
}

@Composable
private fun EncounterDetailDestination(
    key: EncounterDetail,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {},
) {
    val store = koinViewModel<EncounterDetailStore> { parametersOf(key.id) }
    val state by store.state.collectAsStateWithLifecycle()
    val currentOnNavigateBack by rememberUpdatedState(onNavigateBack)
    LaunchedEffect(store) {
        store.effects.collect { effect ->
            when (effect) {
                EncounterDetailEffect.NavigateBack -> currentOnNavigateBack()
            }
        }
    }
    EncounterDetailScreen(
        state = state,
        modifier = modifier,
        contentPadding = contentPadding,
        onDeleteClick = { store.dispatch(EncounterDetailIntent.DeleteClicked) },
        onUndoClick = { store.dispatch(EncounterDetailIntent.UndoClicked) },
    )
}
