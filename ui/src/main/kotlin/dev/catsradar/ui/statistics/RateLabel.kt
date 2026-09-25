package dev.catsradar.ui.statistics

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.catsradar.presentation.statistics.RateState
import dev.catsradar.presentation.statistics.RateUnit
import dev.catsradar.ui.R

@Composable
internal fun RateState?.label(): String = when {
    this == null -> stringResource(R.string.statistics_rate_unavailable)
    unit == RateUnit.PER_MINUTE -> stringResource(R.string.statistics_rate_per_minute, value)
    else -> stringResource(R.string.statistics_rate_per_hour, value)
}
