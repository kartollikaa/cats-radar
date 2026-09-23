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
 * Stacks [above], [fill] and [below] with [gap] between every item, centred across. [fill] takes
 * the height the others leave, but never less than [minFill]: past that the stack scrolls instead.
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
        Layout(
            contents = listOf(above, fill, below),
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(padding),
        ) { (aboveItems, fillItems, belowItems), constraints ->
            val width = constraints.maxWidth
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
