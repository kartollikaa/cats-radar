package dev.catsradar.presentation.encounters

import dev.catsradar.domain.Tuning
import dev.catsradar.domain.model.CatCoat
import dev.catsradar.domain.model.LocationSource
import dev.catsradar.presentation.coat.CoatOption
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class EncountersStateMapperTest {

    private val formatter = FakeDateTimeFormatter()
    private val mapper = EncountersStateMapper(formatter, FakePhotoStorage())
    private val today = LocalDate(2026, 9, 22)

    @Test
    fun `an empty encounter list maps to the empty state`() {
        val state = mapper.map(emptyList(), today, grid = true)

        assertEquals(EncountersState(), state)
    }

    @Test
    fun `an outing packs into a pair of full-size photos, then a tile row of the rest, under its header`() {
        val outing = listOf(
            encounterFixture("e1", BASE),
            encounterFixture("e2", BASE + 1.minutes).copy(coat = CatCoat.GREY),
            encounterFixture("e3", BASE + 2.minutes),
            photoFixture("p1", BASE + 3.minutes),
            photoFixture("p2", BASE + 4.minutes),
        )

        val state = mapper.map(outing, today, grid = true)

        assertEquals(
            EncountersState(
                rows = persistentListOf(
                    OutingHeader(key = "header-e1", label = "2026-09-22, $BASE"),
                    EncountersRow.PhotoPair(
                        first = photoCell("p2", BASE + 4.minutes, "/data/photos/p2.jpg"),
                        second = photoCell("p1", BASE + 3.minutes, "/data/photos/p1.jpg"),
                    ),
                    EncountersRow.Tiles(
                        cells = persistentListOf(
                            cell("e3", BASE + 2.minutes),
                            cell("e2", BASE + 1.minutes, CellLead.Coat(CoatOption.GREY)),
                            cell("e1", BASE),
                        ),
                    ),
                ),
            ),
            state,
        )
    }

    @Test
    fun `packing restarts under each outing, so photos from two outings never share a pair`() {
        val morning = photoFixture("morning", BASE)
        val evening = photoFixture("evening", BASE + 8.hours)

        val rows = mapper.map(listOf(morning, evening), today, grid = true).rows

        assertEquals(
            persistentListOf(
                OutingHeader(key = "header-evening", label = "2026-09-22, ${BASE + 8.hours}"),
                EncountersRow.Cards(
                    persistentListOf(cell("evening", BASE + 8.hours, CellLead.Photo("/data/photos/evening_thumb.jpg"))),
                ),
                OutingHeader(key = "header-morning", label = "2026-09-22, $BASE"),
                EncountersRow.Cards(
                    persistentListOf(cell("morning", BASE, CellLead.Photo("/data/photos/morning_thumb.jpg"))),
                ),
            ),
            rows,
        )
    }

    @Test
    fun `a run of cats without photos ends at its outing header instead of filling a row across it`() {
        val morning = (0 until 3).map { encounterFixture("m$it", BASE + it.minutes) }
        val evening = (0 until 2).map { encounterFixture("e$it", BASE + 8.hours + it.minutes) }

        val rows = mapper.map(morning + evening, today, grid = true).rows

        assertEquals(
            persistentListOf(
                OutingHeader(key = "header-e0", label = "2026-09-22, ${BASE + 8.hours}"),
                EncountersRow.Cards(
                    persistentListOf(cell("e1", BASE + 8.hours + 1.minutes), cell("e0", BASE + 8.hours)),
                ),
                OutingHeader(key = "header-m0", label = "2026-09-22, $BASE"),
                EncountersRow.Tiles(
                    persistentListOf(cell("m2", BASE + 2.minutes), cell("m1", BASE + 1.minutes), cell("m0", BASE)),
                ),
            ),
            rows,
        )
    }

    @Test
    fun `a lone photo in a tile row shows its thumbnail, not its full-size copy`() {
        val older = encounterFixture("older", BASE)
        val middle = encounterFixture("middle", BASE + 1.minutes)
        val photo = photoFixture("photo", BASE + 2.minutes)

        val rows = mapper.map(listOf(older, middle, photo), today, grid = true).rows

        assertEquals(
            EncountersRow.Tiles(
                persistentListOf(
                    cell("photo", BASE + 2.minutes, CellLead.Photo("/data/photos/photo_thumb.jpg")),
                    cell("middle", BASE + 1.minutes),
                    cell("older", BASE),
                ),
            ),
            rows.last(),
        )
    }

    @Test
    fun `with the grid off, every cat is a full row of its outing, placed first to last`() {
        val oldest = encounterFixture("oldest", BASE)
        val middle = encounterFixture("middle", BASE + 5.minutes).copy(coat = CatCoat.BLACK)
        val newest = photoFixture("newest", BASE + 10.minutes)
        val lone = encounterFixture("lone", BASE + 5.hours)

        val state = mapper.map(listOf(oldest, middle, newest, lone), today, grid = false)

        assertEquals(
            EncountersState(
                rows = persistentListOf(
                    OutingHeader(key = "header-lone", label = "2026-09-22, ${BASE + 5.hours}"),
                    EncountersRow.Single(cell("lone", BASE + 5.hours), GroupPosition.ONLY),
                    OutingHeader(key = "header-oldest", label = "2026-09-22, $BASE"),
                    EncountersRow.Single(
                        cell("newest", BASE + 10.minutes, CellLead.Photo("/data/photos/newest_thumb.jpg")),
                        GroupPosition.FIRST,
                    ),
                    EncountersRow.Single(
                        cell("middle", BASE + 5.minutes, CellLead.Coat(CoatOption.BLACK)),
                        GroupPosition.MIDDLE,
                    ),
                    EncountersRow.Single(cell("oldest", BASE), GroupPosition.LAST),
                ),
                layout = EncountersLayout.LIST,
            ),
            state,
        )
    }

    @Test
    fun `every location source maps to its own label, so none of them can collapse onto another`() {
        val sources = LocationSource.entries
        val encounters = sources.mapIndexed { index, source ->
            encounterFixture("e$index", BASE + (index * 2).hours, locationSource = source)
        }

        val labels = mapper.map(encounters, today, grid = true).cells().associate { it.id to it.location }

        assertEquals(
            mapOf(
                "e0" to LocationLabel.FROM_PHOTO,
                "e1" to LocationLabel.CURRENT,
                "e2" to LocationLabel.LAST_KNOWN,
                "e3" to LocationLabel.FROM_OUTING,
                "e4" to LocationLabel.NONE,
            ),
            labels,
        )
        assertEquals(sources.size, labels.values.toSet().size, "two sources share one label")
    }

    @Test
    fun `a photo leads over a coat, and a coat over nothing`() {
        val both = encounterFixture("both", BASE).copy(coat = CatCoat.GINGER).withPhoto("both.jpg", "both_thumb.jpg")
        val coatOnly = encounterFixture("coatOnly", BASE + 5.minutes).copy(coat = CatCoat.GINGER)
        val neither = encounterFixture("neither", BASE + 10.minutes)

        val leads = mapper.map(
            listOf(both, coatOnly, neither),
            today,
            grid = true,
        ).cells().associate { it.id to it.lead }

        assertEquals(
            mapOf(
                "neither" to CellLead.Paw,
                "coatOnly" to CellLead.Coat(CoatOption.GINGER),
                "both" to CellLead.Photo("/data/photos/both_thumb.jpg"),
            ),
            leads,
        )
    }

    @Test
    fun `photos whose thumbnails failed to write lead with a coat or a paw, and never pair up`() {
        val coated = encounterFixture("coated", BASE).copy(coat = CatCoat.GREY).withPhoto(photoPath = "a.jpg")
        val bare = encounterFixture("bare", BASE + 5.minutes).withPhoto(photoPath = "b.jpg")

        val rows = mapper.map(listOf(coated, bare), today, grid = true).rows

        assertEquals(
            EncountersRow.Cards(
                persistentListOf(
                    cell("bare", BASE + 5.minutes),
                    cell("coated", BASE, CellLead.Coat(CoatOption.GREY)),
                ),
            ),
            rows.last(),
        )
    }

    @Test
    fun `cats come back newest first, both across outings and within one`() {
        val old = encounterFixture("old", BASE)
        val mid = encounterFixture("mid", BASE + 10.minutes) // same outing as `old`
        val new = encounterFixture("new", BASE + 2.hours) // past SESSION_GAP from `mid`: a new outing

        val state = mapper.map(listOf(old, mid, new), today, grid = true) // fed in oldest-first order

        assertEquals(listOf("new", "mid", "old"), state.cells().map { it.id })
    }

    @Test
    fun `an encounter pair exactly SESSION_GAP apart stays one outing, one millisecond more splits them`() {
        val sameOuting = mapper.map(
            listOf(encounterFixture("a1", BASE), encounterFixture("a2", BASE + Tuning.SESSION_GAP)),
            today,
            grid = true,
        )
        assertEquals(1, sameOuting.rows.count { it is OutingHeader })
        assertEquals(2, sameOuting.cells().size)

        val split = mapper.map(
            listOf(
                encounterFixture("b1", BASE),
                encounterFixture("b2", BASE + Tuning.SESSION_GAP + 1.milliseconds),
            ),
            today,
            grid = true,
        )
        assertEquals(2, split.rows.count { it is OutingHeader })
        assertEquals(2, split.cells().size)
    }

    @Test
    fun `two outings on the same day get distinct header labels`() {
        val morning = encounterFixture("morning", BASE)
        val evening = encounterFixture("evening", BASE + 8.hours) // past SESSION_GAP: a separate outing

        val state = mapper.map(listOf(morning, evening), today, grid = true)

        val headerLabels = state.rows.filterIsInstance<OutingHeader>().map { it.label }
        assertEquals(2, headerLabels.size)
        assertNotEquals(headerLabels[0], headerLabels[1])
    }

    @Test
    fun `a soft-deleted encounter never appears as a cat or forms its own group`() {
        val kept = encounterFixture("kept", BASE)
        val deleted = encounterFixture("deleted", BASE + 5.minutes, deletedAt = BASE + 1.hours)

        val state = mapper.map(listOf(kept, deleted), today, grid = true)

        assertEquals(listOf("kept"), state.cells().map { it.id })
        assertEquals(1, state.rows.count { it is OutingHeader })
    }

    @Test
    fun `a deleted cat between two photos leaves them a pair`() {
        val older = photoFixture("older", BASE)
        val deleted = encounterFixture("deleted", BASE + 5.minutes, deletedAt = BASE + 1.hours)
        val newer = photoFixture("newer", BASE + 10.minutes)

        val rows = mapper.map(listOf(older, deleted, newer), today, grid = true).rows

        assertEquals(
            EncountersRow.PhotoPair(
                first = photoCell("newer", BASE + 10.minutes, "/data/photos/newer.jpg"),
                second = photoCell("older", BASE, "/data/photos/older.jpg"),
            ),
            rows.last(),
        )
    }

    @Test
    fun `changing only an encounter's own offset changes which local day its outing header uses`() {
        val instant = Instant.parse("2026-09-22T00:10:00Z")

        mapper.map(listOf(encounterFixture("same-zone", instant, tzOffsetMinutes = 0)), today, grid = true)
        val dateAtUtc = formatter.dayHeaderCalls.last()

        mapper.map(listOf(encounterFixture("hour-west", instant, tzOffsetMinutes = -60)), today, grid = true)
        val dateAnHourWest = formatter.dayHeaderCalls.last()

        assertEquals(LocalDate(2026, 9, 22), dateAtUtc)
        assertEquals(LocalDate(2026, 9, 21), dateAnHourWest)
        assertNotEquals(dateAtUtc, dateAnHourWest)
    }

    private data class CellView(val id: String, val location: LocationLabel, val lead: CellLead?)

    @Test
    fun `selected ids mark exactly their cats, and an id with no cat is dropped from the selection`() {
        val earlier = encounterFixture("earlier", BASE)
        val later = encounterFixture("later", BASE + 5.minutes)

        val state = mapper.map(listOf(earlier, later), today, grid = true, selectedIds = setOf("later", "gone"))

        assertEquals(
            EncountersState(
                rows = persistentListOf(
                    OutingHeader(key = "header-earlier", label = "2026-09-22, $BASE"),
                    EncountersRow.Cards(
                        persistentListOf(cell("later", BASE + 5.minutes).copy(selected = true), cell("earlier", BASE)),
                    ),
                ),
                selectedIds = persistentSetOf("later"),
            ),
            state,
        )
        assertEquals(1, state.selectedCount)
    }

    @Test
    fun `a selection marks photo pairs, tiles and list rows alike`() {
        val encounters = listOf(
            encounterFixture("tile", BASE),
            encounterFixture("tile2", BASE + 1.minutes),
            encounterFixture("tile3", BASE + 2.minutes),
            photoFixture("p1", BASE + 3.minutes),
            photoFixture("p2", BASE + 4.minutes),
        )
        val ids = setOf("tile2", "p1")

        val grid = mapper.map(encounters, today, grid = true, selectedIds = ids)
        val list = mapper.map(encounters, today, grid = false, selectedIds = ids)

        assertEquals(persistentSetOf("tile2", "p1"), grid.selectedIds)
        assertEquals(persistentSetOf("tile2", "p1"), list.selectedIds)
        assertEquals(
            listOf("p1", "tile2"),
            list.rows.filterIsInstance<EncountersRow.Single>().filter { it.cell.selected }.map { it.cell.id },
        )
    }

    @Test
    fun `re-marking a selection gives the same state as mapping with it, without formatting again`() {
        val encounters = listOf(
            encounterFixture("a", BASE),
            encounterFixture("b", BASE + 5.minutes),
            photoFixture("c", BASE + 5.hours),
        )
        val first = mapper.map(encounters, today, grid = true, selectedIds = setOf("a"))
        val headersFormatted = formatter.dayHeaderCalls.size

        val reselected = first.withSelection(setOf("b", "c", "gone"))

        assertEquals(headersFormatted, formatter.dayHeaderCalls.size, "re-marking formatted a header again")
        assertEquals(mapper.map(encounters, today, grid = true, selectedIds = setOf("b", "c")), reselected)
    }

    @Test
    fun `a selected cat that has been deleted is no longer selected`() {
        val live = encounterFixture("live", BASE)
        val deleted = encounterFixture("deleted", BASE + 5.minutes, deletedAt = BASE + 1.hours)

        val state = mapper.map(listOf(live, deleted), today, grid = true, selectedIds = setOf("live", "deleted"))

        assertEquals(persistentSetOf("live"), state.selectedIds)
    }

    @Test
    fun `an outing offers the map, by its first cat, only when one of its cats has a location`() {
        val first = encounterFixture("first", BASE)
        val located = encounterFixture("located", BASE + 5.minutes).copy(lat = 41.39, lon = 2.17)
        val unlocatedOuting = encounterFixture("later", BASE + 3.hours)

        val headers = mapper.map(listOf(located, unlocatedOuting, first), today, grid = true)
            .rows
            .filterIsInstance<OutingHeader>()

        assertEquals(listOf(null, "first"), headers.map { it.mapOutingId })
    }

    @Test
    fun `an outing whose only coordinates are off the globe offers no map`() {
        val offGlobe = encounterFixture("off", BASE).copy(lat = 123.4, lon = 2.17)

        val header = mapper.map(listOf(offGlobe), today, grid = true).rows
            .filterIsInstance<OutingHeader>()
            .single()

        assertEquals(null, header.mapOutingId)
    }

    private fun EncountersState.cells(): List<CellView> = rows.flatMap { row ->
        when (row) {
            is OutingHeader -> emptyList()
            is EncountersRow.PhotoPair -> listOf(row.first, row.second).map { CellView(it.id, it.location, null) }
            is EncountersRow.Tiles -> row.cells.map { CellView(it.id, it.location, it.lead) }
            is EncountersRow.Cards -> row.cells.map { CellView(it.id, it.location, it.lead) }
            is EncountersRow.Single -> listOf(CellView(row.cell.id, row.cell.location, row.cell.lead))
        }
    }

    private fun cell(id: String, occurredAt: Instant, lead: CellLead = CellLead.Paw) = EncounterCell(
        id = id,
        timeLabel = occurredAt.toString(),
        location = LocationLabel.NONE,
        lead = lead,
    )

    private fun photoCell(id: String, occurredAt: Instant, photoPath: String) = PhotoCell(
        id = id,
        timeLabel = occurredAt.toString(),
        location = LocationLabel.NONE,
        photoPath = photoPath,
        thumbnailPath = "/data/photos/${id}_thumb.jpg",
    )

    private companion object {
        val BASE = Instant.parse("2026-09-22T10:00:00Z")
    }
}
