package dev.catsradar.domain.usecase

import dev.catsradar.domain.backup.BackupContents
import dev.catsradar.domain.model.PlaceCell
import dev.catsradar.domain.model.PlaceStatus
import dev.catsradar.domain.model.TrackPoint
import dev.catsradar.domain.model.Walk
import dev.catsradar.domain.platform.BackupReadResult
import dev.catsradar.domain.platform.BackupReader
import dev.catsradar.domain.platform.BackupRejection
import dev.catsradar.domain.platform.BackupWriter
import dev.catsradar.domain.testing.FakeEncounterRepository
import dev.catsradar.domain.testing.FakePlaceCellRepository
import dev.catsradar.domain.testing.FakeWalkRepository
import dev.catsradar.domain.testing.encounterAt
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

private val EARLY = Instant.parse("2026-09-01T00:00:00Z")
private val LATE = Instant.parse("2026-09-20T00:00:00Z")

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

private fun walk(id: String) = Walk(id, EARLY, LATE, "device", EARLY, LATE)

private fun point(walkId: String) = TrackPoint(walkId, EARLY, 41.0, 2.0, 5f)

class ExportBackupTest {

    private val encounters = FakeEncounterRepository()
    private val placeCells = FakePlaceCellRepository()
    private val walks = FakeWalkRepository()

    @Test
    fun `an export carries the live cats and the cells that can name them`() = runTest {
        encounters.insert(encounterAt(EARLY).copy(id = "live"))
        placeCells.upsert(cell("ucfv0h"))
        val writer = RecordingWriter()

        val ok = ExportBackup(encounters, placeCells, walks, writer)("content://backup.zip")

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

        ExportBackup(encounters, placeCells, walks, writer)("content://backup.zip")

        assertEquals(listOf("live"), writer.written?.encounters?.map { it.id })
    }

    @Test
    fun `an export carries every walk and every point of its route`() = runTest {
        walks.upsert(walk("w"))
        walks.appendPoints(listOf(point("w")))
        val writer = RecordingWriter()

        ExportBackup(encounters, placeCells, walks, writer)("content://backup.zip")

        assertEquals(listOf(walk("w")), writer.written?.walks)
        assertEquals(listOf(point("w")), writer.written?.trackPoints)
    }

    @Test
    fun `an archive that could not be written is reported as a failure`() = runTest {
        val ok = ExportBackup(encounters, placeCells, walks, RecordingWriter(succeeds = false))("content://x.zip")

        assertEquals(false, ok)
    }
}

class ImportBackupTest {

    private val encounters = FakeEncounterRepository()
    private val placeCells = FakePlaceCellRepository()
    private val walks = FakeWalkRepository()

    private fun importBackup(result: BackupReadResult) =
        ImportBackup(encounters, placeCells, walks, StubReader(result))

    @Test
    fun `an archive's walks and routes are written, and importing them again writes nothing more`() = runTest {
        val archive = BackupContents(walks = listOf(walk("w")), trackPoints = listOf(point("w")))

        importBackup(BackupReadResult.Readable(archive))("content://in.zip")
        importBackup(BackupReadResult.Readable(archive))("content://in.zip")

        assertEquals(listOf(walk("w")), walks.walks())
        assertEquals(listOf(point("w")), walks.points())
    }

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
    fun `a rejected archive writes nothing at all`() = runTest {
        val result = importBackup(BackupReadResult.Rejected(BackupRejection.TOO_NEW))("content://in.zip")

        assertIs<ImportBackupResult.Rejected>(result)
        assertEquals(BackupRejection.TOO_NEW, result.reason)
        assertEquals(emptyList(), encounters.inserted)
        assertEquals(emptyList(), placeCells.upserted)
    }

    @Test
    fun `place cells the backup knows better are written, the rest left alone`() = runTest {
        placeCells.upsert(cell("named", PlaceStatus.RESOLVED))
        placeCells.upsert(cell("pending", PlaceStatus.PENDING))
        val imported = BackupContents(
            placeCells = listOf(cell("named", PlaceStatus.PENDING), cell("pending", PlaceStatus.RESOLVED)),
        )

        importBackup(BackupReadResult.Readable(imported))("content://in.zip")

        assertEquals(listOf("pending"), placeCells.upserted.drop(2).map { it.cellId })
    }
}
