package dev.catsradar.domain.usecase

import dev.catsradar.domain.location.LocationFix
import dev.catsradar.domain.testing.FakeClock
import dev.catsradar.domain.testing.FakeDeviceIdProvider
import dev.catsradar.domain.testing.FakeIdGenerator
import dev.catsradar.domain.testing.FakeLocationProvider
import dev.catsradar.domain.testing.FakeWalkRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class RecordWalkTest {

    @Test
    fun `every fix the device reports goes to the route of the walk that is on`() = runTest {
        val walks = FakeWalkRepository()
        StartWalk(walks, FakeIdGenerator(), FakeDeviceIdProvider(), FakeClock(START))()
        val fixes = flowOf(fix(atSecond = 5, latOffset = 0.0), fix(10, 0.001), fix(15, 0.002))

        RecordWalk(FakeLocationProvider(trackedFixes = fixes), RecordTrackPoint(walks))()

        assertEquals(listOf(5, 10, 15).map { START + it.seconds }, walks.points().map { it.at })
    }

    private fun fix(atSecond: Int, latOffset: Double) =
        LocationFix(41.3851 + latOffset, 2.1734, 8f, START + atSecond.seconds)

    private companion object {
        val START = Instant.parse("2026-09-23T09:00:00Z")
    }
}
