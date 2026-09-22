package dev.catsradar.app.di

import dev.catsradar.domain.platform.DeviceIdProvider
import dev.catsradar.domain.platform.Haptics
import dev.catsradar.domain.platform.IdGenerator
import dev.catsradar.domain.repository.EncounterRepository
import dev.catsradar.domain.usecase.LogTally
import dev.catsradar.domain.usecase.ObserveEncounterCount
import dev.catsradar.domain.usecase.UndoLastTally
import org.junit.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verifyAll

class KoinModulesTest {

    // verifyAll checks each module only against its own definitions plus extraTypes, so every
    // type resolved from a sibling module must be listed here.
    private val crossModuleTypes = listOf(
        EncounterRepository::class,
        IdGenerator::class,
        DeviceIdProvider::class,
        LogTally::class,
        UndoLastTally::class,
        ObserveEncounterCount::class,
        Haptics::class,
    )

    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun `domain, data and presentation modules each resolve given what the others provide`() {
        listOf(domainModule, dataModule, presentationModule).verifyAll(extraTypes = crossModuleTypes)
    }
}
