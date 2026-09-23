package dev.catsradar.ui.counter

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp

/**
 * Stacks [above], one [fill] item and [below] with [gap] between every item, centred across. [fill]
 * takes the height the others leave, but never less than [minFill]: past that the stack scrolls.
 */
@Composable
internal fun FillOrScroll(
    minFill: Dp,
    gap: Dp,
    padding: Dp,
    modifier: Modifier = Modifier,
    above: @Composable () -> Unit = {},
    fill: @Composable () -> Unit = {},
    below: @Composable () -> Unit = {},
) {
    BoxWithConstraints(modifier) {
        val viewportHeight = constraints.maxHeight
        val scroll = rememberScrollState()
        Layout(
            contents = listOf(above, fill, below),
            // Enabled only on overflow: an enabled scroll delays its children's press feedback and
            // turns a tap that drifts past touch slop into a drag.
            modifier = Modifier.verticalScroll(scroll, enabled = scroll.maxValue > 0).padding(padding),
        ) { (aboveItems, fillItems, belowItems), stackConstraints ->
            val width = stackConstraints.maxWidth
            val gapPx = gap.roundToPx()
            val loose = Constraints(maxWidth = width)
            val abovePlaced = aboveItems.map { it.measure(loose) }
            val belowPlaced = belowItems.map { it.measure(loose) }
            val itemCount = abovePlaced.size + belowPlaced.size + fillItems.size
            val othersHeight = (abovePlaced + belowPlaced).sumOf { it.height } + gapPx * (itemCount - 1)
            val room = if (viewportHeight == Constraints.Infinity) 0 else viewportHeight - 2 * padding.roundToPx()
            val fillHeight = maxOf(minFill.roundToPx(), room - othersHeight)
            val fillPlaced = fillItems.map { it.measure(Constraints.fixed(width, fillHeight)) }
            val stack = abovePlaced + fillPlaced + belowPlaced
            layout(width, stack.sumOf { it.height } + gapPx * (stack.size - 1).coerceAtLeast(0)) {
                var y = 0
                stack.forEach { placeable ->
                    placeable.placeRelative((width - placeable.width) / 2, y)
                    y += placeable.height + gapPx
                }
            }
        }
    }
}
