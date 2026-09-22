package dev.catsradar.domain.usecase

import dev.catsradar.domain.Tuning
import dev.catsradar.domain.geo.Geohash
import dev.catsradar.domain.location.LocationFix
import dev.catsradar.domain.model.LocationSource
import dev.catsradar.domain.testing.FakeClock
import dev.catsradar.domain.testing.FakeEncounterRepository
import dev.catsradar.domain.testing.FakeLocationProvider
import dev.catsradar.domain.testing.encounterFixture
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private val Now = Instant.parse("2026-09-22T10:00:00Z")
private val Fix = LocationFix(lat = 55.7558, lon = 37.6173, accuracyMeters = 12f, fixedAt = Now - 2.minutes)

class AttachLocationTest {
    private suspend fun encounter(repository: FakeEncounterRepository, id: String) =
        repository.observeById(id).first()!!

    @Test
    fun `stamps the target encounter with the fix, using the fix's own timestamp, not occurredAt`() = runTest {
        val repository = FakeEncounterRepository()
        repository.insert(encounterFixture(id = "target", occurredAt = Now))
        val attachLocation = AttachLocation(repository, FakeLocationProvider(currentFix = Fix), FakeClock(Now))

        attachLocation("target")

        val updated = encounter(repository, "target")
        assertEquals(Fix.lat, updated.lat)
        assertEquals(Fix.lon, updated.lon)
        assertEquals(Fix.accuracyMeters, updated.accuracyMeters)
        assertEquals(LocationSource.CURRENT_FIX, updated.locationSource)
        assertEquals(Fix.fixedAt, updated.locationFixedAt)
    }

    @Test
    fun `geohash and placeCellId use their own distinct precisions`() = runTest {
        val repository = FakeEncounterRepository()
        repository.insert(encounterFixture(id = "target", occurredAt = Now))
        val attachLocation = AttachLocation(repository, FakeLocationProvider(currentFix = Fix), FakeClock(Now))

        attachLocation("target")

        val updated = encounter(repository, "target")
        val expectedGeohash = Geohash.encode(Fix.lat, Fix.lon, Tuning.GEOHASH_PRECISION)
        assertEquals(expectedGeohash, updated.geohash)
        assertEquals(Geohash.prefix(expectedGeohash, Tuning.PLACE_CELL_PRECISION), updated.placeCellId)
    }

    @Test
    fun `a current fix backfills NONE encounters in the same outing but not an earlier one`() = runTest {
        val repository = FakeEncounterRepository()
        // gap to "sameOuting" is 35 min, past SESSION_GAP (30 min): a separate, earlier outing.
        repository.insert(encounterFixture(id = "prevOuting", occurredAt = Now - 45.minutes))
        repository.insert(encounterFixture(id = "sameOuting", occurredAt = Now - 10.minutes))
        repository.insert(encounterFixture(id = "target", occurredAt = Now))
        val attachLocation = AttachLocation(repository, FakeLocationProvider(currentFix = Fix), FakeClock(Now))

        attachLocation("target")

        val sameOuting = encounter(repository, "sameOuting")
        assertEquals(LocationSource.BACKFILLED, sameOuting.locationSource)
        assertEquals(Fix.lat, sameOuting.lat)
        assertEquals(Fix.lon, sameOuting.lon)

        val prevOuting = encounter(repository, "prevOuting")
        assertEquals(LocationSource.NONE, prevOuting.locationSource)
        assertNull(prevOuting.lat)
    }

    @Test
    fun `backfill never overwrites an encounter that already has coordinates`() = runTest {
        val repository = FakeEncounterRepository()
        repository.insert(
            encounterFixture(
                id = "alreadyLocated",
                occurredAt = Now - 5.minutes,
                locationSource = LocationSource.LAST_KNOWN,
                lat = 1.0,
                lon = 2.0,
            ),
        )
        repository.insert(encounterFixture(id = "target", occurredAt = Now))
        val attachLocation = AttachLocation(repository, FakeLocationProvider(currentFix = Fix), FakeClock(Now))

        attachLocation("target")

        val alreadyLocated = encounter(repository, "alreadyLocated")
        assertEquals(LocationSource.LAST_KNOWN, alreadyLocated.locationSource)
        assertEquals(1.0, alreadyLocated.lat)
        assertEquals(2.0, alreadyLocated.lon)
    }

    @Test
    fun `a last known fix updates only the target, without backfilling the outing`() = runTest {
        val repository = FakeEncounterRepository()
        repository.insert(encounterFixture(id = "sameOuting", occurredAt = Now - 10.minutes))
        repository.insert(encounterFixture(id = "target", occurredAt = Now))
        val attachLocation = AttachLocation(repository, FakeLocationProvider(lastKnownFix = Fix), FakeClock(Now))

        attachLocation("target")

        assertEquals(LocationSource.LAST_KNOWN, encounter(repository, "target").locationSource)
        assertEquals(LocationSource.NONE, encounter(repository, "sameOuting").locationSource)
    }

    @Test
    fun `nothing available leaves the target as NONE`() = runTest {
        val repository = FakeEncounterRepository()
        repository.insert(encounterFixture(id = "target", occurredAt = Now))
        val attachLocation = AttachLocation(repository, FakeLocationProvider(), FakeClock(Now))

        attachLocation("target")

        val updated = encounter(repository, "target")
        assertEquals(LocationSource.NONE, updated.locationSource)
        assertNull(updated.lat)
    }
}
