package dev.catsradar.app.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import dev.catsradar.domain.model.PlaceCell
import dev.catsradar.domain.model.PlaceStatus
import dev.catsradar.domain.platform.GeocodeResult
import dev.catsradar.domain.platform.ReverseGeocoder
import dev.catsradar.domain.repository.PlaceCellRepository
import dev.catsradar.domain.usecase.ResolvePendingPlaces
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

@RunWith(AndroidJUnit4::class)
class GeocodePendingCellsWorkerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val cells = FakeCells(failedOnce("sp3e3q"))

    private fun worker(inputData: Data): GeocodePendingCellsWorker {
        val clock = object : Clock {
            override fun now(): Instant = Instant.parse("2026-09-23T10:00:00Z")
        }
        val failingGeocoder = object : ReverseGeocoder {
            override suspend fun resolve(lat: Double, lon: Double): GeocodeResult = GeocodeResult.Failed
        }
        val resolve = ResolvePendingPlaces(cells, failingGeocoder, clock)
        val koin = koinApplication { modules(module { single { resolve } }) }.koin
        return TestListenableWorkerBuilder<GeocodePendingCellsWorker>(context)
            .setInputData(inputData)
            .setWorkerFactory(KoinWorkerFactory(koin))
            .build()
    }

    @Test
    fun `the untried pass leaves a cell that already failed for the scheduled retry`() = runTest {
        val untriedOnly = Data.Builder().putBoolean(GeocodePendingCellsWorker.KEY_UNTRIED_ONLY, true).build()

        val result = worker(untriedOnly).doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(1, cells.attemptsOf("sp3e3q"))
    }

    @Test
    fun `the scheduled pass retries a cell that already failed`() = runTest {
        val result = worker(Data.EMPTY).doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(2, cells.attemptsOf("sp3e3q"))
    }
}

private class FakeCells(vararg seed: PlaceCell) : PlaceCellRepository {
    private val rows = MutableStateFlow(seed.toList())

    fun attemptsOf(cellId: String): Int = rows.value.single { it.cellId == cellId }.attempts

    override fun observeAll(): Flow<List<PlaceCell>> = rows
    override suspend fun upsert(cell: PlaceCell) {
        rows.update { list -> list.filterNot { it.cellId == cell.cellId } + cell }
    }

    override suspend fun loadById(cellId: String): PlaceCell? = rows.value.firstOrNull { it.cellId == cellId }
    override suspend fun loadPendingPage(afterCellId: String?, limit: Int): List<PlaceCell> =
        rows.value.filter { it.status == PlaceStatus.PENDING && (afterCellId == null || it.cellId > afterCellId) }
            .sortedBy { it.cellId }
            .take(limit)
}

private fun failedOnce(id: String) = PlaceCell(
    cellId = id,
    centerLat = 41.388,
    centerLon = 2.170,
    countryCode = null,
    countryName = null,
    adminArea = null,
    locality = null,
    subLocality = null,
    status = PlaceStatus.PENDING,
    attempts = 1,
    lastAttemptAt = Instant.parse("2026-09-23T04:00:00Z"),
    resolvedAt = null,
)
