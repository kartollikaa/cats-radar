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

class ImportedPlaceCellTest {

    @Test
    fun `a cell centred on its own id comes through unchanged`() {
        assertEquals(namedMoscow, namedMoscow.withCenterFromId())
    }

    @Test
    fun `a centre off the globe is replaced by the centre of the cell's id`() {
        val imported = namedMoscow.copy(centerLat = 95.0, centerLon = -237.0)

        assertEquals(namedMoscow, imported.withCenterFromId())
    }

    @Test
    fun `a centre elsewhere on the globe is replaced by the centre of the cell's id`() {
        val imported = namedMoscow.copy(centerLat = 41.39864, centerLon = 2.17842)

        assertEquals(namedMoscow, imported.withCenterFromId())
    }

    @Test
    fun `an id shorter than a place cell is no cell at all`() {
        assertNull(namedMoscow.copy(cellId = "ucfv0").withCenterFromId())
    }

    @Test
    fun `an id longer than a place cell is no cell at all`() {
        assertNull(namedMoscow.copy(cellId = "ucfv0n01").withCenterFromId())
    }

    @Test
    fun `an id with a letter geohash never uses is no cell at all`() {
        assertNull(namedMoscow.copy(cellId = "ucfv0a").withCenterFromId())
    }

    @Test
    fun `an id in upper case is no cell at all`() {
        assertNull(namedMoscow.copy(cellId = "UCFV0N").withCenterFromId())
    }
}
