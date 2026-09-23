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
    fun `a photo row carries its thumbnail resolved to a full path, a tally row carries none`() {
        val tally = encounterFixture("tally", BASE)
        val photo = encounterFixture("photo", BASE + 5.minutes).copy(thumbPath = "photo_thumb.jpg")

        val rows = mapper.map(listOf(tally, photo), today)
            .rows
            .filterIsInstance<EncounterListItem.Row>()
            .associate { it.id to it.thumbnailPath }

        assertEquals(mapOf("tally" to null, "photo" to "/data/photos/photo_thumb.jpg"), rows)
    }

    @Test
    fun `a photo whose thumbnail failed to write carries none, so the row falls back to a placeholder`() {
        val photo = encounterFixture("photo", BASE).copy(photoPath = "photo.jpg", thumbPath = null)

        val row = mapper.map(listOf(photo), today).rows.filterIsInstance<EncounterListItem.Row>().single()

        assertEquals(null, row.thumbnailPath)
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
    fun `a row carries its coat, and none when no coat was noted`() {
        val ginger = encounterFixture("ginger", BASE).copy(coat = CatCoat.GINGER)
        val unknown = encounterFixture("unknown", BASE + 5.minutes)

        val coats = mapper.map(listOf(ginger, unknown), today)
            .rows
            .filterIsInstance<EncounterListItem.Row>()
            .associate { it.id to it.coat }

        assertEquals(mapOf("unknown" to null, "ginger" to CoatOption.GINGER), coats)
    }

    private companion object {
        val BASE = Instant.parse("2026-09-22T10:00:00Z")
    }
}
