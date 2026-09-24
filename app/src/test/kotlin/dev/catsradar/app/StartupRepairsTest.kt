package dev.catsradar.app

import dev.catsradar.app.reporting.RecordingNonFatalReporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StartupRepairsTest {
    private val reporter = RecordingNonFatalReporter()

    @Test
    fun `a repair that throws is recorded and the app keeps running`() = runTest {
        val broken = IllegalStateException("cells table is corrupt")
        var otherRepairRan = false

        StartupRepairs(listOf({ throw broken }, { otherRepairRan = true }), reporter).launchIn(this)
        advanceUntilIdle()

        assertEquals(listOf<Throwable>(broken), reporter.recorded)
        assertTrue(otherRepairRan)
        assertTrue(coroutineContext.isActive)
    }

    @Test
    fun `a repair that runs out of memory is recorded and the app keeps running`() = runTest {
        val outOfMemory = OutOfMemoryError("Failed to allocate a 180 MB bitmap")

        StartupRepairs(listOf { throw outOfMemory }, reporter).launchIn(this)
        advanceUntilIdle()

        assertEquals(listOf<Throwable>(outOfMemory), reporter.recorded)
        assertTrue(coroutineContext.isActive)
    }

    @Test
    fun `a cancelled repair records nothing`() = runTest {
        StartupRepairs(listOf { throw CancellationException("process is going away") }, reporter).launchIn(this)
        advanceUntilIdle()

        assertEquals(emptyList(), reporter.recorded)
    }
}
