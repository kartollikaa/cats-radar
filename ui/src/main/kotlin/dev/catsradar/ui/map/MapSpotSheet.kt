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
import dev.catsradar.presentation.encounters.EncounterListItem
import dev.catsradar.presentation.encounters.GroupPosition
import dev.catsradar.presentation.encounters.LocationLabel
import dev.catsradar.presentation.encounters.RowLead
import dev.catsradar.presentation.map.MapSpot
import dev.catsradar.ui.R
import dev.catsradar.ui.encounters.EncounterList
import dev.catsradar.ui.encounters.EncounterListContentInset
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
            modifier = Modifier.padding(horizontal = EncounterListContentInset).padding(bottom = 8.dp),
        )
        EncounterList(
            rows = spot.rows,
            modifier = Modifier.weight(1f, fill = false),
            contentPadding = WindowInsets.navigationBars.asPaddingValues(),
            onRowClick = onCatClick,
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
        EncounterListItem.OutingHeader(key = "header-1", label = "Today, 14:10"),
        EncounterListItem.Row(
            id = "1",
            timeLabel = "14:32",
            location = LocationLabel.FROM_OUTING,
            lead = RowLead.Coat(CoatOption.GINGER),
            position = GroupPosition.FIRST,
        ),
        EncounterListItem.Row(
            id = "2",
            timeLabel = "14:10",
            location = LocationLabel.CURRENT,
            position = GroupPosition.LAST,
        ),
    ),
)
