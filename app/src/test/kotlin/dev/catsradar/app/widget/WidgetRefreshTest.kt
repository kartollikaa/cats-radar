package dev.catsradar.app.widget

import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.EncounterKind
import dev.catsradar.domain.model.EncounterOrigin
import dev.catsradar.domain.model.LocationSource
import dev.catsradar.domain.model.LocationStamp
import dev.catsradar.domain.repository.EncounterRepository
import dev.catsradar.domain.usecase.ObserveTodayCount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

private val Now = Instant.parse("2026-09-22T12:00:00Z")

class WidgetRefreshTest {

    private val encounters = FakeTodayRepository()
    private var redraws = 0

    private fun TestScope.startRefresh() {
        WidgetRefresh(
            observeTodayCount = ObserveTodayCount(
                encounterRepository = encounters,
                clock = object : Clock {
                    override fun now(): Instant = Now
                },
            ) { TimeZone.UTC },
            widgetRedraw = { redraws++ },
        ).start(backgroundScope)
    }

    // A process can start after midnight, or because a lock-screen tap just wrote a row: in both the
    // widget is showing an older number than the first one read here.
    @Test
    fun startingRedrawsOnceWithWhatIsTrueNow() = runTest(UnconfinedTestDispatcher()) {
        encounters.add(id = "a", at = Now)
        startRefresh()

        assertEquals(1, redraws)
    }

    @Test
    fun aCatLoggedElsewhereRedrawsTheWidget() = runTest(UnconfinedTestDispatcher()) {
        startRefresh()

        encounters.add(id = "a", at = Now)

        assertEquals(2, redraws)
    }

    @Test
    fun aCatUndoneElsewhereRedrawsTheWidget() = runTest(UnconfinedTestDispatcher()) {
        encounters.add(id = "a", at = Now)
        startRefresh()

        encounters.softDelete("a", deletedAt = Now)

        assertEquals(2, redraws)
    }

    @Test
    fun aCatOnAnotherDayLeavesTheWidgetAlone() = runTest(UnconfinedTestDispatcher()) {
        startRefresh()

        encounters.add(id = "old", at = Instant.parse("2026-09-20T12:00:00Z"))

        assertEquals(1, redraws)
    }
}

private class FakeTodayRepository : EncounterRepository {
    private val rows = MutableStateFlow(emptyList<Encounter>())

    fun add(id: String, at: Instant) {
        rows.update { it + tally(id, at) }
    }

    override fun observeAll(): Flow<List<Encounter>> = rows.map { list -> list.filter { it.deletedAt == null } }

    override suspend fun setPlaceCell(
        id: String,
        lat: Double,
        lon: Double,
        geohash: String,
        placeCellId: String,
    ): Unit = throw NotImplementedError("unused by this test")

    override suspend fun softDelete(id: String, deletedAt: Instant) {
        rows.update { list -> list.map { if (it.id == id) it.copy(deletedAt = deletedAt) else it } }
    }

    override fun observeById(id: String): Flow<Encounter?> = throw NotImplementedError("unused by this test")
    override suspend fun insert(encounter: Encounter): Unit = throw NotImplementedError("unused by this test")
    override suspend fun update(encounter: Encounter): Unit = throw NotImplementedError("unused by this test")
    override suspend fun attachLocation(id: String, stamp: LocationStamp): Unit =
        throw NotImplementedError("unused by this test")

    override suspend fun undoDelete(id: String): Unit = throw NotImplementedError("unused by this test")
    override suspend fun softDeleteAll(ids: List<String>, deletedAt: Instant): Unit =
        throw NotImplementedError("unused by this test")

    override suspend fun undoDeleteAll(ids: List<String>, deletedAt: Instant): Unit =
        throw NotImplementedError("unused by this test")

    override suspend fun findBySourceDigest(sourceDigest: String): Encounter? = null
    override suspend fun loadEvery(): List<Encounter> = rows.value
    override suspend fun loadDeletedBefore(cutoff: Instant): List<Encounter> = emptyList()
    override suspend fun purgeDeletedBefore(cutoff: Instant): Int = 0
}

private fun tally(id: String, at: Instant): Encounter = Encounter(
    id = id,
    occurredAt = at,
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
    createdAt = at,
    updatedAt = at,
    deletedAt = null,
)
