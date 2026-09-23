package dev.catsradar.domain.backup

import dev.catsradar.domain.model.PlaceCell
import dev.catsradar.domain.model.PlaceStatus
import dev.catsradar.domain.testing.encounterAt
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
    coatless: Boolean = true,
) = encounterAt(EARLY).copy(
    id = id,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    photoPath = if (coatless) null else "$id.jpg",
)

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
            local = BackupContents(encounters = listOf(encounter("cat", MIDDLE))),
            imported = BackupContents(encounters = listOf(encounter("cat", MIDDLE, coatless = false))),
        )

        assertEquals(emptyList(), merged.encounters)
        assertEquals(1, merged.unchanged)
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
        // The geocoding worker gives a cell up once its attempts run out; importing must not reset that.
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
