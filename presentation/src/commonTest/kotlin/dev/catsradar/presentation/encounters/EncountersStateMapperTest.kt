package dev.catsradar.presentation.encounters

import dev.catsradar.domain.Tuning
import dev.catsradar.domain.model.CatCoat
import dev.catsradar.domain.model.LocationSource
import dev.catsradar.presentation.coat.CoatOption
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
    fun `every location source maps to its own label, so none of them can collapse onto another`() {
        val sources = LocationSource.entries
        val encounters = sources.mapIndexed { index, source ->
            encounterFixture("e$index", BASE + (index * 2).hours, locationSource = source)
        }

        val labels = mapper.map(encounters, today)
            .rows
            .filterIsInstance<EncounterListItem.Row>()
            .associate { it.id to it.location }

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
    fun `a photo row leads with its thumbnail resolved to a full path, a tally with a paw`() {
        val tally = encounterFixture("tally", BASE)
        val photo = encounterFixture("photo", BASE + 5.minutes).copy(thumbPath = "photo_thumb.jpg")

        val leads = mapper.map(listOf(tally, photo), today)
            .rows
            .filterIsInstance<EncounterListItem.Row>()
            .associate { it.id to it.lead }

        assertEquals(mapOf("tally" to RowLead.Paw, "photo" to RowLead.Photo("/data/photos/photo_thumb.jpg")), leads)
    }

    @Test
    fun `a photo whose thumbnail failed to write falls back to its coat, or to a paw`() {
        val coated = encounterFixture("coated", BASE).copy(photoPath = "a.jpg", thumbPath = null, coat = CatCoat.GREY)
        val bare = encounterFixture("bare", BASE + 5.minutes).copy(photoPath = "b.jpg", thumbPath = null)

        val leads = mapper.map(listOf(coated, bare), today)
            .rows
            .filterIsInstance<EncounterListItem.Row>()
            .associate { it.id to it.lead }

        assertEquals(mapOf("bare" to RowLead.Paw, "coated" to RowLead.Coat(CoatOption.GREY)), leads)
    }

    @Test
    fun `rows come back newest first, both across outings and within one`() {
        val old = encounterFixture("old", BASE)
        val mid = encounterFixture("mid", BASE + 10.minutes) // same outing as `old`
        val new = encounterFixture("new", BASE + 2.hours) // past SESSION_GAP from `mid`: a new outing

        val state = mapper.map(listOf(old, mid, new), today) // fed in oldest-first order

        val rowIds = state.rows.filterIsInstance<EncounterListItem.Row>().map { it.id }
        assertEquals(listOf("new", "mid", "old"), rowIds)
    }

    @Test
    fun `an encounter pair exactly SESSION_GAP apart stays one outing, one millisecond more splits them`() {
        val sameOuting = mapper.map(
            listOf(encounterFixture("a1", BASE), encounterFixture("a2", BASE + Tuning.SESSION_GAP)),
            today,
        )
        assertEquals(1, sameOuting.rows.count { it is EncounterListItem.OutingHeader })
        assertEquals(2, sameOuting.rows.count { it is EncounterListItem.Row })

        val split = mapper.map(
            listOf(
                encounterFixture("b1", BASE),
                encounterFixture("b2", BASE + Tuning.SESSION_GAP + 1.milliseconds),
            ),
            today,
        )
        assertEquals(2, split.rows.count { it is EncounterListItem.OutingHeader })
        assertEquals(2, split.rows.count { it is EncounterListItem.Row })
    }

    @Test
    fun `two outings on the same day get distinct header labels`() {
        val morning = encounterFixture("morning", BASE)
        val evening = encounterFixture("evening", BASE + 8.hours) // past SESSION_GAP: a separate outing

        val state = mapper.map(listOf(morning, evening), today)

        val headerLabels = state.rows.filterIsInstance<EncounterListItem.OutingHeader>().map { it.label }
        assertEquals(2, headerLabels.size)
        assertNotEquals(headerLabels[0], headerLabels[1])
    }

    @Test
    fun `a soft-deleted encounter never appears as a row or forms its own group`() {
        val kept = encounterFixture("kept", BASE)
        val deleted = encounterFixture("deleted", BASE + 5.minutes, deletedAt = BASE + 1.hours)

        val state = mapper.map(listOf(kept, deleted), today)

        assertEquals(listOf("kept"), state.rows.filterIsInstance<EncounterListItem.Row>().map { it.id })
        assertEquals(1, state.rows.count { it is EncounterListItem.OutingHeader })
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

    // Newest first within the outing, so the most recent cat opens the group.
    @Test
    fun `an outing's rows form one group, first to last, and a lone cat is a group of its own`() {
        val outing = listOf(
            encounterFixture("a", BASE),
            encounterFixture("b", BASE + 5.minutes),
            encounterFixture("c", BASE + 10.minutes),
        )
        val lone = encounterFixture("lone", BASE + 5.hours)

        val positions = mapper.map(outing + lone, today)
            .rows
            .filterIsInstance<EncounterListItem.Row>()
            .associate { it.id to it.position }

        assertEquals(
            mapOf(
                "lone" to GroupPosition.ONLY,
                "c" to GroupPosition.FIRST,
                "b" to GroupPosition.MIDDLE,
                "a" to GroupPosition.LAST,
            ),
            positions,
        )
    }

    @Test
    fun `a photo leads over a coat, and a coat over nothing`() {
        val both = encounterFixture("both", BASE).copy(thumbPath = "both_thumb.jpg", coat = CatCoat.GINGER)
        val coatOnly = encounterFixture("coatOnly", BASE + 5.minutes).copy(coat = CatCoat.GINGER)
        val neither = encounterFixture("neither", BASE + 10.minutes)

        val leads = mapper.map(listOf(both, coatOnly, neither), today)
            .rows
            .filterIsInstance<EncounterListItem.Row>()
            .associate { it.id to it.lead }

        assertEquals(
            mapOf(
                "neither" to RowLead.Paw,
                "coatOnly" to RowLead.Coat(CoatOption.GINGER),
                "both" to RowLead.Photo("/data/photos/both_thumb.jpg"),
            ),
            leads,
        )
    }

    @Test
    fun `a deleted cat leaves its outing's rows placed as if it had never been`() {
        val oldest = encounterFixture("oldest", BASE)
        val middle = encounterFixture("middle", BASE + 5.minutes)
        val deleted = encounterFixture("deleted", BASE + 10.minutes, deletedAt = BASE + 1.hours)

        val positions = mapper.map(listOf(oldest, middle, deleted), today)
            .rows
            .filterIsInstance<EncounterListItem.Row>()
            .associate { it.id to it.position }

        assertEquals(mapOf("middle" to GroupPosition.FIRST, "oldest" to GroupPosition.LAST), positions)
    }

    @Test
    fun `an outing offers the map, by its first cat, only when one of its cats has a location`() {
        val first = encounterFixture("first", BASE)
        val located = encounterFixture("located", BASE + 5.minutes).copy(lat = 41.39, lon = 2.17)
        val unlocatedOuting = encounterFixture("later", BASE + 3.hours)

        val headers = mapper.map(listOf(located, unlocatedOuting, first), today)
            .rows
            .filterIsInstance<EncounterListItem.OutingHeader>()

        assertEquals(listOf(null, "first"), headers.map { it.mapOutingId })
    }

    @Test
    fun `an outing whose only coordinates are off the globe offers no map`() {
        val offGlobe = encounterFixture("off", BASE).copy(lat = 123.4, lon = 2.17)

        val header = mapper.map(listOf(offGlobe), today).rows
            .filterIsInstance<EncounterListItem.OutingHeader>()
            .single()

        assertEquals(null, header.mapOutingId)
    }

    private companion object {
        val BASE = Instant.parse("2026-09-22T10:00:00Z")
    }
}
