package dev.catsradar.ui.counter

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.catsradar.presentation.counter.CounterState
import dev.catsradar.presentation.counter.CurrentOutingState
import dev.catsradar.presentation.statistics.RateState
import dev.catsradar.presentation.statistics.RateUnit
import dev.catsradar.ui.R
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews

@Composable
fun CounterScreen(
    state: CounterState,
    modifier: Modifier = Modifier,
    onTallyClick: () -> Unit = {},
    onUndoClick: () -> Unit = {},
    onLocationHintAction: (LocationHintAction) -> Unit = {},
    onCameraClick: () -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(
            onClick = onTallyClick,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text(text = state.totalLabel, style = MaterialTheme.typography.displayLarge)
                TapBurst(count = state.tapBurst, modifier = Modifier.align(Alignment.TopCenter).padding(top = 32.dp))
            }
        }
        state.currentOuting?.let { CurrentOuting(it) }
        if (state.undoVisible) {
            AssistChip(onClick = onUndoClick, label = { Text(text = stringResource(R.string.counter_undo)) })
        }
        Button(onClick = onCameraClick, modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(R.string.counter_camera))
        }
        if (state.locationPermissionHintVisible) {
            LocationPermissionHint(onAction = onLocationHintAction)
        }
    }
}

@Composable
private fun TapBurst(count: Int?, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = count != null,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut(),
        modifier = modifier,
    ) {
        // Held after the state clears so the exit animation has something to fade out.
        val lastShown = remember { mutableIntStateOf(1) }
        count?.let { lastShown.intValue = it }
        Text(
            text = stringResource(R.string.counter_tap_burst, lastShown.intValue),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun CurrentOuting(state: CurrentOutingState, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
    ) {
        Text(
            text = pluralStringResource(R.plurals.counter_outing_now, state.count, state.count, state.elapsedLabel),
            style = MaterialTheme.typography.bodyMedium,
        )
        state.rate?.let {
            val rateRes = if (it.unit == RateUnit.PER_MINUTE) {
                R.string.statistics_rate_per_minute
            } else {
                R.string.statistics_rate_per_hour
            }
            Text(text = stringResource(rateRes, it.value), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun LocationPermissionHint(
    onAction: (LocationHintAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.counter_location_hint),
            style = MaterialTheme.typography.bodySmall,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { onAction(LocationHintAction.GRANT) }) {
                Text(text = stringResource(R.string.counter_location_grant))
            }
            TextButton(onClick = { onAction(LocationHintAction.DISMISS) }) {
                Text(text = stringResource(R.string.counter_location_dismiss))
            }
        }
    }
}

@ThemePreviews
@Composable
private fun CounterScreenEmptyPreview() {
    CatsRadarTheme {
        Surface { CounterScreen(state = sampleCounterStateEmpty) }
    }
}

@ThemePreviews
@Composable
private fun CounterScreenUndoVisiblePreview() {
    CatsRadarTheme {
        Surface { CounterScreen(state = sampleCounterStateUndoVisible) }
    }
}

@ThemePreviews
@Composable
private fun CounterScreenOutingInProgressPreview() {
    CatsRadarTheme {
        Surface { CounterScreen(state = sampleCounterStateOutingInProgress) }
    }
}

@ThemePreviews
@Composable
private fun CounterScreenLocationHintVisiblePreview() {
    CatsRadarTheme {
        Surface { CounterScreen(state = sampleCounterStateLocationHintVisible) }
    }
}

private val sampleCounterStateEmpty = CounterState(totalLabel = "0", undoVisible = false)
private val sampleCounterStateUndoVisible = CounterState(totalLabel = "3", undoVisible = true)
private val sampleCounterStateLocationHintVisible =
    CounterState(totalLabel = "3", undoVisible = false, locationPermissionHintVisible = true)
private val sampleCounterStateOutingInProgress = CounterState(
    totalLabel = "12",
    undoVisible = false,
    currentOuting = CurrentOutingState(
        count = 4,
        elapsedLabel = "35 min",
        rate = RateState(value = "6.9", unit = RateUnit.PER_HOUR),
    ),
    tapBurst = 2,
)
