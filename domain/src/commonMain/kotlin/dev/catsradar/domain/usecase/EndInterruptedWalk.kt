package dev.catsradar.domain.usecase

import dev.catsradar.domain.platform.WalkRecordingState
import dev.catsradar.domain.repository.SettingsRepository
import dev.catsradar.domain.repository.WalkRepository
import kotlin.time.Clock

/**
 * Settles a recording that was cut off rather than stopped: its walk ends where its route ends, or
 * at its start when it has none, and walking mode goes off. A walk that was not recording is left on.
 */
class EndInterruptedWalk(
    private val walkRepository: WalkRepository,
    private val settingsRepository: SettingsRepository,
    private val recordingState: WalkRecordingState,
    private val clock: Clock,
) {
    suspend operator fun invoke() {
        if (!recordingState.recording) return
        walkRepository.openWalk()?.let { walk ->
            val endedAt = walkRepository.lastPoint(walk.id)?.at ?: walk.startedAt
            walkRepository.end(walk.id, endedAt, updatedAt = clock.now())
        }
        settingsRepository.setWalkingMode(false)
        recordingState.markStopped()
    }
}
