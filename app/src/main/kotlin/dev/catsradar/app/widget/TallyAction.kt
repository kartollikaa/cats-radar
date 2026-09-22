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
 * Glance kills a callback that takes more than a few seconds, so this does the insert and hands the
 * location off to a worker — the same two steps a tap in the app takes, in the same order.
 */
class TallyAction : ActionCallback, KoinComponent {

    private val logTally: LogTally by inject()
    private val locationAttachScheduler: LocationAttachScheduler by inject()
    private val haptics: Haptics by inject()

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        haptics.tick()
        val encounter = logTally(origin = EncounterOrigin.WIDGET)
        locationAttachScheduler.schedule(encounter.id)
        CatsRadarWidget().updateAll(context)
    }
}
