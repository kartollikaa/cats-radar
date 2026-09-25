package dev.catsradar.domain.usecase

import dev.catsradar.domain.model.EncounterKind
import dev.catsradar.domain.model.EncounterOrigin
import dev.catsradar.domain.model.LocationSource
import dev.catsradar.domain.model.PlaceStatus
import dev.catsradar.domain.platform.ExifData
import dev.catsradar.domain.testing.FakeClock
import dev.catsradar.domain.testing.FakeDeviceIdProvider
import dev.catsradar.domain.testing.FakeDigest
import dev.catsradar.domain.testing.FakeEncounterRepository
import dev.catsradar.domain.testing.FakeExifReader
import dev.catsradar.domain.testing.FakeGallerySaver
import dev.catsradar.domain.testing.FakeIdGenerator
import dev.catsradar.domain.testing.FakeImageResizer
import dev.catsradar.domain.testing.FakePlaceCellRepository
import dev.catsradar.domain.testing.FakeSettingsRepository
import dev.catsradar.domain.testing.RecordingAnalytics
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class LogPhotoTest {

    private val encounters = FakeEncounterRepository()
    private val placeCells = FakePlaceCellRepository()
    private val settings = FakeSettingsRepository()
    private val exif = FakeExifReader()
    private val resizer = FakeImageResizer()
    private val digest = FakeDigest()
    private val gallery = FakeGallerySaver()

    private fun logPhoto() = LogPhoto(
        encounterRepository = encounters,
        placeCellRepository = placeCells,
        settingsRepository = settings,
        exifReader = exif,
        imageResizer = resizer,
        digest = digest,
        gallerySaver = gallery,
        idGenerator = FakeIdGenerator(),
        deviceIdProvider = FakeDeviceIdProvider(),
        clock = FakeClock(NOW),
        timeZone = TimeZone.UTC,
        analytics = RecordingAnalytics(),
    )

    @Test
    fun `a photo becomes exactly one camera encounter with its own copy`() = runTest {
        val result = logPhoto()(SOURCE)

        val logged = assertIs<PhotoResult.Logged>(result)
        assertEquals(1, encounters.inserted.size)
        assertEquals(EncounterKind.PHOTO, logged.encounter.kind)
        assertEquals(EncounterOrigin.CAMERA, logged.encounter.origin)
        val photo = logged.encounter.photos.single()
        assertEquals(FakeImageResizer.PHOTO_PATH, photo.photoPath)
        assertNotNull(photo.thumbPath)
        assertEquals(1, resizer.calls)
    }

    @Test
    fun `EXIF coordinates become the encounter's own, marked as coming from the photo`() = runTest {
        exif.data = ExifData(lat = 41.39864, lon = 2.17842)

        val logged = assertIs<PhotoResult.Logged>(logPhoto()(SOURCE))

        assertEquals(41.39864, logged.encounter.lat)
        assertEquals(2.17842, logged.encounter.lon)
        assertEquals(LocationSource.EXIF, logged.encounter.locationSource)
        assertNotNull(logged.encounter.geohash)
        assertEquals(false, logged.needsLocation)
    }

    @Test
    fun `a photo with no EXIF location gets none now and is handed to the background attach`() = runTest {
        val logged = assertIs<PhotoResult.Logged>(logPhoto()(SOURCE))

        assertNull(logged.encounter.lat)
        assertNull(logged.encounter.geohash)
        assertEquals(LocationSource.NONE, logged.encounter.locationSource)
        assertTrue(logged.needsLocation)
    }

    @Test
    fun `a photo whose EXIF latitude is off the globe is logged without a location and asks for a fix`() = runTest {
        exif.data = ExifData(lat = 200.0, lon = 2.17842)

        assertLoggedWithoutLocation(logPhoto()(SOURCE))
    }

    @Test
    fun `a photo whose EXIF longitude is off the globe is logged without a location and asks for a fix`() = runTest {
        exif.data = ExifData(lat = 41.39864, lon = -237.5)

        assertLoggedWithoutLocation(logPhoto()(SOURCE))
    }

    @Test
    fun `a photo whose EXIF coordinate is not a number is logged without a location and asks for a fix`() = runTest {
        exif.data = ExifData(lat = 41.39864, lon = Double.NaN)

        assertLoggedWithoutLocation(logPhoto()(SOURCE))
    }

    private fun assertLoggedWithoutLocation(result: PhotoResult) {
        val logged = assertIs<PhotoResult.Logged>(result)
        assertNull(logged.encounter.lat)
        assertNull(logged.encounter.lon)
        assertNull(logged.encounter.geohash)
        assertNull(logged.encounter.placeCellId)
        assertNull(logged.encounter.locationFixedAt)
        assertEquals(LocationSource.NONE, logged.encounter.locationSource)
        assertTrue(logged.needsLocation)
    }

    @Test
    fun `a photo whose EXIF coordinates are off the globe creates no place cell`() = runTest {
        exif.data = ExifData(lat = 41.39864, lon = -237.5)

        logPhoto()(SOURCE)

        assertEquals(emptyList(), placeCells.upserted)
    }

    @Test
    fun `half an EXIF coordinate pair is stored as no coordinates at all`() = runTest {
        exif.data = ExifData(lat = 41.39864)

        val logged = assertIs<PhotoResult.Logged>(logPhoto()(SOURCE))

        assertNull(logged.encounter.lat)
        assertNull(logged.encounter.lon)
        assertEquals(LocationSource.NONE, logged.encounter.locationSource)
    }

    @Test
    fun `a camera photo's EXIF coordinates create the place cell that can name them`() = runTest {
        exif.data = ExifData(lat = 41.39864, lon = 2.17842)

        val logged = assertIs<PhotoResult.Logged>(logPhoto()(SOURCE))

        assertEquals("sp3e98", logged.encounter.placeCellId)
        assertEquals(PlaceStatus.PENDING, placeCells.loadById("sp3e98")?.status)
    }

    @Test
    fun `a photo with no coordinates creates no place cell`() = runTest {
        val logged = assertIs<PhotoResult.Logged>(logPhoto()(SOURCE))

        assertNull(logged.encounter.placeCellId)
        assertEquals(emptyList(), placeCells.upserted)
    }

    @Test
    fun `an unreadable photo creates no encounter at all`() = runTest {
        resizer.result = null

        assertEquals(PhotoResult.Unreadable, logPhoto()(SOURCE))

        assertEquals(emptyList(), encounters.inserted)
    }

    @Test
    fun `an unreadable photo creates no place cell either`() = runTest {
        exif.data = ExifData(lat = 41.39864, lon = 2.17842)
        resizer.result = null

        assertEquals(PhotoResult.Unreadable, logPhoto()(SOURCE))

        assertEquals(emptyList(), placeCells.upserted)
    }

    @Test
    fun `an unreadable photo is never copied to the gallery either`() = runTest {
        resizer.result = null

        logPhoto()(SOURCE)

        // Otherwise the gallery would hold a cat the app has no record of.
        assertEquals(0, gallery.calls)
    }

    @Test
    fun `the original goes to the gallery when the setting is on`() = runTest {
        settings.saveOriginals = true

        val logged = assertIs<PhotoResult.Logged>(logPhoto()(SOURCE))

        assertEquals(1, gallery.calls)
        assertEquals(FakeGallerySaver.URI, logged.encounter.cover?.galleryUri)
    }

    @Test
    fun `the original stays out of the gallery when the setting is off`() = runTest {
        settings.saveOriginals = false

        val logged = assertIs<PhotoResult.Logged>(logPhoto()(SOURCE))

        assertEquals(0, gallery.calls)
        assertNull(logged.encounter.cover?.galleryUri)
    }

    @Test
    fun `a gallery that refuses the copy still leaves the encounter saved`() = runTest {
        settings.saveOriginals = true
        gallery.result = null

        val logged = assertIs<PhotoResult.Logged>(logPhoto()(SOURCE))

        assertEquals(1, encounters.inserted.size)
        assertNull(logged.encounter.cover?.galleryUri)
    }

    @Test
    fun `the original's digest is stored so a later import can recognise it`() = runTest {
        val logged = assertIs<PhotoResult.Logged>(logPhoto()(SOURCE))

        assertEquals(FakeDigest.SHA, logged.encounter.cover?.sourceDigest)
    }

    private companion object {
        const val SOURCE = "file:///cache/capture.jpg"
        val NOW = Instant.parse("2026-09-22T12:00:00Z")
    }
}
