package dev.catsradar.ui.counter

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.presentation.counter.CounterState
import dev.catsradar.presentation.counter.CurrentOutingState
import dev.catsradar.presentation.statistics.RateState
import dev.catsradar.presentation.statistics.RateUnit
import dev.catsradar.ui.R
import dev.catsradar.ui.coat.CoatGrid
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
    onCoatTallyClick: (CoatOption) -> Unit = {},
    onImportClick: () -> Unit = {},
    onUndoImportClick: () -> Unit = {},
    onImportSummaryDismiss: () -> Unit = {},
    onWalkingModeChange: (Boolean) -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TallyBlock(
            totalLabel = state.totalLabel,
            count = state.count,
            tapBurst = state.tapBurst,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            onClick = onTallyClick,
        )
        state.currentOuting?.let { CurrentOuting(it) }
        WalkingModeChip(checked = state.walkingMode, onCheckedChange = onWalkingModeChange)
        CoatGrid(highlighted = state.lastCoat, onCoatClick = onCoatTallyClick)
        if (state.undoVisible) {
            AssistChip(onClick = onUndoClick, label = { Text(text = stringResource(R.string.counter_undo)) })
        }
        CameraButton(onClick = onCameraClick, onLongClick = onImportClick, modifier = Modifier.fillMaxWidth())
        state.importProgress?.let { ImportProgress(it) }
        state.importSummary?.let {
            ImportSummary(state = it, onUndoClick = onUndoImportClick, onDismissClick = onImportSummaryDismiss)
        }
        if (state.locationPermissionHintVisible) {
            LocationPermissionHint(onAction = onLocationHintAction)
        }
    }
}

// A long press is the only entry to import, so the button says so out loud: a gesture nothing
// hints at is a gesture nobody finds.
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CameraButton(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick,
            onLongClickLabel = stringResource(R.string.counter_import),
        ),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = stringResource(R.string.counter_camera), style = MaterialTheme.typography.labelLarge)
            Text(
                text = stringResource(R.string.counter_import_hint),
                style = MaterialTheme.typography.labelSmall,
            )
        }
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

private val sampleCounterStateEmpty = CounterState(totalLabel = "0", count = 0, undoVisible = false)
private val sampleCounterStateUndoVisible = CounterState(totalLabel = "3", count = 3, undoVisible = true)
private val sampleCounterStateLocationHintVisible =
    CounterState(totalLabel = "3", count = 3, undoVisible = false, locationPermissionHintVisible = true)
private val sampleCounterStateOutingInProgress = CounterState(
    totalLabel = "12",
    count = 12,
    undoVisible = false,
    currentOuting = CurrentOutingState(
        count = 4,
        elapsedLabel = "35 min",
        rate = RateState(value = "6.9", unit = RateUnit.PER_HOUR),
    ),
    tapBurst = 2,
)
