package dev.catsradar.domain.usecase

import dev.catsradar.domain.Tuning
import dev.catsradar.domain.geo.GeoPoint
import dev.catsradar.domain.location.LocationFix
import dev.catsradar.domain.testing.FakeClock
import dev.catsradar.domain.testing.FakeLocationProvider
import dev.catsradar.domain.testing.MisbehavingLocationProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private val Now = Instant.parse("2026-09-26T10:00:00Z")
private val Fresh = LocationFix(lat = 41.39864, lon = 2.17842, accuracyMeters = 8f, fixedAt = Now)

class LocatePhoneTest {

    @Test
    fun `a fresh fix says where the phone is`() = runTest {
        val locate = LocatePhone(FakeLocationProvider(currentFix = Fresh), FakeClock(Now))

        assertEquals(GeoPoint(Fresh.lat, Fresh.lon), locate())
    }

    @Test
    fun `a fresh fix wins over a recent last known one`() = runTest {
        val recent = Fresh.copy(lat = 55.7558, lon = 37.6173, fixedAt = Now - 10.minutes)
        val locate = LocatePhone(FakeLocationProvider(currentFix = Fresh, lastKnownFix = recent), FakeClock(Now))

        assertEquals(GeoPoint(Fresh.lat, Fresh.lon), locate())
    }

    @Test
    fun `without a fresh fix, a recent last known one answers`() = runTest {
        val recent = Fresh.copy(lat = 55.7558, lon = 37.6173, fixedAt = Now - 10.minutes)
        val locate = LocatePhone(FakeLocationProvider(lastKnownFix = recent), FakeClock(Now))

        assertEquals(GeoPoint(recent.lat, recent.lon), locate())
    }

    @Test
    fun `a last known fix older than a tally would take says nothing`() = runTest {
        val stale = Fresh.copy(fixedAt = Now - Tuning.LAST_KNOWN_MAX_AGE - 1.minutes)
        val locate = LocatePhone(FakeLocationProvider(lastKnownFix = stale), FakeClock(Now))

        assertNull(locate())
    }

    @Test
    fun `nothing available says nothing`() = runTest {
        assertNull(LocatePhone(FakeLocationProvider(), FakeClock(Now))())
    }

    @Test
    fun `a provider that never answers still ends`() = runTest {
        assertNull(LocatePhone(MisbehavingLocationProvider(), FakeClock(Now))())
    }
}
