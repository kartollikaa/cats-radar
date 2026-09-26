package dev.catsradar.app.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import dev.catsradar.app.worker.LocationAttachScheduler
import dev.catsradar.domain.model.EncounterOrigin
import dev.catsradar.domain.platform.Haptics
import dev.catsradar.domain.usecase.LogTally
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * A tap on the widget.
 *
 * It runs inside a broadcast, which Android allows only a short window, so the location fix is handed
 * to a worker rather than waited for. The redraw here is the one that is sure to happen before the
 * process can go away.
 */
class TallyAction : ActionCallback, KoinComponent {

    private val logTally: LogTally by inject()
    private val locationAttachScheduler: LocationAttachScheduler by inject()
    private val haptics: Haptics by inject()
    private val widgetCount: WidgetCount by inject()

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        haptics.tick()
        // Inside the tally, which returns only after reading the count back: the window may close first.
        widgetCount.tally {
            val encounter = logTally(origin = EncounterOrigin.WIDGET)
            locationAttachScheduler.schedule(encounter.id)
            CatsRadarWidget().updateAll(context)
        }
    }
}
