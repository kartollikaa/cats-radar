package dev.catsradar.domain.usecase

import dev.catsradar.domain.model.LocationSource
import dev.catsradar.domain.model.PlaceStatus
import dev.catsradar.domain.region.PlaceCells
import dev.catsradar.domain.testing.FakeEncounterRepository
import dev.catsradar.domain.testing.FakePlaceCellRepository
import dev.catsradar.domain.testing.encounterFixture
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class RepairPlaceCellsTest {

    private val at = Instant.parse("2026-09-21T10:00:00Z")
    private val encounters = FakeEncounterRepository()

    private fun located(id: String, geohash: String? = null, placeCellId: String? = null) =
        encounterFixture(id, at, LocationSource.CURRENT_FIX, lat = BARCELONA_LAT, lon = BARCELONA_LON)
            .copy(geohash = geohash, placeCellId = placeCellId)

    private val namedBarcelona = PlaceCells.untried(BARCELONA_CELL).copy(
        countryCode = "ES",
        countryName = "Spain",
        locality = "Barcelona",
        status = PlaceStatus.RESOLVED,
        attempts = 1,
    )

    private suspend fun repair(cells: FakePlaceCellRepository) = RepairPlaceCells(encounters, cells)()

    @Test
    fun `a located cat with no geohash gets the geohash and cell its coordinates imply`() = runTest {
        val cells = FakePlaceCellRepository()
        encounters.insert(located("cat"))

        repair(cells)

        val repaired = encounters.observeById("cat").first()!!
        assertEquals(BARCELONA_GEOHASH, repaired.geohash)
        assertEquals(BARCELONA_CELL, repaired.placeCellId)
        assertEquals(listOf(PlaceCells.untried(BARCELONA_CELL)), cells.observeAll().first())
    }

    @Test
    fun `the repair changes nothing about the cat but its geohash and cell`() = runTest {
        val cat = located("cat")
        encounters.insert(cat)

        repair(FakePlaceCellRepository())

        assertEquals(
            cat.copy(geohash = BARCELONA_GEOHASH, placeCellId = BARCELONA_CELL),
            encounters.observeById("cat").first(),
        )
    }

    @Test
    fun `a cat pointing at a cell that does not exist gets that cell created untried`() = runTest {
        val cells = FakePlaceCellRepository()
        encounters.insert(located("cat", geohash = BARCELONA_GEOHASH, placeCellId = BARCELONA_CELL))

        repair(cells)

        assertEquals(listOf(PlaceCells.untried(BARCELONA_CELL)), cells.observeAll().first())
        assertTrue(encounters.setPlaceCellCalls.isEmpty())
    }

    @Test
    fun `a geohash that disagrees with the coordinates is replaced by theirs`() = runTest {
        encounters.insert(located("cat", geohash = "u09tunqu", placeCellId = "u09tun"))

        repair(FakePlaceCellRepository(listOf(namedBarcelona)))

        val repaired = encounters.observeById("cat").first()!!
        assertEquals(BARCELONA_GEOHASH, repaired.geohash)
        assertEquals(BARCELONA_CELL, repaired.placeCellId)
    }

    @Test
    fun `a cell that already has a name keeps it`() = runTest {
        val cells = FakePlaceCellRepository(listOf(namedBarcelona))
        encounters.insert(located("cat"))

        repair(cells)

        assertEquals(BARCELONA_CELL, encounters.observeById("cat").first()!!.placeCellId)
        assertEquals(listOf(namedBarcelona), cells.observeAll().first())
        assertTrue(cells.upserted.isEmpty())
    }

    @Test
    fun `two cats in one new cell create it once`() = runTest {
        val cells = FakePlaceCellRepository()
        encounters.insert(located("first"))
        encounters.insert(located("second"))

        repair(cells)

        assertEquals(1, cells.upserted.size)
        assertEquals(BARCELONA_CELL, encounters.observeById("second").first()!!.placeCellId)
    }

    @Test
    fun `a cat with nothing to repair is not written`() = runTest {
        val cells = FakePlaceCellRepository(listOf(namedBarcelona))
        encounters.insert(located("whole", geohash = BARCELONA_GEOHASH, placeCellId = BARCELONA_CELL))
        encounters.insert(encounterFixture("unlocated", at))
        encounters.insert(encounterFixture("marked-none", at, LocationSource.NONE, BARCELONA_LAT, BARCELONA_LON))
        encounters.insert(encounterFixture("off-globe", at, LocationSource.CURRENT_FIX, lat = 91.0, lon = 2.0))
        encounters.insert(encounterFixture("half-a-point", at, LocationSource.EXIF, lat = BARCELONA_LAT))
        encounters.insert(located("deleted").copy(deletedAt = at))

        repair(cells)

        assertTrue(encounters.setPlaceCellCalls.isEmpty())
        assertTrue(cells.upserted.isEmpty())
    }

    private companion object {
        const val BARCELONA_LAT = 41.39864
        const val BARCELONA_LON = 2.17842
        const val BARCELONA_GEOHASH = "sp3e986k"
        const val BARCELONA_CELL = "sp3e98"
    }
}
