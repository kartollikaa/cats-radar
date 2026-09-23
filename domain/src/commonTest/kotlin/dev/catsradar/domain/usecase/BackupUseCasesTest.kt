package dev.catsradar.domain.usecase

import dev.catsradar.domain.backup.BackupContents
import dev.catsradar.domain.model.LocationSource
import dev.catsradar.domain.model.PlaceCell
import dev.catsradar.domain.model.PlaceStatus
import dev.catsradar.domain.platform.BackupReadResult
import dev.catsradar.domain.platform.BackupReader
import dev.catsradar.domain.platform.BackupRejection
import dev.catsradar.domain.platform.BackupWriter
import dev.catsradar.domain.testing.FakeEncounterRepository
import dev.catsradar.domain.testing.FakePlaceCellRepository
import dev.catsradar.domain.testing.encounterAt
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

private val EARLY = Instant.parse("2026-09-01T00:00:00Z")
private val LATE = Instant.parse("2026-09-20T00:00:00Z")
private const val NAMED_HERE = "ucfv0h"
private const val PENDING_HERE = "ucfv0j"

private val locatedInMoscow = encounterAt(EARLY).copy(
    id = "cat",
    lat = 55.7558,
    lon = 37.6173,
    locationSource = LocationSource.EXIF,
    geohash = "ucfv0n01",
    placeCellId = "ucfv0n",
)

private class RecordingWriter(private val succeeds: Boolean = true) : BackupWriter {
    var written: BackupContents? = null
        private set
    var target: String? = null
        private set

    override suspend fun write(target: String, contents: BackupContents): Boolean {
        this.target = target
        written = contents
        return succeeds
    }
}

private class StubReader(private val result: BackupReadResult) : BackupReader {
    override suspend fun read(source: String): BackupReadResult = result
}

private fun cell(id: String, status: PlaceStatus = PlaceStatus.PENDING) = PlaceCell(
    cellId = id,
    centerLat = 1.0,
    centerLon = 2.0,
    countryCode = null,
    countryName = null,
    adminArea = null,
    locality = null,
    subLocality = null,
    status = status,
    attempts = 0,
    lastAttemptAt = null,
    resolvedAt = null,
)

class ExportBackupTest {

    private val encounters = FakeEncounterRepository()
    private val placeCells = FakePlaceCellRepository()

    @Test
    fun `an export carries the live cats and the cells that can name them`() = runTest {
        encounters.insert(encounterAt(EARLY).copy(id = "live"))
        placeCells.upsert(cell("ucfv0h"))
        val writer = RecordingWriter()

        val ok = ExportBackup(encounters, placeCells, writer)("content://backup.zip")

        assertTrue(ok)
        assertEquals("content://backup.zip", writer.target)
        assertEquals(listOf("live"), writer.written?.encounters?.map { it.id })
        assertEquals(listOf("ucfv0h"), writer.written?.placeCells?.map { it.cellId })
    }

    @Test
    fun `a cat the user deleted is not in the backup`() = runTest {
        encounters.insert(encounterAt(EARLY).copy(id = "live"))
        encounters.insert(encounterAt(EARLY).copy(id = "gone", deletedAt = LATE))
        val writer = RecordingWriter()

        ExportBackup(encounters, placeCells, writer)("content://backup.zip")

        assertEquals(listOf("live"), writer.written?.encounters?.map { it.id })
    }

    @Test
    fun `an archive that could not be written is reported as a failure`() = runTest {
        val ok = ExportBackup(encounters, placeCells, RecordingWriter(succeeds = false))("content://x.zip")

        assertEquals(false, ok)
    }
}

class ImportBackupTest {

    private val encounters = FakeEncounterRepository()
    private val placeCells = FakePlaceCellRepository()

    private fun importBackup(result: BackupReadResult) =
        ImportBackup(encounters, placeCells, StubReader(result))

    @Test
    fun `an unknown cat is inserted and a known one updated in place`() = runTest {
        encounters.insert(encounterAt(EARLY).copy(id = "known", updatedAt = EARLY))
        val imported = BackupContents(
            encounters = listOf(
                encounterAt(EARLY).copy(id = "known", updatedAt = LATE),
                encounterAt(EARLY).copy(id = "fresh", updatedAt = LATE),
            ),
        )

        val result = importBackup(BackupReadResult.Readable(imported))("content://in.zip")

        assertEquals(ImportBackupResult.Merged(added = 1, updated = 1, unchanged = 0), result)
        assertEquals(listOf("fresh"), encounters.inserted.drop(1).map { it.id })
        assertEquals(LATE, encounters.loadEvery().first { it.id == "known" }.updatedAt)
    }

    @Test
    fun `a cat deleted here stays deleted when an older backup offers it back`() = runTest {
        encounters.insert(encounterAt(EARLY).copy(id = "cat", updatedAt = EARLY, deletedAt = LATE))
        val imported = BackupContents(encounters = listOf(encounterAt(EARLY).copy(id = "cat", updatedAt = EARLY)))

        val result = importBackup(BackupReadResult.Readable(imported))("content://in.zip")

        assertEquals(ImportBackupResult.Merged(added = 0, updated = 0, unchanged = 1), result)
        assertEquals(LATE, encounters.loadEvery().single().deletedAt)
    }

    @Test
    fun `a cat whose coordinates are off the globe is imported without its location`() = runTest {
        val offGlobe = encounterAt(EARLY).copy(
            id = "cat",
            lat = 95.0,
            lon = 37.6,
            accuracyMeters = 10f,
            locationSource = LocationSource.EXIF,
            locationFixedAt = EARLY,
            geohash = "ucfv0h8y",
            placeCellId = "ucfv0h",
        )

        val result = importBackup(BackupReadResult.Readable(BackupContents(encounters = listOf(offGlobe))))(
            "content://in.zip",
        )

        assertEquals(ImportBackupResult.Merged(added = 1, updated = 0, unchanged = 0), result)
        assertEquals(listOf(encounterAt(EARLY).copy(id = "cat")), encounters.inserted)
    }

    @Test
    fun `a located cat whose cell the archive lacks gets a pending one`() = runTest {
        val imported = BackupContents(encounters = listOf(locatedInMoscow))

        importBackup(BackupReadResult.Readable(imported))("content://in.zip")

        assertEquals(PlaceStatus.PENDING, placeCells.loadById("ucfv0n")?.status)
    }

    @Test
    fun `a cell the archive names is not replaced by a pending one`() = runTest {
        val imported = BackupContents(
            encounters = listOf(locatedInMoscow),
            placeCells = listOf(cell("ucfv0n", PlaceStatus.RESOLVED)),
        )

        importBackup(BackupReadResult.Readable(imported))("content://in.zip")

        assertEquals(PlaceStatus.RESOLVED, placeCells.loadById("ucfv0n")?.status)
    }

    @Test
    fun `a rejected archive writes nothing at all`() = runTest {
        val result = importBackup(BackupReadResult.Rejected(BackupRejection.TOO_NEW))("content://in.zip")

        assertIs<ImportBackupResult.Rejected>(result)
        assertEquals(BackupRejection.TOO_NEW, result.reason)
        assertEquals(emptyList(), encounters.inserted)
        assertEquals(emptyList(), placeCells.upserted)
    }

    @Test
    fun `place cells the backup knows better are written, the rest left alone`() = runTest {
        placeCells.upsert(cell(NAMED_HERE, PlaceStatus.RESOLVED))
        placeCells.upsert(cell(PENDING_HERE, PlaceStatus.PENDING))
        val imported = BackupContents(
            placeCells = listOf(cell(NAMED_HERE, PlaceStatus.PENDING), cell(PENDING_HERE, PlaceStatus.RESOLVED)),
        )

        importBackup(BackupReadResult.Readable(imported))("content://in.zip")

        assertEquals(listOf(PENDING_HERE), placeCells.upserted.drop(2).map { it.cellId })
    }

    @Test
    fun `an archive's cell is written centred on its id, not where the archive put it`() = runTest {
        val imported = BackupContents(placeCells = listOf(cell("ucfv0n", PlaceStatus.RESOLVED)))

        importBackup(BackupReadResult.Readable(imported))("content://in.zip")

        assertEquals(
            cell("ucfv0n", PlaceStatus.RESOLVED).copy(centerLat = 55.75836181640625, centerLon = 37.6226806640625),
            placeCells.loadById("ucfv0n"),
        )
    }

    @Test
    fun `an archive's cell off the globe still loses to a named one here`() = runTest {
        placeCells.upsert(cell(NAMED_HERE, PlaceStatus.RESOLVED))
        val offGlobe = cell(NAMED_HERE, PlaceStatus.PENDING).copy(centerLat = 95.0, centerLon = -237.0)

        importBackup(BackupReadResult.Readable(BackupContents(placeCells = listOf(offGlobe))))("content://in.zip")

        assertEquals(listOf(cell(NAMED_HERE, PlaceStatus.RESOLVED)), placeCells.upserted)
    }

    @Test
    fun `an archive's cell that wins over the one here is written centred on its id`() = runTest {
        placeCells.upsert(cell(PENDING_HERE, PlaceStatus.PENDING))
        val offGlobe = cell(PENDING_HERE, PlaceStatus.RESOLVED).copy(centerLat = 95.0, centerLon = -237.0)

        importBackup(BackupReadResult.Readable(BackupContents(placeCells = listOf(offGlobe))))("content://in.zip")

        assertEquals(
            cell(PENDING_HERE, PlaceStatus.RESOLVED).copy(centerLat = 55.75286865234375, centerLon = 37.6226806640625),
            placeCells.loadById(PENDING_HERE),
        )
    }

    @Test
    fun `a cell another device had no geocoder for arrives here untried`() = runTest {
        val unavailableThere = cell("ucfv0n", PlaceStatus.UNAVAILABLE).copy(attempts = 1, lastAttemptAt = EARLY)

        importBackup(BackupReadResult.Readable(BackupContents(placeCells = listOf(unavailableThere))))(
            "content://in.zip",
        )

        assertEquals(
            cell("ucfv0n", PlaceStatus.PENDING).copy(centerLat = 55.75836181640625, centerLon = 37.6226806640625),
            placeCells.loadById("ucfv0n"),
        )
    }

    @Test
    fun `an unnamed cell from the archive leaves the unnamed one here as it was`() = runTest {
        val tryingHere = cell(PENDING_HERE, PlaceStatus.PENDING).copy(attempts = 3, lastAttemptAt = EARLY)
        placeCells.upsert(tryingHere)
        val unavailableThere = cell(PENDING_HERE, PlaceStatus.UNAVAILABLE).copy(attempts = 1, lastAttemptAt = LATE)

        importBackup(BackupReadResult.Readable(BackupContents(placeCells = listOf(unavailableThere))))(
            "content://in.zip",
        )

        assertEquals(listOf(tryingHere), placeCells.upserted)
    }

    @Test
    fun `a name the archive gives a cell is written even when a later row for it has none`() = runTest {
        val imported = BackupContents(
            placeCells = listOf(cell("ucfv0n", PlaceStatus.RESOLVED), cell("ucfv0n", PlaceStatus.PENDING)),
        )

        importBackup(BackupReadResult.Readable(imported))("content://in.zip")

        assertEquals(
            cell("ucfv0n", PlaceStatus.RESOLVED).copy(centerLat = 55.75836181640625, centerLon = 37.6226806640625),
            placeCells.loadById("ucfv0n"),
        )
    }

    @Test
    fun `a cell whose id is not a place cell is left out of an archive that is otherwise imported`() = runTest {
        val imported = BackupContents(
            encounters = listOf(locatedInMoscow),
            placeCells = listOf(
                cell("ucfv0", PlaceStatus.RESOLVED),
                cell("ucfv0a", PlaceStatus.RESOLVED),
                cell("UCFV0N", PlaceStatus.RESOLVED),
                cell("ucfv0n0123456", PlaceStatus.PENDING),
            ),
        )

        val result = importBackup(BackupReadResult.Readable(imported))("content://in.zip")

        assertEquals(ImportBackupResult.Merged(added = 1, updated = 0, unchanged = 0), result)
        assertEquals(listOf("ucfv0n" to PlaceStatus.PENDING), placeCells.upserted.map { it.cellId to it.status })
    }
}
