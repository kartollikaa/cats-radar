package dev.catsradar.app.widget

import dev.catsradar.domain.usecase.ObserveTodayCount
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Redraws the widget whenever today's count changes while this process is alive, and once when it
 * starts.
 *
 * A widget has no collector of its own between sessions; the home screen keeps showing the last
 * frame it was given until something asks for a new one.
 */
class WidgetRefresh(
    private val observeTodayCount: ObserveTodayCount,
    private val widgetRedraw: WidgetRedraw,
) {
    fun start(scope: CoroutineScope): Job =
        observeTodayCount()
            .onEach { widgetRedraw.redraw() }
            .launchIn(scope)
}
