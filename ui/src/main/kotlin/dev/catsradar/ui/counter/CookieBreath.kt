package dev.catsradar.ui.counter

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext

private const val BreathDepth = 0.02f
private const val BreathHalfMillis = 1_400

/**
 * The cookie's scale while [breathing]: a slow swell and settle, easing back to 1 when the breath stops, and 1
 * throughout when animations are off. Read it where it is drawn: it changes every frame of a walk.
 */
@Composable
fun rememberCookieBreath(breathing: Boolean): State<Float> {
    val scale = remember { Animatable(1f) }
    val swell = if (breathing && animatorsEnabled()) rememberSwell() else null
    val settle = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    LaunchedEffect(swell) {
        if (swell == null) {
            scale.animateTo(1f, settle)
        } else {
            snapshotFlow { swell.value }.collect { scale.snapTo(it) }
        }
    }
    return scale.asState()
}

@Composable
private fun rememberSwell(): State<Float> = rememberInfiniteTransition(label = "cookieBreath").animateFloat(
    initialValue = 1f,
    targetValue = 1f + BreathDepth,
    animationSpec = infiniteRepeatable(tween(BreathHalfMillis, easing = EaseInOutSine), RepeatMode.Reverse),
    label = "cookieSwell",
)

@Composable
private fun animatorsEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f }
}
