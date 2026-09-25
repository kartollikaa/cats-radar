package dev.catsradar.presentation

import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.repository.EncounterRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart

/** Delegates to [delegate], counting how many times [observeAll] is collected. */
internal class CountingEncounterRepository(private val delegate: EncounterRepository) :
    EncounterRepository by delegate {

    var everyCollection = 0
        private set

    override fun observeAll(): Flow<List<Encounter>> = delegate.observeAll().onStart { everyCollection++ }
}
