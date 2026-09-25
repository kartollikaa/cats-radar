package dev.catsradar.domain.usecase

import app.cash.turbine.test
import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.PlaceCell
import dev.catsradar.domain.model.PlaceStatus
import dev.catsradar.domain.region.EncounterPlace
import dev.catsradar.domain.testing.FakePlaceCellRepository
import dev.catsradar.domain.testing.encounterFixture
import dev.catsradar.domain.testing.locatedFixture
import dev.catsradar.domain.testing.placeCellFixture
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class ObserveEncounterPlaceTest {

    private val cat = locatedFixture("cat", BASE, 41.390, 2.170)

    private suspend fun placeOf(encounter: Encounter?, vararg cells: PlaceCell): EncounterPlace? =
        ObserveEncounterPlace(FakePlaceCellRepository(cells.toList()))(encounter).first()

    @Test
    fun `a located cat in a named cell is in that cell's city and country`() = runTest {
        assertEquals(EncounterPlace("ES", "Spain", "Barcelona"), placeOf(cat, placeCellFixture(cat)))
    }

    @Test
    fun `a cell with no locality falls back to its admin area, and one with no country name to its code`() =
        runTest {
            val rural = placeCellFixture(cat, countryName = null, locality = null, adminArea = "Catalonia")

            assertEquals(EncounterPlace("ES", "ES", "Catalonia"), placeOf(cat, rural))
        }

    @Test
    fun `a cell that names a country but no city gives the country alone`() = runTest {
        assertEquals(
            EncounterPlace("ES", "Spain", city = null),
            placeOf(cat, placeCellFixture(cat, locality = null, adminArea = null)),
        )
    }

    @Test
    fun `a cat with no location, or whose cell is not named yet, is in no place`() = runTest {
        val nowhere = encounterFixture("nowhere", BASE)
        val pending = placeCellFixture(cat, null, null, locality = null, status = PlaceStatus.PENDING)

        assertNull(placeOf(nowhere))
        assertNull(placeOf(cat, pending))
        assertNull(placeOf(cat))
    }

    @Test
    fun `no cat, or a cat whose coordinates were cleared, is in no place even with its cell named`() = runTest {
        val cleared = cat.copy(lat = null, lon = null)

        assertNull(placeOf(null, placeCellFixture(cat)))
        assertNull(placeOf(cleared, placeCellFixture(cat)))
    }

    @Test
    fun `the place appears once the cat's cell is named`() = runTest {
        val unnamed = placeCellFixture(cat, null, null, null, status = PlaceStatus.PENDING)
        val cells = FakePlaceCellRepository(listOf(unnamed))

        ObserveEncounterPlace(cells)(cat).test {
            assertNull(awaitItem())

            cells.upsert(placeCellFixture(cat))

            assertEquals(EncounterPlace("ES", "Spain", "Barcelona"), awaitItem())
        }
    }

    private companion object {
        val BASE = Instant.parse("2026-09-22T08:00:00Z")
    }
}
