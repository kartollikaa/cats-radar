package dev.catsradar.ui.regions

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.presentation.encounters.CellLead
import dev.catsradar.presentation.encounters.EncounterCell
import dev.catsradar.presentation.encounters.EncountersLayout
import dev.catsradar.presentation.encounters.EncountersRow
import dev.catsradar.presentation.encounters.GroupPosition
import dev.catsradar.presentation.encounters.LocationLabel
import dev.catsradar.presentation.encounters.OutingHeader
import dev.catsradar.presentation.regions.RegionRowKey
import dev.catsradar.presentation.regions.RegionRowLabel
import dev.catsradar.presentation.regions.RegionRowState
import dev.catsradar.presentation.regions.RegionsEmptyHint
import dev.catsradar.presentation.regions.RegionsEmptyLabel
import dev.catsradar.presentation.regions.RegionsHeader
import dev.catsradar.presentation.regions.RegionsSection
import dev.catsradar.presentation.regions.RegionsState
import dev.catsradar.presentation.regions.RegionsTitle
import dev.catsradar.ui.R
import dev.catsradar.ui.components.EmptyState
import dev.catsradar.ui.components.SectionCard
import dev.catsradar.ui.encounters.EncounterRows
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews
import kotlinx.collections.immutable.persistentListOf

@Composable
fun RegionsScreen(
    state: RegionsState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    onRegionClick: (RegionRowKey) -> Unit = {},
    onEncounterClick: (String) -> Unit = {},
    onOutingMapClick: (String) -> Unit = {},
) {
    when (state) {
        RegionsState.Loading -> Box(modifier = modifier.fillMaxSize())
        is RegionsState.Empty -> EmptyRegions(state, modifier = modifier.fillMaxSize().padding(contentPadding))
        is RegionsState.Places -> Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            state.header?.let { RegionsHeadline(it) }
            SectionCard(state.section.titleRes()) {
                state.rows.forEach { row -> RegionRow(row, onClick = { onRegionClick(row.key) }) }
            }
        }
        is RegionsState.Cats -> {
            val header = state.header
            val headline: (@Composable () -> Unit)? = remember(header) {
                header?.let { shown ->
                    @Composable {
                        RegionsHeadline(shown, modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp))
                    }
                }
            }
            EncounterRows(
                rows = state.rows,
                layout = EncountersLayout.LIST,
                modifier = modifier.fillMaxSize(),
                contentPadding = contentPadding,
                onEncounterClick = onEncounterClick,
                onOutingMapClick = onOutingMapClick,
                leadingItem = headline,
            )
        }
    }
}

@Composable
private fun EmptyRegions(empty: RegionsState.Empty, modifier: Modifier = Modifier) {
    EmptyState(
        iconRes = R.drawable.ic_location_on,
        title = stringResource(empty.label.titleRes()),
        modifier = modifier,
        hint = empty.hint?.let { stringResource(it.textRes()) },
    )
}

@StringRes
private fun RegionsEmptyLabel.titleRes(): Int = when (this) {
    RegionsEmptyLabel.NO_PLACES_YET -> R.string.regions_no_places_yet
    RegionsEmptyLabel.NO_PLACES_HERE -> R.string.regions_no_places_here
    RegionsEmptyLabel.NO_CATS_HERE -> R.string.regions_no_cats_here
}

@StringRes
private fun RegionsEmptyHint.textRes(): Int = when (this) {
    RegionsEmptyHint.HOW_PLACES_APPEAR -> R.string.regions_no_places_yet_hint
}

@StringRes
private fun RegionsSection.titleRes(): Int = when (this) {
    RegionsSection.COUNTRIES -> R.string.regions_section_countries
    RegionsSection.CITIES -> R.string.regions_section_cities
    RegionsSection.AREAS -> R.string.regions_section_areas
}

@ThemePreviews
@Composable
private fun RegionsScreenPreview() {
    CatsRadarTheme {
        Surface { RegionsScreen(state = sampleCountries) }
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
        Surface {
            Column {
                RegionsEmptyLabel.entries.forEach { label ->
                    RegionsScreen(state = RegionsState.Empty(label), modifier = Modifier.height(240.dp))
                }
            }
        }
    }
}

private val sampleCountries = RegionsState.Places(
    header = RegionsHeader(RegionsTitle.AllPlaces, count = 151),
    section = RegionsSection.COUNTRIES,
    rows = persistentListOf(
        RegionRowState(RegionRowKey.Country("ES"), RegionRowLabel.Named("Spain"), "128", 0.85f, pseudo = false),
        RegionRowState(RegionRowKey.Country("FR"), RegionRowLabel.Named("France"), "14", 0.09f, pseudo = false),
        RegionRowState(RegionRowKey.Unresolved, RegionRowLabel.Unresolved, "6", 0.04f, pseudo = true),
        RegionRowState(RegionRowKey.NoLocation, RegionRowLabel.NoLocation, "3", 0.02f, pseudo = true),
    ),
)

private val sampleCities = RegionsState.Places(
    header = RegionsHeader(RegionsTitle.Of(RegionRowLabel.Named("Spain")), count = 128),
    section = RegionsSection.CITIES,
    rows = persistentListOf(
        RegionRowState(RegionRowKey.City("ES", "Barcelona"), RegionRowLabel.Named("Barcelona"), "97", 0.76f, false),
        RegionRowState(RegionRowKey.City("ES", "Girona"), RegionRowLabel.Named("Girona"), "29", 0.23f, false),
        RegionRowState(RegionRowKey.NoCity("ES"), RegionRowLabel.NoCity, "2", 0.02f, pseudo = true),
    ),
)

private val barcelona = RegionRowKey.City("ES", "Barcelona")

private val sampleAreas = RegionsState.Places(
    header = RegionsHeader(RegionsTitle.Of(RegionRowLabel.Named("Barcelona")), count = 97),
    section = RegionsSection.AREAS,
    rows = persistentListOf(
        RegionRowState(RegionRowKey.Area("sp3e9", barcelona), RegionRowLabel.Named("Gràcia"), "54", 0.56f, false),
        RegionRowState(RegionRowKey.Area("sp3e3", barcelona), RegionRowLabel.Named("Eixample"), "38", 0.39f, false),
        RegionRowState(
            RegionRowKey.Area("sp3sb", barcelona),
            RegionRowLabel.Coordinates("41.40123, 2.20456"),
            countLabel = "5",
            share = 0.05f,
            pseudo = false,
        ),
    ),
)

private val sampleAreaCats = RegionsState.Cats(
    header = RegionsHeader(RegionsTitle.Of(RegionRowLabel.Named("Gràcia")), count = 3),
    rows = persistentListOf(
        OutingHeader(key = "header-evening", label = "Today, 18:40", mapOutingId = "c3"),
        EncountersRow.Single(
            EncounterCell("c3", "19:18", LocationLabel.FROM_PHOTO, CellLead.Coat(CoatOption.GINGER)),
            GroupPosition.FIRST,
        ),
        EncountersRow.Single(EncounterCell("c2", "18:57", LocationLabel.CURRENT), GroupPosition.LAST),
        OutingHeader(key = "header-morning", label = "Yesterday, 08:15", mapOutingId = "c1"),
        EncountersRow.Single(
            EncounterCell("c1", "08:22", LocationLabel.FROM_OUTING, CellLead.Coat(CoatOption.BLACK)),
            GroupPosition.ONLY,
        ),
    ),
)
