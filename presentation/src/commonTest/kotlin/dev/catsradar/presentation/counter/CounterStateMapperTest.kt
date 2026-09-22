package dev.catsradar.presentation.counter

import kotlin.test.Test
import kotlin.test.assertEquals

class CounterStateMapperTest {

    private val mapper = CounterStateMapper()

    @Test
    fun `maps a zero count to the fresh-install label`() {
        assertEquals(CounterState(totalLabel = "0"), mapper.map(count = 0))
    }

    @Test
    fun `maps a positive count to its decimal label`() {
        assertEquals(CounterState(totalLabel = "42"), mapper.map(count = 42))
    }
}
