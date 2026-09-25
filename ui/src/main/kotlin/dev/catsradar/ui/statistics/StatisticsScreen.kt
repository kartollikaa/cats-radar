package dev.catsradar.ui.statistics

import androidx.annotation.StringRes
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
import androidx.compose.ui.unit.dp
import dev.catsradar.presentation.statistics.BestOutingState
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
import dev.catsradar.ui.coat.labelRes
import dev.catsradar.ui.components.EmptyState
import dev.catsradar.ui.components.HeadlineCard
import dev.catsradar.ui.components.SectionCard
import dev.catsradar.ui.components.ValueRow
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews
import kotlinx.collections.immutable.ImmutableList

// A coat nobody noted keeps a blank of the same size, so the names still line up.
private val CoatFaceSize = 28.dp

@Composable
fun StatisticsScreen(
    state: StatisticsState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
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
        SectionCard(R.string.statistics_when) {
            StatRow(R.string.statistics_today, state.todayLabel)
            StatRow(R.string.statistics_week, state.weekLabel)
            StatRow(R.string.statistics_month, state.monthLabel)
            StatRow(R.string.statistics_with_photo, state.withPhotoLabel)
        }
        ByCoatSection(state.byCoat)
        SectionCard(R.string.statistics_streaks) {
            StatRow(R.string.statistics_current_streak, state.currentStreakLabel)
            StatRow(R.string.statistics_longest_streak, state.longestStreakLabel)
        }
        PlacesCard(onClick = onPlacesClick)
        SectionCard(R.string.statistics_outings) {
            StatRow(R.string.statistics_outing_count, state.outingsLabel)
            StatRow(R.string.statistics_active_time, state.activeTimeLabel)
            StatRow(R.string.statistics_overall_rate, state.overallRate.label())
            state.walked?.let { walked ->
                val distance = walked.distance
                val distanceLabel = when (distance.unit) {
                    DistanceUnit.METERS -> stringResource(R.string.statistics_distance_meters, distance.value)
                    DistanceUnit.KILOMETERS -> stringResource(R.string.statistics_distance_kilometers, distance.value)
                }
                val catsPerKmLabel = walked.catsPerKm?.let { stringResource(R.string.statistics_rate_per_km, it) }
                    ?: stringResource(R.string.statistics_rate_unavailable)
                StatRow(R.string.statistics_walked, distanceLabel)
                StatRow(R.string.statistics_cats_per_km, catsPerKmLabel)
            }
            state.bestOuting?.let { best ->
                StatRow(
                    R.string.statistics_best_outing,
                    pluralStringResource(
                        R.plurals.statistics_best_outing_value,
                        best.count,
                        best.count,
                        best.durationLabel,
                    ),
                )
                StatRow(R.string.statistics_best_outing_rate, best.rate.label())
            }
        }
    }
}

@Composable
private fun ByCoatSection(shares: ImmutableList<CoatShareState>, modifier: Modifier = Modifier) {
    if (shares.isEmpty()) return
    SectionCard(R.string.statistics_by_coat, modifier = modifier) {
        shares.forEach { share ->
            ValueRow(
                label = stringResource(share.coat?.labelRes() ?: R.string.coat_not_specified),
                value = stringResource(R.string.statistics_coat_share, share.countLabel, share.sharePercentLabel),
                leading = {
                    val coat = share.coat
                    if (coat != null) {
                        CatFace(coat = coat, modifier = Modifier.size(CoatFaceSize))
                    } else {
                        Box(modifier = Modifier.size(CoatFaceSize))
                    }
                },
            )
        }
    }
}

@Composable
private fun Headline(state: StatisticsState, modifier: Modifier = Modifier) {
    HeadlineCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(vertical = 24.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = state.total.toString(), style = MaterialTheme.typography.displayLarge)
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
        text = stringResource(R.string.statistics_next_milestone, milestone.remainingLabel, milestone.valueLabel),
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier,
    )
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
private fun StatRow(@StringRes labelRes: Int, value: String, modifier: Modifier = Modifier) {
    ValueRow(label = stringResource(labelRes), value = value, modifier = modifier)
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
private fun StatisticsScreenEmptyPreview() {
    CatsRadarTheme {
        Surface { StatisticsScreen(state = StatisticsState()) }
    }
}

private val sampleStatistics = StatisticsState(
    total = 147,
    hasAnyCats = true,
    todayLabel = "3",
    weekLabel = "19",
    monthLabel = "64",
    withPhotoLabel = "41",
    currentStreakLabel = "6",
    longestStreakLabel = "23",
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
