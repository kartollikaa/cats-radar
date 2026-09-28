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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.flow.first
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.roundToInt

/** The pages' fling and nested scrolling, changed so that a touch while the pages settle belongs to what it lands on. */
internal class PagerGestures(val fling: TargetedFlingBehavior, val connection: NestedScrollConnection)

@Composable
internal fun rememberPagerGestures(pagerState: PagerState): PagerGestures {
    val usualFling = PagerDefaults.flingBehavior(pagerState)
    val usualConnection = PagerDefaults.pageNestedScrollConnection(pagerState, Orientation.Horizontal)
    return remember(pagerState, usualFling, usualConnection) {
        val connection = PagesTakeOnlyTheirOwnDrag(usualConnection)
        PagerGestures(CarryOnFling(pagerState, usualFling, connection), connection)
    }
}

// Pages a touch stopped without moving them — a tap, or a drag the photo row took over — carry on to the page they were
// heading for once it lets go; the usual fling would snap them to the nearest page, often the one they came from.
private class CarryOnFling(
    private val pagerState: PagerState,
    private val usual: TargetedFlingBehavior,
    private val pageDrags: PagesTakeOnlyTheirOwnDrag,
) : TargetedFlingBehavior {
    private var stopped: StoppedOnTheWay? = null

    override suspend fun ScrollScope.performFling(
        initialVelocity: Float,
        onRemainingDistanceUpdated: (Float) -> Unit,
    ): Float {
        val stop = stopped?.takeIf { pagerState.isStillAt(it.at) }
        stopped = null
        pageDrags.startOver()
        var headingFor = stop?.headingFor ?: pagerState.currentPage
        val track = { remaining: Float ->
            headingFor = pagerState.pageAfter(remaining)
            onRemainingDistanceUpdated(remaining)
        }
        try {
            return if (stop != null) {
                track((stop.headingFor - pagerState.position()) * pagerState.pageSpan())
                // Held still until the drag on the page lets go, the photos under it follow the finger alone.
                pageDrags.awaitLetGo()
                if (pagerState.isStillAt(stop.at)) {
                    carryOn(stop.headingFor, track)
                    0f
                } else {
                    with(usual) { performFling(0f, track) }
                }
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

    private var pageDragged by mutableStateOf(false)

    // A scroll sent without a fling after it, as a screen reader's scroll action is, would otherwise leave both flags set.
    fun startOver() {
        reachedPages = false
        pageDragged = false
    }

    // Waits a frame first, so that a drag already under way can say so.
    suspend fun awaitLetGo() {
        withFrameNanos {}
        snapshotFlow { pageDragged }.first { !it }
    }

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source == NestedScrollSource.UserInput) pageDragged = true
        return if (reachedPages) usual.onPreScroll(available, source) else Offset.Zero
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source == NestedScrollSource.UserInput && available.x != 0f) reachedPages = true
        return usual.onPostScroll(consumed, available, source)
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        startOver()
        return usual.onPreFling(available)
    }
}

// A touch that moves the pages less than this far, in pages, stopped them rather than dragged them.
private const val STILL_PAGES = 0.02f

private fun PagerState.position(): Float = currentPage + currentPageOffsetFraction

private fun PagerState.isStillAt(position: Float): Boolean = abs(position() - position) < STILL_PAGES

private fun PagerState.pageSpan(): Float = (layoutInfo.pageSize + layoutInfo.pageSpacing).toFloat()

private fun PagerState.pageAfter(remaining: Float): Int =
    if (pageSpan() == 0f) currentPage else currentPage + (remaining / pageSpan()).roundToInt()
