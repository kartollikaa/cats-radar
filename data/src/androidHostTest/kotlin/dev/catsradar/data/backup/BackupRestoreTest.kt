package dev.catsradar.data.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.data.NoAnalytics
import dev.catsradar.data.db.RoomTransactionRunner
import dev.catsradar.data.db.TestCatsDatabase
import dev.catsradar.data.db.buildInMemoryCatsDatabase
import dev.catsradar.data.platform.AndroidPhotoStorage
import dev.catsradar.data.repository.EncounterRepositoryImpl
import dev.catsradar.data.repository.PlaceCellRepositoryImpl
import dev.catsradar.data.repository.WalkRepositoryImpl
import dev.catsradar.data.repository.inShotOf
import dev.catsradar.data.repository.withPhoto
import dev.catsradar.domain.Tuning
import dev.catsradar.domain.geo.Geohash
import dev.catsradar.domain.model.CatCoat
import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.EncounterKind
import dev.catsradar.domain.model.EncounterOrigin
import dev.catsradar.domain.model.LocationSource
import dev.catsradar.domain.model.PlaceCell
import dev.catsradar.domain.model.PlaceStatus
import dev.catsradar.domain.model.TrackPoint
import dev.catsradar.domain.model.Walk
import dev.catsradar.domain.stats.StatsCalculator
import dev.catsradar.domain.usecase.ExportBackup
import dev.catsradar.domain.usecase.ImportBackup
import dev.catsradar.domain.usecase.ImportBackupResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private val Morning = Instant.parse("2026-09-20T08:00:00Z")
private val Now = Instant.parse("2026-09-22T12:00:00Z")
private val Today = LocalDate(2026, 9, 22)

private data class Snapshot(
    val encounters: List<Encounter>,
    val placeCells: List<PlaceCell>,
    val walks: List<Walk>,
    val trackPoints: List<TrackPoint>,
)

@RunWith(AndroidJUnit4::class)
class BackupRestoreTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val photoStorage = AndroidPhotoStorage(context)
    private val here = Device(buildInMemoryCatsDatabase(context))
    private val elsewhere = Device(buildInMemoryCatsDatabase(context))
    private val archive: String get() = File(temporaryFolder.root, "backup.zip").path

    @After
    fun closeDatabases() {
        here.database.close()
        elsewhere.database.close()
    }

    private inner class Device(val database: TestCatsDatabase) {
        val encounters = EncounterRepositoryImpl(database.encounterDao())
        val placeCells = PlaceCellRepositoryImpl(database.placeCellDao())
        val walks = WalkRepositoryImpl(database.walkDao(), database.trackPointDao())

        suspend fun export(target: String): Boolean = ExportBackup(
            encounterRepository = encounters,
            placeCellRepository = placeCells,
            walkRepository = walks,
            backupWriter = ZipBackupWriter(
                context = context,
                photoStorage = photoStorage,
                deviceIdProvider = StubDeviceId("phone-a"),
                clock = FixedClock(Now),
                appVersion = "1.1.0",
            ),
            analytics = NoAnalytics,
        )(target)

        suspend fun import(source: String): ImportBackupResult = ImportBackup(
            encounterRepository = encounters,
            placeCellRepository = placeCells,
            walkRepository = walks,
            transactionRunner = RoomTransactionRunner(database),
            backupReader = ZipBackupReader(context, photoStorage),
            analytics = NoAnalytics,
        )(source)

        suspend fun snapshot() = Snapshot(
            encounters = encounters.loadEvery().sortedBy { it.id },
            placeCells = placeCells.observeAll().first().sortedBy { it.cellId },
            walks = walks.observeAll().first().sortedBy { it.id },
            trackPoints = walks.loadEveryPoint().sortedWith(compareBy({ it.walkId }, { it.at })),
        )

        suspend fun stats() = StatsCalculator.calculate(encounters.observeAll().first(), today = Today, now = Now)
    }

    private suspend fun fillHere(): Map<String, ByteArray> {
        val photos = mapOf(
            "photo.jpg" to Random(1).nextBytes(4096),
            "photo_thumb.jpg" to Random(2).nextBytes(512),
            "second.jpg" to Random(3).nextBytes(4096),
            "second_thumb.jpg" to Random(4).nextBytes(512),
        ) + SHOT.flatMap { id -> listOf("$id.jpg", "${id}_thumb.jpg") }
            .mapIndexed { index, path -> path to Random(10 + index).nextBytes(1024) }
        photos.forEach { (path, bytes) -> photoStorage.prepare(path).writeBytes(bytes) }
        here.encounters.insert(tally("tally", Morning))
        val photographed = tally("photo", Morning + 5.minutes).copy(
            kind = EncounterKind.PHOTO,
            origin = EncounterOrigin.GALLERY,
            coat = CatCoat.GINGER_WHITE,
        ).withPhoto(
            photoPath = "photo.jpg",
            thumbPath = "photo_thumb.jpg",
            galleryUri = "content://media/external/images/media/42",
            sourceDigest = "9f86d081884c7d65",
        )
        val second = photographed.photos.single().copy(
            id = "second",
            photoPath = "second.jpg",
            thumbPath = "second_thumb.jpg",
            galleryUri = null,
            sourceMediaUri = "content://media/external/images/media/43",
            sourceDigest = "c3ab8ff13720e8ad",
            addedAt = Morning + 1.hours,
        )
        here.encounters.insert(photographed.copy(photos = photographed.photos + second))
        here.encounters.insert(located("located", Morning + 12.minutes, lat = 41.39864, lon = 2.17842))
        shotOfThree().forEach { here.encounters.insert(it) }
        here.encounters.insert(tally("deleted", Morning + 20.minutes))
        here.encounters.softDelete("deleted", Morning + 30.minutes)
        here.placeCells.upsert(resolvedCellFor(lat = 41.39864, lon = 2.17842))
        here.placeCells.upsert(noGeocoderCell)
        here.walks.upsert(Walk("walk", Morning, Morning + 40.minutes, "phone-a", Morning, Morning + 40.minutes))
        here.walks.upsert(Walk("on", Morning + 1.hours, null, "phone-a", Morning + 1.hours, Morning + 1.hours))
        here.walks.appendPoints(
            listOf(
                TrackPoint("walk", Morning + 1.minutes, 41.3985, 2.1783, 6.5f),
                TrackPoint("walk", Morning + 2.minutes, 41.3987, 2.1786, 9f),
                TrackPoint("on", Morning + 1.hours + 3.minutes, 41.3990, 2.1790, 5f),
            ),
        )
        return photos
    }

    // What the import rules make of the two rows that do not travel as written: an unnamed cell is
    // tried again on the new device, and a walk being recorded elsewhere cannot go on here.
    private fun Snapshot.asImported() = copy(
        encounters = encounters.filter { it.deletedAt == null },
        placeCells = placeCells.map { if (it.cellId == noGeocoderCell.cellId) untried(it.cellId) else it },
        walks = walks.map { if (it.id == "on") it.copy(endedAt = Morning + 1.hours + 3.minutes) else it },
    )

    private suspend fun wipe(photos: Map<String, ByteArray>) {
        photos.keys.forEach { path -> photoStorage.delete(path) }
        assertTrue(photos.keys.none { photoStorage.fileFor(it).exists() }, "the photos were not wiped")
    }

    @Test
    fun aBackupRestoredOnAnEmptyDeviceBringsBackEveryLiveRowAndEveryPhoto() = runTest {
        val photos = fillHere()
        assertTrue(here.export(archive))
        wipe(photos)

        elsewhere.import(archive)

        assertEquals(here.snapshot().asImported(), elsewhere.snapshot())
        photos.forEach { (path, bytes) ->
            assertTrue(bytes.contentEquals(photoStorage.fileFor(path).readBytes()), path)
        }
    }

    @Test
    fun aCatDeletedBeforeTheExportDoesNotComeBack() = runTest {
        fillHere()
        assertTrue(here.export(archive))

        elsewhere.import(archive)

        assertEquals(
            listOf("located", "photo") + SHOT + "tally",
            elsewhere.encounters.loadEvery().map { it.id }.sorted(),
        )
    }

    @Test
    fun aRestoredDeviceShowsTheSameStatistics() = runTest {
        val photos = fillHere()
        assertTrue(here.export(archive))
        wipe(photos)

        elsewhere.import(archive)

        assertEquals(here.stats(), elsewhere.stats())
    }

    @Test
    fun aShotOfThreeCatsComesBackAsOneShotWithEveryCoatAndItsOwnFiles() = runTest {
        val photos = fillHere()
        assertTrue(here.export(archive))
        wipe(photos)

        elsewhere.import(archive)

        val shot = elsewhere.encounters.loadEvery().filter { it.id in SHOT }.sortedBy { it.id }
        assertEquals(
            listOf(
                Triple("shot-a-ginger", CatCoat.GINGER, "shot-a-ginger"),
                Triple("shot-b-ginger", CatCoat.GINGER, "shot-a-ginger"),
                Triple("shot-c-unseen", null, "shot-a-ginger"),
            ),
            shot.map { Triple(it.id, it.coat, it.photos.single().shotId) },
        )
        shot.flatMap { it.photos }.flatMap { listOfNotNull(it.photoPath, it.thumbPath) }.forEach { path ->
            assertTrue(photos.getValue(path).contentEquals(photoStorage.fileFor(path).readBytes()), path)
        }
    }

    @Test
    fun importingTheSameBackupAgainAddsNothingAndChangesNothing() = runTest {
        fillHere()
        assertTrue(here.export(archive))
        elsewhere.import(archive)
        val restored = elsewhere.snapshot()

        val again = elsewhere.import(archive)

        assertEquals(ImportBackupResult.Merged(added = 0, updated = 0, unchanged = 6), again)
        assertEquals(restored, elsewhere.snapshot())
    }
}

private val SHOT = listOf("shot-a-ginger", "shot-b-ginger", "shot-c-unseen")

private fun shotOfThree(): List<Encounter> = SHOT.mapIndexed { index, id ->
    tally(id, Morning + 25.minutes).copy(
        kind = EncounterKind.PHOTO,
        origin = EncounterOrigin.CAMERA,
        coat = if (id.endsWith("ginger")) CatCoat.GINGER else null,
    ).withPhoto(
        photoPath = "$id.jpg",
        thumbPath = "${id}_thumb.jpg",
        galleryUri = "content://media/external/images/media/77",
        sourceDigest = "d-shot",
    ).let { cat -> if (index == 0) cat else cat.inShotOf(SHOT.first()) }
}

private fun tally(id: String, at: Instant) = Encounter(
    id = id,
    occurredAt = at,
    tzOffsetMinutes = 120,
    kind = EncounterKind.TALLY,
    origin = EncounterOrigin.APP,
    coat = null,
    lat = null,
    lon = null,
    accuracyMeters = null,
    locationSource = LocationSource.NONE,
    locationFixedAt = null,
    geohash = null,
    placeCellId = null,
    deviceId = "phone-a",
    createdAt = at,
    updatedAt = at,
    deletedAt = null,
)

private fun located(id: String, at: Instant, lat: Double, lon: Double): Encounter {
    val geohash = Geohash.encode(lat, lon, Tuning.GEOHASH_PRECISION)
    return tally(id, at).copy(
        lat = lat,
        lon = lon,
        accuracyMeters = 8f,
        locationSource = LocationSource.CURRENT_FIX,
        locationFixedAt = at + 1.minutes,
        geohash = geohash,
        placeCellId = Geohash.prefix(geohash, Tuning.PLACE_CELL_PRECISION),
        updatedAt = at + 1.minutes,
    )
}

private val noGeocoderCell = untried("sp3e3w").copy(
    status = PlaceStatus.UNAVAILABLE,
    attempts = 1,
    lastAttemptAt = Morning + 15.minutes,
)

private fun untried(cellId: String): PlaceCell {
    val bounds = Geohash.decode(cellId)
    return PlaceCell(
        cellId = cellId,
        centerLat = bounds.centerLat,
        centerLon = bounds.centerLon,
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
}

private fun resolvedCellFor(lat: Double, lon: Double): PlaceCell {
    val cellId = Geohash.encode(lat, lon, Tuning.PLACE_CELL_PRECISION)
    val bounds = Geohash.decode(cellId)
    return PlaceCell(
        cellId = cellId,
        centerLat = bounds.centerLat,
        centerLon = bounds.centerLon,
        countryCode = "ES",
        countryName = "Spain",
        adminArea = "Catalonia",
        locality = "Barcelona",
        subLocality = "Eixample",
        status = PlaceStatus.RESOLVED,
        attempts = 1,
        lastAttemptAt = Morning + 15.minutes,
        resolvedAt = Morning + 15.minutes,
    )
}
