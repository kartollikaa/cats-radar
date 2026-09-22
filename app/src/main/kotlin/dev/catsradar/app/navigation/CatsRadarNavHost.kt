package dev.catsradar.app.navigation

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dev.catsradar.app.permission.LocationPermissionRequester
import dev.catsradar.app.worker.LocationAttachScheduler
import dev.catsradar.domain.platform.Haptics
import dev.catsradar.presentation.counter.CounterIntent
import dev.catsradar.presentation.counter.CounterStore
import dev.catsradar.ui.counter.CounterScreen
import dev.catsradar.ui.counter.LocationHintAction
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun CatsRadarNavHost() {
    val backStack = rememberNavBackStack(Counter)

    NavDisplay(
        backStack = backStack,
        onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<Counter> {
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
                val locationPermissionRequester = LocationPermissionRequester {
                    permissionLauncher.launch(
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                    )
                }
                LaunchedEffect(store, haptics, locationAttachScheduler, locationPermissionRequester) {
                    store.effects.collect { effect ->
                        handleCounterEffect(effect, haptics, locationAttachScheduler, locationPermissionRequester)
                    }
                }
                CounterScreen(
                    state = state,
                    onTallyClick = { store.dispatch(CounterIntent.TallyClicked) },
                    onUndoClick = { store.dispatch(CounterIntent.UndoClicked) },
                    onLocationHintAction = { action ->
                        when (action) {
                            LocationHintAction.GRANT -> store.dispatch(CounterIntent.GrantLocationClicked)
                            LocationHintAction.DISMISS -> store.dispatch(CounterIntent.LocationPermissionHintDismissed)
                        }
                    },
                )
            }
        },
    )
}
