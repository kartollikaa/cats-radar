package dev.catsradar.ui.counter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.catsradar.presentation.counter.CounterMilestoneState
import dev.catsradar.presentation.counter.CurrentOutingState
import dev.catsradar.presentation.statistics.RateState
import dev.catsradar.presentation.statistics.RateUnit
import dev.catsradar.ui.R
import dev.catsradar.ui.statistics.label
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews

// With no outing open the next milestone, or an empty line of the same style, holds the outing's place,
// so an outing starting or ending leaves the count above it the same size at any font scale.
@Composable
internal fun CurrentOutingLine(
    state: CurrentOutingState?,
    modifier: Modifier = Modifier,
    milestone: CounterMilestoneState? = null,
) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        when {
            state != null -> CurrentOuting(state)
            milestone != null -> Text(
                text = stringResource(
                    R.string.statistics_next_milestone,
                    milestone.next.remainingLabel,
                    milestone.next.valueLabel,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            else -> Text(
                text = "",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}

@Composable
private fun CurrentOuting(state: CurrentOutingState, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(OutingGap, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Short of room the rate stays whole: the count gives way first, then the time.
        Row(
            modifier = Modifier.weight(1f, fill = false),
            horizontalArrangement = Arrangement.spacedBy(OutingGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = pluralStringResource(R.plurals.counter_outing_cats, state.count, state.count),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            OutingSeparator()
            Text(
                text = state.elapsedLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        state.rate?.let {
            OutingSeparator()
            Text(
                text = it.label(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

// The inner and the outer row must part their items alike, or the dots stop being evenly spaced.
private val OutingGap = 8.dp

@Composable
private fun OutingSeparator(modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(4.dp).background(MaterialTheme.colorScheme.outline, CircleShape))
}

@ThemePreviews
@Composable
private fun CurrentOutingPreview() {
    CatsRadarTheme {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(16.dp)) {
            CurrentOuting(sampleOutingWithRate)
            CurrentOuting(sampleOutingWithRate.copy(count = 1, elapsedLabel = "2 min", rate = null))
        }
    }
}

private val sampleOutingWithRate = CurrentOutingState(
    count = 4,
    elapsedLabel = "35 min",
    rate = RateState(value = "6.9", unit = RateUnit.PER_HOUR),
)
