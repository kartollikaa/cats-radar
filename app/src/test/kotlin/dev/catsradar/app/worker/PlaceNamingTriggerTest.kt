package dev.catsradar.app.worker

import dev.catsradar.domain.model.PlaceStatus
import dev.catsradar.domain.usecase.ObserveUntriedPlaceCells
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

class PlaceNamingTriggerTest {

    private val cells = InMemoryPlaceCells()
    private var requests = 0

    private fun TestScope.startTrigger() {
        PlaceNamingTrigger(
            observeUntriedPlaceCells = ObserveUntriedPlaceCells(cells),
            placeNamingScheduler = { requests++ },
        ).start(backgroundScope)
    }

    @Test
    fun aCellLoggedWhileTheProcessIsAliveIsNamedWithoutWaitingForTheSchedule() = runTest(UnconfinedTestDispatcher()) {
        startTrigger()

        cells.put(untriedCell("sp3e3q"))

        assertEquals(1, requests)
    }

    // The process may have died between the cell being written and the request being made.
    @Test
    fun aCellStillWaitingWhenTheProcessStartsIsNamedOnce() = runTest(UnconfinedTestDispatcher()) {
        cells.put(untriedCell("sp3e3q"))

        startTrigger()

        assertEquals(1, requests)
    }

    @Test
    fun nothingWaitingAtStartRequestsNothing() = runTest(UnconfinedTestDispatcher()) {
        cells.put(untriedCell("named").copy(status = PlaceStatus.RESOLVED, attempts = 1))
        cells.put(untriedCell("failed-once").copy(attempts = 1))

        startTrigger()

        assertEquals(0, requests)
    }

    // A pass writes its cells one at a time, so the others are still untried after each write.
    @Test
    fun aCellBeingLookedUpDoesNotAskForAnotherPass() = runTest(UnconfinedTestDispatcher()) {
        cells.put(untriedCell("sp3e3q"))
        cells.put(untriedCell("sp3e3r"))
        startTrigger()

        cells.put(untriedCell("sp3e3q").copy(attempts = 1))

        assertEquals(1, requests)
    }

    @Test
    fun aSecondCellWhileTheFirstIsStillWaitingAsksAgain() = runTest(UnconfinedTestDispatcher()) {
        cells.put(untriedCell("sp3e3q"))
        startTrigger()

        cells.put(untriedCell("sp3e3r"))

        assertEquals(2, requests)
    }
}
