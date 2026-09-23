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
import dev.catsradar.presentation.encounters.EncountersStore
import dev.catsradar.presentation.map.MapEffect
import dev.catsradar.presentation.map.MapIntent
import dev.catsradar.presentation.map.MapState
import dev.catsradar.presentation.map.MapStore
import dev.catsradar.presentation.statistics.StatisticsStore
import dev.catsradar.ui.encounters.EncountersScreen
import dev.catsradar.ui.map.MapScreen
import dev.catsradar.ui.statistics.StatisticsScreen
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun EncountersDestination(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    onRowClick: (String) -> Unit = {},
    onOutingMapClick: (String) -> Unit = {},
) {
    val store = koinViewModel<EncountersStore>()
    val state by store.state.collectAsStateWithLifecycle()
    EncountersScreen(
        state = state,
        modifier = modifier,
        contentPadding = contentPadding,
        onRowClick = onRowClick,
        onOutingMapClick = onOutingMapClick,
    )
}

@Composable
internal fun MapDestination(
    contentPadding: PaddingValues,
    focusRequest: MapFocusRequest,
    onOpenCat: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val store = koinViewModel<MapStore>()
    val state by store.state.collectAsStateWithLifecycle()
    val openCat by rememberUpdatedState(onOpenCat)
    LaunchedEffect(store, focusRequest.outing) {
        focusRequest.consume()?.let { store.dispatch(MapIntent.OutingFocused(it)) }
    }
    BackHandler(enabled = (state as? MapState.Located)?.focus != null) { store.dispatch(MapIntent.FocusCleared) }
    LaunchedEffect(store) {
        store.effects.collect { effect ->
            when (effect) {
                is MapEffect.OpenCat -> openCat(effect.id)
            }
        }
    }
    MapScreen(
        state = state,
        modifier = modifier,
        contentPadding = contentPadding,
        onCatsTap = { ids -> store.dispatch(MapIntent.CatsTapped(ids)) },
        onSpotDismiss = { store.dispatch(MapIntent.SpotDismissed) },
        onOutingFocus = { id -> store.dispatch(MapIntent.OutingFocused(id)) },
        onFocusClear = { store.dispatch(MapIntent.FocusCleared) },
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
