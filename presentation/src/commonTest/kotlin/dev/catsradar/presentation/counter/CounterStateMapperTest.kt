package dev.catsradar.presentation.counter

import dev.catsradar.presentation.encounters.FakeDateTimeFormatter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes

class CounterStateMapperTest {

    private val mapper = CounterStateMapper(FakeDateTimeFormatter())

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
}
