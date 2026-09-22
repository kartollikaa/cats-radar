package dev.catsradar.domain.usecase

import dev.catsradar.domain.Tuning
import dev.catsradar.domain.geo.Geohash
import dev.catsradar.domain.location.LocationFix
import dev.catsradar.domain.model.LocationSource
import dev.catsradar.domain.model.PlaceStatus
import dev.catsradar.domain.platform.LocationProvider
import dev.catsradar.domain.testing.FakeClock
import dev.catsradar.domain.testing.FakeEncounterRepository
import dev.catsradar.domain.testing.FakeLocationProvider
import dev.catsradar.domain.testing.FakePlaceCellRepository
import dev.catsradar.domain.testing.HangingLocationProvider
import dev.catsradar.domain.testing.MisbehavingLocationProvider
import dev.catsradar.domain.testing.encounterFixture
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private val Now = Instant.parse("2026-09-22T10:00:00Z")
private val Fix = LocationFix(lat = 55.7558, lon = 37.6173, accuracyMeters = 12f, fixedAt = Now - 2.minutes)

class AttachLocationTest {

    private val placeCells = FakePlaceCellRepository()
    private suspend fun encounter(repository: FakeEncounterRepository, id: String) =
        repository.observeById(id).first()!!

    @Test
    fun `attaching a fix remembers its place cell as pending, for the geocoder to name later`() = runTest {
        val repository = FakeEncounterRepository()
        repository.insert(encounterFixture(id = "target", occurredAt = Now))
        val attachLocation = AttachLocation(
            repository,
            placeCells,
            FakeLocationProvider(currentFix = Fix),
            FakeClock(Now)
        )

        attachLocation("target")

        val cell = placeCells.upserted.single()
        assertEquals(PlaceStatus.PENDING, cell.status)
        assertEquals(0, cell.attempts)
    }

    @Test
    fun `a second cat in the same cell does not queue it for naming twice`() = runTest {
        val repository = FakeEncounterRepository()
        repository.insert(encounterFixture(id = "target", occurredAt = Now))
        repository.insert(encounterFixture(id = "second", occurredAt = Now + 1.minutes))
        val attachLocation = AttachLocation(
            repository,
            placeCells,
            FakeLocationProvider(currentFix = Fix),
            FakeClock(Now)
        )

        attachLocation("target")
        attachLocation("second")

        assertEquals(1, placeCells.upserted.size)
    }

    @Test
    fun `stamps the target encounter with the fix, using the fix's own timestamp, not occurredAt`() = runTest {
        val repository = FakeEncounterRepository()
        repository.insert(encounterFixture(id = "target", occurredAt = Now))
        val attachLocation = AttachLocation(
            repository,
            placeCells,
            FakeLocationProvider(currentFix = Fix),
            FakeClock(Now)
        )

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
        val attachLocation = AttachLocation(
            repository,
            placeCells,
            FakeLocationProvider(currentFix = Fix),
            FakeClock(Now)
        )

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
        val attachLocation = AttachLocation(
            repository,
            placeCells,
            FakeLocationProvider(currentFix = Fix),
            FakeClock(Now)
        )

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
        val attachLocation = AttachLocation(
            repository,
            placeCells,
            FakeLocationProvider(currentFix = Fix),
            FakeClock(Now)
        )

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
        val attachLocation = AttachLocation(
            repository,
            placeCells,
            FakeLocationProvider(lastKnownFix = Fix),
            FakeClock(Now)
        )

        attachLocation("target")

        assertEquals(LocationSource.LAST_KNOWN, encounter(repository, "target").locationSource)
        assertEquals(LocationSource.NONE, encounter(repository, "sameOuting").locationSource)
    }

    @Test
    fun `nothing available leaves the target as NONE`() = runTest {
        val repository = FakeEncounterRepository()
        repository.insert(encounterFixture(id = "target", occurredAt = Now))
        val attachLocation = AttachLocation(repository, placeCells, FakeLocationProvider(), FakeClock(Now))

        attachLocation("target")

        val updated = encounter(repository, "target")
        assertEquals(LocationSource.NONE, updated.locationSource)
        assertNull(updated.lat)
    }

    @Test
    fun `a fix that resolves after the target was undone does not resurrect it`() = runTest {
        val repository = FakeEncounterRepository()
        repository.insert(encounterFixture(id = "target", occurredAt = Now))
        // Simulates the undo landing while the up-to-LOCATION_TIMEOUT fetch is still in flight.
        val locationProvider = object : LocationProvider {
            override suspend fun getCurrentFix(timeout: Duration): LocationFix? {
                repository.softDelete("target", Now)
                return Fix
            }

            override suspend fun lastKnown(): LocationFix? = null
        }
        val attachLocation = AttachLocation(repository, placeCells, locationProvider, FakeClock(Now))

        attachLocation("target")

        val target = encounter(repository, "target")
        assertEquals(Now, target.deletedAt)
        assertEquals(LocationSource.NONE, target.locationSource)
        assertNull(target.lat)
    }

    @Test
    fun `a soft-deleted encounter inside the outing is never backfilled`() = runTest {
        val repository = FakeEncounterRepository()
        // Its own timestamp falls inside the outing that "liveInOuting" and "target" form, even
        // though SessionSplitter never sees it (it filters deletedAt itself): only the candidate
        // list's own filter stands between it and a backfill attempt.
        repository.insert(encounterFixture(id = "liveInOuting", occurredAt = Now - 15.minutes))
        repository.insert(encounterFixture(id = "deletedInOuting", occurredAt = Now - 10.minutes))
        repository.softDelete("deletedInOuting", Now - 5.minutes)
        repository.insert(encounterFixture(id = "target", occurredAt = Now))
        val attachLocation = AttachLocation(
            repository,
            placeCells,
            FakeLocationProvider(currentFix = Fix),
            FakeClock(Now)
        )

        attachLocation("target")

        assertFalse("deletedInOuting" in repository.attachLocationCalls)
        val deleted = encounter(repository, "deletedInOuting")
        assertEquals(LocationSource.NONE, deleted.locationSource)
        assertNull(deleted.lat)

        // Positive control: the outing itself was computed and a real candidate in it was backfilled.
        assertEquals(LocationSource.BACKFILLED, encounter(repository, "liveInOuting").locationSource)
    }

    @Test
    fun `an encounter from a later outing is never backfilled`() = runTest {
        val repository = FakeEncounterRepository()
        repository.insert(encounterFixture(id = "sameOuting", occurredAt = Now - 10.minutes))
        repository.insert(encounterFixture(id = "target", occurredAt = Now))
        // 45 min after the target, past SESSION_GAP: a separate, later outing.
        repository.insert(encounterFixture(id = "laterOuting", occurredAt = Now + 45.minutes))
        val attachLocation = AttachLocation(
            repository,
            placeCells,
            FakeLocationProvider(currentFix = Fix),
            FakeClock(Now)
        )

        attachLocation("target")

        val later = encounter(repository, "laterOuting")
        assertEquals(LocationSource.NONE, later.locationSource)
        assertNull(later.lat)
    }

    @Test
    fun `an already-located target is left untouched and never re-triggers backfill`() = runTest {
        val repository = FakeEncounterRepository()
        repository.insert(
            encounterFixture(
                id = "target",
                occurredAt = Now,
                locationSource = LocationSource.CURRENT_FIX,
                lat = 1.0,
                lon = 1.0,
            ),
        )
        repository.insert(encounterFixture(id = "sameOuting", occurredAt = Now - 10.minutes))
        val attachLocation = AttachLocation(
            repository,
            placeCells,
            FakeLocationProvider(currentFix = Fix),
            FakeClock(Now)
        )

        attachLocation("target")

        val target = encounter(repository, "target")
        assertEquals(1.0, target.lat)
        assertEquals(1.0, target.lon)
        val sameOuting = encounter(repository, "sameOuting")
        assertEquals(LocationSource.NONE, sameOuting.locationSource)
    }

    @Test
    fun `a current fix that never arrives times out and falls through to a fresh last known`() = runTest {
        val repository = FakeEncounterRepository()
        repository.insert(encounterFixture(id = "target", occurredAt = Now))
        val lastKnownFix = Fix.copy(fixedAt = Now - 1.hours)
        val locationProvider = HangingLocationProvider(lastKnownFix = lastKnownFix)
        val attachLocation = AttachLocation(repository, placeCells, locationProvider, FakeClock(Now))

        attachLocation("target")

        assertEquals(Tuning.LOCATION_TIMEOUT, locationProvider.recordedTimeout)
        val updated = encounter(repository, "target")
        assertEquals(LocationSource.LAST_KNOWN, updated.locationSource)
        assertEquals(lastKnownFix.lat, updated.lat)
    }

    @Test
    fun `a current fix that never arrives times out and falls through to NONE without a last known`() = runTest {
        val repository = FakeEncounterRepository()
        repository.insert(encounterFixture(id = "target", occurredAt = Now))
        val locationProvider = HangingLocationProvider(lastKnownFix = null)
        val attachLocation = AttachLocation(repository, placeCells, locationProvider, FakeClock(Now))

        attachLocation("target")

        assertEquals(Tuning.LOCATION_TIMEOUT, locationProvider.recordedTimeout)
        val updated = encounter(repository, "target")
        assertEquals(LocationSource.NONE, updated.locationSource)
        assertNull(updated.lat)
    }

    @Test
    fun `a provider that ignores its own timeout is still bounded by AttachLocation's own backstop`() = runTest {
        val repository = FakeEncounterRepository()
        repository.insert(encounterFixture(id = "target", occurredAt = Now))
        val attachLocation = AttachLocation(repository, placeCells, MisbehavingLocationProvider(), FakeClock(Now))

        attachLocation("target")

        val updated = encounter(repository, "target")
        assertEquals(LocationSource.NONE, updated.locationSource)
        assertNull(updated.lat)
    }
}
