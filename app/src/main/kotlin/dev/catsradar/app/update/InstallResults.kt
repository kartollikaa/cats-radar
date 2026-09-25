package dev.catsradar.app.update

import dev.catsradar.presentation.settings.InstallOutcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/** Carries an install's ending from the receiver Android calls to the screen that asked for it. */
class InstallResults {
    private val outcomes = MutableSharedFlow<InstallOutcome>(extraBufferCapacity = 1)

    fun post(outcome: InstallOutcome) {
        outcomes.tryEmit(outcome)
    }

    fun observe(): Flow<InstallOutcome> = outcomes
}
