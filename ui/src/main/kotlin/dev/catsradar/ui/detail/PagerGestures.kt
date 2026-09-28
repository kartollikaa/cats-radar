package dev.catsradar.ui.detail

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.TargetedFlingBehavior
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The pages' own fling, except after a touch that stopped them on their way without moving them — a tap, or a drag the
 * photo row took over: then they carry on to the page they were heading for instead of snapping to the nearest one.
 */
@Composable
internal fun rememberCarryOnFling(pagerState: PagerState): TargetedFlingBehavior {
    val usual = PagerDefaults.flingBehavior(pagerState)
    return remember(pagerState, usual) { CarryOnFling(pagerState, usual) }
}

/** The pages' own nested scrolling, except that a drag on a page moves the pages only once it has reached them. */
@Composable
internal fun rememberPagesTakeOnlyTheirOwnDrag(pagerState: PagerState): NestedScrollConnection {
    val usual = PagerDefaults.pageNestedScrollConnection(pagerState, Orientation.Horizontal)
    return remember(usual) { PagesTakeOnlyTheirOwnDrag(usual) }
}

private class CarryOnFling(
    private val pagerState: PagerState,
    private val usual: TargetedFlingBehavior,
) : TargetedFlingBehavior {
    private var stopped: StoppedOnTheWay? = null

    override suspend fun ScrollScope.performFling(
        initialVelocity: Float,
        onRemainingDistanceUpdated: (Float) -> Unit,
    ): Float {
        val stop = stopped?.takeIf { initialVelocity == 0f && abs(pagerState.position() - it.at) < STILL_PAGES }
        stopped = null
        var headingFor = stop?.headingFor ?: pagerState.currentPage
        val track = { remaining: Float ->
            headingFor = pagerState.pageAfter(remaining)
            onRemainingDistanceUpdated(remaining)
        }
        try {
            return if (stop != null) {
                carryOn(stop.headingFor, track)
                0f
            } else {
                with(usual) { performFling(initialVelocity, track) }
            }
        } catch (cancelled: CancellationException) {
            stopped = StoppedOnTheWay(headingFor, pagerState.position())
            throw cancelled
        }
    }

    private suspend fun ScrollScope.carryOn(page: Int, onRemainingDistanceUpdated: (Float) -> Unit) {
        val distance = (page - pagerState.position()) * pagerState.pageSpan()
        var moved = 0f
        animate(0f, distance, animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow, 1f)) {
                value,
                _,
            ->
            moved += scrollBy(value - moved)
            onRemainingDistanceUpdated(distance - moved)
        }
    }

    private class StoppedOnTheWay(val headingFor: Int, val at: Float)
}

// Foundation's page connection hands a page's drag to the pages first whenever they sit between two pages, as they do
// while they settle, so a drag on the photo row there would move the pages instead.
private class PagesTakeOnlyTheirOwnDrag(private val usual: NestedScrollConnection) : NestedScrollConnection by usual {
    private var reachedPages = false

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
        if (reachedPages) usual.onPreScroll(available, source) else Offset.Zero

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source == NestedScrollSource.UserInput && available.x != 0f) reachedPages = true
        return usual.onPostScroll(consumed, available, source)
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        reachedPages = false
        return usual.onPreFling(available)
    }
}

// A touch that moves the pages less than this far, in pages, stopped them rather than dragged them.
private const val STILL_PAGES = 0.02f

private fun PagerState.position(): Float = currentPage + currentPageOffsetFraction

private fun PagerState.pageSpan(): Float = (layoutInfo.pageSize + layoutInfo.pageSpacing).toFloat()

private fun PagerState.pageAfter(remaining: Float): Int =
    if (pageSpan() == 0f) currentPage else currentPage + (remaining / pageSpan()).roundToInt()
