package dev.catsradar.ui.encounters

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.catsradar.presentation.encounters.EncounterListItem
import dev.catsradar.presentation.encounters.EncountersState
import dev.catsradar.presentation.encounters.LocationLabel
import dev.catsradar.ui.R
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews
import kotlinx.collections.immutable.persistentListOf

private val ThumbnailSize = 48.dp

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
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = contentPadding) {
        items(items = state.rows, key = { it.key }) { row ->
            when (row) {
                is EncounterListItem.OutingHeader -> OutingHeaderRow(row)
                is EncounterListItem.Row -> EncounterRow(row, onClick = { onRowClick(row.id) })
            }
        }
    }
}

@Composable
private fun OutingHeaderRow(header: EncounterListItem.OutingHeader, modifier: Modifier = Modifier) {
    Text(
        text = header.label,
        style = MaterialTheme.typography.titleSmall,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun EncounterRow(row: EncounterListItem.Row, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    Row(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            EncounterThumbnail(path = row.thumbnailPath)
            Text(text = row.timeLabel, style = MaterialTheme.typography.bodyLarge)
        }
        Text(text = stringResource(row.location.labelRes()), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun EncounterThumbnail(path: String?, modifier: Modifier = Modifier) {
    val shape = MaterialTheme.shapes.small
    if (path == null) {
        Box(modifier = modifier.size(ThumbnailSize).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant))
        return
    }
    AsyncImage(
        model = path,
        contentDescription = stringResource(R.string.encounters_photo_description),
        modifier = modifier.size(ThumbnailSize).clip(shape),
        contentScale = ContentScale.Crop,
    )
}

@Composable
private fun EmptyEncounters(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(text = stringResource(R.string.encounters_empty), style = MaterialTheme.typography.bodyLarge)
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
        EncounterListItem.Row(id = "1", timeLabel = "14:32", location = LocationLabel.CURRENT),
        EncounterListItem.Row(id = "2", timeLabel = "14:10", location = LocationLabel.FROM_OUTING),
        EncounterListItem.OutingHeader(key = "header-3", label = "Today, 09:05"),
        EncounterListItem.Row(id = "3", timeLabel = "09:05", location = LocationLabel.NONE),
    ),
)
