package dev.catsradar.ui.counter

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.catsradar.ui.R
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

private val HoldToStop = 1.seconds
private const val TicksPerHold = 20

private val WalkButtonHeight = 56.dp

/**
 * Starts a walk on a tap, and stops one only when held until the fill crosses it: a stop ends the walk
 * and its route, which a stray touch must not do. [onHoldRelease] hears a press let go before then.
 */
@Composable
internal fun WalkButton(
    walking: Boolean,
    modifier: Modifier = Modifier,
    onWalkingChange: (Boolean) -> Unit = {},
    onHoldRelease: () -> Unit = {},
) {
    val fill = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val currentOnWalkingChange by rememberUpdatedState(onWalkingChange)
    val currentOnHoldRelease by rememberUpdatedState(onHoldRelease)
    val startLabel = stringResource(R.string.counter_walk_start)
    val stopLabel = stringResource(R.string.counter_walk_stop)
    val corner by animateDpAsState(
        targetValue = if (walking) 16.dp else WalkButtonHeight / 2,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "walkCorner",
    )
    val shape = RoundedCornerShape(corner)
    LaunchedEffect(walking) { fill.snapTo(0f) }
    val gesture = if (walking) {
        Modifier
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    val hold = scope.launch {
                        // Still full: the last stop has not landed, and this press must earn its own.
                        if (fill.value == 1f) fill.snapTo(0f)
                        val remaining = (HoldToStop.inWholeMilliseconds * (1f - fill.value)).roundToInt()
                        var ticked = ticksCrossed(fill.value)
                        fill.animateTo(1f, tween(durationMillis = remaining, easing = LinearEasing)) {
                            val crossed = ticksCrossed(value)
                            // The last boundary is the stop itself, which gets Confirm instead of a tick.
                            if (crossed > ticked && crossed < TicksPerHold) {
                                ticked = crossed
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                            }
                        }
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        currentOnWalkingChange(false)
                    }
                    waitForUpOrCancellation()
                    if (hold.isActive) {
                        hold.cancel()
                        scope.launch { fill.animateTo(0f, tween(durationMillis = 250)) }
                        currentOnHoldRelease()
                    }
                }
            }
            // The hold guards against touches nobody meant; a screen reader's double tap is always meant.
            .semantics(mergeDescendants = true) {
                role = Role.Button
                onClick(label = stopLabel) {
                    currentOnWalkingChange(false)
                    true
                }
            }
    } else {
        Modifier.clickable(role = Role.Button, onClickLabel = startLabel) { onWalkingChange(true) }
    }
    WalkButtonSurface(
        walking = walking,
        shape = shape,
        fill = { fill.value },
        modifier = modifier.clip(shape).then(gesture),
    )
}

@Composable
private fun WalkButtonSurface(
    walking: Boolean,
    shape: Shape,
    fill: () -> Float,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val spec = MaterialTheme.motionScheme.defaultEffectsSpec<Color>()
    val container by animateColorAsState(
        targetValue = if (walking) colors.tertiaryContainer else colors.secondaryContainer,
        animationSpec = spec,
        label = "walkContainer",
    )
    val content by animateColorAsState(
        targetValue = if (walking) colors.onTertiaryContainer else colors.onSecondaryContainer,
        animationSpec = spec,
        label = "walkContent",
    )
    val fillColor = colors.tertiary.copy(alpha = 0.4f)
    Surface(modifier = modifier, shape = shape, color = container, contentColor = content) {
        Row(
            modifier = Modifier
                .heightIn(min = WalkButtonHeight)
                .drawBehind { drawFill(fill(), fillColor) }
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WalkingCat(walking = walking, modifier = Modifier.size(20.dp))
            Text(
                text = stringResource(if (walking) R.string.counter_walk_hold else R.string.counter_walk),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
        }
    }
}

private fun ticksCrossed(filled: Float): Int = (filled * TicksPerHold).toInt()

private fun DrawScope.drawFill(filled: Float, color: Color) {
    val width = size.width * filled
    val start = if (layoutDirection == LayoutDirection.Rtl) size.width - width else 0f
    drawRect(color = color, topLeft = Offset(start, 0f), size = Size(width, size.height))
}

@ThemePreviews
@Composable
private fun WalkButtonPreview() {
    CatsRadarTheme {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(16.dp)) {
            WalkButton(walking = false)
            WalkButton(walking = true)
            WalkButtonSurface(walking = true, shape = RoundedCornerShape(16.dp), fill = { 0.4f })
        }
    }
}
