package dev.catsradar.domain.usecase

import dev.catsradar.domain.geo.GeoPoint
import dev.catsradar.domain.location.LocationFix
import dev.catsradar.domain.testing.FakeEncounterRepository
import dev.catsradar.domain.testing.FakeLocationProvider
import dev.catsradar.domain.testing.encounterFixture
import dev.catsradar.domain.testing.locatedFixture
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private val LoggedAt = Instant.parse("2026-09-20T08:30:00Z")
private val OldFix = LocationFix(lat = 59.9386, lon = 30.3141, accuracyMeters = 20f, fixedAt = LoggedAt - 30.days)

class WhereToLookTest {

    private val repository = FakeEncounterRepository()

    @Test
    fun `the located cat logged closest in time is where to look, before the phone's own position`() = runTest {
        repository.insert(encounterFixture(id = "cat", occurredAt = LoggedAt))
        repository.insert(locatedFixture(id = "near", occurredAt = LoggedAt + 5.minutes, lat = 41.39864, lon = 2.17842))
        repository.insert(locatedFixture(id = "far", occurredAt = LoggedAt + 3.days, lat = 55.7558, lon = 37.6173))

        val look = WhereToLook(repository, FakeLocationProvider(lastKnownFix = OldFix))

        assertEquals(GeoPoint(41.39864, 2.17842), look("cat"))
    }

    @Test
    fun `with no other located cat, the phone's last known position is, however old`() = runTest {
        repository.insert(encounterFixture(id = "cat", occurredAt = LoggedAt))
        repository.insert(encounterFixture(id = "unlocated", occurredAt = LoggedAt + 1.minutes))

        val look = WhereToLook(repository, FakeLocationProvider(lastKnownFix = OldFix))

        assertEquals(GeoPoint(OldFix.lat, OldFix.lon), look("cat"))
    }

    @Test
    fun `with neither, there is nowhere to look`() = runTest {
        repository.insert(encounterFixture(id = "cat", occurredAt = LoggedAt))

        assertNull(WhereToLook(repository, FakeLocationProvider())("cat"))
    }

    @Test
    fun `a last known position off the globe is nowhere`() = runTest {
        repository.insert(encounterFixture(id = "cat", occurredAt = LoggedAt))
        val offGlobe = OldFix.copy(lat = 95.0)

        assertNull(WhereToLook(repository, FakeLocationProvider(lastKnownFix = offGlobe))("cat"))
    }
}
