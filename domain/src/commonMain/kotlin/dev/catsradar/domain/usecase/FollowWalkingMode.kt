package dev.catsradar.domain.usecase

import dev.catsradar.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.distinctUntilChanged

/** Keeps a walk open for as long as walking mode is on, and ends it when the mode goes off. */
class FollowWalkingMode(
    private val settingsRepository: SettingsRepository,
    private val startWalk: StartWalk,
    private val endWalk: EndWalk,
) {
    suspend operator fun invoke() {
        settingsRepository.walkingMode().distinctUntilChanged().collect { on ->
            if (on) startWalk() else endWalk()
        }
    }
}
