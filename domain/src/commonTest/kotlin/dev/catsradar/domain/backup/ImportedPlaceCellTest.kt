package dev.catsradar.domain.backup

import dev.catsradar.domain.model.PlaceCell
import dev.catsradar.domain.model.PlaceStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

private val namedMoscow = PlaceCell(
    cellId = "ucfv0n",
    centerLat = 55.75836181640625,
    centerLon = 37.6226806640625,
    countryCode = "RU",
    countryName = "Russia",
    adminArea = "Moscow",
    locality = "Moscow",
    subLocality = "Tverskoy",
    status = PlaceStatus.RESOLVED,
    attempts = 2,
    lastAttemptAt = Instant.parse("2026-09-12T19:29:58Z"),
    resolvedAt = Instant.parse("2026-09-12T19:30:00Z"),
)

private val untriedMoscow = PlaceCell(
    cellId = "ucfv0n",
    centerLat = 55.75836181640625,
    centerLon = 37.6226806640625,
    countryCode = null,
    countryName = null,
    adminArea = null,
    locality = null,
    subLocality = null,
    status = PlaceStatus.PENDING,
    attempts = 0,
    lastAttemptAt = null,
    resolvedAt = null,
)

private fun triedMoscow(status: PlaceStatus, attempts: Int) = untriedMoscow.copy(
    centerLat = 41.39864,
    centerLon = 2.17842,
    status = status,
    attempts = attempts,
    lastAttemptAt = Instant.parse("2026-09-12T19:29:58Z"),
)

class ImportedPlaceCellTest {

    @Test
    fun `a cell centred on its own id comes through unchanged`() {
        assertEquals(namedMoscow, namedMoscow.asImported())
    }

    @Test
    fun `a centre off the globe is replaced by the centre of the cell's id`() {
        val imported = namedMoscow.copy(centerLat = 95.0, centerLon = -237.0)

        assertEquals(namedMoscow, imported.asImported())
    }

    @Test
    fun `a centre elsewhere on the globe is replaced by the centre of the cell's id`() {
        val imported = namedMoscow.copy(centerLat = 41.39864, centerLon = 2.17842)

        assertEquals(namedMoscow, imported.asImported())
    }

    @Test
    fun `an id shorter than a place cell is no cell at all`() {
        assertNull(namedMoscow.copy(cellId = "ucfv0").asImported())
    }

    @Test
    fun `an id longer than a place cell is no cell at all`() {
        assertNull(namedMoscow.copy(cellId = "ucfv0n01").asImported())
    }

    @Test
    fun `an id with a letter geohash never uses is no cell at all`() {
        assertNull(namedMoscow.copy(cellId = "ucfv0a").asImported())
    }

    @Test
    fun `an id in upper case is no cell at all`() {
        assertNull(namedMoscow.copy(cellId = "UCFV0N").asImported())
    }

    @Test
    fun `a cell another device is still trying arrives untried`() {
        assertEquals(untriedMoscow, triedMoscow(PlaceStatus.PENDING, attempts = 3).asImported())
    }

    @Test
    fun `a cell another device gave up on arrives untried`() {
        assertEquals(untriedMoscow, triedMoscow(PlaceStatus.FAILED, attempts = 5).asImported())
    }

    @Test
    fun `a cell another device had no geocoder for arrives untried`() {
        assertEquals(untriedMoscow, triedMoscow(PlaceStatus.UNAVAILABLE, attempts = 1).asImported())
    }

    @Test
    fun `an unnamed cell whose id is not a place cell is no cell at all`() {
        assertNull(triedMoscow(PlaceStatus.PENDING, attempts = 3).copy(cellId = "ucfv0n0123456").asImported())
    }

    @Test
    fun `a name written on a cell that was never resolved does not come with it`() {
        assertEquals(untriedMoscow, namedMoscow.copy(status = PlaceStatus.FAILED).asImported())
    }
}
