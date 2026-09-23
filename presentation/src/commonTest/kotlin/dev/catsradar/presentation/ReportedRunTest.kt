package dev.catsradar.presentation

import dev.catsradar.domain.repository.ReportedJob
import dev.catsradar.presentation.counter.FakeSettingsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ReportedRunTest {

    private val settings = FakeSettingsRepository()

    private fun importRun() = ReportedRun(settings, ReportedJob.GALLERY_IMPORT)

    @Test
    fun `a run is reported the first time and not again`() = runTest {
        val run = importRun()

        assertTrue(run.claim("run-1"))
        assertFalse(run.claim("run-1"))
    }

    @Test
    fun `a run the user has dealt with is not reported, and a newer one is`() = runTest {
        importRun().acknowledge("run-1")
        val run = importRun()

        assertFalse(run.claim("run-1"))
        assertTrue(run.claim("run-2"))
    }

    @Test
    fun `dealing with a run of one job leaves the other job's run to be reported`() = runTest {
        importRun().acknowledge("run-1")

        assertTrue(ReportedRun(settings, ReportedJob.BACKUP).claim("run-1"))
    }

    @Test
    fun `a failed acknowledgement is not an error, and the run is reported again`() = runTest {
        val failing = FakeSettingsRepository(writesFail = true)

        ReportedRun(failing, ReportedJob.GALLERY_IMPORT).acknowledge("run-1")

        assertTrue(ReportedRun(failing, ReportedJob.GALLERY_IMPORT).claim("run-1"))
    }

    @Test
    fun `an acknowledgement is written even when the screen goes away mid-write`() = runTest {
        val gate = CompletableDeferred<Unit>()
        settings.acknowledgedRunWriteGate = gate

        val caller = launch { importRun().acknowledge("run-1") }
        runCurrent()
        caller.cancel()
        gate.complete(Unit)
        runCurrent()

        assertFalse(importRun().claim("run-1"))
    }

    @Test
    fun `acknowledging by default records the run reported, not one still being checked`() = runTest {
        val run = importRun()
        assertTrue(run.claim("run-1"))
        val gate = CompletableDeferred<Unit>()
        settings.acknowledgedRunReadGate = gate
        val newer = async { run.claim("run-2") }
        runCurrent()

        run.acknowledge()
        gate.complete(Unit)

        assertTrue(newer.await())
    }

    @Test
    fun `a repeat arriving while the run is being checked is not reported twice`() = runTest {
        val gate = CompletableDeferred<Unit>()
        settings.acknowledgedRunReadGate = gate
        val run = importRun()

        val first = async { run.claim("run-1") }
        runCurrent()
        val repeat = async { run.claim("run-1") }
        runCurrent()
        gate.complete(Unit)

        assertEquals(listOf(true, false), listOf(first.await(), repeat.await()))
    }

    @Test
    fun `a newer run claimed while an older one is being checked is the one reported`() = runTest {
        val gate = CompletableDeferred<Unit>()
        settings.acknowledgedRunReadGate = gate
        val run = importRun()

        val older = async { run.claim("run-1") }
        runCurrent()
        val newer = async { run.claim("run-2") }
        runCurrent()
        gate.complete(Unit)

        assertEquals(listOf(false, true), listOf(older.await(), newer.await()))
        assertEquals("run-2", run.reportedId)
    }
}
