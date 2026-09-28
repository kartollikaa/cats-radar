package dev.catsradar.ui.encounters

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.catsradar.presentation.encounters.EncountersRow
import dev.catsradar.presentation.encounters.EncountersTotals
import dev.catsradar.presentation.encounters.GroupPosition
import dev.catsradar.presentation.encounters.OutingHeader
import dev.catsradar.ui.R
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews

const val OutingCardTestTag = "outing-card"
const val WalkChipTestTag = "walk-chip"
const val MapPillIconTestTag = "map-pill-icon"

private val CardPadding = 8.dp
private val HeaderStart = 4.dp

/** The headline as the list's leading item, the same lambda while [totals] stay the same; none without totals. */
@Composable
internal fun rememberHeadline(totals: EncountersTotals?, modifier: Modifier = Modifier): (@Composable () -> Unit)? =
    remember(totals, modifier) {
        totals?.let { shown -> @Composable { EncountersHeadline(totals = shown, modifier = modifier) } }
    }

/** The tab's title and how many cats and outings it holds, lined up with the day heading each outing's card. */
@Composable
internal fun EncountersHeadline(totals: EncountersTotals, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(start = CardPadding + HeaderStart),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = stringResource(R.string.tab_encounters),
            style = MaterialTheme.typography.headlineMediumEmphasized,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(
                R.string.encounters_totals,
                pluralStringResource(R.plurals.encounters_total_cats, totals.cats, totals.cats),
                pluralStringResource(R.plurals.encounters_total_outings, totals.outings, totals.outings),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * One piece of an outing's card: the header's piece takes the card's top corners and the outing's last row its bottom
 * ones, so the pieces of one outing join into a single card; [gap] separates a row from the next inside it.
 */
@Composable
internal fun OutingCardPiece(
    modifier: Modifier = Modifier,
    opens: Boolean = false,
    closes: Boolean = false,
    gap: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    val corners = MaterialTheme.shapes.extraLarge
    val shape = when {
        opens && closes -> corners
        opens -> corners.copy(bottomStart = ZeroCornerSize, bottomEnd = ZeroCornerSize)
        closes -> corners.copy(topStart = ZeroCornerSize, topEnd = ZeroCornerSize)
        else -> RectangleShape
    }
    Box(
        modifier = modifier
            .testTag(OutingCardTestTag)
            .background(MaterialTheme.colorScheme.surfaceContainerLow, shape)
            .padding(start = CardPadding, end = CardPadding, bottom = if (closes) CardPadding else gap),
    ) {
        content()
    }
}

// The pill sits beside the day alone, so the summary under them keeps the card's whole width at a large font.
@Composable
internal fun OutingCardHeader(header: OutingHeader, modifier: Modifier = Modifier, onMapClick: (String) -> Unit = {}) {
    Column(
        modifier = modifier.padding(start = HeaderStart, top = 12.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = header.dayLabel,
                style = MaterialTheme.typography.titleLargeEmphasized,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            header.mapOutingId?.let { id -> OnTheMapPill(outingLabel = header.label, onClick = { onMapClick(id) }) }
        }
        Text(
            text = header.summary(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (header.onWalk) WalkChip(modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun OutingHeader.summary(): String {
    val span = spanLabel
    return if (span != null) {
        pluralStringResource(R.plurals.encounters_outing_summary, count, startLabel, count, span)
    } else {
        pluralStringResource(R.plurals.encounters_outing_summary_short, count, startLabel, count)
    }
}

@Composable
private fun WalkChip(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.testTag(WalkChipTestTag),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.cat_walk_0),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Text(text = stringResource(R.string.encounters_on_a_walk), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun OnTheMapPill(outingLabel: String, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    val description = stringResource(R.string.encounters_outing_on_map_description, outingLabel)
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = description },
        contentPadding = ButtonDefaults.SmallContentPadding,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_nav_map),
            contentDescription = null,
            modifier = Modifier.size(18.dp).testTag(MapPillIconTestTag),
        )
        Text(text = stringResource(R.string.encounters_outing_on_map), modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
internal fun OutingCardRow(
    row: EncountersRow,
    modifier: Modifier = Modifier,
    rowGap: Dp = CellGap,
    selecting: Boolean = false,
    onEncounterClick: (String) -> Unit = {},
    onEncounterLongClick: ((String) -> Unit)? = null,
    onOutingMapClick: (String) -> Unit = {},
) {
    val insideCard = MaterialTheme.colorScheme.surface
    when (row) {
        is OutingHeader -> OutingCardPiece(modifier = modifier.padding(top = 12.dp), opens = true) {
            OutingCardHeader(header = row, onMapClick = onOutingMapClick)
        }
        is EncountersRow.PhotoPair -> OutingCardPiece(modifier = modifier, closes = row.closesOuting, gap = rowGap) {
            PhotoPairRow(row, Modifier, selecting, onEncounterClick, onEncounterLongClick)
        }
        is EncountersRow.Tiles -> OutingCardPiece(modifier = modifier, closes = row.closesOuting, gap = rowGap) {
            TileRow(row, Modifier, selecting, onEncounterClick, onEncounterLongClick)
        }
        is EncountersRow.Cards -> OutingCardPiece(modifier = modifier, closes = row.closesOuting, gap = rowGap) {
            CardRow(row, Modifier, selecting, insideCard, onEncounterClick, onEncounterLongClick)
        }
        is EncountersRow.Single -> OutingCardPiece(
            modifier = modifier,
            closes = row.position == GroupPosition.LAST || row.position == GroupPosition.ONLY,
            gap = rowGap,
        ) {
            SingleRow(row, Modifier, selecting, insideCard, onEncounterClick, onEncounterLongClick)
        }
    }
}

@ThemePreviews
@Composable
private fun OutingCardPreview() {
    CatsRadarTheme {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            EncountersHeadline(totals = EncountersTotals(cats = 147, outings = 38))
            OutingCardPiece(modifier = Modifier.fillMaxWidth(), opens = true, closes = true) {
                OutingCardHeader(header = sampleWalkedHeader)
            }
            OutingCardPiece(modifier = Modifier.fillMaxWidth(), opens = true, closes = true) {
                OutingCardHeader(header = sampleLoneHeader)
            }
        }
    }
}

private val sampleWalkedHeader = OutingHeader(
    key = "header-1",
    label = "Today, 4:12 PM",
    mapOutingId = "1",
    dayLabel = "Today",
    startLabel = "4:12 PM",
    count = 6,
    spanLabel = "48 min",
    onWalk = true,
)

private val sampleLoneHeader = OutingHeader(
    key = "header-2",
    label = "Sep 24, 2026, 11:17 PM",
    dayLabel = "Sep 24, 2026",
    startLabel = "11:17 PM",
    count = 1,
)
