package dev.catsradar.ui.map

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import dev.catsradar.presentation.map.MapSpot
import dev.catsradar.ui.R
import dev.catsradar.ui.encounters.EncounterListTextInset
import dev.catsradar.ui.encounters.EncounterRows
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews
import kotlinx.collections.immutable.persistentListOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MapSpotSheet(
    spot: MapSpot,
    modifier: Modifier = Modifier,
    onCatClick: (String) -> Unit = {},
    onDismiss: () -> Unit = {},
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        contentWindowInsets = { WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal) },
    ) {
        MapSpotContent(spot = spot, onCatClick = onCatClick)
    }
}

@Composable
private fun MapSpotContent(spot: MapSpot, modifier: Modifier = Modifier, onCatClick: (String) -> Unit = {}) {
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
        )
    }
}

@ThemePreviews
@Composable
private fun MapSpotContentPreview() {
    CatsRadarTheme { MapSpotContent(spot = sampleSpot) }
}

private val sampleSpot = MapSpot(
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
