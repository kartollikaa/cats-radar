package dev.catsradar.app.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.catsradar.presentation.encounters.EncountersEffect
import dev.catsradar.presentation.encounters.EncountersIntent
import dev.catsradar.presentation.encounters.EncountersStore
import dev.catsradar.presentation.map.MapEffect
import dev.catsradar.presentation.map.MapIntent
import dev.catsradar.presentation.map.MapSpotEffect
import dev.catsradar.presentation.map.MapSpotIntent
import dev.catsradar.presentation.map.MapSpotStore
import dev.catsradar.presentation.map.MapState
import dev.catsradar.presentation.map.MapStore
import dev.catsradar.presentation.statistics.StatisticsStore
import dev.catsradar.ui.encounters.EncountersScreen
import dev.catsradar.ui.map.MapScreen
import dev.catsradar.ui.map.MapSpotScreen
import dev.catsradar.ui.statistics.StatisticsScreen
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
internal fun EncountersDestination(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    onOpenEncounter: (String) -> Unit = {},
    onOutingMapClick: (String) -> Unit = {},
) {
    val store = koinViewModel<EncountersStore>()
    val state by store.state.collectAsStateWithLifecycle()
    val currentOnOpenEncounter by rememberUpdatedState(onOpenEncounter)
    LaunchedEffect(store) {
        store.effects.collect { effect ->
            when (effect) {
                is EncountersEffect.OpenEncounter -> currentOnOpenEncounter(effect.id)
            }
        }
    }
    BackHandler(enabled = state.isSelecting) { store.dispatch(EncountersIntent.SelectionDismissed) }
    EncountersScreen(
        state = state,
        modifier = modifier,
        contentPadding = contentPadding,
        onEncounterClick = { id -> store.dispatch(EncountersIntent.EncounterClicked(id)) },
        onEncounterLongClick = { id -> store.dispatch(EncountersIntent.EncounterLongPressed(id)) },
        onSelectionDismiss = { store.dispatch(EncountersIntent.SelectionDismissed) },
        onDeleteSelectedClick = { store.dispatch(EncountersIntent.DeleteSelectedClicked) },
        onUndoClick = { store.dispatch(EncountersIntent.UndoClicked) },
        onOutingMapClick = onOutingMapClick,
    )
}

@Composable
internal fun MapDestination(
    contentPadding: PaddingValues,
    focusRequest: MapFocusRequest,
    onOpenCat: (String) -> Unit,
    onOpenSpot: (MapSpot) -> Unit,
    modifier: Modifier = Modifier,
) {
    val store = koinViewModel<MapStore>()
    val state by store.state.collectAsStateWithLifecycle()
    val openCat by rememberUpdatedState(onOpenCat)
    val openSpot by rememberUpdatedState(onOpenSpot)
    LaunchedEffect(store, focusRequest.pending) {
        focusRequest.consume()?.let(store::dispatch)
    }
    BackHandler(enabled = (state as? MapState.Located)?.focus != null) { store.dispatch(MapIntent.FocusCleared) }
    LaunchedEffect(store) {
        store.effects.collect { effect ->
            when (effect) {
                is MapEffect.OpenCat -> openCat(effect.id)
                is MapEffect.OpenSpot -> openSpot(MapSpot(effect.catIds, effect.coats))
            }
        }
    }
    MapScreen(
        state = state,
        modifier = modifier,
        contentPadding = contentPadding,
        onCatsTap = { ids -> store.dispatch(MapIntent.CatsTapped(ids)) },
        onFocusClear = { store.dispatch(MapIntent.FocusCleared) },
        onHeatToggle = { store.dispatch(MapIntent.HeatToggled) },
        onCoatToggle = { coat -> store.dispatch(MapIntent.CoatToggled(coat)) },
        onCoatFilterClear = { store.dispatch(MapIntent.CoatFilterCleared) },
        onCatReach = { store.dispatch(MapIntent.CatReached) },
        onThumbnailUnreadable = { path -> store.dispatch(MapIntent.ThumbnailUnreadable(path)) },
    )
}

@Composable
internal fun MapSpotDestination(
    key: MapSpot,
    onOpenCat: (String) -> Unit,
    onFocusOuting: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val store = koinViewModel<MapSpotStore> { parametersOf(key.catIds, key.coats) }
    val state by store.state.collectAsStateWithLifecycle()
    val openCat by rememberUpdatedState(onOpenCat)
    val focusOuting by rememberUpdatedState(onFocusOuting)
    val close by rememberUpdatedState(onClose)
    LaunchedEffect(store) {
        store.effects.collect { effect ->
            when (effect) {
                is MapSpotEffect.OpenCat -> openCat(effect.id)
                is MapSpotEffect.FocusOuting -> focusOuting(effect.encounterId)
                MapSpotEffect.Close -> close()
            }
        }
    }
    MapSpotScreen(
        state = state,
        modifier = modifier,
        onCatClick = { id -> store.dispatch(MapSpotIntent.CatClicked(id)) },
        onOutingMapClick = { id -> store.dispatch(MapSpotIntent.OutingMapClicked(id)) },
    )
}

@Composable
internal fun StatisticsDestination(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    onPlacesClick: () -> Unit = {},
) {
    val store = koinViewModel<StatisticsStore>()
    val state by store.state.collectAsStateWithLifecycle()
    StatisticsScreen(
        state = state,
        modifier = modifier,
        contentPadding = contentPadding,
        onPlacesClick = onPlacesClick,
    )
}
