package dev.catsradar.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.presentation.encounters.CellLead
import dev.catsradar.presentation.encounters.EncounterCell
import dev.catsradar.presentation.encounters.EncountersLayout
import dev.catsradar.presentation.encounters.EncountersRow
import dev.catsradar.presentation.encounters.GroupPosition
import dev.catsradar.presentation.encounters.LocationLabel
import dev.catsradar.presentation.encounters.OutingHeader
import dev.catsradar.presentation.map.MapSpotState
import dev.catsradar.ui.R
import dev.catsradar.ui.encounters.EncounterListTextInset
import dev.catsradar.ui.encounters.EncounterRows
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews
import kotlinx.collections.immutable.persistentListOf

@Composable
fun MapSpotScreen(
    state: MapSpotState,
    modifier: Modifier = Modifier,
    onCatClick: (String) -> Unit = {},
    onOutingMapClick: (String) -> Unit = {},
) {
    when (state) {
        // A sheet settles by the height it first measures, so while loading this takes what a long list would.
        MapSpotState.Loading -> Box(modifier = modifier.fillMaxHeight())
        is MapSpotState.Listed -> MapSpotLoaded(
            spot = state,
            modifier = modifier,
            onCatClick = onCatClick,
            onOutingMapClick = onOutingMapClick,
        )
    }
}

@Composable
private fun MapSpotLoaded(
    spot: MapSpotState.Listed,
    modifier: Modifier = Modifier,
    onCatClick: (String) -> Unit = {},
    onOutingMapClick: (String) -> Unit = {},
) {
    Column(modifier = modifier) {
        Text(
            text = pluralStringResource(R.plurals.map_spot_title, spot.catCount, spot.catCount),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = EncounterListTextInset).padding(bottom = 8.dp),
        )
        EncounterRows(
            rows = spot.rows,
            layout = EncountersLayout.LIST,
            modifier = Modifier.weight(1f, fill = false),
            contentPadding = WindowInsets.navigationBars.asPaddingValues(),
            onEncounterClick = onCatClick,
            onOutingMapClick = onOutingMapClick,
        )
    }
}

@ThemePreviews
@Composable
private fun MapSpotScreenPreview() {
    CatsRadarTheme { MapSpotScreen(state = sampleSpot) }
}

private val sampleSpot = MapSpotState.Listed(
    catCount = 2,
    rows = persistentListOf(
        OutingHeader(key = "header-1", label = "Today, 14:10"),
        EncountersRow.Single(
            EncounterCell("1", "14:32", LocationLabel.FROM_OUTING, CellLead.Coat(CoatOption.GINGER)),
            GroupPosition.FIRST,
        ),
        EncountersRow.Single(EncounterCell("2", "14:10", LocationLabel.CURRENT), GroupPosition.LAST),
    ),
)
