package dev.catsradar.presentation.encounters

import dev.catsradar.domain.Tuning
import dev.catsradar.domain.model.CatCoat
import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.LocationSource
import dev.catsradar.presentation.coat.CoatOption
import kotlinx.collections.immutable.persistentListOf
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
        val state = mapper.map(emptyList(), today)

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

        val state = mapper.map(outing, today)

        assertEquals(
            EncountersState(
                rows = persistentListOf(
                    OutingHeader(key = "header-e1", label = "2026-09-22, $BASE"),
                    EncounterGridRow.PhotoPair(
                        first = photoCell("p2", BASE + 4.minutes, "/data/photos/p2.jpg"),
                        second = photoCell("p1", BASE + 3.minutes, "/data/photos/p1.jpg"),
                    ),
                    EncounterGridRow.Tiles(
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

        val rows = mapper.map(listOf(morning, evening), today).rows

        assertEquals(
            persistentListOf(
                OutingHeader(key = "header-evening", label = "2026-09-22, ${BASE + 8.hours}"),
                EncounterGridRow.Cards(
                    persistentListOf(cell("evening", BASE + 8.hours, CellLead.Photo("/data/photos/evening_thumb.jpg"))),
                ),
                OutingHeader(key = "header-morning", label = "2026-09-22, $BASE"),
                EncounterGridRow.Cards(
                    persistentListOf(cell("morning", BASE, CellLead.Photo("/data/photos/morning_thumb.jpg"))),
                ),
            ),
            rows,
        )
    }

    @Test
    fun `a pair cat without a full-size copy shows its thumbnail`() {
        val thumbOnly = photoFixture("thumbOnly", BASE).copy(photoPath = null)
        val full = photoFixture("full", BASE + 1.minutes)

        val pair = mapper.map(listOf(thumbOnly, full), today).rows.filterIsInstance<EncounterGridRow.PhotoPair>()

        assertEquals(
            listOf("/data/photos/full.jpg", "/data/photos/thumbOnly_thumb.jpg"),
            pair.flatMap { listOf(it.first.photoPath, it.second.photoPath) },
        )
    }

    @Test
    fun `every location source maps to its own label, so none of them can collapse onto another`() {
        val sources = LocationSource.entries
        val encounters = sources.mapIndexed { index, source ->
            encounterFixture("e$index", BASE + (index * 2).hours, locationSource = source)
        }

        val labels = mapper.map(encounters, today).cells().associate { it.id to it.location }

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
        val both = encounterFixture("both", BASE).copy(thumbPath = "both_thumb.jpg", coat = CatCoat.GINGER)
        val coatOnly = encounterFixture("coatOnly", BASE + 5.minutes).copy(coat = CatCoat.GINGER)
        val neither = encounterFixture("neither", BASE + 10.minutes)

        val leads = mapper.map(listOf(both, coatOnly, neither), today).cells().associate { it.id to it.lead }

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
        val coated = encounterFixture("coated", BASE).copy(photoPath = "a.jpg", thumbPath = null, coat = CatCoat.GREY)
        val bare = encounterFixture("bare", BASE + 5.minutes).copy(photoPath = "b.jpg", thumbPath = null)

        val rows = mapper.map(listOf(coated, bare), today).rows

        assertEquals(
            EncounterGridRow.Cards(
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

        val state = mapper.map(listOf(old, mid, new), today) // fed in oldest-first order

        assertEquals(listOf("new", "mid", "old"), state.cells().map { it.id })
    }

    @Test
    fun `an encounter pair exactly SESSION_GAP apart stays one outing, one millisecond more splits them`() {
        val sameOuting = mapper.map(
            listOf(encounterFixture("a1", BASE), encounterFixture("a2", BASE + Tuning.SESSION_GAP)),
            today,
        )
        assertEquals(1, sameOuting.rows.count { it is OutingHeader })
        assertEquals(2, sameOuting.cells().size)

        val split = mapper.map(
            listOf(
                encounterFixture("b1", BASE),
                encounterFixture("b2", BASE + Tuning.SESSION_GAP + 1.milliseconds),
            ),
            today,
        )
        assertEquals(2, split.rows.count { it is OutingHeader })
        assertEquals(2, split.cells().size)
    }

    @Test
    fun `two outings on the same day get distinct header labels`() {
        val morning = encounterFixture("morning", BASE)
        val evening = encounterFixture("evening", BASE + 8.hours) // past SESSION_GAP: a separate outing

        val state = mapper.map(listOf(morning, evening), today)

        val headerLabels = state.rows.filterIsInstance<OutingHeader>().map { it.label }
        assertEquals(2, headerLabels.size)
        assertNotEquals(headerLabels[0], headerLabels[1])
    }

    @Test
    fun `a soft-deleted encounter never appears as a cat or forms its own group`() {
        val kept = encounterFixture("kept", BASE)
        val deleted = encounterFixture("deleted", BASE + 5.minutes, deletedAt = BASE + 1.hours)

        val state = mapper.map(listOf(kept, deleted), today)

        assertEquals(listOf("kept"), state.cells().map { it.id })
        assertEquals(1, state.rows.count { it is OutingHeader })
    }

    @Test
    fun `a deleted cat between two photos leaves them a pair`() {
        val older = photoFixture("older", BASE)
        val deleted = encounterFixture("deleted", BASE + 5.minutes, deletedAt = BASE + 1.hours)
        val newer = photoFixture("newer", BASE + 10.minutes)

        val rows = mapper.map(listOf(older, deleted, newer), today).rows

        assertEquals(
            EncounterGridRow.PhotoPair(
                first = photoCell("newer", BASE + 10.minutes, "/data/photos/newer.jpg"),
                second = photoCell("older", BASE, "/data/photos/older.jpg"),
            ),
            rows.last(),
        )
    }

    @Test
    fun `changing only an encounter's own offset changes which local day its outing header uses`() {
        val instant = Instant.parse("2026-09-22T00:10:00Z")

        mapper.map(listOf(encounterFixture("same-zone", instant, tzOffsetMinutes = 0)), today)
        val dateAtUtc = formatter.dayHeaderCalls.last()

        mapper.map(listOf(encounterFixture("hour-west", instant, tzOffsetMinutes = -60)), today)
        val dateAnHourWest = formatter.dayHeaderCalls.last()

        assertEquals(LocalDate(2026, 9, 22), dateAtUtc)
        assertEquals(LocalDate(2026, 9, 21), dateAnHourWest)
        assertNotEquals(dateAtUtc, dateAnHourWest)
    }

    @Test
    fun `the plain list keeps one row per cat under each outing header, newest first`() {
        val older = photoFixture("older", BASE)
        val newer = photoFixture("newer", BASE + 10.minutes, locationSource = LocationSource.CURRENT_FIX)
        val lone = encounterFixture("lone", BASE + 5.hours)

        val items = mapper.mapList(listOf(older, newer, lone), today)

        assertEquals(
            persistentListOf(
                OutingHeader(key = "header-lone", label = "2026-09-22, ${BASE + 5.hours}"),
                EncounterListItem.Row(id = "lone", timeLabel = "${BASE + 5.hours}", location = LocationLabel.NONE),
                OutingHeader(key = "header-older", label = "2026-09-22, $BASE"),
                EncounterListItem.Row(
                    id = "newer",
                    timeLabel = "${BASE + 10.minutes}",
                    location = LocationLabel.CURRENT,
                ),
                EncounterListItem.Row(id = "older", timeLabel = "$BASE", location = LocationLabel.NONE),
            ),
            items,
        )
    }

    private data class CellView(val id: String, val location: LocationLabel, val lead: CellLead?)

    private fun EncountersState.cells(): List<CellView> = rows.flatMap { row ->
        when (row) {
            is OutingHeader -> emptyList()
            is EncounterGridRow.PhotoPair -> listOf(row.first, row.second).map { CellView(it.id, it.location, null) }
            is EncounterGridRow.Tiles -> row.cells.map { CellView(it.id, it.location, it.lead) }
            is EncounterGridRow.Cards -> row.cells.map { CellView(it.id, it.location, it.lead) }
        }
    }

    private fun photoFixture(
        id: String,
        occurredAt: Instant,
        locationSource: LocationSource = LocationSource.NONE,
    ): Encounter = encounterFixture(id, occurredAt, locationSource = locationSource)
        .copy(photoPath = "$id.jpg", thumbPath = "${id}_thumb.jpg")

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
    )

    private companion object {
        val BASE = Instant.parse("2026-09-22T10:00:00Z")
    }
}
