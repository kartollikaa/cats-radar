package dev.catsradar.data.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.data.platform.AndroidPhotoStorage
import dev.catsradar.domain.backup.BackupContents
import dev.catsradar.domain.model.CatCoat
import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.EncounterKind
import dev.catsradar.domain.model.EncounterOrigin
import dev.catsradar.domain.model.EncounterPhoto
import dev.catsradar.domain.model.LocationSource
import dev.catsradar.domain.platform.BackupReadResult
import dev.catsradar.domain.platform.BackupRejection
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipFile
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@RunWith(AndroidJUnit4::class)
class ZipBackupPhotoListTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val photoStorage = AndroidPhotoStorage(context)
    private val writer = ZipBackupWriter(context, photoStorage, StubDeviceId(), FixedClock(ADDED), "1.0")
    private val reader = ZipBackupReader(context, photoStorage)
    private val path by lazy { File(temporaryFolder.root, "backup.zip").path }

    @Test
    fun everyPhotoOfEveryCatIsListedWithEveryFieldAndNoneRidesOnItsCat() = runTest {
        writer.write(path, BackupContents(encounters = listOf(twoPhotoCat, onePhotoCat)))

        val listed = ZipFile(path).use { zip -> zip.text(ENCOUNTER_PHOTOS_ENTRY) }
        assertEquals(
            (twoPhotoCat.photos + onePhotoCat.photos).map { it.toRecord() },
            Json.decodeFromString<List<EncounterPhotoRecord>>(listed),
        )
        val cats = ZipFile(path).use { zip -> Json.parseToJsonElement(zip.text(ENCOUNTERS_ENTRY)).jsonArray }
        assertEquals(emptyList(), cats.flatMap { (it as JsonObject).keys.filter { key -> key in PHOTO_FIELDS } })
    }

    @Test
    fun aCatWithTwoPhotosSurvivesTheRoundTripWithBoth() = runTest {
        writer.write(path, BackupContents(encounters = listOf(twoPhotoCat, onePhotoCat)))

        val read = reader.read(path)

        assertIs<BackupReadResult.Readable>(read)
        assertEquals(listOf(twoPhotoCat, onePhotoCat), read.contents.encounters)
    }

    @Test
    fun theFilesOfEveryPhotoTravelNotOnlyTheCovers() = runTest {
        twoPhotoCat.photos.flatMap { listOfNotNull(it.photoPath, it.thumbPath) }.forEach {
            photoStorage.prepare(it).writeText("bytes of $it")
        }
        writer.write(path, BackupContents(encounters = listOf(twoPhotoCat)))

        val entries = ZipFile(path).use { zip ->
            zip.entries().toList().map {
                it.name
            }.filter { it.startsWith(PHOTOS_PREFIX) }
        }

        assertEquals(
            setOf("photos/first.jpg", "photos/first_thumb.jpg", "photos/second.jpg", "photos/second_thumb.jpg"),
            entries.toSet(),
        )
    }

    @Test
    fun aShotOfThreeCatsSurvivesTheRoundTripAsOneShot() = runTest {
        writer.write(path, BackupContents(encounters = shotCats))

        val read = reader.read(path)

        assertIs<BackupReadResult.Readable>(read)
        assertEquals(shotCats, read.contents.encounters)
        assertEquals(List(3) { "shot-first" }, read.contents.encounters.map { it.cover?.shotId })
    }

    @Test
    fun everyPhotoWritesItsShotAndAPhotoOfOneCatNamesItself() = runTest {
        writer.write(path, BackupContents(encounters = shotCats + onePhotoCat))

        val listed = ZipFile(path).use { zip -> Json.parseToJsonElement(zip.text(ENCOUNTER_PHOTOS_ENTRY)).jsonArray }

        assertEquals(
            listOf(
                "\"shot-first\"" to "\"shot-first\"",
                "\"shot-second\"" to "\"shot-first\"",
                "\"shot-third\"" to "\"shot-first\"",
                "\"only\"" to "\"only\"",
            ),
            listed.map { (it as JsonObject).let { photo -> photo["id"].toString() to photo["shotId"].toString() } },
        )
    }

    @Test
    fun aPhotoWhoseCatIsNotInTheArchiveIsLeftOutAndTheRestImports() = runTest {
        File(path).writeArchive(
            currentLists(
                cats = """[${catJson("a")}]""",
                photos = """[${photoJson("a", "a.jpg")},${photoJson("elsewhere", "elsewhere.jpg")}]""",
            ),
        )

        val read = reader.read(path)

        assertIs<BackupReadResult.Readable>(read)
        assertEquals(
            listOf("a" to listOf("a.jpg")),
            read.contents.encounters.map { cat ->
                cat.id to cat.photos.map {
                    it.photoPath
                }
            }
        )
    }

    @Test
    fun aListedPhotoWhoseCopyClimbsOutOfThePhotoDirectoryRefusesTheArchive() = runTest {
        File(path).writeArchive(
            currentLists(cats = """[${catJson("a")}]""", photos = """[${photoJson("a", "../cats_radar.db")}]"""),
        )

        assertEquals(BackupReadResult.Rejected(BackupRejection.UNREADABLE), reader.read(path))
    }

    @Test
    fun aListedPhotoWhoseThumbnailClimbsOutOfThePhotoDirectoryRefusesTheArchive() = runTest {
        val escaping = photoJson("a", "a.jpg").removeSuffix("}") + ""","thumbPath":"../../shared_prefs/settings.xml"}"""
        File(path).writeArchive(currentLists(cats = """[${catJson("a")}]""", photos = "[$escaping]"))

        assertEquals(BackupReadResult.Rejected(BackupRejection.UNREADABLE), reader.read(path))
    }

    @Test
    fun aCurrentArchiveWithoutItsPhotoListWasCutOffAndIsRefused() = runTest {
        File(path).writeArchive(currentLists(cats = "[]", photos = null))

        assertEquals(BackupReadResult.Rejected(BackupRejection.UNREADABLE), reader.read(path))
    }

    private companion object {
        val ADDED = Instant.parse("2026-09-25T10:00:00Z")
        val PHOTO_FIELDS = setOf("photoPath", "thumbPath", "galleryUri", "sourceMediaUri", "sourceDigest")

        val twoPhotoCat = cat("two").let { cat ->
            cat.copy(
                photos = listOf(
                    photo(cat, id = "first", path = "first.jpg", addedAt = ADDED),
                    photo(cat, id = "second", path = "second.jpg", addedAt = ADDED + 1.minutes),
                ),
            )
        }
        val onePhotoCat = cat(
            "one"
        ).let { cat -> cat.copy(photos = listOf(photo(cat, id = "only", path = "only.jpg", addedAt = ADDED))) }

        val shotCats = listOf(
            cat("ginger").copy(coat = CatCoat.GINGER).let { cat ->
                cat.copy(photos = listOf(photo(cat, id = "shot-first", path = "shot-1.jpg", addedAt = ADDED)))
            },
            cat("ginger-too").copy(coat = CatCoat.GINGER).let { cat ->
                cat.copy(
                    photos = listOf(
                        photo(cat, id = "shot-second", path = "shot-2.jpg", addedAt = ADDED, shotId = "shot-first"),
                    ),
                )
            },
            cat("unseen").let { cat ->
                cat.copy(
                    photos = listOf(
                        photo(cat, id = "shot-third", path = "shot-3.jpg", addedAt = ADDED, shotId = "shot-first"),
                    ),
                )
            },
        )

        fun cat(id: String) = Encounter(
            id = id,
            occurredAt = Instant.parse("2026-09-20T08:30:00Z"),
            tzOffsetMinutes = 180,
            kind = EncounterKind.PHOTO,
            origin = EncounterOrigin.CAMERA,
            coat = null,
            lat = null,
            lon = null,
            accuracyMeters = null,
            locationSource = LocationSource.NONE,
            locationFixedAt = null,
            geohash = null,
            placeCellId = null,
            deviceId = "device-1",
            createdAt = Instant.parse("2026-09-20T08:31:00Z"),
            updatedAt = Instant.parse("2026-09-21T09:00:00Z"),
            deletedAt = null,
        )

        fun photo(cat: Encounter, id: String, path: String, addedAt: Instant, shotId: String = id) = EncounterPhoto(
            id = id,
            encounterId = cat.id,
            photoPath = path,
            thumbPath = path.replace(".jpg", "_thumb.jpg"),
            galleryUri = "content://media/external/images/media/$id-saved",
            sourceMediaUri = "content://media/external/images/media/$id-picked",
            sourceDigest = "digest-$id",
            deviceId = "install-$id",
            addedAt = addedAt,
            shotId = shotId,
        )
    }
}

private fun ZipFile.text(entry: String): String = getInputStream(getEntry(entry)).readBytes().decodeToString()

private fun currentLists(cats: String, photos: String?): List<Pair<String, String>> = listOfNotNull(
    MANIFEST_ENTRY to CurrentManifest,
    ENCOUNTERS_ENTRY to cats,
    photos?.let { ENCOUNTER_PHOTOS_ENTRY to it },
    PLACE_CELLS_ENTRY to "[]",
    WALKS_ENTRY to "[]",
    TRACK_POINTS_ENTRY to "[]",
)

private fun catJson(id: String) = """{"id":"$id","occurredAt":0,"tzOffsetMinutes":0,"kind":"PHOTO",""" +
    """"origin":"APP","locationSource":"NONE","deviceId":"d","createdAt":0,"updatedAt":0}"""

private fun photoJson(catId: String, photoPath: String) =
    """{"id":"$catId-photo","encounterId":"$catId","photoPath":"$photoPath","deviceId":"d","addedAt":0}"""
