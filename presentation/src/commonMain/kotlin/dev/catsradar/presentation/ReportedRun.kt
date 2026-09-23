package dev.catsradar.presentation

import dev.catsradar.domain.repository.ReportedJob
import dev.catsradar.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first

/** Which finished run of [job] a screen reports: each run once per screen, and none once dealt with. */
internal class ReportedRun(
    private val settingsRepository: SettingsRepository,
    private val job: ReportedJob,
) {
    var id: String? = null
        private set

    /** True when [runId] is to be reported: neither a repeat of this run nor one already dealt with. */
    suspend fun claim(runId: String): Boolean {
        // Claimed before the suspending read: a repeat arriving meanwhile finds the run taken, and a
        // newer run claimed meanwhile is not overwritten by this one.
        if (runId == id) return false
        id = runId
        val dealtWith = settingsRepository.acknowledgedRun(job).first() == runId
        return !dealtWith && id == runId
    }

    /** Records that the user has dealt with [runId]; a failed write only lets it be reported again. */
    suspend fun acknowledge(runId: String) {
        runStorageWrite { settingsRepository.setAcknowledgedRun(job, runId) }
    }
}
