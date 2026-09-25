package dev.catsradar.data.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.data.platform.AndroidPhotoStorage
import dev.catsradar.data.repository.withPhoto
import dev.catsradar.domain.backup.BackupContents
import dev.catsradar.domain.model.CatCoat
import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.EncounterKind
import dev.catsradar.domain.model.EncounterOrigin
import dev.catsradar.domain.model.LocationSource
import dev.catsradar.domain.model.PlaceCell
import dev.catsradar.domain.model.PlaceStatus
import dev.catsradar.domain.model.TrackPoint
import dev.catsradar.domain.model.Walk
import dev.catsradar.domain.platform.BackupReadResult
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipFile
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private val ExportedAt = Instant.parse("2026-09-22T12:00:00Z")

private fun encounter(id: String, photoPath: String? = null, thumbPath: String? = null) = Encounter(
    id = id,
    occurredAt = Instant.parse("2026-09-20T08:30:00Z"),
    tzOffsetMinutes = 180,
    kind = if (photoPath == null) EncounterKind.TALLY else EncounterKind.PHOTO,
    origin = EncounterOrigin.APP,
    coat = CatCoat.GINGER_WHITE,
    lat = 41.39864,
    lon = 2.17842,
    accuracyMeters = 7.5f,
    locationSource = LocationSource.EXIF,
    locationFixedAt = Instant.parse("2026-09-20T08:30:00Z"),
    geohash = "sp3e3qe7",
    placeCellId = "sp3e3q",
    deviceId = "device-1",
    createdAt = Instant.parse("2026-09-20T08:31:00Z"),
    updatedAt = Instant.parse("2026-09-21T09:00:00Z"),
    deletedAt = null,
).let {
    if (photoPath == null) {
        it
    } else {
        it.withPhoto(
            photoPath = photoPath,
            thumbPath = thumbPath,
            galleryUri = "content://media/1",
            sourceMediaUri = "content://media/external/images/media/17",
            sourceDigest = "abc",
        )
    }
}

private fun placeCell(id: String = "sp3e3q") = PlaceCell(
    cellId = id,
    centerLat = 41.4,
    centerLon = 2.17,
    countryCode = "ES",
    countryName = "Spain",
    adminArea = "Catalonia",
    locality = "Barcelona",
    subLocality = "Eixample",
    status = PlaceStatus.RESOLVED,
    attempts = 2,
    lastAttemptAt = Instant.parse("2026-09-21T10:00:00Z"),
    resolvedAt = Instant.parse("2026-09-21T10:00:01Z"),
)

@RunWith(AndroidJUnit4::class)
class ZipBackupArchiveTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val photoStorage = AndroidPhotoStorage(context)

    private fun writer() = ZipBackupWriter(
        context = context,
        photoStorage = photoStorage,
        deviceIdProvider = StubDeviceId(),
        clock = FixedClock(ExportedAt),
        appVersion = "1.0",
    )

    private fun reader() = ZipBackupReader(context, photoStorage)

    private fun target(): String = File(temporaryFolder.root, "backup.zip").path

    private fun writePhoto(relativePath: String, body: String) {
        photoStorage.prepare(relativePath).writeText(body)
    }

    @Test
    fun everyFieldOfEveryRowSurvivesTheRoundTrip() = runTest {
        val contents = BackupContents(
            encounters = listOf(encounter("a", photoPath = "a.jpg", thumbPath = "a_thumb.jpg"), encounter("b")),
            placeCells = listOf(placeCell()),
        )
        val path = target()

        assertTrue(writer().write(path, contents))
        val read = reader().read(path)

        assertIs<BackupReadResult.Readable>(read)
        assertEquals(contents.encounters, read.contents.encounters)
        assertEquals(contents.placeCells, read.contents.placeCells)
    }

    @Test
    fun everyWalkAndEveryPointOfItsRouteSurvivesTheRoundTrip() = runTest {
        val start = Instant.parse("2026-09-22T09:00:00Z")
        val contents = BackupContents(
            walks = listOf(
                Walk("ended", start, start + 30.minutes, "device-1", start, start + 31.minutes),
                Walk("on", start + 1.hours, null, "device-1", start + 1.hours, start + 1.hours),
            ),
            trackPoints = listOf(
                TrackPoint("ended", start + 1.minutes, 41.3851, 2.1734, 7.5f),
                TrackPoint("ended", start + 2.minutes, 41.3862, 2.1745, 12f),
            ),
        )
        val path = target()

        assertTrue(writer().write(path, contents))
        val read = reader().read(path)

        assertIs<BackupReadResult.Readable>(read)
        assertEquals(contents.walks, read.contents.walks)
        assertEquals(contents.trackPoints, read.contents.trackPoints)
    }

    @Test
    fun anArchiveSaysItIsFormatFourSoAnAppBeforeThePhotoListRefusesIt() = runTest {
        val path = target()

        assertTrue(writer().write(path, BackupContents()))

        val manifest = ZipFile(path).use { zip ->
            zip.getInputStream(zip.getEntry(MANIFEST_ENTRY)).readBytes().decodeToString()
        }
        assertTrue("\"formatVersion\":4" in manifest, manifest)
    }

    @Test
    fun anArchiveCarriesThePhotoFilesItsRowsPointAt() = runTest {
        writePhoto("cat.jpg", "full size")
        writePhoto("cat_thumb.jpg", "thumb")
        val path = target()
        writer().write(path, BackupContents(encounters = listOf(encounter("a", "cat.jpg", "cat_thumb.jpg"))))

        photoStorage.fileFor("cat.jpg").delete()
        photoStorage.fileFor("cat_thumb.jpg").delete()
        reader().read(path)

        assertEquals("full size", photoStorage.fileFor("cat.jpg").readText())
        assertEquals("thumb", photoStorage.fileFor("cat_thumb.jpg").readText())
    }

    @Test
    fun aPhotoThatIsAlreadyHereIsNotOverwrittenByTheArchivesCopy() = runTest {
        writePhoto("cat.jpg", "the archive's copy")
        val path = target()
        writer().write(path, BackupContents(encounters = listOf(encounter("a", "cat.jpg"))))
        writePhoto("cat.jpg", "the one already here")

        reader().read(path)

        assertEquals("the one already here", photoStorage.fileFor("cat.jpg").readText())
    }

    @Test
    fun aRowWhosePhotoWentMissingExportsWithoutLeavingAnEmptyOneBehind() = runTest {
        val path = target()

        val ok = writer().write(path, BackupContents(encounters = listOf(encounter("a", "never-written.jpg"))))

        assertTrue(ok)
        assertIs<BackupReadResult.Readable>(reader().read(path))
        // Skipped, not written empty: an entry opened for a file that cannot be read would restore
        // as a zero-byte photo, which renders as a broken image rather than the placeholder.
        assertTrue(
            !photoStorage.fileFor("never-written.jpg").exists(),
            "an empty photo was restored from the archive",
        )
    }
}
