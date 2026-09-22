package dev.catsradar.app.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dev.catsradar.presentation.detail.EncounterDetailEffect
import dev.catsradar.presentation.detail.EncounterDetailIntent
import dev.catsradar.presentation.detail.EncounterDetailStore
import dev.catsradar.presentation.regions.RegionRowKey
import dev.catsradar.presentation.regions.RegionsStore
import dev.catsradar.presentation.settings.SettingsIntent
import dev.catsradar.presentation.settings.SettingsStore
import dev.catsradar.ui.detail.EncounterDetailScreen
import dev.catsradar.ui.navigation.CatsRadarBottomBar
import dev.catsradar.ui.regions.RegionsScreen
import dev.catsradar.ui.settings.SettingsScreen
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun CatsRadarNavHost(modifier: Modifier = Modifier) {
    val backStack = rememberBottomNavBackStack()

    Scaffold(
        modifier = modifier,
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
                entry<Statistics> {
                    StatisticsDestination(
                        contentPadding = innerPadding,
                        onPlacesClick = { backStack.push(Regions()) },
                    )
                }
                entry<Settings> { SettingsDestination(contentPadding = innerPadding) }
                entry<Regions> { key ->
                    RegionsDestination(
                        key = key,
                        contentPadding = innerPadding,
                        onRegionClick = { row -> backStack.push(row.toNavKey()) },
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
private fun SettingsDestination(contentPadding: PaddingValues, modifier: Modifier = Modifier) {
    val store = koinViewModel<SettingsStore>()
    val state by store.state.collectAsStateWithLifecycle()
    SettingsScreen(
        state = state,
        modifier = modifier,
        contentPadding = contentPadding,
        onSaveOriginalsChange = { store.dispatch(SettingsIntent.SaveOriginalsToggled(it)) },
    )
}

@Composable
private fun RegionsDestination(
    key: Regions,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    onRegionClick: (RegionRowKey) -> Unit = {},
) {
    val store = koinViewModel<RegionsStore> { parametersOf(key.toRegionKey()) }
    val state by store.state.collectAsStateWithLifecycle()
    RegionsScreen(
        state = state,
        modifier = modifier,
        contentPadding = contentPadding,
        onRegionClick = onRegionClick,
    )
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
        onCoatClick = { coat -> store.dispatch(EncounterDetailIntent.CoatPicked(coat)) },
    )
}
