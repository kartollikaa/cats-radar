package dev.catsradar.ui.statistics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.catsradar.presentation.statistics.BestOutingState
import dev.catsradar.presentation.statistics.DistanceState
import dev.catsradar.presentation.statistics.DistanceUnit
import dev.catsradar.presentation.statistics.RateState
import dev.catsradar.presentation.statistics.RateUnit
import dev.catsradar.presentation.statistics.StatisticsState
import dev.catsradar.presentation.statistics.WalkedState
import dev.catsradar.ui.R
import dev.catsradar.ui.components.SectionCard
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews

const val OutingFigureTestTag = "outing-figure"

/** The outings' figures two to a row; the walked pair only once something was walked. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OutingsGrid(state: StatisticsState, modifier: Modifier = Modifier) {
    SectionCard(R.string.statistics_outings, modifier = modifier) {
        FlowRow(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            maxItemsInEachRow = 2,
        ) {
            OutingFigure(state.outingsLabel, stringResource(R.string.statistics_outing_count), Modifier.weight(1f))
            OutingFigure(state.activeTimeLabel, stringResource(R.string.statistics_active_time), Modifier.weight(1f))
            OutingFigure(
                value = state.overallRate.label(),
                label = stringResource(R.string.statistics_overall_rate),
                modifier = Modifier.weight(1f),
            )
            state.walked?.let { walked ->
                OutingFigure(walked.distance.label(), stringResource(R.string.statistics_walked), Modifier.weight(1f))
                OutingFigure(
                    value = walked.catsPerKm?.let { stringResource(R.string.statistics_rate_per_km, it) }
                        ?: stringResource(R.string.statistics_rate_unavailable),
                    label = stringResource(R.string.statistics_cats_per_km),
                    modifier = Modifier.weight(1f),
                )
            }
            state.bestOuting?.let { best ->
                OutingFigure(
                    value = pluralStringResource(
                        R.plurals.statistics_best_outing_value,
                        best.count,
                        best.count,
                        best.durationLabel,
                    ),
                    label = stringResource(R.string.statistics_best_outing_with_rate, best.rate.label()),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun OutingFigure(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {}.testTag(OutingFigureTestTag),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Figure(
            value = value,
            style = MaterialTheme.typography.titleMediumEmphasized,
            unitStyle = MaterialTheme.typography.labelMedium,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DistanceState.label(): String = when (unit) {
    DistanceUnit.METERS -> stringResource(R.string.statistics_distance_meters, value)
    DistanceUnit.KILOMETERS -> stringResource(R.string.statistics_distance_kilometers, value)
}

@ThemePreviews
@Composable
private fun OutingsGridPreview() {
    CatsRadarTheme {
        OutingsGrid(
            state = StatisticsState(
                outingsLabel = "38",
                activeTimeLabel = "14 h 20 min",
                overallRate = RateState(value = "4.2", unit = RateUnit.PER_HOUR),
                walked = WalkedState(DistanceState("42.7", DistanceUnit.KILOMETERS), catsPerKm = null),
                bestOuting = BestOutingState(
                    count = 9,
                    durationLabel = "42 min",
                    rate = RateState(value = "1.3", unit = RateUnit.PER_MINUTE),
                ),
            ),
            modifier = Modifier.padding(16.dp),
        )
    }
}
