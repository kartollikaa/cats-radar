package dev.catsradar.ui.encounters

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.presentation.encounters.EncounterListItem
import dev.catsradar.presentation.encounters.EncountersState
import dev.catsradar.presentation.encounters.GroupPosition
import dev.catsradar.presentation.encounters.LocationLabel
import dev.catsradar.presentation.encounters.RowLead
import dev.catsradar.ui.R
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews
import kotlinx.collections.immutable.persistentListOf

@Composable
fun EncountersScreen(
    state: EncountersState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    onRowClick: (String) -> Unit = {},
) {
    if (state.isEmpty) {
        EmptyEncounters(modifier = modifier.fillMaxSize().padding(contentPadding))
        return
    }
    EncounterList(
        rows = state.rows,
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        onRowClick = onRowClick,
    )
}

@Composable
private fun EmptyEncounters(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_nav_pets),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp),
        )
        Text(
            text = stringResource(R.string.encounters_empty),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.encounters_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@ThemePreviews
@Composable
private fun EncountersScreenEmptyPreview() {
    CatsRadarTheme {
        Surface { EncountersScreen(state = sampleEncountersStateEmpty) }
    }
}

@ThemePreviews
@Composable
private fun EncountersScreenPopulatedPreview() {
    CatsRadarTheme {
        Surface { EncountersScreen(state = sampleEncountersStatePopulated) }
    }
}

private val sampleEncountersStateEmpty = EncountersState()

// Two outings on the same day, so the preview also shows how their headers tell them apart.
private val sampleEncountersStatePopulated = EncountersState(
    rows = persistentListOf(
        EncounterListItem.OutingHeader(key = "header-1", label = "Today, 14:10"),
        EncounterListItem.Row(
            id = "1",
            timeLabel = "14:32",
            location = LocationLabel.CURRENT,
            lead = RowLead.Coat(CoatOption.TRICOLOR_MOSTLY_WHITE),
            position = GroupPosition.FIRST,
        ),
        EncounterListItem.Row(
            id = "2",
            timeLabel = "14:20",
            location = LocationLabel.FROM_OUTING,
            lead = RowLead.Coat(CoatOption.BLACK),
            position = GroupPosition.MIDDLE,
        ),
        EncounterListItem.Row(
            id = "3",
            timeLabel = "14:10",
            location = LocationLabel.FROM_OUTING,
            position = GroupPosition.LAST,
        ),
        EncounterListItem.OutingHeader(key = "header-4", label = "Today, 09:05"),
        EncounterListItem.Row(id = "4", timeLabel = "09:05", location = LocationLabel.NONE),
    ),
)
