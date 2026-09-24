package dev.catsradar.presentation

import dev.catsradar.domain.repository.ReportedJob
import dev.catsradar.domain.repository.SettingsRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Which finished run of [job] a screen reports: each run once per screen, and none once dealt with. */
internal class ReportedRun(
    private val settingsRepository: SettingsRepository,
    private val job: ReportedJob,
) {
    private var claimedId: String? = null

    /** The run [claim] last let through, which is the one the user can deal with. */
    var reportedId: String? = null
        private set

    /** True when [runId] is to be reported: not a repeat, not dealt with, and not overtaken by a newer run. */
    suspend fun claim(runId: String): Boolean {
        // Claimed before the suspending read: a repeat arriving meanwhile finds the run taken, and a
        // newer run claimed meanwhile is not overwritten by this one.
        if (runId == claimedId) return false
        claimedId = runId
        val dealtWith = settingsRepository.acknowledgedRun(job).first() == runId
        val reported = !dealtWith && claimedId == runId
        if (reported) reportedId = runId
        return reported
    }

    /** Records that the user has dealt with [runId]; a failed write only lets it be reported again. */
    suspend fun acknowledge(runId: String? = reportedId) {
        if (runId == null) return
        // Leaving the screen right after OK cancels its scope, and a cancelled edit is never written.
        withContext(NonCancellable) {
            runStorageWrite { settingsRepository.setAcknowledgedRun(job, runId) }
        }
    }
}
