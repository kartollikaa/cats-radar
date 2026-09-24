package dev.catsradar.app.di

import android.content.Context
import dev.catsradar.domain.region.RegionKey
import org.junit.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.test.verify.verify

class KoinModulesTest {

    // A single wrapper module's includes() are flattened into one graph before verify() walks it,
    // unlike verifyAll() (org.koin.test.verify), which checks each module only against its own
    // definitions -- a binding present in a sibling module would be invisible to it.
    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun `domain, data, presentation and worker modules resolve together`() {
        module { includes(domainModule, dataModule, presentationModule, workerModule) }
            // RegionKey and a spot's Sets are handed in with parametersOf when the screen opens, exactly
            // like Context; verify() cannot see call-time parameters, so it has to be told.
            .verify(extraTypes = listOf(Context::class, RegionKey::class, Set::class))
    }
}
