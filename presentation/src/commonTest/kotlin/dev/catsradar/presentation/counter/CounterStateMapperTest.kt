package dev.catsradar.presentation.counter

import dev.catsradar.presentation.encounters.FakeDateTimeFormatter
import kotlin.test.Test
import kotlin.test.assertEquals

class CounterStateMapperTest {

    private val mapper = CounterStateMapper(FakeDateTimeFormatter())

    @Test
    fun `maps a zero count with the undo chip hidden`() {
        assertEquals(
            CounterState(totalLabel = "0", undoVisible = false),
            mapper.map(count = 0, undoVisible = false),
        )
    }

    @Test
    fun `maps a positive count with the undo chip visible`() {
        assertEquals(
            CounterState(totalLabel = "42", undoVisible = true),
            mapper.map(count = 42, undoVisible = true),
        )
    }

    @Test
    fun `maps the location permission hint visibility through unchanged`() {
        assertEquals(
            CounterState(totalLabel = "0", undoVisible = false, locationPermissionHintVisible = true),
            mapper.map(count = 0, undoVisible = false, locationPermissionHintVisible = true),
        )
    }
}
