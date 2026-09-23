package dev.catsradar.ui.counter

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.catsradar.ui.R
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

private val HoldToStop = 1.seconds

/**
 * Starts a walk on a tap, and stops one only when held until the fill crosses it: a stop ends the walk
 * and its route, which a stray touch must not do.
 */
@Composable
internal fun WalkButton(
    walking: Boolean,
    modifier: Modifier = Modifier,
    onWalkingChange: (Boolean) -> Unit = {},
) {
    val fill = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val currentOnWalkingChange by rememberUpdatedState(onWalkingChange)
    val stopLabel = stringResource(R.string.counter_walk_stop)
    LaunchedEffect(walking) { fill.snapTo(0f) }
    val gesture = if (walking) {
        Modifier
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    val hold = scope.launch {
                        val remaining = (HoldToStop.inWholeMilliseconds * (1f - fill.value)).roundToInt()
                        fill.animateTo(1f, tween(durationMillis = remaining, easing = LinearEasing))
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        currentOnWalkingChange(false)
                    }
                    waitForUpOrCancellation()
                    if (hold.isActive) {
                        hold.cancel()
                        scope.launch { fill.animateTo(0f, tween(durationMillis = 250)) }
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
        Modifier.clickable(role = Role.Button) { onWalkingChange(true) }
    }
    WalkButtonSurface(walking = walking, fill = { fill.value }, modifier = modifier.clip(CircleShape).then(gesture))
}

@Composable
private fun WalkButtonSurface(walking: Boolean, fill: () -> Float, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val fillColor = colors.tertiary.copy(alpha = 0.4f)
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = if (walking) colors.tertiaryContainer else colors.secondaryContainer,
        contentColor = if (walking) colors.onTertiaryContainer else colors.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier
                .drawBehind { drawFill(fill(), fillColor) }
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_directions_walk),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = stringResource(if (walking) R.string.counter_walk_stop else R.string.counter_walk_start),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(
                        if (walking) R.string.counter_walk_stop_hint else R.string.counter_walk_start_hint,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

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
            WalkButtonSurface(walking = true, fill = { 0.4f })
        }
    }
}
