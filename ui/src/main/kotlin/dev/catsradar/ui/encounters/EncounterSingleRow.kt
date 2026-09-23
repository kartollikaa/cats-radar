package dev.catsradar.ui.encounters

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.presentation.encounters.CellLead
import dev.catsradar.presentation.encounters.EncounterCell
import dev.catsradar.presentation.encounters.EncountersRow
import dev.catsradar.presentation.encounters.GroupPosition
import dev.catsradar.presentation.encounters.LocationLabel
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews

// Tight enough that one outing's rows read as a single card.
internal val ListRowGap = 2.dp

private val OutingOuterCorner = 20.dp
private val OutingJoinCorner = 4.dp

@Composable
internal fun SingleRow(
    row: EncountersRow.Single,
    modifier: Modifier = Modifier,
    onEncounterClick: (String) -> Unit = {},
) {
    EncounterCard(
        cell = row.cell,
        modifier = modifier,
        shape = row.position.shape(),
        onClick = { onEncounterClick(row.cell.id) },
    )
}

private fun GroupPosition.shape(): RoundedCornerShape {
    val top = if (this == GroupPosition.FIRST || this == GroupPosition.ONLY) OutingOuterCorner else OutingJoinCorner
    val bottom = if (this == GroupPosition.LAST || this == GroupPosition.ONLY) OutingOuterCorner else OutingJoinCorner
    return RoundedCornerShape(topStart = top, topEnd = top, bottomEnd = bottom, bottomStart = bottom)
}

@ThemePreviews
@Composable
private fun SingleRowPreview() {
    CatsRadarTheme {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(ListRowGap),
        ) {
            sampleOuting.forEach { SingleRow(row = it) }
            SingleRow(row = sampleLoneCat)
        }
    }
}

private val sampleOuting = listOf(
    EncountersRow.Single(
        EncounterCell("1", "14:32", LocationLabel.CURRENT, CellLead.Coat(CoatOption.TRICOLOR_MOSTLY_WHITE)),
        GroupPosition.FIRST,
    ),
    EncountersRow.Single(
        EncounterCell("2", "14:20", LocationLabel.FROM_OUTING, CellLead.Coat(CoatOption.BLACK)),
        GroupPosition.MIDDLE,
    ),
    EncountersRow.Single(EncounterCell("3", "14:10", LocationLabel.FROM_OUTING), GroupPosition.LAST),
)

private val sampleLoneCat = EncountersRow.Single(
    EncounterCell("4", "09:05", LocationLabel.NONE),
    GroupPosition.ONLY,
)
