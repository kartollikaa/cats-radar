package dev.catsradar.ui.counter

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import dev.catsradar.presentation.counter.MilestoneMomentState

const val RungRingTestTag = "rung-ring"

private const val RungBounce = 1.07f
private const val GlowAlpha = 0.45f
private const val GlowWidth = 4f

/** The cookie's scale for a rung just reached: up once and back on the motion scheme's springs. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun rememberRungBounce(moment: MilestoneMomentState?): State<Float> {
    val bounce = remember { Animatable(1f) }
    val up = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val settle = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(moment?.value) {
        if (moment == null) return@LaunchedEffect
        bounce.animateTo(RungBounce, up)
        bounce.animateTo(1f, settle)
    }
    return bounce.asState()
}

/** The ring stood full for a rung just reached, with a glow that fades as it settles. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun RungRing(moment: MilestoneMomentState, modifier: Modifier = Modifier) {
    val glow = remember { Animatable(GlowAlpha) }
    val fade = MaterialTheme.motionScheme.slowEffectsSpec<Float>()
    LaunchedEffect(moment.value) {
        glow.snapTo(GlowAlpha)
        glow.animateTo(0f, fade)
    }
    val ring = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier.testTag(RungRingTestTag)) {
        val stroke = size.minDimension * RingStrokeFraction
        val topLeft = Offset(stroke / 2, stroke / 2)
        val circle = Size(size.width - stroke, size.height - stroke)
        drawArc(
            color = ring.copy(alpha = glow.value),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = circle,
            style = Stroke(width = stroke * GlowWidth),
        )
        drawArc(
            color = ring,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = circle,
            style = Stroke(width = stroke),
        )
    }
}
