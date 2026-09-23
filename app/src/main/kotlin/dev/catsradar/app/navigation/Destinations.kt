package dev.catsradar.app.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.catsradar.presentation.encounters.EncountersStore
import dev.catsradar.presentation.statistics.StatisticsStore
import dev.catsradar.ui.encounters.EncountersScreen
import dev.catsradar.ui.statistics.StatisticsScreen
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun EncountersDestination(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    onEncounterClick: (String) -> Unit = {},
) {
    val store = koinViewModel<EncountersStore>()
    val state by store.state.collectAsStateWithLifecycle()
    EncountersScreen(
        state = state,
        modifier = modifier,
        contentPadding = contentPadding,
        onEncounterClick = onEncounterClick,
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
