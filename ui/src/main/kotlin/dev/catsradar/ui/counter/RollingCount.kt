package dev.catsradar.ui.counter

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private val MaxCountSize = 112.sp
private const val RollMillis = 450

@Immutable
internal data class DigitSlot(val place: Int, val digit: Char?)

/** One cell per place, the units last, behind an empty cell for a gained digit to roll into. */
internal fun digitSlots(label: String): List<DigitSlot> {
    val cells = listOf(null) + label.toList()
    return cells.mapIndexed { index, digit -> DigitSlot(place = cells.lastIndex - index, digit = digit) }
}

internal fun carryDelayMillis(place: Int): Int = place * 60

internal enum class Roll { UP, DOWN, NONE }

internal fun rollBetween(previous: Int?, current: Int?): Roll = when {
    previous == null || current == null -> Roll.NONE
    current >= previous -> Roll.UP
    else -> Roll.DOWN
}

internal fun fitScale(width: Int, height: Int, maxWidth: Int, maxHeight: Int, minScale: Float): Float {
    fun room(max: Int, size: Int) = if (size == 0) 1f else max.toFloat() / size
    return minOf(1f, room(maxWidth, width), room(maxHeight, height)).coerceAtLeast(minScale)
}

@Immutable
private data class DigitFrame(val digit: Char?, val count: Int?)

private class ShownCount(var value: Int? = null)

/** The total with every digit rolling on its own: up on a rise, down on a fall, a carry rippling left. */
@Composable
internal fun RollingCount(label: String, count: Int?, modifier: Modifier = Modifier) {
    // Against the number last shown: an interrupted roll's transition still starts from the one before.
    val roll = rollBetween(rememberShownCount(count), count)
    val style = MaterialTheme.typography.displayLarge.copy(
        fontSize = MaxCountSize,
        lineHeight = 1.em,
        // Tabular figures: every digit is one width, so a rolling digit never shoves its neighbours.
        fontFeatureSettings = "tnum",
    )
    ShrinkToFit(minScale = 32.sp.value / MaxCountSize.value, modifier = modifier) {
        // A number reads left to right in every locale; under RTL a Row would put the units first.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row {
                digitSlots(label).forEach { slot ->
                    key(slot.place) {
                        RollingDigit(
                            frame = DigitFrame(slot.digit, count),
                            roll = roll,
                            place = slot.place,
                            style = style,
                        )
                    }
                }
            }
        }
    }
}

/** The count the last applied composition showed, or null on the first. */
@Composable
private fun rememberShownCount(count: Int?): Int? {
    val shown = remember { ShownCount() }
    val previous = shown.value
    SideEffect { shown.value = count }
    return previous
}

@Composable
private fun RollingDigit(frame: DigitFrame, roll: Roll, place: Int, style: TextStyle, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = frame,
        modifier = modifier,
        contentKey = { it.digit },
        contentAlignment = Alignment.Center,
        transitionSpec = {
            if (roll == Roll.NONE) {
                (EnterTransition.None togetherWith ExitTransition.None).using(null)
            } else {
                val rising = roll == Roll.UP
                val delay = carryDelayMillis(place)
                val slide = tween<IntOffset>(RollMillis, delay, EaseOutBack)
                val fade = tween<Float>(RollMillis / 2, delay)
                val enter = slideInVertically(slide) { height -> if (rising) height else -height } + fadeIn(fade)
                val exit = slideOutVertically(slide) { height -> if (rising) -height else height } + fadeOut(fade)
                val size = SizeTransform(clip = true) { _, _ -> tween(RollMillis, delay, FastOutSlowInEasing) }
                (enter togetherWith exit).using(size)
            }
        },
        label = "digit",
    ) { target ->
        target.digit?.let { Text(text = it.toString(), style = style, maxLines = 1, softWrap = false) }
    }
}

@Composable
private fun ShrinkToFit(minScale: Float, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val placeable = measurables.single().measure(Constraints())
        val scale = fitScale(placeable.width, placeable.height, constraints.maxWidth, constraints.maxHeight, minScale)
        val width = constraints.constrainWidth((placeable.width * scale).roundToInt())
        val height = constraints.constrainHeight((placeable.height * scale).roundToInt())
        layout(width, height) {
            val x = ((width - placeable.width * scale) / 2).roundToInt()
            val y = ((height - placeable.height * scale) / 2).roundToInt()
            placeable.placeWithLayer(x, y) {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
            }
        }
    }
}
