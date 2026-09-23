package dev.catsradar.ui.encounters

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.presentation.encounters.EncounterListItem
import dev.catsradar.presentation.encounters.GroupPosition
import dev.catsradar.presentation.encounters.LocationLabel
import dev.catsradar.presentation.encounters.RowLead
import dev.catsradar.ui.R
import dev.catsradar.ui.coat.CatFace
import dev.catsradar.ui.coat.labelRes
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

private val LeadingSize = 48.dp

// The header lines up with a row's content, so its inset is the row's two insets added.
private val RowOuterInset = 16.dp
private val RowInnerInset = 12.dp
private val OutingOuterCorner = 20.dp
private val OutingJoinCorner = 4.dp

/** Cats grouped by outing, as the Encounters tab shows them. */
@Composable
fun EncounterList(
    rows: ImmutableList<EncounterListItem>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    onRowClick: (String) -> Unit = {},
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(items = rows, key = { it.key }) { row ->
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
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = RowOuterInset + RowInnerInset)
            .padding(top = 20.dp, bottom = 6.dp),
    )
}

@Composable
private fun EncounterRow(row: EncounterListItem.Row, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = RowOuterInset)
            .clip(row.position.shape())
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick)
            .padding(horizontal = RowInnerInset, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EncounterLead(lead = row.lead)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = row.timeLabel, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(row.location.labelRes()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun GroupPosition.shape(): RoundedCornerShape {
    val top = if (this == GroupPosition.FIRST || this == GroupPosition.ONLY) OutingOuterCorner else OutingJoinCorner
    val bottom = if (this == GroupPosition.LAST || this == GroupPosition.ONLY) OutingOuterCorner else OutingJoinCorner
    return RoundedCornerShape(topStart = top, topEnd = top, bottomEnd = bottom, bottomStart = bottom)
}

@Composable
private fun EncounterLead(lead: RowLead, modifier: Modifier = Modifier) {
    val shape = MaterialTheme.shapes.small
    val tile = modifier.size(LeadingSize).clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHighest)
    when (lead) {
        is RowLead.Photo -> AsyncImage(
            model = lead.thumbnailPath,
            contentDescription = stringResource(R.string.encounters_photo_description),
            modifier = tile,
            contentScale = ContentScale.Crop,
        )
        is RowLead.Coat -> {
            val coatLabel = stringResource(lead.coat.labelRes())
            Box(modifier = tile.semantics { contentDescription = coatLabel }, contentAlignment = Alignment.Center) {
                CatFace(coat = lead.coat, modifier = Modifier.size(36.dp))
            }
        }
        RowLead.Paw -> Box(modifier = tile, contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(R.drawable.ic_nav_pets),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@ThemePreviews
@Composable
private fun EncounterListPreview() {
    CatsRadarTheme { EncounterList(rows = sampleRows) }
}

private val sampleRows = persistentListOf(
    EncounterListItem.OutingHeader(key = "header-1", label = "Today, 14:10"),
    EncounterListItem.Row(
        id = "1",
        timeLabel = "14:32",
        location = LocationLabel.CURRENT,
        lead = RowLead.Coat(CoatOption.GINGER),
        position = GroupPosition.FIRST,
    ),
    EncounterListItem.Row(
        id = "2",
        timeLabel = "14:10",
        location = LocationLabel.FROM_OUTING,
        position = GroupPosition.LAST,
    ),
)
