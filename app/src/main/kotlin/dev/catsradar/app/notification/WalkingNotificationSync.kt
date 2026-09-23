package dev.catsradar.app.notification

import dev.catsradar.domain.repository.SettingsRepository
import dev.catsradar.domain.usecase.ObserveStats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/**
 * Holds the walking notification equal to the stored flag and to the outing it is counting, for as
 * long as the process lives.
 *
 * A screen that posted the notification itself would leave it gone — with the flag still reading on
 * — after a reboot, a force-stop, or a swipe away from the shade.
 */
class WalkingNotificationSync(
    private val settingsRepository: SettingsRepository,
    private val observeStats: ObserveStats,
    private val notifications: WalkingNotifications,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun start(scope: CoroutineScope, appOnScreen: Flow<Boolean>): Job =
        settingsRepository.walkingMode()
            // Nothing observes the encounters while the mode is off, which is nearly always.
            .flatMapLatest { enabled ->
                if (enabled) {
                    combine(observeStats().map { it.currentOuting?.count ?: 0 }, appOnScreen, ::Shown)
                } else {
                    flowOf(null)
                }
            }
            // The stats flow ticks to keep elapsed time moving; the notification carries no time.
            .distinctUntilChanged()
            .onEach { shown ->
                if (shown == null) notifications.clear() else notifications.show(shown.count, shown.appOnScreen)
            }
            .launchIn(scope)

    private data class Shown(val count: Int, val appOnScreen: Boolean)
}
