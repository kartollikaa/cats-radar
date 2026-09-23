package dev.catsradar.domain.usecase

import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.PlaceCell
import dev.catsradar.domain.model.PlaceStatus
import dev.catsradar.domain.region.RegionKey
import dev.catsradar.domain.testing.FakeEncounterRepository
import dev.catsradar.domain.testing.FakePlaceCellRepository
import dev.catsradar.domain.testing.areaOf
import dev.catsradar.domain.testing.encounterFixture
import dev.catsradar.domain.testing.locatedFixture
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class ObserveRegionTest {

    private val named = locatedFixture("named", BASE, 41.390, 2.170)
    private val pending = locatedFixture("pending", BASE, 41.600, 2.290)
    private val nowhere = encounterFixture("nowhere", BASE)

    private suspend fun view(parent: RegionKey?): RegionView {
        val encounters = FakeEncounterRepository()
        listOf(named, pending, nowhere).forEach { encounters.insert(it) }
        val cells = FakePlaceCellRepository(listOf(namedCell(named), pendingCell(pending)))
        return ObserveRegion(encounters, cells)(parent).first()
    }

    @Test
    fun `with no parent it opens the countries, then the two pseudo-nodes`() = runTest {
        val view = view(parent = null)

        assertEquals(
            listOf(RegionKey.Country("ES"), RegionKey.Unresolved, RegionKey.NoLocation),
            view.children.map { it.key },
        )
        assertEquals(emptyList(), view.encounters)
    }

    @Test
    fun `a country opens its cities`() = runTest {
        val view = view(RegionKey.Country("ES"))

        assertEquals(listOf(RegionKey.City("ES", "Barcelona")), view.children.map { it.key })
        assertEquals(emptyList(), view.encounters)
    }

    @Test
    fun `a city opens its areas`() = runTest {
        val view = view(RegionKey.City("ES", "Barcelona"))

        assertEquals(listOf(areaOf(named)), view.children.map { it.key })
        assertEquals(emptyList(), view.encounters)
    }

    @Test
    fun `Not named yet opens its areas`() = runTest {
        val view = view(RegionKey.Unresolved)

        assertEquals(listOf(areaOf(pending)), view.children.map { it.key })
        assertEquals(emptyList(), view.encounters)
    }

    @Test
    fun `an area opens its cats and no more rows`() = runTest {
        val view = view(areaOf(named))

        assertEquals(emptyList(), view.children)
        assertEquals(listOf(named), view.encounters)
    }

    @Test
    fun `No location opens its cats and no more rows`() = runTest {
        val view = view(RegionKey.NoLocation)

        assertEquals(emptyList(), view.children)
        assertEquals(listOf(nowhere), view.encounters)
    }

    private fun namedCell(encounter: Encounter) = PlaceCell(
        cellId = encounter.placeCellId!!,
        centerLat = encounter.lat!!,
        centerLon = encounter.lon!!,
        countryCode = "ES",
        countryName = "Spain",
        adminArea = null,
        locality = "Barcelona",
        subLocality = null,
        status = PlaceStatus.RESOLVED,
        attempts = 1,
        lastAttemptAt = BASE,
        resolvedAt = BASE,
    )

    private fun pendingCell(encounter: Encounter) = namedCell(encounter)
        .copy(countryCode = null, countryName = null, locality = null, status = PlaceStatus.PENDING, resolvedAt = null)

    private companion object {
        val BASE = Instant.parse("2026-09-22T08:00:00Z")
    }
}
