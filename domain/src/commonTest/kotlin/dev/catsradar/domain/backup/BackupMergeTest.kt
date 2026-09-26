package dev.catsradar.domain.backup

import dev.catsradar.domain.model.CatCoat
import dev.catsradar.domain.model.PlaceCell
import dev.catsradar.domain.model.PlaceStatus
import dev.catsradar.domain.testing.encounterAt
import dev.catsradar.domain.testing.inShotOf
import dev.catsradar.domain.testing.withPhoto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

private val EARLY = Instant.parse("2026-09-01T00:00:00Z")
private val MIDDLE = Instant.parse("2026-09-10T00:00:00Z")
private val LATE = Instant.parse("2026-09-20T00:00:00Z")

private fun encounter(
    id: String,
    updatedAt: Instant,
    deletedAt: Instant? = null,
    photographed: Boolean = false,
) = encounterAt(EARLY).copy(id = id, updatedAt = updatedAt, deletedAt = deletedAt).let {
    if (photographed) it.withPhoto() else it
}

private fun cell(
    id: String = "ucfv0h",
    status: PlaceStatus,
    resolvedAt: Instant? = null,
    locality: String? = null,
    attempts: Int = 0,
) = PlaceCell(
    cellId = id,
    centerLat = 55.75,
    centerLon = 37.62,
    countryCode = null,
    countryName = null,
    adminArea = null,
    locality = locality,
    subLocality = null,
    status = status,
    attempts = attempts,
    lastAttemptAt = null,
    resolvedAt = resolvedAt,
)

private fun shotOfThree() = Triple(
    encounter("first", MIDDLE).copy(coat = CatCoat.GINGER).withPhoto(),
    encounter("second", MIDDLE).copy(coat = CatCoat.GINGER).withPhoto().inShotOf("first"),
    encounter("third", MIDDLE).withPhoto().inShotOf("first"),
)

class BackupMergeTest {

    @Test
    fun `a cat the device has never seen is added`() {
        val merged = BackupMerge.merge(
            local = BackupContents(),
            imported = BackupContents(encounters = listOf(encounter("new", MIDDLE))),
        )

        assertEquals(listOf("new"), merged.encounters.map { it.id })
        assertEquals(1, merged.added)
        assertEquals(0, merged.updated)
    }

    @Test
    fun `the later edit of the same cat wins`() {
        val merged = BackupMerge.merge(
            local = BackupContents(encounters = listOf(encounter("cat", EARLY))),
            imported = BackupContents(encounters = listOf(encounter("cat", LATE))),
        )

        assertEquals(LATE, merged.encounters.single().updatedAt)
        assertEquals(1, merged.updated)
        assertEquals(0, merged.added)
    }

    @Test
    fun `an older copy of a cat is left alone and counted as unchanged`() {
        val merged = BackupMerge.merge(
            local = BackupContents(encounters = listOf(encounter("cat", LATE))),
            imported = BackupContents(encounters = listOf(encounter("cat", EARLY))),
        )

        assertEquals(emptyList(), merged.encounters)
        assertEquals(1, merged.unchanged)
    }

    @Test
    fun `two rows edited at the same moment keep what is already here`() {
        val merged = BackupMerge.merge(
            local = BackupContents(encounters = listOf(encounter("cat", MIDDLE).copy(coat = CatCoat.BLACK))),
            imported = BackupContents(encounters = listOf(encounter("cat", MIDDLE).copy(coat = CatCoat.WHITE))),
        )

        assertEquals(emptyList(), merged.encounters)
        assertEquals(1, merged.unchanged)
    }

    @Test
    fun `a new cat's photos arrive beside it, and its row carries none`() {
        val cat = encounter("new", MIDDLE, photographed = true)

        val merged = BackupMerge.merge(local = BackupContents(), imported = BackupContents(encounters = listOf(cat)))

        assertEquals(
            MergeResult(encounters = listOf(cat.copy(photos = emptyList())), photos = cat.photos, added = 1),
            merged,
        )
    }

    @Test
    fun `a cat here without a photo gains the archive's even when its own row is kept`() {
        val offered = encounter("cat", EARLY, photographed = true)

        val merged = BackupMerge.merge(
            local = BackupContents(encounters = listOf(encounter("cat", LATE))),
            imported = BackupContents(encounters = listOf(offered)),
        )

        assertEquals(MergeResult(photos = offered.photos, updated = 1), merged)
    }

    @Test
    fun `a photo already here is never replaced, even by a later copy of its cat`() {
        val here = encounter("cat", EARLY).withPhoto(photoPath = "here.jpg")
        val later = encounter("cat", LATE).withPhoto(photoPath = "archive.jpg")

        val merged = BackupMerge.merge(
            local = BackupContents(encounters = listOf(here)),
            imported = BackupContents(encounters = listOf(later)),
        )

        assertEquals(MergeResult(encounters = listOf(later.copy(photos = emptyList())), updated = 1), merged)
    }

    @Test
    fun `a cat that stays deleted here gains no photo`() {
        val merged = BackupMerge.merge(
            local = BackupContents(encounters = listOf(encounter("cat", EARLY, deletedAt = LATE))),
            imported = BackupContents(encounters = listOf(encounter("cat", MIDDLE, photographed = true))),
        )

        assertEquals(MergeResult(unchanged = 1), merged)
    }

    @Test
    fun `a cat the archive brings back gains its photos`() {
        val offered = encounter("cat", LATE, photographed = true)

        val merged = BackupMerge.merge(
            local = BackupContents(encounters = listOf(encounter("cat", EARLY, deletedAt = MIDDLE))),
            imported = BackupContents(encounters = listOf(offered)),
        )

        assertEquals(
            MergeResult(encounters = listOf(offered.copy(photos = emptyList())), photos = offered.photos, updated = 1),
            merged,
        )
    }

    @Test
    fun `one photo the archive lists on two cats arrives once`() {
        val first = encounter("first", MIDDLE, photographed = true)
        val second = encounter("second", MIDDLE).copy(photos = first.photos.map { it.copy(encounterId = "second") })

        val merged = BackupMerge.merge(
            local = BackupContents(),
            imported = BackupContents(encounters = listOf(first, second)),
        )

        assertEquals(first.photos, merged.photos)
    }

    @Test
    fun `a cat of a shot that is not here yet joins its shot`() {
        val (first, second, third) = shotOfThree()
        val loggedElsewhere = third.copy(deviceId = "other-install")

        val merged = BackupMerge.merge(
            local = BackupContents(encounters = listOf(first, second)),
            imported = BackupContents(encounters = listOf(first, second, loggedElsewhere)),
        )

        assertEquals(listOf(loggedElsewhere.copy(photos = emptyList())), merged.encounters)
        assertEquals(loggedElsewhere.photos, merged.photos)
        assertEquals(listOf("first"), merged.photos.map { it.shotId })
    }

    @Test
    fun `a first cat deleted here after the export stays deleted and the others keep their shot`() {
        val (first, second, third) = shotOfThree()

        val merged = BackupMerge.merge(
            local = BackupContents(encounters = listOf(first.copy(deletedAt = LATE))),
            imported = BackupContents(encounters = listOf(first, second, third)),
        )

        assertEquals(listOf("second", "third"), merged.encounters.map { it.id })
        assertEquals(listOf("first", "first"), merged.photos.map { it.shotId })
        assertEquals(1, merged.unchanged)
    }

    @Test
    fun `a later cat whose shot's first row is not in the archive keeps its shot`() {
        val (_, _, third) = shotOfThree()

        val merged = BackupMerge.merge(local = BackupContents(), imported = BackupContents(encounters = listOf(third)))

        assertEquals(
            MergeResult(encounters = listOf(third.copy(photos = emptyList())), photos = third.photos, added = 1),
            merged,
        )
        assertEquals(listOf("first"), merged.photos.map { it.shotId })
    }

    @Test
    fun `an old backup cannot resurrect a cat deleted after it was taken`() {
        val merged = BackupMerge.merge(
            local = BackupContents(encounters = listOf(encounter("cat", EARLY, deletedAt = LATE))),
            imported = BackupContents(encounters = listOf(encounter("cat", MIDDLE))),
        )

        assertEquals(emptyList(), merged.encounters)
        assertEquals(1, merged.unchanged)
    }

    @Test
    fun `a backup newer than the deletion brings the cat back`() {
        // The user deleted it, then kept using another device, then restored from that device:
        // the later edit is the more recent statement of intent.
        val merged = BackupMerge.merge(
            local = BackupContents(encounters = listOf(encounter("cat", EARLY, deletedAt = MIDDLE))),
            imported = BackupContents(encounters = listOf(encounter("cat", LATE))),
        )

        assertEquals(LATE, merged.encounters.single().updatedAt)
        assertEquals(null, merged.encounters.single().deletedAt)
    }

    @Test
    fun `a deletion exactly as old as the imported edit does not win`() {
        val merged = BackupMerge.merge(
            local = BackupContents(encounters = listOf(encounter("cat", EARLY, deletedAt = MIDDLE))),
            imported = BackupContents(encounters = listOf(encounter("cat", MIDDLE))),
        )

        assertEquals(listOf("cat"), merged.encounters.map { it.id })
    }

    @Test
    fun `a whole batch is merged in one pass, each row on its own terms`() {
        val merged = BackupMerge.merge(
            local = BackupContents(
                encounters = listOf(
                    encounter("keeps-local", LATE),
                    encounter("takes-imported", EARLY),
                    encounter("stays-deleted", EARLY, deletedAt = LATE),
                ),
            ),
            imported = BackupContents(
                encounters = listOf(
                    encounter("keeps-local", EARLY),
                    encounter("takes-imported", LATE),
                    encounter("stays-deleted", MIDDLE),
                    encounter("brand-new", MIDDLE),
                ),
            ),
        )

        assertEquals(listOf("takes-imported", "brand-new"), merged.encounters.map { it.id })
        assertEquals(1, merged.added)
        assertEquals(1, merged.updated)
        assertEquals(2, merged.unchanged)
    }

    @Test
    fun `a cat the archive lists twice is added once, as its later edit`() {
        val later = encounter("cat", LATE)

        val merged = BackupMerge.merge(
            local = BackupContents(),
            imported = BackupContents(encounters = listOf(encounter("cat", MIDDLE), later)),
        )

        assertEquals(MergeResult(encounters = listOf(later), added = 1), merged)
    }

    @Test
    fun `a cat the archive lists twice keeps its later edit when that one is listed first`() {
        val later = encounter("cat", LATE)

        val merged = BackupMerge.merge(
            local = BackupContents(),
            imported = BackupContents(encounters = listOf(later, encounter("cat", MIDDLE))),
        )

        assertEquals(MergeResult(encounters = listOf(later), added = 1), merged)
    }

    @Test
    fun `a cat the archive lists three times keeps its latest edit wherever it is listed`() {
        val latest = encounter("cat", LATE)

        val merged = BackupMerge.merge(
            local = BackupContents(),
            imported = BackupContents(encounters = listOf(encounter("cat", EARLY), latest, encounter("cat", MIDDLE))),
        )

        assertEquals(MergeResult(encounters = listOf(latest), added = 1), merged)
    }

    @Test
    fun `a deletion written into an archive row does not decide between two of its rows`() {
        val laterEdit = encounter("cat", MIDDLE)

        val merged = BackupMerge.merge(
            local = BackupContents(),
            imported = BackupContents(encounters = listOf(encounter("cat", EARLY, deletedAt = LATE), laterEdit)),
        )

        assertEquals(MergeResult(encounters = listOf(laterEdit), added = 1), merged)
    }

    @Test
    fun `two rows for one cat edited at the same moment keep the one listed first`() {
        val first = encounter("cat", MIDDLE)

        val merged = BackupMerge.merge(
            local = BackupContents(),
            imported = BackupContents(encounters = listOf(first, encounter("cat", MIDDLE, photographed = true))),
        )

        assertEquals(MergeResult(encounters = listOf(first), added = 1), merged)
    }

    @Test
    fun `a cat the archive lists twice is weighed against the one here by its later edit listed first`() {
        val later = encounter("cat", LATE)

        val merged = BackupMerge.merge(
            local = BackupContents(encounters = listOf(encounter("cat", EARLY))),
            imported = BackupContents(encounters = listOf(later, encounter("cat", MIDDLE))),
        )

        assertEquals(MergeResult(encounters = listOf(later), updated = 1), merged)
    }

    @Test
    fun `a cat the archive lists twice is weighed against the one here by its later edit listed last`() {
        val later = encounter("cat", LATE)

        val merged = BackupMerge.merge(
            local = BackupContents(encounters = listOf(encounter("cat", EARLY))),
            imported = BackupContents(encounters = listOf(encounter("cat", MIDDLE), later)),
        )

        assertEquals(MergeResult(encounters = listOf(later), updated = 1), merged)
    }

    @Test
    fun `a cat the archive lists twice, both older than the one here, is unchanged once`() {
        val merged = BackupMerge.merge(
            local = BackupContents(encounters = listOf(encounter("cat", LATE))),
            imported = BackupContents(encounters = listOf(encounter("cat", EARLY), encounter("cat", MIDDLE))),
        )

        assertEquals(MergeResult(unchanged = 1), merged)
    }

    @Test
    fun `a cat deleted here comes back when the later of its two archived edits post-dates the deletion`() {
        val later = encounter("cat", LATE)

        val merged = BackupMerge.merge(
            local = BackupContents(encounters = listOf(encounter("cat", EARLY, deletedAt = MIDDLE))),
            imported = BackupContents(encounters = listOf(encounter("cat", EARLY), later)),
        )

        assertEquals(MergeResult(encounters = listOf(later), updated = 1), merged)
    }

    @Test
    fun `a named cell beats one that is still pending`() {
        val merged = BackupMerge.merge(
            local = BackupContents(placeCells = listOf(cell(status = PlaceStatus.PENDING, attempts = 3))),
            imported = BackupContents(
                placeCells = listOf(cell(status = PlaceStatus.RESOLVED, resolvedAt = MIDDLE, locality = "Moscow")),
            ),
        )

        assertEquals("Moscow", merged.placeCells.single().locality)
    }

    @Test
    fun `a pending cell never overwrites one that already has a name`() {
        val merged = BackupMerge.merge(
            local = BackupContents(
                placeCells = listOf(cell(status = PlaceStatus.RESOLVED, resolvedAt = MIDDLE, locality = "Moscow")),
            ),
            imported = BackupContents(placeCells = listOf(cell(status = PlaceStatus.PENDING))),
        )

        assertEquals(emptyList(), merged.placeCells)
    }

    @Test
    fun `a cell that gave up does not overwrite one that has a name`() {
        val merged = BackupMerge.merge(
            local = BackupContents(
                placeCells = listOf(cell(status = PlaceStatus.RESOLVED, resolvedAt = EARLY, locality = "Moscow")),
            ),
            imported = BackupContents(placeCells = listOf(cell(status = PlaceStatus.FAILED))),
        )

        assertEquals(emptyList(), merged.placeCells)
    }

    @Test
    fun `between two named cells the fresher answer wins`() {
        val merged = BackupMerge.merge(
            local = BackupContents(
                placeCells = listOf(cell(status = PlaceStatus.RESOLVED, resolvedAt = EARLY, locality = "Old name")),
            ),
            imported = BackupContents(
                placeCells = listOf(cell(status = PlaceStatus.RESOLVED, resolvedAt = LATE, locality = "New name")),
            ),
        )

        assertEquals("New name", merged.placeCells.single().locality)
    }

    @Test
    fun `an unnamed imported cell leaves the local attempt count alone`() {
        val merged = BackupMerge.merge(
            local = BackupContents(placeCells = listOf(cell(status = PlaceStatus.PENDING, attempts = 4))),
            imported = BackupContents(placeCells = listOf(cell(status = PlaceStatus.PENDING, attempts = 0))),
        )

        assertEquals(emptyList(), merged.placeCells)
    }

    @Test
    fun `a cell the device has never seen is added`() {
        val merged = BackupMerge.merge(
            local = BackupContents(),
            imported = BackupContents(placeCells = listOf(cell(id = "u4pruy", status = PlaceStatus.PENDING))),
        )

        assertEquals(listOf("u4pruy"), merged.placeCells.map { it.cellId })
    }

    @Test
    fun `a name the archive gives a cell survives a later row for it that has none`() {
        val named = cell(status = PlaceStatus.RESOLVED, resolvedAt = MIDDLE, locality = "Moscow")

        val merged = BackupMerge.merge(
            local = BackupContents(),
            imported = BackupContents(placeCells = listOf(named, cell(status = PlaceStatus.PENDING))),
        )

        assertEquals(listOf(named), merged.placeCells)
    }

    @Test
    fun `a name the archive gives a cell beats an earlier row for it that has none`() {
        val named = cell(status = PlaceStatus.RESOLVED, resolvedAt = MIDDLE, locality = "Moscow")

        val merged = BackupMerge.merge(
            local = BackupContents(),
            imported = BackupContents(placeCells = listOf(cell(status = PlaceStatus.PENDING), named)),
        )

        assertEquals(listOf(named), merged.placeCells)
    }

    @Test
    fun `of two names the archive gives one cell, the fresher is weighed against the one here`() {
        val fresher = cell(status = PlaceStatus.RESOLVED, resolvedAt = LATE, locality = "New name")

        val merged = BackupMerge.merge(
            local = BackupContents(
                placeCells = listOf(cell(status = PlaceStatus.RESOLVED, resolvedAt = EARLY, locality = "Old name")),
            ),
            imported = BackupContents(
                placeCells = listOf(
                    fresher,
                    cell(status = PlaceStatus.RESOLVED, resolvedAt = MIDDLE, locality = "Middle name"),
                ),
            ),
        )

        assertEquals(listOf(fresher), merged.placeCells)
    }

    @Test
    fun `of two names the archive gives one cell, the fresher listed second is weighed against the one here`() {
        val fresher = cell(status = PlaceStatus.RESOLVED, resolvedAt = LATE, locality = "New name")

        val merged = BackupMerge.merge(
            local = BackupContents(
                placeCells = listOf(cell(status = PlaceStatus.RESOLVED, resolvedAt = EARLY, locality = "Old name")),
            ),
            imported = BackupContents(
                placeCells = listOf(
                    cell(status = PlaceStatus.RESOLVED, resolvedAt = MIDDLE, locality = "Middle name"),
                    fresher,
                ),
            ),
        )

        assertEquals(listOf(fresher), merged.placeCells)
    }

    @Test
    fun `of two names the archive gives one cell, neither replaces a fresher one here`() {
        val merged = BackupMerge.merge(
            local = BackupContents(
                placeCells = listOf(cell(status = PlaceStatus.RESOLVED, resolvedAt = LATE, locality = "Here")),
            ),
            imported = BackupContents(
                placeCells = listOf(
                    cell(status = PlaceStatus.RESOLVED, resolvedAt = EARLY, locality = "Oldest"),
                    cell(status = PlaceStatus.RESOLVED, resolvedAt = MIDDLE, locality = "Older"),
                ),
            ),
        )

        assertEquals(emptyList(), merged.placeCells)
    }

    @Test
    fun `of two names the archive gives one cell from the same moment, the first is kept`() {
        val first = cell(status = PlaceStatus.RESOLVED, resolvedAt = MIDDLE, locality = "First")

        val merged = BackupMerge.merge(
            local = BackupContents(),
            imported = BackupContents(
                placeCells = listOf(
                    first,
                    cell(status = PlaceStatus.RESOLVED, resolvedAt = MIDDLE, locality = "Second"),
                ),
            ),
        )

        assertEquals(listOf(first), merged.placeCells)
    }

    @Test
    fun `importing a backup of exactly what is here writes nothing`() {
        val here = BackupContents(
            encounters = listOf(encounter("a", MIDDLE), encounter("b", LATE)),
            placeCells = listOf(cell(status = PlaceStatus.RESOLVED, resolvedAt = MIDDLE)),
        )

        val merged = BackupMerge.merge(local = here, imported = here)

        assertTrue(merged.encounters.isEmpty(), "encounters: ${merged.encounters}")
        assertTrue(merged.placeCells.isEmpty(), "place cells: ${merged.placeCells}")
        assertEquals(2, merged.unchanged)
    }
}
