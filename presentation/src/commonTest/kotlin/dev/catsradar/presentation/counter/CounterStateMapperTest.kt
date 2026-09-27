package dev.catsradar.presentation.counter

import dev.catsradar.domain.stats.Milestone
import dev.catsradar.presentation.encounters.FakeDateTimeFormatter
import dev.catsradar.presentation.encounters.FakePhotoStorage
import dev.catsradar.presentation.encounters.encounterFixture
import dev.catsradar.presentation.encounters.photoFixture
import dev.catsradar.presentation.encounters.withPhoto
import dev.catsradar.presentation.statistics.MilestoneState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class CounterStateMapperTest {

    private val mapper = CounterStateMapper(FakeDateTimeFormatter(), FakePhotoStorage())

    @Test
    fun `before the total is read there is no number, not a zero`() {
        assertEquals(CounterState(totalLabel = "", count = null, undoVisible = false), mapper.initial())
    }

    @Test
    fun `maps a zero count with the undo chip hidden`() {
        assertEquals(
            CounterState(totalLabel = "0", count = 0, undoVisible = false),
            mapper.map(count = 0, undoVisible = false),
        )
    }

    @Test
    fun `maps a positive count with the undo chip visible`() {
        assertEquals(
            CounterState(totalLabel = "42", count = 42, undoVisible = true),
            mapper.map(count = 42, undoVisible = true),
        )
    }

    @Test
    fun `maps the location permission hint visibility through unchanged`() {
        assertEquals(
            CounterState(totalLabel = "0", count = 0, undoVisible = false, locationPermissionHintVisible = true),
            mapper.map(count = 0, undoVisible = false, locationPermissionHintVisible = true),
        )
    }

    @Test
    fun `the coat prompt shows the photo's thumbnail from the photo directory`() {
        val photo = photoFixture(id = "cat-7", occurredAt = Instant.parse("2026-09-22T10:00:00Z"))

        assertEquals(
            CoatPromptState("cat-7", "cat-7", thumbPath = "/data/photos/cat-7_thumb.jpg"),
            mapper.coatPrompt(photo),
        )
    }

    @Test
    fun `the coat prompt has no picture when the thumbnail could not be made`() {
        val photo = encounterFixture(id = "cat-7", occurredAt = Instant.parse("2026-09-22T10:00:00Z"))
            .withPhoto(photoPath = "cat-7.jpg", thumbPath = null)

        assertEquals(CoatPromptState("cat-7", "cat-7", thumbPath = null), mapper.coatPrompt(photo))
    }

    @Test
    fun `the count carries the next milestone and how far it has come from the last one`() {
        assertEquals(
            CounterMilestoneState(MilestoneState(valueLabel = "100", remainingLabel = "38"), fraction = 12f / 50f),
            mapper.map(
                count = 62,
                undoVisible = false,
                milestone = Milestone(value = 100, remaining = 38, reached = 50),
            ).milestone,
        )
    }

    @Test
    fun `with no cats yet there is no milestone to reach`() {
        val first = Milestone(value = 1, remaining = 1, reached = 0)

        assertNull(mapper.map(count = 0, undoVisible = false, milestone = first).milestone)
    }

    @Test
    fun `at the first cat the arc starts from nothing`() {
        assertEquals(
            0f,
            mapper.map(count = 1, undoVisible = false, milestone = Milestone(value = 10, remaining = 9, reached = 1))
                .milestone?.fraction,
        )
    }

    @Test
    fun `past the last rung there is no milestone`() {
        assertNull(mapper.map(count = 10_000, undoVisible = false, milestone = null).milestone)
    }
}
