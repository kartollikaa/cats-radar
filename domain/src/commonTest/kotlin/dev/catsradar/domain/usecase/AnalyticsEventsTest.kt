package dev.catsradar.domain.usecase

import dev.catsradar.domain.analytics.AnalyticsEvent
import dev.catsradar.domain.analytics.AnalyticsEvent.BackupExported
import dev.catsradar.domain.analytics.AnalyticsEvent.BackupImported
import dev.catsradar.domain.analytics.AnalyticsEvent.BackupRejected
import dev.catsradar.domain.analytics.AnalyticsEvent.CatLogged
import dev.catsradar.domain.analytics.AnalyticsEvent.CatsDeleted
import dev.catsradar.domain.analytics.AnalyticsEvent.CoatSet
import dev.catsradar.domain.analytics.AnalyticsEvent.DeleteUndone
import dev.catsradar.domain.analytics.AnalyticsEvent.ImportUndone
import dev.catsradar.domain.analytics.AnalyticsEvent.PhotoAttached
import dev.catsradar.domain.analytics.AnalyticsEvent.PhotosImported
import dev.catsradar.domain.analytics.AnalyticsEvent.TallyUndone
import dev.catsradar.domain.analytics.AnalyticsEvent.WalkEnded
import dev.catsradar.domain.analytics.AnalyticsEvent.WalkStarted
import dev.catsradar.domain.backup.BackupContents
import dev.catsradar.domain.model.CatCoat
import dev.catsradar.domain.model.EncounterKind
import dev.catsradar.domain.model.EncounterOrigin
import dev.catsradar.domain.platform.BackupReadResult
import dev.catsradar.domain.platform.BackupReader
import dev.catsradar.domain.platform.BackupRejection
import dev.catsradar.domain.platform.BackupWriter
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
import dev.catsradar.domain.testing.FakeSourceFileTime
import dev.catsradar.domain.testing.FakeTransactionRunner
import dev.catsradar.domain.testing.FakeWalkRepository
import dev.catsradar.domain.testing.RecordingAnalytics
import dev.catsradar.domain.testing.RecordingPhotoStorage
import dev.catsradar.domain.testing.encounterFixture
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private val NOW = Instant.parse("2026-09-24T10:00:00Z")

class AnalyticsEventsTest {
    private val analytics = RecordingAnalytics()
    private val encounters = FakeEncounterRepository()
    private val placeCells = FakePlaceCellRepository()
    private val resizer = FakeImageResizer()
    private val digest = FakeDigest()
    private val walks = FakeWalkRepository()
    private val clock = FakeClock(NOW)

    private fun assertLogged(vararg events: AnalyticsEvent) = assertEquals(events.toList(), analytics.logged)

    private suspend fun storedTally(id: String = "cat-1") =
        encounterFixture(id, NOW).also { encounters.insert(it) }

    @Test
    fun `a tally is logged with its origin and whether it has a coat`() = runTest {
        LogTally(encounters, FakeIdGenerator(), FakeDeviceIdProvider(), clock, analytics, TimeZone.UTC)(
            coat = CatCoat.GINGER,
            origin = EncounterOrigin.WIDGET,
        )

        assertLogged(CatLogged(EncounterKind.TALLY, EncounterOrigin.WIDGET, hasCoat = true))
    }

    private fun logPhoto() = LogPhoto(
        encounterRepository = encounters,
        placeCellRepository = placeCells,
        settingsRepository = FakeSettingsRepository(),
        exifReader = FakeExifReader(),
        imageResizer = resizer,
        digest = digest,
        gallerySaver = FakeGallerySaver(),
        idGenerator = FakeIdGenerator(),
        deviceIdProvider = FakeDeviceIdProvider(),
        clock = clock,
        analytics = analytics,
        timeZone = TimeZone.UTC,
    )

    @Test
    fun `a photo from the camera is logged as a cat with no coat yet`() = runTest {
        logPhoto()("content://camera/1")

        assertLogged(CatLogged(EncounterKind.PHOTO, EncounterOrigin.CAMERA, hasCoat = false))
    }

    @Test
    fun `an unreadable photo logs nothing`() = runTest {
        resizer.undecodable += "content://camera/1"

        logPhoto()("content://camera/1")

        assertLogged()
    }

    @Test
    fun `undoing a tally is logged`() = runTest {
        val cat = storedTally()

        UndoLastTally(encounters, clock, analytics)(cat.id)

        assertLogged(TallyUndone)
    }

    @Test
    fun `setting and clearing a coat is logged with the coat, an unchanged or missing cat logs nothing`() = runTest {
        val cat = storedTally()
        val setCoat = SetCoat(encounters, clock, analytics)

        setCoat(cat.id, CatCoat.BLACK)
        setCoat(cat.id, CatCoat.BLACK)
        setCoat(cat.id, null)
        setCoat("no-such-cat", CatCoat.GREY)

        assertLogged(CoatSet(CatCoat.BLACK), CoatSet(null))
    }

    private fun attachPhoto() = AttachPhoto(
        encounterRepository = encounters,
        settingsRepository = FakeSettingsRepository(),
        imageResizer = resizer,
        digest = digest,
        gallerySaver = FakeGallerySaver(),
        photoStorage = RecordingPhotoStorage(),
        idGenerator = FakeIdGenerator(),
        clock = clock,
        analytics = analytics,
    )

    @Test
    fun `attaching a photo is logged with its source`() = runTest {
        val cat = storedTally()

        attachPhoto()(cat.id, "content://gallery/7", PhotoSource.GALLERY)

        assertLogged(PhotoAttached(PhotoSource.GALLERY))
    }

    @Test
    fun `a photo that cannot be attached logs nothing`() = runTest {
        val cat = storedTally()
        resizer.undecodable += "content://gallery/broken"

        attachPhoto()(cat.id, "content://gallery/broken", PhotoSource.GALLERY)
        attachPhoto()("no-such-cat", "content://gallery/7", PhotoSource.CAMERA)

        assertLogged()
    }

    @Test
    fun `an import batch is logged once with what it added, skipped as duplicates and failed`() = runTest {
        digest.perUri["content://gallery/3"] = "another-photo"
        resizer.undecodable += "content://gallery/3"
        val importPhotos = ImportPhotos(
            encounterRepository = encounters,
            placeCellRepository = placeCells,
            exifReader = FakeExifReader(),
            imageResizer = resizer,
            digest = digest,
            sourceFileTime = FakeSourceFileTime(),
            idGenerator = FakeIdGenerator(),
            deviceIdProvider = FakeDeviceIdProvider(),
            clock = clock,
            analytics = analytics,
            timeZone = TimeZone.UTC,
        )

        importPhotos(listOf("content://gallery/1", "content://gallery/2", "content://gallery/3"))

        assertLogged(PhotosImported(added = 1, duplicates = 1, failed = 1))
    }

    @Test
    fun `an import of nothing logs nothing`() = runTest {
        ImportPhotos(
            encounterRepository = encounters,
            placeCellRepository = placeCells,
            exifReader = FakeExifReader(),
            imageResizer = resizer,
            digest = digest,
            sourceFileTime = FakeSourceFileTime(),
            idGenerator = FakeIdGenerator(),
            deviceIdProvider = FakeDeviceIdProvider(),
            clock = clock,
            analytics = analytics,
            timeZone = TimeZone.UTC,
        )(emptyList())

        assertLogged()
    }

    @Test
    fun `undoing an import is logged with how many cats it removed`() = runTest {
        storedTally("cat-1")
        storedTally("cat-2")

        UndoImport(encounters, clock, analytics)(listOf("cat-1", "cat-2"))

        assertLogged(ImportUndone(2))
    }

    @Test
    fun `deleting and undeleting cats are logged with their counts`() = runTest {
        val one = storedTally("cat-1")
        storedTally("cat-2")
        storedTally("cat-3")

        DeleteEncounter(encounters, clock, analytics)(one.id)
        UndoDelete(encounters, analytics)(one.id)
        val batch = DeleteEncounters(encounters, clock, analytics)(listOf("cat-2", "cat-3"))
        UndoDeleteEncounters(encounters, analytics)(batch)

        assertLogged(CatsDeleted(1), DeleteUndone(1), CatsDeleted(2), DeleteUndone(2))
    }

    private class Writer(private val succeeds: Boolean) : BackupWriter {
        override suspend fun write(target: String, contents: BackupContents): Boolean = succeeds
    }

    private class Reader(private val result: BackupReadResult) : BackupReader {
        override suspend fun read(source: String): BackupReadResult = result
    }

    @Test
    fun `an export is logged only when the archive is written`() = runTest {
        ExportBackup(encounters, placeCells, walks, Writer(succeeds = false), analytics)("content://backup")
        ExportBackup(encounters, placeCells, walks, Writer(succeeds = true), analytics)("content://backup")

        assertLogged(BackupExported)
    }

    private fun importBackup(result: BackupReadResult) =
        ImportBackup(encounters, placeCells, walks, FakeTransactionRunner(), Reader(result), analytics)

    @Test
    fun `a merged backup is logged with what it added, updated and left alone`() = runTest {
        storedTally("cat-1")
        val archived = BackupContents(encounters = listOf(encounterFixture("cat-2", NOW)))

        importBackup(BackupReadResult.Readable(archived))("content://backup")

        assertLogged(BackupImported(added = 1, updated = 0, unchanged = 0))
    }

    @Test
    fun `a refused backup is logged with why`() = runTest {
        importBackup(BackupReadResult.Rejected(BackupRejection.TOO_NEW))("content://backup")

        assertLogged(BackupRejected(BackupRejection.TOO_NEW))
    }

    @Test
    fun `starting a walk is logged once, not again while it is open`() = runTest {
        val startWalk = StartWalk(walks, FakeIdGenerator(), FakeDeviceIdProvider(), clock, analytics)

        startWalk()
        startWalk()

        assertLogged(WalkStarted)
    }

    @Test
    fun `ending a walk is logged with its whole minutes, and nothing when none is open`() = runTest {
        StartWalk(walks, FakeIdGenerator(), FakeDeviceIdProvider(), clock, analytics)()
        val later = FakeClock(NOW + 95.minutes + 40.seconds)

        EndWalk(walks, later, analytics)()
        EndWalk(walks, later, analytics)()

        assertLogged(WalkStarted, WalkEnded(95))
    }
}
