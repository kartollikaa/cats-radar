package dev.catsradar.ui.counter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import dev.catsradar.presentation.counter.CurrentOutingState
import dev.catsradar.presentation.statistics.RateState
import dev.catsradar.presentation.statistics.RateUnit
import dev.catsradar.ui.R
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews

/** The outing line centred across the row, Undo at its end; where the two would meet, the line gives way. */
@Composable
internal fun OutingRow(
    outing: CurrentOutingState?,
    undoVisible: Boolean,
    modifier: Modifier = Modifier,
    onUndoClick: () -> Unit = {},
) {
    Layout(
        contents = listOf(
            { outing?.let { CurrentOuting(it) } },
            { UndoChip(visible = undoVisible, onClick = onUndoClick) },
        ),
        // Never shorter than Undo, so neither it nor the outing coming and going resizes the count.
        modifier = modifier.fillMaxWidth().minimumInteractiveComponentSize(),
    ) { (lineItems, undoItems), constraints ->
        val rowWidth = constraints.maxWidth
        val gap = 8.dp.roundToPx()
        // A hidden Undo leaves no layout node behind, not a zero-width one.
        val undo = undoItems.firstOrNull()?.measure(Constraints(maxWidth = rowWidth))
        val undoWidth = undo?.width ?: 0
        val line = lineItems.firstOrNull()?.measure(Constraints(maxWidth = lineMaxWidth(rowWidth, undoWidth, gap)))
        val height = maxOf(line?.height ?: 0, undo?.height ?: 0)
        layout(rowWidth, height) {
            line?.placeRelative(lineStart(rowWidth, line.width, undoWidth, gap), (height - line.height) / 2)
            undo?.placeRelative(rowWidth - undoWidth, (height - undo.height) / 2)
        }
    }
}

internal fun lineMaxWidth(rowWidth: Int, undoWidth: Int, gap: Int): Int =
    (rowWidth - undoReserve(undoWidth, gap)).coerceAtLeast(0)

internal fun lineStart(rowWidth: Int, lineWidth: Int, undoWidth: Int, gap: Int): Int =
    minOf((rowWidth - lineWidth) / 2, rowWidth - undoReserve(undoWidth, gap) - lineWidth).coerceAtLeast(0)

private fun undoReserve(undoWidth: Int, gap: Int): Int = if (undoWidth == 0) 0 else undoWidth + gap

@Composable
private fun CurrentOuting(state: CurrentOutingState, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
private fun OutingRowPreview() {
    CatsRadarTheme {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(16.dp)) {
            OutingRow(outing = null, undoVisible = false)
            OutingRow(outing = null, undoVisible = true)
            OutingRow(outing = sampleOuting, undoVisible = false)
            OutingRow(outing = sampleOuting, undoVisible = true)
        }
    }
}

@Preview(widthDp = 280, locale = "ru", fontScale = 1.3f)
@Composable
private fun OutingRowCrampedPreview() {
    CatsRadarTheme {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(16.dp)) {
            OutingRow(outing = sampleOuting, undoVisible = false)
            OutingRow(outing = sampleOuting, undoVisible = true)
        }
    }
}

private val sampleOuting = CurrentOutingState(
    count = 4,
    elapsedLabel = "35 min",
    rate = RateState(value = "6.9", unit = RateUnit.PER_HOUR),
)
