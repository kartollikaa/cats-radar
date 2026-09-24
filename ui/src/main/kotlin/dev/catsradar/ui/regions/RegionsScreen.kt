package dev.catsradar.ui.regions

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.catsradar.presentation.encounters.EncounterListItem
import dev.catsradar.presentation.encounters.OutingHeader
import dev.catsradar.presentation.regions.RegionRowKey
import dev.catsradar.presentation.regions.RegionRowLabel
import dev.catsradar.presentation.regions.RegionRowState
import dev.catsradar.presentation.regions.RegionsEmptyLabel
import dev.catsradar.presentation.regions.RegionsState
import dev.catsradar.ui.R
import dev.catsradar.ui.encounters.labelRes
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews
import kotlinx.collections.immutable.persistentListOf

@Composable
fun RegionsScreen(
    state: RegionsState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    onRegionClick: (RegionRowKey) -> Unit = {},
) {
    when (state) {
        RegionsState.Loading -> Box(modifier = modifier.fillMaxSize())
        is RegionsState.Empty -> Box(
            modifier = modifier.fillMaxSize().padding(contentPadding),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = stringResource(state.label.textRes()), style = MaterialTheme.typography.bodyLarge)
        }
        is RegionsState.Loaded -> LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = contentPadding) {
            items(items = state.rows, key = { it.key.toString() }) { row ->
                RegionRow(row, onClick = { onRegionClick(row.key) })
            }
            items(items = state.encounters, key = { it.key }) { item ->
                when (item) {
                    is OutingHeader -> OutingHeaderRow(item.label)
                    is EncounterListItem.Row -> EncounterRow(item)
                }
            }
        }
    }
}

@StringRes
private fun RegionsEmptyLabel.textRes(): Int = when (this) {
    RegionsEmptyLabel.NO_PLACES -> R.string.regions_empty_places
    RegionsEmptyLabel.NO_CATS -> R.string.regions_empty_cats
}

@Composable
private fun RegionRow(row: RegionRowState, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (row.drillable) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = row.label.text(), style = MaterialTheme.typography.bodyLarge)
        Text(text = row.countLabel, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun RegionRowLabel.text(): String = when (this) {
    is RegionRowLabel.Named -> name
    is RegionRowLabel.Coordinates -> stringResource(R.string.regions_area_at, text)
    RegionRowLabel.Unresolved -> stringResource(R.string.regions_unresolved)
    RegionRowLabel.NoCity -> stringResource(R.string.regions_no_city)
    RegionRowLabel.NoLocation -> stringResource(R.string.regions_no_location)
}

@Composable
private fun OutingHeaderRow(label: String, modifier: Modifier = Modifier) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun EncounterRow(row: EncounterListItem.Row, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = row.timeLabel, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = stringResource(row.location.labelRes()),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@ThemePreviews
@Composable
private fun RegionsScreenPreview() {
    CatsRadarTheme {
        Surface { RegionsScreen(state = sampleRegions) }
    }
}

@ThemePreviews
@Composable
private fun RegionsScreenCitiesPreview() {
    CatsRadarTheme {
        Surface { RegionsScreen(state = sampleCities) }
    }
}

@ThemePreviews
@Composable
private fun RegionsScreenEmptyPreview() {
    CatsRadarTheme {
        Surface { RegionsScreen(state = RegionsState.Empty(RegionsEmptyLabel.NO_PLACES)) }
    }
}

@ThemePreviews
@Composable
private fun RegionsScreenEmptyCatsPreview() {
    CatsRadarTheme {
        Surface { RegionsScreen(state = RegionsState.Empty(RegionsEmptyLabel.NO_CATS)) }
    }
}

private val sampleRegions = RegionsState.Loaded(
    rows = persistentListOf(
        RegionRowState(RegionRowKey.Country("ES"), RegionRowLabel.Named("Spain"), "128", drillable = true),
        RegionRowState(RegionRowKey.Country("FR"), RegionRowLabel.Named("France"), "14", drillable = true),
        RegionRowState(RegionRowKey.Unresolved, RegionRowLabel.Unresolved, "6", drillable = true),
        RegionRowState(RegionRowKey.NoLocation, RegionRowLabel.NoLocation, "3", drillable = true),
    ),
)

private val sampleCities = RegionsState.Loaded(
    rows = persistentListOf(
        RegionRowState(RegionRowKey.City("ES", "Barcelona"), RegionRowLabel.Named("Barcelona"), "97", drillable = true),
        RegionRowState(RegionRowKey.City("ES", "Girona"), RegionRowLabel.Named("Girona"), "29", drillable = true),
        RegionRowState(RegionRowKey.NoCity("ES"), RegionRowLabel.NoCity, "2", drillable = true),
    ),
)
