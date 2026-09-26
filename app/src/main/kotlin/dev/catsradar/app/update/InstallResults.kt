package dev.catsradar.app.update

import dev.catsradar.presentation.settings.InstallOutcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.onEach

/**
 * Carries an install's ending from the receiver Android calls to the screen that asked for it. An ending
 * that arrives while no screen listens waits for the next one, and each is taken once.
 */
class InstallResults {
    private val outcomes = MutableSharedFlow<InstallOutcome>(replay = 1)

    fun post(outcome: InstallOutcome) {
        outcomes.tryEmit(outcome)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class) // resetReplayCache
    fun observe(): Flow<InstallOutcome> = outcomes.onEach { outcomes.resetReplayCache() }
}
