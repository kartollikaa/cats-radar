package dev.catsradar.app.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.get
import androidx.navigation3.runtime.metadata
import androidx.navigation3.scene.Scene
import androidx.navigationevent.NavigationEvent

internal enum class NavMotion { FADE_THROUGH, FORWARD, BACKWARD }

private data object TabRootKey : NavMetadataKey<Boolean>

internal fun tabRootMetadata(): Map<String, Any> = metadata { put(TabRootKey, true) }

// Only a one-level step moves along the axis; a tab switch that happens to push or pop still fades.
internal fun navMotion(from: Scene<*>, to: Scene<*>): NavMotion = when {
    from.isTabRoot && to.isTabRoot -> NavMotion.FADE_THROUGH
    to.sitsDirectlyOn(from) -> NavMotion.FORWARD
    from.sitsDirectlyOn(to) -> NavMotion.BACKWARD
    else -> NavMotion.FADE_THROUGH
}

private val Scene<*>.isTabRoot: Boolean get() = metadata[TabRootKey] == true

private fun Scene<*>.sitsDirectlyOn(other: Scene<*>): Boolean {
    // A sheet under a screen is drawn as the screen under the sheet, so it is no level of its own.
    val below = previousEntries.lastOrNull { !it.isSheet } ?: return false
    return below.contentKey == other.entries.last().contentKey
}

internal fun AnimatedContentTransitionScope<out Scene<*>>.navTransition(density: Density): ContentTransform {
    val slideDistancePx = with(density) { SlideDistance.roundToPx() }
    return when (navMotion(initialState, targetState)) {
        NavMotion.FADE_THROUGH -> fadeThrough()
        NavMotion.FORWARD -> sharedAxisX(slideDistancePx)
        NavMotion.BACKWARD -> sharedAxisX(-slideDistancePx)
    }
}

// The gesture seeks this transition, so each spec's share of the duration is its share of the swipe.
internal fun predictivePopTransition(swipeEdge: Int): ContentTransform {
    val shrink = scaleOut(
        animationSpec = tween(MotionDuration, easing = LinearOutSlowInEasing),
        targetScale = PredictiveBackScale,
        transformOrigin = TransformOrigin(pivotFractionX = shrinkPivotX(swipeEdge), pivotFractionY = 0.5f),
    )
    val fadeAway = fadeOut(tween(PredictiveFadeDuration, easing = LinearEasing))
    val fadeUp = fadeIn(tween(PredictiveFadeDuration, delayMillis = PredictiveFadeInDelay, easing = LinearEasing))
    return fadeUp togetherWith shrink + fadeAway
}

/** The screen shrinks toward the edge the finger is moving to, or toward its centre when no edge started it. */
internal fun shrinkPivotX(swipeEdge: Int): Float = when (swipeEdge) {
    NavigationEvent.EDGE_LEFT -> 1f
    NavigationEvent.EDGE_RIGHT -> 0f
    else -> TransformOrigin.Center.pivotFractionX
}

private fun fadeThrough(): ContentTransform =
    fadeIn(incoming()) + scaleIn(incoming(), initialScale = FadeThroughInitialScale) togetherWith fadeOut(outgoing())

private fun sharedAxisX(offsetPx: Int): ContentTransform {
    val slide = tween<IntOffset>(MotionDuration, easing = FastOutSlowInEasing)
    return slideInHorizontally(slide) { offsetPx } + fadeIn(incoming()) togetherWith
        slideOutHorizontally(slide) { -offsetPx } + fadeOut(outgoing())
}

private fun <T> incoming(): FiniteAnimationSpec<T> =
    tween(MotionDuration - OutgoingFadeDuration, delayMillis = OutgoingFadeDuration, easing = LinearOutSlowInEasing)

private fun <T> outgoing(): FiniteAnimationSpec<T> = tween(OutgoingFadeDuration, easing = FastOutLinearInEasing)

private val SlideDistance = 30.dp

private const val MotionDuration = 300
private const val OutgoingFadeDuration = 90
private const val PredictiveFadeDuration = 180
private const val PredictiveFadeInDelay = MotionDuration - PredictiveFadeDuration
private const val FadeThroughInitialScale = 0.92f
private const val PredictiveBackScale = 0.9f
