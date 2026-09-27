package dev.catsradar.ui.statistics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.presentation.statistics.BestOutingState
import dev.catsradar.presentation.statistics.ChartRange
import dev.catsradar.presentation.statistics.CoatShareState
import dev.catsradar.presentation.statistics.DistanceState
import dev.catsradar.presentation.statistics.DistanceUnit
import dev.catsradar.presentation.statistics.MilestoneState
import dev.catsradar.presentation.statistics.RateState
import dev.catsradar.presentation.statistics.RateUnit
import dev.catsradar.presentation.statistics.StatisticsState
import dev.catsradar.presentation.statistics.WalkedState
import dev.catsradar.ui.R
import dev.catsradar.ui.coat.CatFace
import dev.catsradar.ui.coat.faceRim
import dev.catsradar.ui.coat.labelRes
import dev.catsradar.ui.coat.look
import dev.catsradar.ui.components.EmptyState
import dev.catsradar.ui.components.HeadlineCard
import dev.catsradar.ui.components.SectionCard
import dev.catsradar.ui.components.ShareBar
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

// A coat nobody noted keeps a blank of the same size, so the names still line up.
private val CoatFaceSize = 28.dp

@Composable
fun StatisticsScreen(
    state: StatisticsState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    onRangeClick: (ChartRange) -> Unit = {},
    onDayClick: (Long) -> Unit = {},
    onPlacesClick: () -> Unit = {},
) {
    if (!state.hasAnyCats) {
        EmptyStatistics(modifier = modifier.fillMaxSize().padding(contentPadding))
        return
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Headline(state)
        DayChart(chart = state.chart, onRangeClick = onRangeClick, onDayClick = onDayClick)
        StatTiles(state)
        ByCoatSection(state.byCoat)
        PlacesCard(onClick = onPlacesClick)
        OutingsGrid(state)
    }
}

@Composable
private fun Headline(state: StatisticsState, modifier: Modifier = Modifier) {
    HeadlineCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(vertical = 24.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = state.total.toString(), style = MaterialTheme.typography.displayLargeEmphasized)
            Text(
                text = pluralStringResource(R.plurals.statistics_total, state.total),
                style = MaterialTheme.typography.titleMedium,
            )
            state.nextMilestone?.let { MilestoneLine(it, modifier = Modifier.padding(top = 8.dp)) }
        }
    }
}

@Composable
private fun MilestoneLine(milestone: MilestoneState, modifier: Modifier = Modifier) {
    Text(
        text = milestone.label(),
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier,
    )
}

@Composable
private fun ByCoatSection(shares: ImmutableList<CoatShareState>, modifier: Modifier = Modifier) {
    if (shares.isEmpty()) return
    SectionCard(R.string.statistics_by_coat, modifier = modifier) {
        shares.forEach { share -> CoatShareRow(share) }
    }
}

@Composable
private fun CoatShareRow(share: CoatShareState, modifier: Modifier = Modifier) {
    val coat = share.coat
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (coat != null) {
            CatFace(coat = coat, modifier = Modifier.size(CoatFaceSize))
        } else {
            Box(modifier = Modifier.size(CoatFaceSize))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(coat?.labelRes() ?: R.string.coat_not_specified),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.statistics_coat_share, share.countLabel, share.sharePercentLabel),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            // A white coat on the light track and a black one on the dark track need the faces' own rim to show.
            ShareBar(
                share = share.share,
                color = coat?.look()?.fur ?: colors.outline,
                rim = coat?.let { colors.faceRim() },
            )
        }
    }
}

@Composable
private fun PlacesCard(modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = stringResource(R.string.statistics_places), style = MaterialTheme.typography.titleMedium)
            Icon(painter = painterResource(R.drawable.ic_chevron_right), contentDescription = null)
        }
    }
}

@Composable
private fun EmptyStatistics(modifier: Modifier = Modifier) {
    EmptyState(
        iconRes = R.drawable.ic_nav_bar_chart,
        title = stringResource(R.string.statistics_empty),
        modifier = modifier,
    )
}

@ThemePreviews
@Composable
private fun StatisticsScreenPreview() {
    CatsRadarTheme {
        Surface { StatisticsScreen(state = sampleStatistics) }
    }
}

@ThemePreviews
@Composable
private fun StatisticsScreenMonthNoWalksPreview() {
    CatsRadarTheme {
        Surface {
            StatisticsScreen(state = sampleStatistics.copy(chart = sampleChart(ChartRange.MONTH), walked = null))
        }
    }
}

@ThemePreviews
@Composable
private fun StatisticsScreenEmptyPreview() {
    CatsRadarTheme {
        Surface { StatisticsScreen(state = StatisticsState()) }
    }
}

private val sampleStatistics = StatisticsState(
    total = 147,
    hasAnyCats = true,
    chart = sampleChart(ChartRange.WEEK),
    todayLabel = "3",
    weekLabel = "19",
    monthLabel = "64",
    withPhotoLabel = "41",
    byCoat = persistentListOf(
        CoatShareState(CoatOption.GINGER_WHITE, countLabel = "38", sharePercentLabel = "26", share = 1f),
        CoatShareState(CoatOption.BLACK, countLabel = "29", sharePercentLabel = "20", share = 0.76f),
        CoatShareState(CoatOption.WHITE, countLabel = "22", sharePercentLabel = "15", share = 0.58f),
        CoatShareState(coat = null, countLabel = "31", sharePercentLabel = "21", share = 0.82f),
    ),
    currentStreak = 6,
    longestStreak = 23,
    nextMilestone = MilestoneState(valueLabel = "250", remainingLabel = "103"),
    outingsLabel = "38",
    activeTimeLabel = "14 h 20 min",
    overallRate = RateState(value = "4.2", unit = RateUnit.PER_HOUR),
    walked = WalkedState(DistanceState("42.7", DistanceUnit.KILOMETERS), catsPerKm = "3.1"),
    bestOuting = BestOutingState(
        count = 9,
        durationLabel = "42 min",
        rate = RateState(value = "1.3", unit = RateUnit.PER_MINUTE),
    ),
)
