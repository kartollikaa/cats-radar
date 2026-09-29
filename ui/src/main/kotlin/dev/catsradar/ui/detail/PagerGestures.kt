package dev.catsradar.ui.detail

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.TargetedFlingBehavior
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.util.fastAny
import kotlinx.coroutines.flow.first
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.roundToInt

/** The pages' fling, nested scrolling and touch watcher, so that a touch while they settle goes where it lands. */
internal class PagerGestures(
    val fling: TargetedFlingBehavior,
    val connection: NestedScrollConnection,
    val touches: Modifier,
)

@Composable
internal fun rememberPagerGestures(pagerState: PagerState): PagerGestures {
    val usualFling = PagerDefaults.flingBehavior(pagerState)
    val usualConnection = PagerDefaults.pageNestedScrollConnection(pagerState, Orientation.Horizontal)
    return remember(pagerState, usualFling, usualConnection) {
        val touch = PagesTouch()
        val connection = PagesTakeOnlyTheirOwnDrag(usualConnection, touch)
        PagerGestures(CarryOnFling(pagerState, usualFling, touch), connection, Modifier.watching(touch))
    }
}

private class PagesTouch {
    var down by mutableStateOf(false)
    var reachedPages = false
}

// Watches only, in the first pass, so every touch on the pages is seen before anything under it reacts.
private fun Modifier.watching(touch: PagesTouch): Modifier = pointerInput(touch) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        touch.down = true
        touch.reachedPages = false
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
        } while (event.changes.fastAny { it.pressed })
        touch.down = false
    }
}

// Pages a touch stopped without moving them — a tap, or a drag the photo row took over — carry on to the page they were
// heading for once it lets go; the usual fling would snap them to the nearest page, often the one they came from.
private class CarryOnFling(
    private val pagerState: PagerState,
    private val usual: TargetedFlingBehavior,
    private val touch: PagesTouch,
) : TargetedFlingBehavior {
    private var stopped: StoppedOnTheWay? = null

    override suspend fun ScrollScope.performFling(
        initialVelocity: Float,
        onRemainingDistanceUpdated: (Float) -> Unit,
    ): Float {
        val stop = stopped?.takeIf { pagerState.isStillAt(it.at) }
        stopped = null
        var headingFor = stop?.headingFor ?: pagerState.currentPage
        val track = { remaining: Float ->
            headingFor = pagerState.pageAfter(remaining)
            onRemainingDistanceUpdated(remaining)
        }
        try {
            return if (stop != null) carryOnAfter(stop, track) else with(usual) { performFling(initialVelocity, track) }
        } catch (cancelled: CancellationException) {
            stopped = StoppedOnTheWay(headingFor, pagerState.position())
            throw cancelled
        }
    }

    private suspend fun ScrollScope.carryOnAfter(stop: StoppedOnTheWay, track: (Float) -> Unit): Float {
        track((stop.headingFor - pagerState.position()) * pagerState.pageSpan())
        // Held still while the finger stays down, the photos under it follow the finger alone.
        snapshotFlow { touch.down }.first { !it }
        if (!pagerState.isStillAt(stop.at)) return with(usual) { performFling(0f, track) }
        val distance = (stop.headingFor - pagerState.position()) * pagerState.pageSpan()
        var moved = 0f
        animate(0f, distance, animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow, 1f)) {
                value,
                _,
            ->
            moved += scrollBy(value - moved)
            track(distance - moved)
        }
        return 0f
    }

    private class StoppedOnTheWay(val headingFor: Int, val at: Float)
}

// Foundation's page connection hands a page's drag to the pages first whenever they sit between two pages, as they do
// while they settle, so a drag on the photo row there would move the pages instead.
private class PagesTakeOnlyTheirOwnDrag(
    private val usual: NestedScrollConnection,
    private val touch: PagesTouch,
) : NestedScrollConnection by usual {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
        if (touch.reachedPages) usual.onPreScroll(available, source) else Offset.Zero

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source == NestedScrollSource.UserInput && available.x != 0f) touch.reachedPages = true
        return usual.onPostScroll(consumed, available, source)
    }
}

// A touch that moves the pages less than this far, in pages, stopped them rather than dragged them.
private const val STILL_PAGES = 0.02f

private fun PagerState.position(): Float = currentPage + currentPageOffsetFraction

private fun PagerState.isStillAt(position: Float): Boolean = abs(position() - position) < STILL_PAGES

private fun PagerState.pageSpan(): Float = (layoutInfo.pageSize + layoutInfo.pageSpacing).toFloat()

private fun PagerState.pageAfter(remaining: Float): Int =
    if (pageSpan() == 0f) currentPage else currentPage + (remaining / pageSpan()).roundToInt()
