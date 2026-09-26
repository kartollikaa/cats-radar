package dev.catsradar.app.widget

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Redraws the widget whenever the count it shows changes while this process is alive — a tap's, before
 * its row is written, included — and once when it starts.
 *
 * A widget has no collector of its own between sessions; the home screen keeps showing the last
 * frame it was given until something asks for a new one.
 */
class WidgetRefresh(
    private val widgetCount: WidgetCount,
    private val widgetRedraw: WidgetRedraw,
) {
    fun start(scope: CoroutineScope): Job =
        widgetCount.shown
            .onEach { widgetRedraw.redraw() }
            .launchIn(scope)
}
