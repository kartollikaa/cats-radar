package dev.catsradar.domain.backup

import dev.catsradar.domain.model.CatCoat
import dev.catsradar.domain.model.EncounterKind
import dev.catsradar.domain.model.EncounterOrigin
import dev.catsradar.domain.model.LocationSource
import dev.catsradar.domain.testing.encounterAt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

private val OCCURRED = Instant.parse("2026-09-10T08:00:00Z")
private val FIXED = Instant.parse("2026-09-10T08:00:05Z")
private val EDITED = Instant.parse("2026-09-12T19:30:00Z")

private val locatedPhoto = encounterAt(OCCURRED).copy(
    id = "cat",
    kind = EncounterKind.PHOTO,
    origin = EncounterOrigin.CAMERA,
    coat = CatCoat.GINGER,
    photoPath = "2026/09/cat.jpg",
    thumbPath = "2026/09/cat_thumb.jpg",
    lat = 55.7558,
    lon = 37.6173,
    accuracyMeters = 12f,
    locationSource = LocationSource.CURRENT_FIX,
    locationFixedAt = FIXED,
    geohash = "ucfv0h8y",
    placeCellId = "ucfv0h",
    updatedAt = EDITED,
)

private val unlocatedPhoto = locatedPhoto.copy(
    lat = null,
    lon = null,
    accuracyMeters = null,
    locationSource = LocationSource.NONE,
    locationFixedAt = null,
    geohash = null,
    placeCellId = null,
)

class ImportedLocationTest {

    @Test
    fun `a cat on the globe keeps its location`() {
        assertEquals(locatedPhoto, locatedPhoto.withoutLocationUnlessOnGlobe())
    }

    @Test
    fun `a latitude past a pole leaves the cat without a location`() {
        val imported = locatedPhoto.copy(lat = 155.7558)

        assertEquals(unlocatedPhoto, imported.withoutLocationUnlessOnGlobe())
    }

    @Test
    fun `a longitude past the antimeridian leaves the cat without a location`() {
        val imported = locatedPhoto.copy(lon = -237.6173)

        assertEquals(unlocatedPhoto, imported.withoutLocationUnlessOnGlobe())
    }

    @Test
    fun `half a coordinate pair leaves the cat without a location`() {
        val imported = locatedPhoto.copy(lon = null)

        assertEquals(unlocatedPhoto, imported.withoutLocationUnlessOnGlobe())
    }

    @Test
    fun `a cat that never had a location stays as it is`() {
        assertEquals(unlocatedPhoto, unlocatedPhoto.withoutLocationUnlessOnGlobe())
    }
}
