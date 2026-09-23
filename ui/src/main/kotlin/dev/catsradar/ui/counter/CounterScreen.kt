package dev.catsradar.ui.counter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
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
}

@Composable
private fun PhotoButton(
    onCameraClick: () -> Unit,
    onImportClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val height = SplitButtonDefaults.MediumContainerHeight
    SplitButtonLayout(
        modifier = modifier,
        leadingButton = {
            SplitButtonDefaults.LeadingButton(
                onClick = onCameraClick,
                modifier = Modifier.fillMaxWidth().heightIn(min = height),
                shapes = SplitButtonDefaults.leadingButtonShapesFor(height),
                contentPadding = SplitButtonDefaults.leadingButtonContentPaddingFor(height),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_photo_camera),
                    contentDescription = null,
                    modifier = Modifier
                        .padding(end = ButtonDefaults.iconSpacingFor(height))
                        .size(SplitButtonDefaults.leadingButtonIconSizeFor(height)),
                )
                Text(text = stringResource(R.string.counter_camera), style = ButtonDefaults.textStyleFor(height))
            }
        },
        trailingButton = {
            SplitButtonDefaults.TrailingButton(
                onClick = onImportClick,
                modifier = Modifier.heightIn(min = height),
                shapes = SplitButtonDefaults.trailingButtonShapesFor(height),
                contentPadding = SplitButtonDefaults.trailingButtonContentPaddingFor(height),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_photo_library),
                    contentDescription = stringResource(R.string.counter_import),
                    modifier = Modifier.size(SplitButtonDefaults.trailingButtonIconSizeFor(height)),
                )
            }
        },
    )
}

// An empty line of the same style holds its place, so an outing starting or ending leaves the count
// above it the same size at any font scale.
@Composable
private fun CurrentOutingLine(state: CurrentOutingState?, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        if (state == null) {
            Text(text = "", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.clearAndSetSemantics {})
        } else {
            CurrentOuting(state)
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
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        state.rate?.let {
            val rateRes = if (it.unit == RateUnit.PER_MINUTE) {
                R.string.statistics_rate_per_minute
            } else {
                R.string.statistics_rate_per_hour
            }
            Text(text = stringResource(rateRes, it.value), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
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
