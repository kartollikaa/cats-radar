package dev.catsradar.app.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import dev.catsradar.domain.location.LocationFix
import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.EncounterKind
import dev.catsradar.domain.model.EncounterOrigin
import dev.catsradar.domain.model.LocationSource
import dev.catsradar.domain.model.LocationStamp
import dev.catsradar.domain.model.PlaceCell
import dev.catsradar.domain.platform.LocationProvider
import dev.catsradar.domain.repository.EncounterRepository
import dev.catsradar.domain.repository.PlaceCellRepository
import dev.catsradar.domain.usecase.AttachLocation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

private val Now = Instant.parse("2026-09-22T10:00:00Z")
private val Fix = LocationFix(lat = 55.7558, lon = 37.6173, accuracyMeters = 12f, fixedAt = Now)

private class FakePlaceCellRepository : PlaceCellRepository {
    override fun observeAll(): Flow<List<PlaceCell>> = MutableStateFlow(emptyList())
    override suspend fun upsert(cell: PlaceCell) = Unit
    override suspend fun loadById(cellId: String): PlaceCell? = null
    override suspend fun loadPendingPage(limit: Int, offset: Int): List<PlaceCell> = emptyList()
}

private class FakeEncounterRepository(seed: Encounter) : EncounterRepository {
    private val encounters = MutableStateFlow(listOf(seed))

    override fun observeAll(): Flow<List<Encounter>> = encounters
    override fun observeById(id: String): Flow<Encounter?> = encounters.map { list -> list.firstOrNull { it.id == id } }
    override suspend fun insert(encounter: Encounter): Unit = throw NotImplementedError("unused by this test")
    override suspend fun update(encounter: Encounter): Unit = throw NotImplementedError("unused by this test")

    override suspend fun attachLocation(id: String, stamp: LocationStamp) {
        encounters.update { list ->
            list.map { encounter ->
                if (encounter.id == id) {
                    encounter.copy(
                        lat = stamp.lat,
                        lon = stamp.lon,
                        accuracyMeters = stamp.accuracyMeters,
                        locationSource = stamp.locationSource,
                        locationFixedAt = stamp.locationFixedAt,
                        geohash = stamp.geohash,
                        placeCellId = stamp.placeCellId,
                        updatedAt = stamp.updatedAt,
                    )
                } else {
                    encounter
                }
            }
        }
    }

    override suspend fun softDelete(id: String, deletedAt: Instant): Unit =
        throw NotImplementedError("unused by this test")

    override suspend fun undoDelete(id: String): Unit = throw NotImplementedError("unused by this test")
    override suspend fun findBySourceDigest(sourceDigest: String): Encounter? = null

    override suspend fun loadEvery(): List<Encounter> = encounters.value

    override suspend fun loadDeletedBefore(cutoff: Instant): List<Encounter> =
        encounters.value.filter { it.deletedAt != null && it.deletedAt!! < cutoff }

    override suspend fun purgeDeletedBefore(cutoff: Instant): Int = 0

    fun current(id: String): Encounter = encounters.value.first { it.id == id }
}

private class FakeLocationProvider : LocationProvider {
    override suspend fun getCurrentFix(timeout: Duration): LocationFix = Fix
    override suspend fun lastKnown(): LocationFix? = null
    override fun trackFixes(): Flow<LocationFix> = emptyFlow()
}

private fun targetEncounter(): Encounter = Encounter(
    id = "target",
    occurredAt = Now,
    tzOffsetMinutes = 0,
    kind = EncounterKind.TALLY,
    origin = EncounterOrigin.APP,
    coat = null,
    photoPath = null,
    thumbPath = null,
    galleryUri = null,
    sourceDigest = null,
    lat = null,
    lon = null,
    accuracyMeters = null,
    locationSource = LocationSource.NONE,
    locationFixedAt = null,
    geohash = null,
    placeCellId = null,
    deviceId = "device-1",
    createdAt = Now,
    updatedAt = Now,
    deletedAt = null,
)

// Proves the worker actually runs: doWork() and KoinWorkerFactory.createWorker()'s class-name
// match were previously exercised by no test at all.
@RunWith(AndroidJUnit4::class)
class AttachLocationWorkerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `built through the real KoinWorkerFactory, it attaches the fix and succeeds`() = runTest {
        val repository = FakeEncounterRepository(targetEncounter())
        val clock = object : Clock {
            override fun now(): Instant = Now
        }
        val attachLocation = AttachLocation(repository, FakePlaceCellRepository(), FakeLocationProvider(), clock)
        val koin = koinApplication { modules(module { single { attachLocation } }) }.koin

        val worker = TestListenableWorkerBuilder<AttachLocationWorker>(context)
            .setInputData(Data.Builder().putString(AttachLocationWorker.KEY_ENCOUNTER_ID, "target").build())
            .setWorkerFactory(KoinWorkerFactory(koin))
            .build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        val updated = repository.current("target")
        assertEquals(LocationSource.CURRENT_FIX, updated.locationSource)
        assertEquals(Fix.lat, updated.lat)
    }
}
