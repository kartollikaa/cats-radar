package dev.catsradar.ui.counter

import androidx.compose.animation.core.Animatable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import kotlin.math.abs

private const val CookieTurnStep = 8f

// More cats at once than this (an import finishing) set the cookie's turn in place rather than spin it round.
private const val MaxTurnedSteps = 5

/** The cookie's turn for the count it shows: a step per cat, so each one turns it further and an undo turns it back. */
internal fun cookieTurnFor(count: Int?): Float = (count ?: 0) * CookieTurnStep

internal fun cookieTurnAnimates(shown: Int?, next: Int?): Boolean =
    shown != null && next != null && abs(next - shown) <= MaxTurnedSteps

@Composable
internal fun rememberCookieTurn(count: Int?): State<Float> {
    val turn = remember { Animatable(cookieTurnFor(count)) }
    val shown = remember { LastTurnedCount(count) }
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    LaunchedEffect(count) {
        val previous = shown.value
        shown.value = count
        val target = cookieTurnFor(count)
        if (cookieTurnAnimates(previous, count)) {
            turn.animateTo(target, spec)
        } else {
            turn.snapTo(target)
        }
    }
    return turn.asState()
}

private class LastTurnedCount(var value: Int?)
