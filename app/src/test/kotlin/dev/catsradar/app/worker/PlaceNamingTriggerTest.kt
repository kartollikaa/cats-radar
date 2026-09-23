package dev.catsradar.app.worker

import dev.catsradar.domain.model.PlaceCell
import dev.catsradar.domain.model.PlaceStatus
import dev.catsradar.domain.repository.PlaceCellRepository
import dev.catsradar.domain.usecase.ObserveUntriedPlaceCells
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

class PlaceNamingTriggerTest {

    private val cells = FakeCellRepository()
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

        cells.put(pendingCell("sp3e3q"))

        assertEquals(1, requests)
    }

    // The process may have died between the cell being written and the request being made.
    @Test
    fun aCellStillWaitingWhenTheProcessStartsIsNamedOnce() = runTest(UnconfinedTestDispatcher()) {
        cells.put(pendingCell("sp3e3q"))

        startTrigger()

        assertEquals(1, requests)
    }

    @Test
    fun nothingWaitingAtStartRequestsNothing() = runTest(UnconfinedTestDispatcher()) {
        cells.put(pendingCell("named").copy(status = PlaceStatus.RESOLVED, attempts = 1))
        cells.put(pendingCell("failed-once").copy(attempts = 1))

        startTrigger()

        assertEquals(0, requests)
    }

    // A pass writes its cells one at a time, so the others are still untried after each write.
    @Test
    fun aCellBeingLookedUpDoesNotAskForAnotherPass() = runTest(UnconfinedTestDispatcher()) {
        cells.put(pendingCell("sp3e3q"))
        cells.put(pendingCell("sp3e3r"))
        startTrigger()

        cells.put(pendingCell("sp3e3q").copy(attempts = 1))

        assertEquals(1, requests)
    }

    @Test
    fun aSecondCellWhileTheFirstIsStillWaitingAsksAgain() = runTest(UnconfinedTestDispatcher()) {
        cells.put(pendingCell("sp3e3q"))
        startTrigger()

        cells.put(pendingCell("sp3e3r"))

        assertEquals(2, requests)
    }
}

private class FakeCellRepository : PlaceCellRepository {
    private val rows = MutableStateFlow(emptyList<PlaceCell>())

    fun put(cell: PlaceCell) {
        rows.update { list -> list.filterNot { it.cellId == cell.cellId } + cell }
    }

    override fun observeAll(): Flow<List<PlaceCell>> = rows
    override suspend fun upsert(cell: PlaceCell) = put(cell)
    override suspend fun loadById(cellId: String): PlaceCell? = rows.value.firstOrNull { it.cellId == cellId }
    override suspend fun loadPendingPage(afterCellId: String?, limit: Int): List<PlaceCell> =
        throw NotImplementedError("unused by this test")
}

private fun pendingCell(id: String) = PlaceCell(
    cellId = id,
    centerLat = 41.388,
    centerLon = 2.170,
    countryCode = null,
    countryName = null,
    adminArea = null,
    locality = null,
    subLocality = null,
    status = PlaceStatus.PENDING,
    attempts = 0,
    lastAttemptAt = null,
    resolvedAt = null,
)
