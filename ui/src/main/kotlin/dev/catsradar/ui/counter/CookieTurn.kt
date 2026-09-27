package dev.catsradar.ui.counter

import androidx.compose.animation.core.Animatable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import kotlin.math.abs

private const val CookieTurnStep = 8f

// A jump of more cats than this at once is an import landing, not taps: the turn is set in place rather than spun.
private const val MaxTurnedSteps = 5

fun cookieTurnFor(count: Int?): Float = (count ?: 0) * CookieTurnStep

internal fun cookieTurnAnimates(shown: Int?, next: Int?): Boolean =
    shown != null && next != null && abs(next - shown) <= MaxTurnedSteps

@Composable
fun rememberCookieTurn(count: Int?): State<Float> {
    val turn = remember { Animatable(cookieTurnFor(count)) }
    val previous = rememberShownCount(count)
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    LaunchedEffect(count) {
        val target = cookieTurnFor(count)
        if (cookieTurnAnimates(previous, count)) {
            turn.animateTo(target, spec)
        } else {
            turn.snapTo(target)
        }
    }
    return turn.asState()
}
