package dev.catsradar.domain.region

import dev.catsradar.domain.testing.locatedFixture
import dev.catsradar.domain.testing.placeCellFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class EncounterPlaceTest {

    @Test
    fun `a named cell other than the cat's own gives the cat no place`() {
        val cat = locatedFixture("cat", BASE, 41.390, 2.170)
        val elsewhere = locatedFixture("elsewhere", BASE, 48.857, 2.352)

        assertEquals(EncounterPlace("ES", "Spain", "Barcelona"), cat.placeIn(placeCellFixture(cat)))
        assertNull(cat.placeIn(placeCellFixture(elsewhere)))
    }

    private companion object {
        val BASE = Instant.parse("2026-09-22T08:00:00Z")
    }
}
