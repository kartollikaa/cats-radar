package dev.catsradar.ui.counter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.catsradar.presentation.counter.CounterMilestoneState
import dev.catsradar.presentation.counter.CurrentOutingState
import dev.catsradar.presentation.counter.MilestoneMomentState
import dev.catsradar.presentation.statistics.MilestoneState
import dev.catsradar.ui.R
import dev.catsradar.ui.statistics.label
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal const val RingFraction = 0.76f
internal const val RingStrokeFraction = 0.026f
private const val NumberFraction = 0.58f
private val PillPadding = 4.dp
private val GoalSides = 10.dp
private val OutingSides = 12.dp
private val UndoClearance = 4.dp

/** Where the ring's stroke runs, measured from the top of the square it is drawn in. */
private fun ringLine(
    square: Float
): Float = (1 - RingFraction) / 2 * square + RingFraction * square * RingStrokeFraction / 2

/**
 * The goal at the ring's top and the outing at its bottom, over a block whose square holds the ring. The outing
 * narrows on both sides by [undoWidth], in pixels, so it stays centred and clear of Undo at the block's end.
 */
@Composable
internal fun BoxScope.RingTags(
    milestone: CounterMilestoneState?,
    moment: MilestoneMomentState?,
    currentOuting: CurrentOutingState?,
    scale: () -> Float,
    undoWidth: () -> Int = { 0 },
) {
    Box(
        modifier = Modifier
            .matchParentSize()
            .graphicsLayer {
                scaleX = scale()
                scaleY = scale()
            },
    ) {
        if (moment == null) {
            milestone?.let {
                GoalTag(next = it.next, modifier = Modifier.align(Alignment.TopCenter).onRingLine(top = true))
            }
        }
        // Its sides may overhang the block, so the bottom tag's own line keeps the block's width.
        val bottom = Modifier
            .align(Alignment.BottomCenter)
            .onRingLine(top = false, overhang = OutingSides, clearOf = undoWidth)
        if (moment != null) {
            RungPill(moment = moment, modifier = bottom)
        } else {
            currentOuting?.let {
                RingPill(contentColor = MaterialTheme.colorScheme.onSurface, sides = OutingSides, modifier = bottom) {
                    CurrentOuting(it)
                }
            }
        }
    }
}

/** The number's share of the square, less whatever the tags on the ring reach into. */
internal fun Modifier.clearOfRingTags(top: Dp, bottom: Dp): Modifier = layout { measurable, constraints ->
    val square = min(constraints.maxWidth, constraints.maxHeight)
    val inset = (1 - NumberFraction) / 2 * square
    val topInset = max(inset, ringLine(square.toFloat()) + top.toPx() / 2)
    val bottomInset = max(inset, ringLine(square.toFloat()) + bottom.toPx() / 2)
    val width = (NumberFraction * square).roundToInt()
    val height = (square - topInset - bottomInset).roundToInt().coerceAtLeast(0)
    val placeable = measurable.measure(Constraints.fixed(width, height))
    layout(square, square) { placeable.place((square - width) / 2, topInset.roundToInt()) }
}

@Composable
internal fun rememberGoalTagHeight(milestone: CounterMilestoneState?): Dp =
    rememberPillHeight(MaterialTheme.typography.labelLarge, shown = milestone != null)

@Composable
internal fun rememberOutingTagHeight(currentOuting: CurrentOutingState?): Dp =
    rememberPillHeight(MaterialTheme.typography.bodyMedium, shown = currentOuting != null)

// Measured rather than taken from the style's line height, which a large font scale does not follow exactly.
@Composable
private fun rememberPillHeight(style: TextStyle, shown: Boolean): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(measurer, style, density, shown) {
        if (!shown) 0.dp else with(density) { measurer.measure("0", style).size.height.toDp() } + PillPadding * 2
    }
}

// Centred on the ring's line; the ring's square is the largest that fits the block, centred in it.
private fun Modifier.onRingLine(
    top: Boolean,
    overhang: Dp = 0.dp,
    clearOf: () -> Int = { 0 },
): Modifier = layout { measurable, constraints ->
    val square = min(constraints.maxWidth, constraints.maxHeight)
    val corner = clearOf()
    val free = constraints.maxWidth + 2 * overhang.roundToPx()
    val width = if (corner == 0) free else min(free, constraints.maxWidth - 2 * (corner + UndoClearance.roundToPx()))
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0, maxWidth = width.coerceAtLeast(0)))
    val line = (constraints.maxHeight - square) / 2f + ringLine(square.toFloat())
    layout(placeable.width, placeable.height) {
        val shift = (line - placeable.height / 2f).roundToInt()
        placeable.place(0, if (top) shift else -shift)
    }
}

@Composable
private fun GoalTag(next: MilestoneState, modifier: Modifier = Modifier) {
    val label = next.label()
    RingPill(
        contentColor = MaterialTheme.colorScheme.primary,
        sides = GoalSides,
        // Its own node, read as the Statistics line, so the number stays out of the block's label.
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = label },
    ) {
        Icon(painter = painterResource(R.drawable.ic_flag), contentDescription = null, modifier = Modifier.size(14.dp))
        Text(
            text = next.valueLabel,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            // Its own node, hidden, so the pill reads only as the Statistics line.
            modifier = Modifier.semantics(mergeDescendants = true) { hideFromAccessibility() },
        )
    }
}

@Composable
private fun RungPill(moment: MilestoneMomentState, modifier: Modifier = Modifier) {
    RingPill(
        contentColor = MaterialTheme.colorScheme.onPrimary,
        sides = OutingSides,
        color = MaterialTheme.colorScheme.primary,
        // Its own node, spoken once as it appears, where the milestone toast used to speak.
        modifier = modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(
            text = pluralStringResource(R.plurals.counter_milestone, moment.value, moment.value),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun RingPill(
    contentColor: Color,
    sides: Dp,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = color,
        contentColor = contentColor,
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = sides, vertical = PillPadding),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}
