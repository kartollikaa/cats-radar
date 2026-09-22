package dev.catsradar.domain.usecase

import dev.catsradar.domain.model.EncounterKind
import dev.catsradar.domain.model.EncounterOrigin
import dev.catsradar.domain.model.LocationSource
import dev.catsradar.domain.platform.ExifData
import dev.catsradar.domain.testing.FakeClock
import dev.catsradar.domain.testing.FakeDeviceIdProvider
import dev.catsradar.domain.testing.FakeDigest
import dev.catsradar.domain.testing.FakeEncounterRepository
import dev.catsradar.domain.testing.FakeExifReader
import dev.catsradar.domain.testing.FakeIdGenerator
import dev.catsradar.domain.testing.FakeImageResizer
import dev.catsradar.domain.testing.FakeSourceFileTime
import dev.catsradar.domain.testing.encounterFixture
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
    private val exifReader = FakeExifReader()
    private val imageResizer = FakeImageResizer()
    private val digest = FakeDigest()
    private val sourceFileTime = FakeSourceFileTime()

    private fun importPhotos(timeZone: TimeZone = TimeZone.UTC) = ImportPhotos(
        encounterRepository = repository,
        exifReader = exifReader,
        imageResizer = imageResizer,
        digest = digest,
        sourceFileTime = sourceFileTime,
        idGenerator = FakeIdGenerator(),
        deviceIdProvider = FakeDeviceIdProvider(),
        clock = FakeClock(NOW),
        timeZone = timeZone,
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
        assertEquals(FakeImageResizer.PHOTO_PATH, inserted.photoPath)
        assertNull(inserted.galleryUri)
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
        repository.insert(encounterFixture("already-here", LAST_MONTH).copy(sourceDigest = FakeDigest.SHA))

        val summary = importPhotos()(listOf("content://picked/1"))

        assertEquals(ImportSummary(added = emptyList(), skipped = 1, failed = 0), summary)
        assertEquals(0, imageResizer.calls)
        assertEquals(1, repository.inserted.size)
    }

    @Test
    fun `a soft-deleted twin does not block re-importing the same photo`() = runTest {
        repository.insert(
            encounterFixture("removed", LAST_MONTH)
                .copy(sourceDigest = FakeDigest.SHA, deletedAt = NOW - 1.days),
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

    private companion object {
        val NOW = Instant.parse("2026-09-22T12:00:00Z")
        val LAST_MONTH = Instant.parse("2026-08-22T09:00:00Z")
    }
}
