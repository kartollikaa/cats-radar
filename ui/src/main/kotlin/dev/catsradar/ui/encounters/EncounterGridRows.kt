package dev.catsradar.ui.encounters

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.presentation.encounters.CellLead
import dev.catsradar.presentation.encounters.EncounterCell
import dev.catsradar.presentation.encounters.EncountersRow
import dev.catsradar.presentation.encounters.LocationLabel
import dev.catsradar.presentation.encounters.PhotoCell
import dev.catsradar.ui.R
import dev.catsradar.ui.coat.CatFace
import dev.catsradar.ui.coat.labelRes
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews
import kotlinx.collections.immutable.persistentListOf

// Between rows and between the cells of a row alike, so the grid's gutters read as one.
internal val CellGap = 8.dp

@Composable
internal fun PhotoPairRow(
    row: EncountersRow.PhotoPair,
    modifier: Modifier = Modifier,
    onEncounterClick: (String) -> Unit = {},
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(CellGap)) {
        PhotoTile(row.first, Modifier.weight(1f), onClick = { onEncounterClick(row.first.id) })
        PhotoTile(row.second, Modifier.weight(1f), onClick = { onEncounterClick(row.second.id) })
    }
}

@Composable
internal fun TileRow(
    row: EncountersRow.Tiles,
    modifier: Modifier = Modifier,
    onEncounterClick: (String) -> Unit = {},
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(CellGap)) {
        row.cells.forEach { cell ->
            key(cell.id) {
                EncounterTile(cell, Modifier.weight(1f), onClick = { onEncounterClick(cell.id) })
            }
        }
    }
}

@Composable
internal fun CardRow(
    row: EncountersRow.Cards,
    modifier: Modifier = Modifier,
    onEncounterClick: (String) -> Unit = {},
) {
    Row(
        modifier = modifier.height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(CellGap),
    ) {
        row.cells.forEach { cell ->
            key(cell.id) {
                EncounterCard(cell, Modifier.weight(1f).fillMaxHeight(), onClick = { onEncounterClick(cell.id) })
            }
        }
    }
}

@Composable
private fun PhotoTile(cell: PhotoCell, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    val subject = stringResource(R.string.encounters_photo_description)
    val description = cellDescription(subject, cell.timeLabel, cell.location)
    var fullCopyUnreadable by remember(cell.photoPath) { mutableStateOf(false) }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        AsyncImage(
            model = if (fullCopyUnreadable) cell.thumbnailPath else cell.photoPath,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            onError = { fullCopyUnreadable = true },
        )
        Text(
            text = cell.timeLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), CircleShape)
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun EncounterTile(cell: EncounterCell, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    val description = cellDescription(cell.lead.description(), cell.timeLabel, cell.location)
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .clearAndSetSemantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        EncounterLead(lead = cell.lead, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
        val timeStyle = MaterialTheme.typography.labelMedium
        Text(
            text = cell.timeLabel,
            style = timeStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = timeStyle.fontSize),
            modifier = Modifier.padding(bottom = 4.dp),
        )
    }
}

@Composable
internal fun EncounterCard(
    cell: EncounterCell,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    onClick: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EncounterLead(lead = cell.lead, modifier = Modifier.size(48.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = cell.timeLabel, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(cell.location.labelRes()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun EncounterLead(lead: CellLead, modifier: Modifier = Modifier) {
    val tile = modifier.clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceContainerHighest)
    when (lead) {
        is CellLead.Photo -> AsyncImage(
            model = lead.thumbnailPath,
            contentDescription = stringResource(R.string.encounters_photo_description),
            modifier = tile,
            contentScale = ContentScale.Crop,
        )
        is CellLead.Coat -> {
            val coatLabel = stringResource(lead.coat.labelRes())
            Box(modifier = tile.semantics { contentDescription = coatLabel }, contentAlignment = Alignment.Center) {
                CatFace(coat = lead.coat, modifier = Modifier.fillMaxSize(0.75f))
            }
        }
        CellLead.Paw -> Box(modifier = tile, contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(R.drawable.ic_nav_pets),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxSize(0.5f),
            )
        }
    }
}

@Composable
private fun CellLead.description(): String = when (this) {
    is CellLead.Photo -> stringResource(R.string.encounters_photo_description)
    is CellLead.Coat -> stringResource(coat.labelRes())
    CellLead.Paw -> stringResource(R.string.encounters_paw_description)
}

@Composable
private fun cellDescription(subject: String, timeLabel: String, location: LocationLabel): String =
    stringResource(R.string.encounters_cell_description, subject, timeLabel, stringResource(location.labelRes()))

@ThemePreviews
@Composable
private fun EncountersRowsPreview() {
    CatsRadarTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(CellGap),
        ) {
            PhotoPairRow(row = samplePhotoPair)
            TileRow(row = sampleTilesOfFive)
            TileRow(row = sampleTilesOfThree)
            CardRow(row = sampleCardsOfTwo)
            CardRow(row = sampleCardsOfOne)
        }
    }
}

private val samplePhotoPair = EncountersRow.PhotoPair(
    first = PhotoCell("1", "14:32", LocationLabel.FROM_PHOTO, "/photos/1.jpg", "/photos/1_thumb.jpg"),
    second = PhotoCell("2", "14:30", LocationLabel.CURRENT, "/photos/2.jpg", "/photos/2_thumb.jpg"),
)

private val sampleTilesOfFive = EncountersRow.Tiles(
    persistentListOf(
        EncounterCell("3", "14:28", LocationLabel.CURRENT, CellLead.Coat(CoatOption.GINGER)),
        EncounterCell("4", "14:25", LocationLabel.CURRENT),
        EncounterCell("5", "14:22", LocationLabel.CURRENT, CellLead.Coat(CoatOption.BLACK)),
        EncounterCell("6", "14:20", LocationLabel.FROM_OUTING, CellLead.Coat(CoatOption.TRICOLOR_MOSTLY_WHITE)),
        EncounterCell("7", "14:14", LocationLabel.FROM_OUTING),
    ),
)

private val sampleTilesOfThree = EncountersRow.Tiles(
    persistentListOf(
        EncounterCell("8", "14:13", LocationLabel.FROM_OUTING, CellLead.Coat(CoatOption.GREY)),
        EncounterCell("9", "14:12", LocationLabel.FROM_OUTING),
        EncounterCell("10", "14:10", LocationLabel.LAST_KNOWN, CellLead.Coat(CoatOption.GINGER)),
    ),
)

private val sampleCardsOfTwo = EncountersRow.Cards(
    persistentListOf(
        EncounterCell("11", "09:20", LocationLabel.LAST_KNOWN, CellLead.Coat(CoatOption.BLACK)),
        EncounterCell("12", "09:05", LocationLabel.NONE),
    ),
)

private val sampleCardsOfOne = EncountersRow.Cards(
    persistentListOf(EncounterCell("13", "08:40", LocationLabel.CURRENT, CellLead.Coat(CoatOption.GREY))),
)
