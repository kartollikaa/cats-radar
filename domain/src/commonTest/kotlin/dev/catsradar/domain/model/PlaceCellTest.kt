package dev.catsradar.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class PlaceCellTest {
    @Test
    fun `PlaceStatus has exactly four entries in spec order`() {
        assertEquals(
            listOf(PlaceStatus.PENDING, PlaceStatus.RESOLVED, PlaceStatus.FAILED, PlaceStatus.UNAVAILABLE),
            PlaceStatus.entries,
        )
    }
}
