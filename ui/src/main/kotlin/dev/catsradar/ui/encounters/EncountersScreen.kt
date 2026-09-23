package dev.catsradar.ui.encounters

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import dev.catsradar.presentation.encounters.CellLead
import dev.catsradar.presentation.encounters.EncounterCell
import dev.catsradar.presentation.encounters.EncounterGridRow
import dev.catsradar.presentation.encounters.EncountersState
import dev.catsradar.presentation.encounters.LocationLabel
import dev.catsradar.presentation.encounters.OutingHeader
import dev.catsradar.presentation.encounters.PhotoCell
import dev.catsradar.ui.R
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews
import kotlinx.collections.immutable.persistentListOf

@Composable
fun EncountersScreen(
    state: EncountersState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    onEncounterClick: (String) -> Unit = {},
) {
    if (state.isEmpty) {
        EmptyEncounters(modifier = modifier.fillMaxSize().padding(contentPadding))
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(CellGap),
    ) {
        items(items = state.rows, key = { it.key }, contentType = { it::class }) { row ->
            val rowModifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            when (row) {
                is OutingHeader -> OutingHeaderRow(row, modifier = rowModifier)
                is EncounterGridRow.PhotoPair -> PhotoPairRow(row, rowModifier, onEncounterClick)
                is EncounterGridRow.Tiles -> TileRow(row, rowModifier, onEncounterClick)
                is EncounterGridRow.Cards -> CardRow(row, rowModifier, onEncounterClick)
            }
        }
    }
}

@Composable
private fun OutingHeaderRow(header: OutingHeader, modifier: Modifier = Modifier) {
    Text(
        text = header.label,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 16.dp),
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
        OutingHeader(key = "header-1", label = "Today, 14:10"),
        EncounterGridRow.PhotoPair(
            first = PhotoCell("1", "14:32", LocationLabel.FROM_PHOTO, photoPath = "/photos/1.jpg"),
            second = PhotoCell("2", "14:30", LocationLabel.CURRENT, photoPath = "/photos/2.jpg"),
        ),
        EncounterGridRow.Tiles(
            persistentListOf(
                EncounterCell("3", "14:28", LocationLabel.CURRENT, CellLead.Coat(CoatOption.GINGER)),
                EncounterCell("4", "14:20", LocationLabel.FROM_OUTING),
                EncounterCell("5", "14:10", LocationLabel.FROM_OUTING, CellLead.Coat(CoatOption.BLACK)),
            ),
        ),
        OutingHeader(key = "header-6", label = "Today, 09:05"),
        EncounterGridRow.Cards(
            persistentListOf(
                EncounterCell("6", "09:20", LocationLabel.LAST_KNOWN, CellLead.Coat(CoatOption.TRICOLOR_MOSTLY_WHITE)),
                EncounterCell("7", "09:05", LocationLabel.NONE),
            ),
        ),
    ),
)
