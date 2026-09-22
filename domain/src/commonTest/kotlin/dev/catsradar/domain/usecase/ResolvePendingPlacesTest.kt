package dev.catsradar.domain.usecase

import dev.catsradar.domain.model.PlaceCell
import dev.catsradar.domain.model.PlaceStatus
import dev.catsradar.domain.platform.GeocodeResult
import dev.catsradar.domain.platform.PlaceName
import dev.catsradar.domain.platform.ReverseGeocoder
import dev.catsradar.domain.testing.FakeClock
import dev.catsradar.domain.testing.FakePlaceCellRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private class ScriptedGeocoder(private vararg val results: GeocodeResult) : ReverseGeocoder {
    var calls = 0
        private set

    override suspend fun resolve(lat: Double, lon: Double): GeocodeResult =
        results[minOf(calls++, results.lastIndex)]
}

class ResolvePendingPlacesTest {

    private fun pending(id: String, attempts: Int = 0) = PlaceCell(
        cellId = id,
        centerLat = 41.4,
        centerLon = 2.2,
        countryCode = null,
        countryName = null,
        adminArea = null,
        locality = null,
        subLocality = null,
        status = PlaceStatus.PENDING,
        attempts = attempts,
        lastAttemptAt = null,
        resolvedAt = null,
    )

    private fun resolver(repository: FakePlaceCellRepository, geocoder: ReverseGeocoder) =
        ResolvePendingPlaces(repository, geocoder, FakeClock(NOW))

    @Test
    fun `a resolved cell keeps its name and stops being pending`() = runTest {
        val repository = FakePlaceCellRepository(listOf(pending("cell-1")))
        val name = PlaceName(countryCode = "ES", countryName = "Spain", locality = "Barcelona")

        resolver(repository, ScriptedGeocoder(GeocodeResult.Resolved(name)))()

        val cell = assertNotNull(repository.loadById("cell-1"))
        assertEquals(PlaceStatus.RESOLVED, cell.status)
        assertEquals("Barcelona", cell.locality)
        assertEquals(NOW, cell.resolvedAt)
    }

    @Test
    fun `a failure leaves the cell pending so the next run tries again`() = runTest {
        val repository = FakePlaceCellRepository(listOf(pending("cell-1")))

        resolver(repository, ScriptedGeocoder(GeocodeResult.Failed))()

        val cell = assertNotNull(repository.loadById("cell-1"))
        assertEquals(PlaceStatus.PENDING, cell.status)
        assertEquals(1, cell.attempts)
        assertEquals(NOW, cell.lastAttemptAt)
    }

    @Test
    fun `a cell that has failed too often is given up on rather than retried forever`() = runTest {
        val repository = FakePlaceCellRepository(listOf(pending("cell-1", attempts = MAX_GEOCODE_ATTEMPTS - 1)))

        resolver(repository, ScriptedGeocoder(GeocodeResult.Failed))()

        assertEquals(PlaceStatus.FAILED, assertNotNull(repository.loadById("cell-1")).status)
    }

    @Test
    fun `no geocoder on the device marks the cell unavailable and stops the run`() = runTest {
        val repository = FakePlaceCellRepository(listOf(pending("a"), pending("b")))
        val geocoder = ScriptedGeocoder(GeocodeResult.Unavailable)

        val keepGoing = resolver(repository, geocoder)()

        assertFalse(keepGoing, "the caller must learn there is no point rescheduling")
        // The second cell is never even attempted: the answer would be the same.
        assertEquals(1, geocoder.calls)
        assertEquals(PlaceStatus.UNAVAILABLE, assertNotNull(repository.loadById("a")).status)
        assertEquals(PlaceStatus.PENDING, assertNotNull(repository.loadById("b")).status)
    }

    @Test
    fun `every pending cell is attempted when the geocoder works`() = runTest {
        val repository = FakePlaceCellRepository(List(3) { pending("cell-$it") })
        val geocoder = ScriptedGeocoder(GeocodeResult.Resolved(PlaceName(countryCode = "ES")))

        val keepGoing = resolver(repository, geocoder)()

        assertTrue(keepGoing)
        assertEquals(3, geocoder.calls)
    }

    @Test
    fun `an already resolved cell is never looked up again`() = runTest {
        val resolved = pending("done").copy(status = PlaceStatus.RESOLVED, countryCode = "ES")
        val repository = FakePlaceCellRepository(listOf(resolved))
        val geocoder = ScriptedGeocoder(GeocodeResult.Resolved(PlaceName(countryCode = "FR")))

        resolver(repository, geocoder)()

        assertEquals(0, geocoder.calls)
        assertEquals("ES", assertNotNull(repository.loadById("done")).countryCode)
    }

    @Test
    fun `nothing pending is not an error`() = runTest {
        val repository = FakePlaceCellRepository()

        assertTrue(resolver(repository, ScriptedGeocoder(GeocodeResult.Failed))())
        assertNull(repository.loadById("anything"))
    }

    private companion object {
        val NOW = Instant.parse("2026-09-22T12:00:00Z")
    }
}
