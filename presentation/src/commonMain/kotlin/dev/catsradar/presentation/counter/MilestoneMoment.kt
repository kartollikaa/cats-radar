package dev.catsradar.presentation.counter

import dev.catsradar.domain.Tuning
import dev.catsradar.domain.repository.SettingsRepository
import dev.catsradar.presentation.runStorageWrite
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** The rung the count has just landed on, each rung once, until the run of taps that reached it closes. */
internal class MilestoneMoment(
    private val settingsRepository: SettingsRepository,
    private val scope: CoroutineScope,
    private val show: () -> Unit,
) {
    var rung: Int? = null
        private set
    private var celebrated: Int? = null
    private var seenBefore = 0
    private var timeout: Job? = null

    /** Celebrates the highest rung [total] has reached if it is new; with no run open to close it, for the window. */
    suspend fun reach(total: Int, runOpen: () -> Boolean) {
        val reached = Tuning.MILESTONES.filter { it <= total }.maxOrNull() ?: return
        val seen = settingsRepository.lastSeenMilestone().first()
        if (reached <= seen || !recorded(reached)) return
        if (rung == null) seenBefore = seen
        celebrated = reached
        rung = reached
        show()
        timeout?.cancel()
        if (!runOpen()) endAfterTheWindow()
    }

    // Persisted before it shows: a process death between the two would otherwise celebrate
    // the same milestone again on the next launch.
    private suspend fun recorded(reached: Int): Boolean {
        var written = false
        runStorageWrite {
            settingsRepository.setLastSeenMilestone(reached)
            written = true
        }
        return written
    }

    // Taken back with the cats that reached it, even after it has ended, so landing on it again celebrates again.
    suspend fun takeBackBelow(total: Int) {
        val reached = celebrated ?: return
        if (total >= reached) return
        celebrated = null
        rung = null
        timeout?.cancel()
        runStorageWrite { settingsRepository.setLastSeenMilestone(seenBefore) }
    }

    /** A run has opened: its close ends the moment, not the window started without one. */
    fun holdForTheRun() {
        timeout?.cancel()
    }

    fun endAfterTheWindow() {
        if (rung == null) return
        timeout?.cancel()
        timeout = scope.launch {
            delay(Tuning.UNDO_VISIBLE)
            end()
        }
    }

    fun end() {
        timeout?.cancel()
        if (rung == null) return
        rung = null
        show()
    }
}
