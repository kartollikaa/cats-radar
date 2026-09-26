package dev.catsradar.app.update

import dev.catsradar.presentation.settings.InstallOutcome
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

class InstallResultsTest {

    @Test
    fun anOutcomePostedWhileNoScreenListensWaitsForTheNextOne() = runTest {
        val results = InstallResults()

        results.post(InstallOutcome.CANCELLED)

        assertEquals(InstallOutcome.CANCELLED, results.observe().first())
    }

    @Test
    fun anOutcomeIsTakenOnce() = runTest(UnconfinedTestDispatcher()) {
        val results = InstallResults()
        results.post(InstallOutcome.FAILED)
        results.observe().first()

        val later = mutableListOf<InstallOutcome>()
        backgroundScope.launch { results.observe().collect { later += it } }

        assertEquals(emptyList(), later)
    }
}
