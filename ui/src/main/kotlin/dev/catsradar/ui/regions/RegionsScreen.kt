package dev.catsradar.ui.regions

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
import dev.catsradar.presentation.encounters.LocationLabel
import dev.catsradar.presentation.encounters.OutingHeader
import dev.catsradar.presentation.regions.RegionRowKey
import dev.catsradar.presentation.regions.RegionRowLabel
import dev.catsradar.presentation.regions.RegionRowState
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
    if (state.isEmpty) {
        Box(modifier = modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) {
            Text(text = stringResource(R.string.regions_empty), style = MaterialTheme.typography.bodyLarge)
        }
        return
    }
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = contentPadding) {
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

@Composable
private fun RegionRow(row: RegionRowState, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
private fun RegionsScreenAreasPreview() {
    CatsRadarTheme {
        Surface { RegionsScreen(state = sampleAreas) }
    }
}

@ThemePreviews
@Composable
private fun RegionsScreenAreaCatsPreview() {
    CatsRadarTheme {
        Surface { RegionsScreen(state = sampleAreaCats) }
    }
}

@ThemePreviews
@Composable
private fun RegionsScreenEmptyPreview() {
    CatsRadarTheme {
        Surface { RegionsScreen(state = RegionsState()) }
    }
}

private val sampleRegions = RegionsState(
    rows = persistentListOf(
        RegionRowState(RegionRowKey.Country("ES"), RegionRowLabel.Named("Spain"), "128"),
        RegionRowState(RegionRowKey.Country("FR"), RegionRowLabel.Named("France"), "14"),
        RegionRowState(RegionRowKey.Unresolved, RegionRowLabel.Unresolved, "6"),
        RegionRowState(RegionRowKey.NoLocation, RegionRowLabel.NoLocation, "3"),
    ),
)

private val sampleCities = RegionsState(
    rows = persistentListOf(
        RegionRowState(RegionRowKey.City("ES", "Barcelona"), RegionRowLabel.Named("Barcelona"), "97"),
        RegionRowState(RegionRowKey.City("ES", "Girona"), RegionRowLabel.Named("Girona"), "29"),
        RegionRowState(RegionRowKey.NoCity("ES"), RegionRowLabel.NoCity, "2"),
    ),
)

private val barcelona = RegionRowKey.City("ES", "Barcelona")

private val sampleAreas = RegionsState(
    rows = persistentListOf(
        RegionRowState(RegionRowKey.Area("sp3e9", barcelona), RegionRowLabel.Named("Gràcia"), "54"),
        RegionRowState(RegionRowKey.Area("sp3e3", barcelona), RegionRowLabel.Named("Eixample"), "38"),
        RegionRowState(RegionRowKey.Area("sp3sb", barcelona), RegionRowLabel.Coordinates("41.40123, 2.20456"), "5"),
    ),
)

private val sampleAreaCats = RegionsState(
    encounters = persistentListOf(
        OutingHeader(key = "header-evening", label = "Today, 18:40"),
        EncounterListItem.Row(id = "c3", timeLabel = "19:18", location = LocationLabel.FROM_PHOTO),
        EncounterListItem.Row(id = "c2", timeLabel = "18:57", location = LocationLabel.CURRENT),
        OutingHeader(key = "header-morning", label = "Yesterday, 08:15"),
        EncounterListItem.Row(id = "c1", timeLabel = "08:22", location = LocationLabel.FROM_OUTING),
    ),
)
