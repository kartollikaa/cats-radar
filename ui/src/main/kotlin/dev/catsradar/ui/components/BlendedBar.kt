package dev.catsradar.ui.components

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import dev.catsradar.ui.theme.contrast

private const val ShadeSteps = 100

// Each colour keeps the middle of its part and melts into its neighbour over this much of the part.
private const val BlendReach = 0.3f

/** This colour, or the least blend of it toward [toward] that stands [minContrast] apart from [track]. */
internal fun Color.legibleOn(track: Color, toward: Color, minContrast: Float): Color {
    if (contrast(this, track) >= minContrast) return this
    return (1..ShadeSteps).asSequence()
        .map { step -> lerp(this, toward, step / ShadeSteps.toFloat()) }
        .firstOrNull { contrast(it, track) >= minContrast }
        ?: toward
}

/**
 * The colour stops of a bar blending [parts], each made legible on [track]. The parts run from the one
 * closest to the track to the one furthest from it, so the bar always ends on a colour that shows.
 */
internal fun blendStops(
    parts: List<BarPart>,
    track: Color,
    toward: Color,
    minContrast: Float,
): List<Pair<Float, Color>> {
    val legible = parts
        .map { it.copy(color = it.color.legibleOn(track, toward, minContrast)) }
        .sortedBy { contrast(it.color, track) }
    val total = legible.sumOf { it.weight.toDouble() }.toFloat()
    val bounds = legible.runningFold(0f) { at, part -> at + part.weight / total }
    return legible.flatMapIndexed { index, part ->
        val reach = (bounds[index + 1] - bounds[index]) * BlendReach
        listOf(
            (if (index == 0) 0f else bounds[index] + reach) to part.color,
            (if (index == legible.lastIndex) 1f else bounds[index + 1] - reach) to part.color,
        )
    }
}

/** The brush for [blendStops], laid from the bar's start, which is its right edge when [rightToLeft]. */
internal fun blendedBrush(
    parts: List<BarPart>,
    track: Color,
    toward: Color,
    minContrast: Float,
    rightToLeft: Boolean,
): Brush {
    val logical = blendStops(parts, track, toward, minContrast)
    val stops = if (rightToLeft) logical.reversed().map { (at, color) -> 1f - at to color } else logical
    return if (stops.all { it.second == stops.first().second }) {
        SolidColor(stops.first().second)
    } else {
        @Suppress("SpreadOperator") // horizontalGradient takes its stops only as varargs.
        Brush.horizontalGradient(*stops.toTypedArray())
    }
}
