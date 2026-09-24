package dev.catsradar.presentation.counter

import dev.catsradar.presentation.encounters.FakeDateTimeFormatter
import dev.catsradar.presentation.encounters.FakePhotoStorage
import dev.catsradar.presentation.encounters.photoFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
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
    fun `the walk time is formatted only while walking mode is on and a walk has started`() {
        assertEquals(32.minutes.toString(), mapper.walkElapsedLabel(walking = true, elapsed = 32.minutes))
        assertNull(mapper.walkElapsedLabel(walking = true, elapsed = null))
        assertNull(mapper.walkElapsedLabel(walking = false, elapsed = 32.minutes))
    }

    @Test
    fun `the walk time is carried through a rebuild of the whole state`() {
        assertEquals(
            CounterState(totalLabel = "0", count = 0, undoVisible = false, walkingMode = true, walkElapsedLabel = "5m"),
            mapper.map(count = 0, undoVisible = false, walkingMode = true, walkElapsedLabel = "5m"),
        )
    }

    @Test
    fun `the coat prompt shows the photo's thumbnail from the photo directory`() {
        val photo = photoFixture(id = "cat-7", occurredAt = Instant.parse("2026-09-22T10:00:00Z"))

        assertEquals(CoatPromptState(thumbPath = "/data/photos/cat-7_thumb.jpg"), mapper.coatPrompt(photo))
    }

    @Test
    fun `the coat prompt has no picture when the thumbnail could not be made`() {
        val photo = photoFixture(id = "cat-7", occurredAt = Instant.parse("2026-09-22T10:00:00Z"))
            .copy(thumbPath = null)

        assertEquals(CoatPromptState(thumbPath = null), mapper.coatPrompt(photo))
    }
}
