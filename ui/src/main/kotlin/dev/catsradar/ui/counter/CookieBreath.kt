package dev.catsradar.ui.counter

import android.provider.Settings
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

private const val BreathDepth = 0.02f
private const val BreathHalfMillis = 1_400

/** The cookie's scale while [breathing]: a slow swell and settle, or 1 when still or when animations are off. */
@Composable
fun rememberCookieBreath(breathing: Boolean): State<Float> {
    if (!breathing || !animatorsEnabled()) return remember { mutableFloatStateOf(1f) }
    return rememberInfiniteTransition(label = "cookieBreath").animateFloat(
        initialValue = 1f,
        targetValue = 1f + BreathDepth,
        animationSpec = infiniteRepeatable(tween(BreathHalfMillis, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "cookieBreath",
    )
}

@Composable
private fun animatorsEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f }
}
