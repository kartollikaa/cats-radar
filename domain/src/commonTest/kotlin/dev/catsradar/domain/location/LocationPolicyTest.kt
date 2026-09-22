package dev.catsradar.domain.location

import dev.catsradar.domain.model.LocationSource
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private val Now = Instant.parse("2026-09-22T10:00:00Z")
private val MaxAge = 6.hours

private fun fixAt(instant: Instant, lat: Double = 10.0, lon: Double = 20.0) =
    LocationFix(lat = lat, lon = lon, accuracyMeters = 5f, fixedAt = instant)

class LocationPolicyTest {
    @Test
    fun `a current fix wins even when a fresher-looking last known is also available`() = runTest {
        val currentFixValue = fixAt(Now, lat = 1.0, lon = 1.0)
        val lastKnownValue = fixAt(Now, lat = 2.0, lon = 2.0)

        val result = LocationPolicy.resolve(
            now = Now,
            currentFix = { currentFixValue },
            lastKnown = { lastKnownValue },
            lastKnownMaxAge = MaxAge,
        )

        assertEquals(LocationResult(currentFixValue, LocationSource.CURRENT_FIX), result)
    }

    @Test
    fun `a successful current fix never touches the last-known provider`() = runTest {
        val currentFixValue = fixAt(Now)
        var lastKnownCalls = 0

        val result = LocationPolicy.resolve(
            now = Now,
            currentFix = { currentFixValue },
            lastKnown = {
                lastKnownCalls++
                fixAt(Now)
            },
            lastKnownMaxAge = MaxAge,
        )

        assertEquals(0, lastKnownCalls)
        assertEquals(LocationSource.CURRENT_FIX, result.source)
    }

    @Test
    fun `falls back to a fresh last known when no current fix is available`() = runTest {
        val lastKnownValue = fixAt(Now - 1.hours)

        val result = LocationPolicy.resolve(
            now = Now,
            currentFix = { null },
            lastKnown = { lastKnownValue },
            lastKnownMaxAge = MaxAge,
        )

        assertEquals(LocationResult(lastKnownValue, LocationSource.LAST_KNOWN), result)
    }

    @Test
    fun `a last known exactly at the max age boundary is still accepted`() = runTest {
        val lastKnownValue = fixAt(Now - MaxAge)

        val result = LocationPolicy.resolve(
            now = Now,
            currentFix = { null },
            lastKnown = { lastKnownValue },
            lastKnownMaxAge = MaxAge,
        )

        assertEquals(LocationResult(lastKnownValue, LocationSource.LAST_KNOWN), result)
    }

    @Test
    fun `a last known older than the max age is rejected, not just an arbitrary stale one`() = runTest {
        val lastKnownValue = fixAt(Now - MaxAge - 1.minutes)

        val result = LocationPolicy.resolve(
            now = Now,
            currentFix = { null },
            lastKnown = { lastKnownValue },
            lastKnownMaxAge = MaxAge,
        )

        assertEquals(LocationResult(null, LocationSource.NONE), result)
    }

    @Test
    fun `nothing available resolves to NONE`() = runTest {
        val result =
            LocationPolicy.resolve(now = Now, currentFix = { null }, lastKnown = { null }, lastKnownMaxAge = MaxAge)

        assertEquals(LocationResult(null, LocationSource.NONE), result)
    }
}
