package dev.catsradar.app.navigation

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
import dev.catsradar.presentation.encounters.EncountersStore
import dev.catsradar.ui.counter.CounterScreen
import dev.catsradar.ui.counter.LocationHintAction
import dev.catsradar.ui.encounters.EncountersScreen
import dev.catsradar.ui.navigation.CatsRadarBottomBar
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun CatsRadarNavHost(modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(Counter)

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            CatsRadarBottomBar(
                selectedTab = backStack.last().toBottomNavTab(),
                onTabSelect = { tab -> backStack.selectBottomNavTab(tab) },
            )
        },
    ) { innerPadding ->
        NavDisplay(
            backStack = backStack,
            onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider {
                entry<Counter> { CounterDestination(contentPadding = innerPadding) }
                entry<Encounters> { EncountersDestination(contentPadding = innerPadding) }
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
    LaunchedEffect(store, haptics, locationAttachScheduler, locationPermissionRequester) {
        store.effects.collect { effect ->
            handleCounterEffect(effect, haptics, locationAttachScheduler, locationPermissionRequester)
        }
    }
    CounterScreen(
        state = state,
        modifier = modifier.padding(contentPadding),
        onTallyClick = { store.dispatch(CounterIntent.TallyClicked) },
        onUndoClick = { store.dispatch(CounterIntent.UndoClicked) },
        onLocationHintAction = { action -> store.dispatch(action.toCounterIntent()) },
    )
}

private fun LocationHintAction.toCounterIntent(): CounterIntent = when (this) {
    LocationHintAction.GRANT -> CounterIntent.GrantLocationClicked
    LocationHintAction.DISMISS -> CounterIntent.LocationPermissionHintDismissed
}

@Composable
private fun EncountersDestination(contentPadding: PaddingValues, modifier: Modifier = Modifier) {
    val store = koinViewModel<EncountersStore>()
    val state by store.state.collectAsStateWithLifecycle()
    EncountersScreen(state = state, modifier = modifier, contentPadding = contentPadding)
}
