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
import dev.catsradar.presentation.statistics.StatisticsStore
import dev.catsradar.ui.encounters.EncountersScreen
import dev.catsradar.ui.statistics.StatisticsScreen
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun EncountersDestination(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    onOpenEncounter: (String) -> Unit = {},
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
        onRowClick = { id -> store.dispatch(EncountersIntent.RowClicked(id)) },
        onRowLongClick = { id -> store.dispatch(EncountersIntent.RowLongPressed(id)) },
        onSelectionDismiss = { store.dispatch(EncountersIntent.SelectionDismissed) },
        onDeleteSelectedClick = { store.dispatch(EncountersIntent.DeleteSelectedClicked) },
        onUndoClick = { store.dispatch(EncountersIntent.UndoClicked) },
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
