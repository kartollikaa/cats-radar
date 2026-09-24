package dev.catsradar.ui.counter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews

/** The walk button centred across the row, Undo at its end; where the two would meet, the button gives way. */
@Composable
internal fun WalkRow(
    walkingMode: Boolean,
    walkElapsedLabel: String?,
    undoVisible: Boolean,
    modifier: Modifier = Modifier,
    onWalkingModeChange: (Boolean) -> Unit = {},
    onUndoClick: () -> Unit = {},
) {
    Layout(
        contents = listOf(
            {
                WalkButton(
                    walking = walkingMode,
                    elapsedLabel = walkElapsedLabel,
                    onWalkingChange = onWalkingModeChange,
                )
            },
            { UndoChip(visible = undoVisible, onClick = onUndoClick) },
        ),
        modifier = modifier.fillMaxWidth(),
    ) { (walkItems, undoItems), constraints ->
        val rowWidth = constraints.maxWidth
        val gap = 8.dp.roundToPx()
        // A hidden Undo leaves no layout node behind, not a zero-width one.
        val undo = undoItems.firstOrNull()?.measure(Constraints(maxWidth = rowWidth))
        val undoWidth = undo?.width ?: 0
        val walkMaxWidth = walkButtonMaxWidth(rowWidth, undoWidth, gap)
        val walk = walkItems.single().measure(
            Constraints(minWidth = walkButtonMinWidth(rowWidth, walkMaxWidth), maxWidth = walkMaxWidth),
        )
        val height = maxOf(walk.height, undo?.height ?: 0)
        layout(rowWidth, height) {
            walk.placeRelative(walkButtonStart(rowWidth, walk.width, undoWidth, gap), (height - walk.height) / 2)
            undo?.placeRelative(rowWidth - undoWidth, (height - undo.height) / 2)
        }
    }
}

internal fun walkButtonMaxWidth(rowWidth: Int, undoWidth: Int, gap: Int): Int =
    (rowWidth - undoReserve(undoWidth, gap)).coerceAtLeast(0)

internal fun walkButtonMinWidth(rowWidth: Int, maxWidth: Int): Int = minOf(rowWidth / 2, maxWidth)

internal fun walkButtonStart(rowWidth: Int, buttonWidth: Int, undoWidth: Int, gap: Int): Int =
    minOf((rowWidth - buttonWidth) / 2, rowWidth - undoReserve(undoWidth, gap) - buttonWidth).coerceAtLeast(0)

private fun undoReserve(undoWidth: Int, gap: Int): Int = if (undoWidth == 0) 0 else undoWidth + gap

@ThemePreviews
@Composable
private fun WalkRowPreview() {
    CatsRadarTheme {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(16.dp)) {
            WalkRow(walkingMode = false, walkElapsedLabel = null, undoVisible = false)
            WalkRow(walkingMode = false, walkElapsedLabel = null, undoVisible = true)
            WalkRow(walkingMode = true, walkElapsedLabel = "32 min", undoVisible = true)
        }
    }
}

@Preview(widthDp = 280, locale = "ru", fontScale = 1.3f)
@Composable
private fun WalkRowCrampedPreview() {
    CatsRadarTheme {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(16.dp)) {
            WalkRow(walkingMode = false, walkElapsedLabel = null, undoVisible = false)
            WalkRow(walkingMode = false, walkElapsedLabel = null, undoVisible = true)
            WalkRow(walkingMode = true, walkElapsedLabel = "1 ч 5 мин", undoVisible = true)
        }
    }
}
