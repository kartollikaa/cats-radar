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
import dev.catsradar.domain.testing.FakeGalleryItemLocator
import dev.catsradar.domain.testing.FakeIdGenerator
import dev.catsradar.domain.testing.FakeImageResizer
import dev.catsradar.domain.testing.FakePlaceCellRepository
import dev.catsradar.domain.testing.FakeSourceFileTime
import dev.catsradar.domain.testing.RecordingAnalytics
import dev.catsradar.domain.testing.encounterFixture
import dev.catsradar.domain.testing.withPhoto
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class ImportPhotosTest {

    private val repository = FakeEncounterRepository()
    private val placeCells = FakePlaceCellRepository()
    private val exifReader = FakeExifReader()
    private val imageResizer = FakeImageResizer()
    private val digest = FakeDigest()
    private val sourceFileTime = FakeSourceFileTime()
    private val locator = FakeGalleryItemLocator(mapOf(PICKED_FROM_PHONE to PHONE_ITEM))

    private fun importPhotos(timeZone: TimeZone = TimeZone.UTC) = ImportPhotos(
        encounterRepository = repository,
        placeCellRepository = placeCells,
        exifReader = exifReader,
        imageResizer = imageResizer,
        digest = digest,
        sourceFileTime = sourceFileTime,
        galleryItemLocator = locator,
        idGenerator = FakeIdGenerator(),
        deviceIdProvider = FakeDeviceIdProvider(),
        clock = FakeClock(NOW),
        timeZone = timeZone,
        analytics = RecordingAnalytics(),
    )

    @Test
    fun `an imported photo is a gallery encounter that keeps the original where it already is`() = runTest {
        exifReader.data = ExifData(takenAt = LAST_MONTH)

        val summary = importPhotos()(listOf("content://picked/1"))

        assertEquals(listOf(ImportedPhoto(id = "id-1", needsLocation = false)), summary.added)
        val inserted = repository.inserted.single()
        assertEquals(EncounterOrigin.GALLERY, inserted.origin)
        assertEquals(EncounterKind.PHOTO, inserted.kind)
        assertEquals(LAST_MONTH, inserted.occurredAt)
        assertEquals(FakeImageResizer.PHOTO_PATH, inserted.cover?.photoPath)
        assertNull(inserted.cover?.galleryUri)
    }

    @Test
    fun `EXIF coordinates become the encounter's location, geohashed and stamped at capture time`() = runTest {
        exifReader.data = ExifData(lat = 55.75, lon = 37.62, takenAt = LAST_MONTH)

        importPhotos()(listOf("content://picked/1"))

        val inserted = repository.inserted.single()
        assertEquals(LocationSource.EXIF, inserted.locationSource)
        assertEquals(55.75, inserted.lat)
        assertEquals(LAST_MONTH, inserted.locationFixedAt)
        assertEquals("ucfv0hfp", inserted.geohash)
    }

    @Test
    fun `an imported photo's coordinates create the place cell that can name them`() = runTest {
        exifReader.data = ExifData(lat = 55.75, lon = 37.62, takenAt = LAST_MONTH)

        importPhotos()(listOf("content://picked/1"))

        // Without the cell the photo has coordinates nothing can resolve, so it reads as
        // "no location" in Places despite knowing exactly where it was taken.
        assertEquals("ucfv0h", repository.inserted.single().placeCellId)
        assertEquals(PlaceStatus.PENDING, placeCells.loadById("ucfv0h")?.status)
    }

    @Test
    fun `a photo with no coordinates creates no place cell`() = runTest {
        exifReader.data = ExifData(takenAt = LAST_MONTH)

        importPhotos()(listOf("content://picked/1"))

        assertNull(repository.inserted.single().placeCellId)
        assertNull(placeCells.loadById("ucfv0h"))
    }

    @Test
    fun `a photo whose coordinates are off the globe is imported without them`() = runTest {
        exifReader.data = ExifData(lat = 55.75, lon = 237.62, takenAt = LAST_MONTH)

        val summary = importPhotos()(listOf("content://picked/1"))

        assertEquals(listOf(ImportedPhoto(id = "id-1", needsLocation = false)), summary.added)
        val inserted = repository.inserted.single()
        assertEquals(LocationSource.NONE, inserted.locationSource)
        assertNull(inserted.lat)
        assertNull(inserted.lon)
        assertNull(inserted.geohash)
        assertNull(inserted.placeCellId)
        assertEquals(emptyList(), placeCells.upserted)
    }

    @Test
    fun `a recent photo whose coordinates are off the globe asks for a fix instead`() = runTest {
        exifReader.data = ExifData(lat = 200.0, lon = 37.62, takenAt = NOW - 10.minutes)

        val summary = importPhotos()(listOf("content://picked/1"))

        assertEquals(listOf(ImportedPhoto(id = "id-1", needsLocation = true)), summary.added)
        assertNull(repository.inserted.single().lat)
    }

    @Test
    fun `a photo from an hour ago asks for a fix instead of carrying none`() = runTest {
        exifReader.data = ExifData(takenAt = NOW - 10.minutes)

        val summary = importPhotos()(listOf("content://picked/1"))

        assertEquals(listOf(ImportedPhoto(id = "id-1", needsLocation = true)), summary.added)
        assertEquals(LocationSource.NONE, repository.inserted.single().locationSource)
    }

    @Test
    fun `an old photo without coordinates never receives today's location`() = runTest {
        exifReader.data = ExifData(takenAt = LAST_MONTH)

        val summary = importPhotos()(listOf("content://picked/1"))

        assertEquals(false, summary.added.single().needsLocation)
        assertNull(repository.inserted.single().lat)
    }

    @Test
    fun `the same bytes already in the database are skipped without touching the disk`() = runTest {
        repository.insert(encounterFixture("already-here", LAST_MONTH).withPhoto(sourceDigest = FakeDigest.SHA))

        val summary = importPhotos()(listOf("content://picked/1"))

        assertEquals(ImportSummary(added = emptyList(), skipped = 1, failed = 0), summary)
        assertEquals(0, imageResizer.calls)
        assertEquals(1, repository.inserted.size)
    }

    @Test
    fun `a soft-deleted twin does not block re-importing the same photo`() = runTest {
        repository.insert(
            encounterFixture("removed", LAST_MONTH)
                .copy(deletedAt = NOW - 1.days)
                .withPhoto(sourceDigest = FakeDigest.SHA),
        )

        val summary = importPhotos()(listOf("content://picked/1"))

        assertEquals(1, summary.added.size)
    }

    @Test
    fun `an unreadable photo is counted as failed and the rest of the batch continues`() = runTest {
        imageResizer.undecodable += "content://picked/2"
        digest.perUri["content://picked/1"] = "digest-1"
        digest.perUri["content://picked/2"] = "digest-2"
        digest.perUri["content://picked/3"] = "digest-3"

        val summary = importPhotos()(
            listOf("content://picked/1", "content://picked/2", "content://picked/3"),
        )

        assertEquals(2, summary.added.size)
        assertEquals(1, summary.failed)
        assertEquals(0, summary.skipped)
    }

    @Test
    fun `two picks of the same photo in one batch import it once`() = runTest {
        val summary = importPhotos()(listOf("content://picked/1", "content://picked/1"))

        assertEquals(ImportSummary(added = summary.added, skipped = 1, failed = 0), summary)
        assertEquals(1, summary.added.size)
    }

    @Test
    fun `progress is reported once per photo, all the way to the last`() = runTest {
        digest.perUri["content://picked/1"] = "digest-1"
        digest.perUri["content://picked/2"] = "digest-2"
        val reported = mutableListOf<Pair<Int, Int>>()

        importPhotos()(listOf("content://picked/1", "content://picked/2")) { done, total ->
            reported += done to total
        }

        assertEquals(listOf(1 to 2, 2 to 2), reported)
    }

    @Test
    fun `a photo with no metadata at all falls back to its file date`() = runTest {
        sourceFileTime.fileDate = LAST_MONTH

        importPhotos()(listOf("content://picked/1"))

        assertEquals(LAST_MONTH, repository.inserted.single().occurredAt)
    }

    @Test
    fun `an undecodable photo leaves no encounter behind`() = runTest {
        imageResizer.undecodable += "content://picked/1"

        val summary = importPhotos()(listOf("content://picked/1"))

        assertEquals(ImportSummary(added = emptyList(), skipped = 0, failed = 1), summary)
        assertEquals(emptyList(), repository.inserted)
    }

    @Test
    fun `an import from the phone's gallery remembers the item it came from, and is still not copied back`() =
        runTest {
            exifReader.data = ExifData(takenAt = LAST_MONTH)

            importPhotos()(listOf(PICKED_FROM_PHONE))

            val inserted = repository.inserted.single()
            assertEquals(PHONE_ITEM, inserted.cover?.sourceMediaUri)
            assertEquals(null, inserted.cover?.galleryUri)
        }

    @Test
    fun `a pick that names no item on the phone is imported with no link to one`() = runTest {
        exifReader.data = ExifData(takenAt = LAST_MONTH)

        importPhotos()(listOf("content://com.google.android.apps.photos.contentprovider/-1/1/abc"))

        assertEquals(null, repository.inserted.single().cover?.sourceMediaUri)
    }

    private companion object {
        const val PICKED_FROM_PHONE =
            "content://media/picker_get_content/0/com.android.providers.media.photopicker/media/17"
        const val PHONE_ITEM = "content://media/external/images/media/17"
        val NOW = Instant.parse("2026-09-22T12:00:00Z")
        val LAST_MONTH = Instant.parse("2026-08-22T09:00:00Z")
    }
}
