package dev.catsradar.domain.usecase

import dev.catsradar.domain.backup.BackupContents
import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.LocationSource
import dev.catsradar.domain.model.PlaceCell
import dev.catsradar.domain.model.PlaceStatus
import dev.catsradar.domain.model.TrackPoint
import dev.catsradar.domain.model.Walk
import dev.catsradar.domain.platform.BackupReadResult
import dev.catsradar.domain.platform.BackupReader
import dev.catsradar.domain.platform.BackupRejection
import dev.catsradar.domain.platform.BackupWriter
import dev.catsradar.domain.repository.EncounterRepository
import dev.catsradar.domain.repository.PlaceCellRepository
import dev.catsradar.domain.repository.WalkRepository
import dev.catsradar.domain.testing.FakeEncounterRepository
import dev.catsradar.domain.testing.FakePlaceCellRepository
import dev.catsradar.domain.testing.FakeTransactionRunner
import dev.catsradar.domain.testing.FakeWalkRepository
import dev.catsradar.domain.testing.RecordingAnalytics
import dev.catsradar.domain.testing.encounterAt
import dev.catsradar.domain.testing.withPhoto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

private val EARLY = Instant.parse("2026-09-01T00:00:00Z")
private val MIDDLE = Instant.parse("2026-09-10T00:00:00Z")
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

private class DiskFullOnSecondUpsert(private val delegate: PlaceCellRepository) : PlaceCellRepository by delegate {
    private var upserts = 0

    override suspend fun upsert(cell: PlaceCell) {
        check(++upserts < 2) { "database or disk is full" }
        delegate.upsert(cell)
    }
}

private class ReadsInTransaction(private val transactions: FakeTransactionRunner) {
    val seen = mutableSetOf<Pair<String, Boolean>>()

    fun record(read: String) {
        seen += read to transactions.isOpen
    }
}

private class WatchedEncounters(
    private val delegate: EncounterRepository,
    private val reads: ReadsInTransaction,
) : EncounterRepository by delegate {
    override suspend fun loadEvery(): List<Encounter> =
        delegate.loadEvery().also { reads.record("encounters.loadEvery") }
}

private class WatchedPlaceCells(
    private val delegate: PlaceCellRepository,
    private val reads: ReadsInTransaction,
) : PlaceCellRepository by delegate {
    override fun observeAll(): Flow<List<PlaceCell>> =
        delegate.observeAll().also { reads.record("placeCells.observeAll") }

    override suspend fun loadById(cellId: String): PlaceCell? =
        delegate.loadById(cellId).also { reads.record("placeCells.loadById") }
}

private class WatchedWalks(
    private val delegate: WalkRepository,
    private val reads: ReadsInTransaction,
) : WalkRepository by delegate {
    override fun observeAll(): Flow<List<Walk>> = delegate.observeAll().also { reads.record("walks.observeAll") }

    override suspend fun loadEveryPoint(): List<TrackPoint> =
        delegate.loadEveryPoint().also { reads.record("walks.loadEveryPoint") }
}

private class DiskFullOnAppendPoints(delegate: WalkRepository) : WalkRepository by delegate {
    override suspend fun appendPoints(points: List<TrackPoint>) = error("database or disk is full")
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

        val ok = ExportBackup(
            encounters,
            placeCells,
            walks,
            writer,
            analytics = RecordingAnalytics()
        )("content://backup.zip")

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

        ExportBackup(encounters, placeCells, walks, writer, analytics = RecordingAnalytics())("content://backup.zip")

        assertEquals(listOf("live"), writer.written?.encounters?.map { it.id })
    }

    @Test
    fun `an export carries every walk and every point of its route`() = runTest {
        walks.upsert(walk("w"))
        walks.appendPoints(listOf(point("w")))
        val writer = RecordingWriter()

        ExportBackup(encounters, placeCells, walks, writer, analytics = RecordingAnalytics())("content://backup.zip")

        assertEquals(listOf(walk("w")), writer.written?.walks)
        assertEquals(listOf(point("w")), writer.written?.trackPoints)
    }

    @Test
    fun `an archive that could not be written is reported as a failure`() = runTest {
        val ok = ExportBackup(
            encounters,
            placeCells,
            walks,
            RecordingWriter(succeeds = false),
            analytics = RecordingAnalytics()
        )("content://x.zip")

        assertEquals(false, ok)
    }
}

class ImportBackupTest {

    private val encounters = FakeEncounterRepository()
    private val placeCells = FakePlaceCellRepository()
    private val walks = FakeWalkRepository()

    private val transactions = FakeTransactionRunner(encounters, placeCells, walks)

    private fun importBackup(
        result: BackupReadResult,
        encounterRepository: EncounterRepository = encounters,
        placeCellRepository: PlaceCellRepository = placeCells,
        walkRepository: WalkRepository = walks,
    ) = ImportBackup(
        encounterRepository,
        placeCellRepository,
        walkRepository,
        transactions,
        StubReader(result),
        analytics = RecordingAnalytics()
    )

    @Test
    fun `an import that fails once cats are written leaves every cat and cell as it was`() = runTest {
        encounters.insert(encounterAt(EARLY).copy(id = "known", updatedAt = EARLY))
        placeCells.upsert(cell(PENDING_HERE))
        val before = encounters.loadEvery() to placeCells.observeAll().first()
        val imported = BackupContents(
            encounters = listOf(encounterAt(EARLY).copy(id = "known", updatedAt = LATE), locatedInMoscow),
            placeCells = listOf(cell("ucfv0n", PlaceStatus.RESOLVED), cell("ucfv0p", PlaceStatus.RESOLVED)),
        )

        assertFailsWith<IllegalStateException> {
            importBackup(BackupReadResult.Readable(imported), placeCellRepository = DiskFullOnSecondUpsert(placeCells))(
                "content://in.zip",
            )
        }

        assertEquals(listOf("known", "cat"), encounters.inserted.map { it.id })
        assertEquals(listOf(PENDING_HERE, "ucfv0n"), placeCells.upserted.map { it.cellId })
        assertEquals(before, encounters.loadEvery() to placeCells.observeAll().first())
    }

    @Test
    fun `the merge reads what is here inside the transaction it writes in`() = runTest {
        val reads = ReadsInTransaction(transactions)
        val imported = BackupContents(encounters = listOf(locatedInMoscow), placeCells = listOf(cell("ucfv0n")))

        importBackup(
            BackupReadResult.Readable(imported),
            encounterRepository = WatchedEncounters(encounters, reads),
            placeCellRepository = WatchedPlaceCells(placeCells, reads),
            walkRepository = WatchedWalks(walks, reads),
        )("content://in.zip")

        assertEquals(
            setOf(
                "encounters.loadEvery" to true,
                "placeCells.observeAll" to true,
                "placeCells.loadById" to true,
                "walks.observeAll" to true,
                "walks.loadEveryPoint" to true,
            ),
            reads.seen,
        )
    }

    @Test
    fun `an import that fails once walks are written leaves every walk as it was`() = runTest {
        val archive = BackupContents(walks = listOf(walk("w")), trackPoints = listOf(point("w")))

        assertFailsWith<IllegalStateException> {
            importBackup(BackupReadResult.Readable(archive), walkRepository = DiskFullOnAppendPoints(walks))(
                "content://in.zip",
            )
        }

        assertEquals(listOf(walk("w")), walks.upserted)
        assertEquals(emptyList(), walks.walks())
    }

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
    fun `a cat the archive lists twice is inserted once, as its later edit`() = runTest {
        val later = encounterAt(EARLY).copy(id = "cat", updatedAt = LATE)
        val earlier = encounterAt(EARLY).copy(id = "cat", updatedAt = EARLY)
        val imported = BackupContents(encounters = listOf(later, earlier))

        val result = importBackup(BackupReadResult.Readable(imported))("content://in.zip")

        assertEquals(ImportBackupResult.Merged(added = 1, updated = 0, unchanged = 0), result)
        assertEquals(listOf(later), encounters.inserted)
    }

    @Test
    fun `a cat here that the archive lists twice takes the later edit listed first`() = runTest {
        encounters.insert(encounterAt(EARLY).copy(id = "cat", updatedAt = EARLY))
        val later = encounterAt(EARLY).copy(id = "cat", updatedAt = LATE)
        val earlier = encounterAt(EARLY).copy(id = "cat", updatedAt = MIDDLE)
        val imported = BackupContents(encounters = listOf(later, earlier))

        val result = importBackup(BackupReadResult.Readable(imported))("content://in.zip")

        assertEquals(ImportBackupResult.Merged(added = 0, updated = 1, unchanged = 0), result)
        assertEquals(listOf(later), encounters.loadEvery())
    }

    @Test
    fun `a cat here that the archive lists twice takes the later edit listed last`() = runTest {
        encounters.insert(encounterAt(EARLY).copy(id = "cat", updatedAt = EARLY))
        val later = encounterAt(EARLY).copy(id = "cat", updatedAt = LATE)
        val earlier = encounterAt(EARLY).copy(id = "cat", updatedAt = MIDDLE)
        val imported = BackupContents(encounters = listOf(earlier, later))

        val result = importBackup(BackupReadResult.Readable(imported))("content://in.zip")

        assertEquals(ImportBackupResult.Merged(added = 0, updated = 1, unchanged = 0), result)
        assertEquals(listOf(later), encounters.loadEvery())
    }

    @Test
    fun `an archive's photo reaches a cat here that had none, and importing it again writes nothing more`() = runTest {
        encounters.insert(encounterAt(EARLY).copy(id = "cat", updatedAt = LATE))
        val archived = encounterAt(EARLY).copy(id = "cat", updatedAt = EARLY).withPhoto(photoPath = "archive.jpg")
        val imported = BackupContents(encounters = listOf(archived))

        val first = importBackup(BackupReadResult.Readable(imported))("content://in.zip")
        val again = importBackup(BackupReadResult.Readable(imported))("content://in.zip")

        assertEquals(ImportBackupResult.Merged(added = 0, updated = 1, unchanged = 0), first)
        assertEquals(ImportBackupResult.Merged(added = 0, updated = 0, unchanged = 1), again)
        assertEquals(
            encounterAt(EARLY).copy(id = "cat", updatedAt = LATE).copy(photos = archived.photos),
            encounters.loadEvery().single()
        )
    }

    @Test
    fun `a later copy of a cat from the archive does not replace the photo here`() = runTest {
        val here = encounterAt(EARLY).copy(id = "cat", updatedAt = EARLY).withPhoto(photoPath = "here.jpg")
        encounters.insert(here)
        val later = encounterAt(EARLY).copy(id = "cat", updatedAt = LATE).withPhoto(photoPath = "archive.jpg")

        val result = importBackup(
            BackupReadResult.Readable(BackupContents(encounters = listOf(later)))
        )("content://in.zip")

        assertEquals(ImportBackupResult.Merged(added = 0, updated = 1, unchanged = 0), result)
        assertEquals(here.copy(updatedAt = LATE), encounters.loadEvery().single())
    }

    @Test
    fun `a new cat from the archive arrives with its photo`() = runTest {
        val archived = encounterAt(EARLY).copy(id = "cat").withPhoto(photoPath = "archive.jpg")

        importBackup(BackupReadResult.Readable(BackupContents(encounters = listOf(archived))))("content://in.zip")

        assertEquals(listOf(archived), encounters.loadEvery())
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
