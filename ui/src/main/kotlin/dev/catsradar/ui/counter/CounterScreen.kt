package dev.catsradar.ui.counter

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.presentation.counter.CounterState
import dev.catsradar.presentation.counter.CurrentOutingState
import dev.catsradar.presentation.statistics.RateState
import dev.catsradar.presentation.statistics.RateUnit
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
    onCoatPromptPick: (CoatOption) -> Unit = {},
    onCoatPromptDismiss: () -> Unit = {},
) {
    // A large font or a small phone must never leave the tally button zero pixels tall.
    FillOrScroll(
        minFill = 120.dp,
        gap = 16.dp,
        padding = 24.dp,
        modifier = modifier.fillMaxSize(),
        above = {
            // Above the count, which gives up its room first.
            state.importProgress?.let { ImportProgress(it) }
            state.importSummary?.let {
                ImportSummary(state = it, onUndoClick = onUndoImportClick, onDismissClick = onImportSummaryDismiss)
            }
            if (state.locationPermissionHintVisible) {
                LocationPermissionHint(onAction = onLocationHintAction)
            }
        },
        fill = {
            TallyBlock(
                totalLabel = state.totalLabel,
                count = state.count,
                tapBurst = state.tapBurst,
                onClick = onTallyClick,
            )
        },
        below = {
            CurrentOutingLine(state.currentOuting)
            WalkRow(
                walkingMode = state.walkingMode,
                walkElapsedLabel = state.walkElapsedLabel,
                undoVisible = state.undoVisible,
                onWalkingModeChange = onWalkingModeChange,
                onUndoClick = onUndoClick,
            )
            CoatGrid(highlighted = state.lastCoat, onCoatClick = onCoatTallyClick)
            PhotoButton(
                onCameraClick = onCameraClick,
                onImportClick = onImportClick,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
    state.coatPrompt?.let {
        CoatPromptSheet(prompt = it, onCoatClick = onCoatPromptPick, onDismiss = onCoatPromptDismiss)
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
private fun CounterScreenLoadingPreview() {
    CatsRadarTheme {
        Surface { CounterScreen(state = sampleCounterStateUnread) }
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

@Preview(heightDp = 600, fontScale = 1.5f)
@Composable
private fun CounterScreenCrampedPreview() {
    CatsRadarTheme {
        Surface { CounterScreen(state = sampleCounterStateLocationHintVisible) }
    }
}

@ThemePreviews
@Composable
private fun CounterScreenLocationHintVisiblePreview() {
    CatsRadarTheme {
        Surface { CounterScreen(state = sampleCounterStateLocationHintVisible) }
    }
}

private val sampleCounterStateUnread = CounterState(totalLabel = "", count = null, undoVisible = false)
private val sampleCounterStateEmpty = CounterState(totalLabel = "0", count = 0, undoVisible = false)
private val sampleCounterStateUndoVisible = CounterState(totalLabel = "3", count = 3, undoVisible = true, tapBurst = 2)
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
    walkingMode = true,
    walkElapsedLabel = "48 min",
)
