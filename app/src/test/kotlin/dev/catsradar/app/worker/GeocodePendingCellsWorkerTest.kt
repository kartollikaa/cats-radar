package dev.catsradar.app.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import dev.catsradar.app.reporting.NonFatalReporter
import dev.catsradar.app.reporting.RecordingNonFatalReporter
import dev.catsradar.domain.platform.GeocodeResult
import dev.catsradar.domain.platform.ReverseGeocoder
import dev.catsradar.domain.usecase.ResolvePendingPlaces
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
    private val cells = InMemoryPlaceCells(
        untriedCell("sp3e3q").copy(attempts = 1, lastAttemptAt = Instant.parse("2026-09-23T04:00:00Z")),
    )

    private fun worker(inputData: Data): GeocodePendingCellsWorker {
        val clock = object : Clock {
            override fun now(): Instant = Instant.parse("2026-09-23T10:00:00Z")
        }
        val failingGeocoder = object : ReverseGeocoder {
            override suspend fun resolve(lat: Double, lon: Double): GeocodeResult = GeocodeResult.Failed
        }
        val resolve = ResolvePendingPlaces(cells, failingGeocoder, clock)
        val koin = koinApplication {
            modules(
                module {
                    single { resolve }
                    single { GeocodeWorkScheduler(lazy { WorkManager.getInstance(context) }) }
                    single<NonFatalReporter> { RecordingNonFatalReporter() }
                },
            )
        }.koin
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
