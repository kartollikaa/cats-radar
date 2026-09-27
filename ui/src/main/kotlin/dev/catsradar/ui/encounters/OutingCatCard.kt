package dev.catsradar.ui.encounters

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.presentation.encounters.CellLead
import dev.catsradar.presentation.encounters.EncounterCell
import dev.catsradar.presentation.encounters.LocationLabel
import dev.catsradar.ui.R
import dev.catsradar.ui.coat.coatShapeFor
import dev.catsradar.ui.coat.labelRes
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews

const val OutingCatCardTestTag = "outing-cat-card"

/** A cat inside its outing's card: its lead in its coat's shape, the coat's name, and when and where it was met. */
@Composable
internal fun OutingCatCard(
    cell: EncounterCell,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    selecting: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .testTag(OutingCatCardTestTag)
            .clip(shape)
            .background(if (cell.selected) colors.secondaryContainer else colors.surface)
            .selectableCell(cell.selected, selecting, toggleLabel(cell.selected), onClick, onLongClick)
            .padding(start = 8.dp, top = 8.dp, end = 12.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            EncounterLead(
                lead = cell.lead,
                modifier = Modifier.size(48.dp),
                badgeCount = cell.badgeCount,
                shape = cell.lead.coatShape(),
            )
            if (cell.selected) SelectionBadge(modifier = Modifier.align(Alignment.TopEnd))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = cell.badgeCount?.let { shotDescription(it) } ?: cell.lead.coatName(),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                cell.badgeCount?.let {
                    ShotBadge(
                        count = it,
                        containerColor = colors.primaryContainer,
                        contentColor = colors.onPrimaryContainer,
                    )
                }
            }
            val place = stringResource(cell.location.labelRes())
            val unplaced = cell.location == LocationLabel.NONE
            Text(
                text = stringResource(R.string.encounters_card_line, cell.timeLabel, place),
                style = MaterialTheme.typography.bodySmall,
                color = if (unplaced) colors.onTertiaryContainer else colors.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The shape a cell's lead takes: its coat's own, or no coat's for a paw or a shot's photo. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CellLead.coatShape(): Shape = coatShapeFor(coat()).toShape()

/** The coat whose shape a cell's lead takes; none for a paw or a shot's photo. */
internal fun CellLead.coat(): CoatOption? = when (this) {
    is CellLead.Coat -> coat
    is CellLead.Photo -> coat
    CellLead.Paw -> null
}

@Composable
private fun CellLead.coatName(): String =
    coat()?.let { stringResource(it.labelRes()) } ?: stringResource(R.string.encounters_paw_description)

@ThemePreviews
@Composable
private fun OutingCatCardPreview() {
    CatsRadarTheme {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutingCatCard(cell = sampleGinger)
            OutingCatCard(cell = sampleUnplaced)
        }
    }
}

private val sampleGinger = EncounterCell("1", "4:58 PM", LocationLabel.CURRENT, CellLead.Coat(CoatOption.GINGER_WHITE))

private val sampleUnplaced = EncounterCell("2", "4:51 PM", LocationLabel.NONE)
