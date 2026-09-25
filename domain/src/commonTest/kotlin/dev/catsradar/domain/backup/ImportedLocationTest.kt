package dev.catsradar.domain.backup

import dev.catsradar.domain.model.CatCoat
import dev.catsradar.domain.model.EncounterKind
import dev.catsradar.domain.model.EncounterOrigin
import dev.catsradar.domain.model.LocationSource
import dev.catsradar.domain.testing.encounterAt
import dev.catsradar.domain.testing.withPhoto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

private val OCCURRED = Instant.parse("2026-09-10T08:00:00Z")
private val FIXED = Instant.parse("2026-09-10T08:00:05Z")
private val EDITED = Instant.parse("2026-09-12T19:30:00Z")

private val locatedPhoto = encounterAt(OCCURRED, tzOffsetMinutes = 180).copy(
    id = "cat",
    kind = EncounterKind.PHOTO,
    origin = EncounterOrigin.CAMERA,
    coat = CatCoat.GINGER,
    lat = 55.7558,
    lon = 37.6173,
    accuracyMeters = 12f,
    locationSource = LocationSource.CURRENT_FIX,
    locationFixedAt = FIXED,
    geohash = "ucfv0n01",
    placeCellId = "ucfv0n",
    updatedAt = EDITED,
).withPhoto(
    photoPath = "2026/09/cat.jpg",
    thumbPath = "2026/09/cat_thumb.jpg",
    galleryUri = "content://media/external/images/media/4211",
    sourceDigest = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08",
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
        assertEquals(locatedPhoto, locatedPhoto.withLocationFromCoordinates())
    }

    @Test
    fun `a geohash and place cell that disagree with the coordinates are derived from them again`() {
        val imported = locatedPhoto.copy(geohash = "u", placeCellId = "zzzzzz")

        assertEquals(locatedPhoto, imported.withLocationFromCoordinates())
    }

    @Test
    fun `a latitude past a pole leaves the cat without a location`() {
        val imported = locatedPhoto.copy(lat = 155.7558)

        assertEquals(unlocatedPhoto, imported.withLocationFromCoordinates())
    }

    @Test
    fun `a longitude past the antimeridian leaves the cat without a location`() {
        val imported = locatedPhoto.copy(lon = -237.6173)

        assertEquals(unlocatedPhoto, imported.withLocationFromCoordinates())
    }

    @Test
    fun `half a coordinate pair leaves the cat without a location`() {
        val imported = locatedPhoto.copy(lon = null)

        assertEquals(unlocatedPhoto, imported.withLocationFromCoordinates())
    }

    @Test
    fun `a location source with no coordinates leaves the cat without a location`() {
        val imported = locatedPhoto.copy(lat = null, lon = null)

        assertEquals(unlocatedPhoto, imported.withLocationFromCoordinates())
    }

    @Test
    fun `coordinates on a cat marked as having no location are dropped`() {
        val imported = locatedPhoto.copy(locationSource = LocationSource.NONE)

        assertEquals(unlocatedPhoto, imported.withLocationFromCoordinates())
    }

    @Test
    fun `a cat that never had a location stays as it is`() {
        assertEquals(unlocatedPhoto, unlocatedPhoto.withLocationFromCoordinates())
    }
}
