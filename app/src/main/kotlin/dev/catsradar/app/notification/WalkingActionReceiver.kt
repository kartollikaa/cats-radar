package dev.catsradar.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.catsradar.app.worker.LocationAttachScheduler
import dev.catsradar.domain.model.EncounterOrigin
import dev.catsradar.domain.platform.Haptics
import dev.catsradar.domain.repository.SettingsRepository
import dev.catsradar.domain.usecase.LogTally
import dev.catsradar.domain.usecase.ObserveStats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * The lock-screen tally. A broadcast rather than an activity: an activity would demand an unlock
 * and put a screen in front of someone who is looking at a cat.
 */
class WalkingActionReceiver : BroadcastReceiver(), KoinComponent {

    private val logTally: LogTally by inject()
    private val observeStats: ObserveStats by inject()
    private val settingsRepository: SettingsRepository by inject()
    private val locationAttachScheduler: LocationAttachScheduler by inject()
    private val notifier: WalkingNotifier by inject()
    private val haptics: Haptics by inject()

    override fun onReceive(context: Context, intent: Intent) {
        // The receiver's own lifetime ends when onReceive returns, so the work is held open by a
        // pending result rather than by this object.
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                when (intent.action) {
                    WalkingAction.TALLY -> tally()
                    WalkingAction.STOP -> stop()
                }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun tally() {
        haptics.tick()
        val encounter = logTally(origin = EncounterOrigin.NOTIFICATION)
        locationAttachScheduler.schedule(encounter.id)
        // Re-read rather than counting locally: the process may have died since the last tap, and
        // the outing is derived from the rows anyway.
        notifier.show(observeStats().first().currentOuting?.count ?: 1, appOnScreen = false)
    }

    private suspend fun stop() {
        settingsRepository.setWalkingMode(false)
        notifier.clear()
    }
}
