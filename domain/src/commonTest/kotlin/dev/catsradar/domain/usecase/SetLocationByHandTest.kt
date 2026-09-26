package dev.catsradar.domain.usecase

import dev.catsradar.domain.Tuning
import dev.catsradar.domain.geo.Geohash
import dev.catsradar.domain.model.LocationSource
import dev.catsradar.domain.model.PlaceStatus
import dev.catsradar.domain.testing.FakeClock
import dev.catsradar.domain.testing.FakeEncounterRepository
import dev.catsradar.domain.testing.FakePlaceCellRepository
import dev.catsradar.domain.testing.RecordingAnalytics
import dev.catsradar.domain.testing.encounterFixture
import dev.catsradar.domain.testing.locatedFixture
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

private val LoggedAt = Instant.parse("2026-09-20T08:30:00Z")
private val Now = Instant.parse("2026-09-25T10:00:00Z")
private const val LAT = 41.39864
private const val LON = 2.17842

class SetLocationByHandTest {

    private val repository = FakeEncounterRepository()
    private val placeCells = FakePlaceCellRepository()
    private val setLocationByHand = SetLocationByHand(repository, placeCells, FakeClock(Now), RecordingAnalytics())

    private suspend fun cat(id: String) = repository.loadEvery().first { it.id == id }

    @Test
    fun `a cat with no location gets the point as set by hand, with no accuracy, at the time it was set`() = runTest {
        repository.insert(encounterFixture(id = "cat", occurredAt = LoggedAt))

        val set = setLocationByHand("cat", LAT, LON)

        assertTrue(set)
        val cat = cat("cat")
        val geohash = Geohash.encode(LAT, LON, Tuning.GEOHASH_PRECISION)
        assertEquals(LAT, cat.lat)
        assertEquals(LON, cat.lon)
        assertNull(cat.accuracyMeters)
        assertEquals(LocationSource.MANUAL, cat.locationSource)
        assertEquals(Now, cat.locationFixedAt)
        assertEquals(Now, cat.updatedAt)
        assertEquals(geohash, cat.geohash)
        assertEquals(Geohash.prefix(geohash, Tuning.PLACE_CELL_PRECISION), cat.placeCellId)
    }

    @Test
    fun `the point's place cell is remembered for the geocoder to name`() = runTest {
        repository.insert(encounterFixture(id = "cat", occurredAt = LoggedAt))

        setLocationByHand("cat", LAT, LON)

        val cell = placeCells.upserted.single()
        assertEquals(cat("cat").placeCellId, cell.cellId)
        assertEquals(PlaceStatus.PENDING, cell.status)
    }

    @Test
    fun `a cat that already has a location keeps it`() = runTest {
        val located = locatedFixture(id = "cat", occurredAt = LoggedAt, lat = 55.7558, lon = 37.6173)
        repository.insert(located)

        assertFalse(setLocationByHand("cat", LAT, LON))

        assertEquals(located, cat("cat"))
        assertTrue(placeCells.upserted.isEmpty())
    }

    @Test
    fun `a deleted cat gets nothing`() = runTest {
        val deleted = encounterFixture(id = "cat", occurredAt = LoggedAt).copy(deletedAt = LoggedAt + 1.hours)
        repository.insert(deleted)

        assertFalse(setLocationByHand("cat", LAT, LON))

        assertEquals(deleted, cat("cat"))
        assertTrue(placeCells.upserted.isEmpty())
    }

    @Test
    fun `an id no cat has writes nothing`() = runTest {
        repository.insert(encounterFixture(id = "other", occurredAt = LoggedAt))

        assertFalse(setLocationByHand("missing", LAT, LON))

        assertEquals(LocationSource.NONE, cat("other").locationSource)
        assertTrue(placeCells.upserted.isEmpty())
    }

    @Test
    fun `a point off the globe is refused`() = runTest {
        repository.insert(encounterFixture(id = "cat", occurredAt = LoggedAt))

        assertFalse(setLocationByHand("cat", 91.0, LON))
        assertFalse(setLocationByHand("cat", LAT, 181.0))
        assertFalse(setLocationByHand("cat", Double.NaN, LON))

        assertEquals(LocationSource.NONE, cat("cat").locationSource)
        assertTrue(repository.attachLocationCalls.isEmpty())
        assertTrue(placeCells.upserted.isEmpty())
    }
}
