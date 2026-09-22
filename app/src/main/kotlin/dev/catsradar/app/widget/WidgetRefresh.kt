package dev.catsradar.app.widget

import dev.catsradar.domain.usecase.ObserveTodayCount
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Redraws the widget when the count changes for a reason the widget itself cannot see — a cat logged
 * in the app, or one undone.
 *
 * A widget nobody is looking at has no live collector of its own; the home screen keeps showing the
 * last frame it was given until something asks for a new one.
 */
class WidgetRefresh(
    private val observeTodayCount: ObserveTodayCount,
    private val widgetRedraw: WidgetRedraw,
) {
    fun start(scope: CoroutineScope): Job =
        observeTodayCount()
            // The first value is what the widget already drew; only later changes are news.
            .drop(1)
            .onEach { widgetRedraw.redraw() }
            .launchIn(scope)
}
