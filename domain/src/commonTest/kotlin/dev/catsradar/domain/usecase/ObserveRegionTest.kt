package dev.catsradar.domain.usecase

import dev.catsradar.domain.model.PlaceStatus
import dev.catsradar.domain.region.RegionKey
import dev.catsradar.domain.testing.FakeEncounterRepository
import dev.catsradar.domain.testing.FakePlaceCellRepository
import dev.catsradar.domain.testing.areaOf
import dev.catsradar.domain.testing.encounterFixture
import dev.catsradar.domain.testing.locatedFixture
import dev.catsradar.domain.testing.placeCellFixture
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant

class ObserveRegionTest {

    private val barcelona = locatedFixture("barcelona", BASE, 41.390, 2.170)
    private val girona = locatedFixture("girona", BASE, 41.980, 2.820)
    private val paris = locatedFixture("paris", BASE, 48.850, 2.350)
    private val remote = locatedFixture("remote", BASE, 42.100, 1.400)
    private val pending = locatedFixture("pending", BASE, 41.600, 2.290)
    private val nowhere = encounterFixture("nowhere", BASE)

    private suspend fun view(parent: RegionKey?): RegionView {
        val encounters = FakeEncounterRepository()
        listOf(barcelona, girona, paris, remote, pending, nowhere).forEach { encounters.insert(it) }
        val cells = FakePlaceCellRepository(
            listOf(
                placeCellFixture(barcelona),
                placeCellFixture(girona, locality = "Girona"),
                placeCellFixture(paris, "FR", "France", "Paris"),
                placeCellFixture(remote, locality = null),
                placeCellFixture(pending, null, null, locality = null, status = PlaceStatus.PENDING),
            ),
        )
        return ObserveRegion(encounters, cells)(parent).first()
    }

    @Test
    fun `with no parent it opens the countries, then the two pseudo-nodes`() = runTest {
        val view = view(parent = null)

        assertEquals(
            listOf(RegionKey.Country("ES"), RegionKey.Country("FR"), RegionKey.Unresolved, RegionKey.NoLocation),
            view.placeKeys(),
        )
    }

    @Test
    fun `a country opens its own cities`() = runTest {
        val view = view(RegionKey.Country("ES"))

        assertEquals(
            listOf(RegionKey.City("ES", "Barcelona"), RegionKey.City("ES", "Girona"), RegionKey.NoCity("ES")),
            view.placeKeys(),
        )
    }

    @Test
    fun `a city opens its own areas`() = runTest {
        val view = view(RegionKey.City("ES", "Barcelona"))

        assertEquals(listOf(areaOf(barcelona, RegionKey.City("ES", "Barcelona"))), view.placeKeys())
    }

    @Test
    fun `No city opens its areas`() = runTest {
        val view = view(RegionKey.NoCity("ES"))

        assertEquals(listOf(areaOf(remote, RegionKey.NoCity("ES"))), view.placeKeys())
    }

    @Test
    fun `Not named yet opens its areas`() = runTest {
        val view = view(RegionKey.Unresolved)

        assertEquals(listOf(areaOf(pending, RegionKey.Unresolved)), view.placeKeys())
    }

    @Test
    fun `an area opens its cats and no more rows`() = runTest {
        val view = view(areaOf(barcelona, RegionKey.City("ES", "Barcelona")))

        assertEquals(listOf(barcelona), assertIs<RegionView.Cats>(view).encounters)
    }

    @Test
    fun `No location opens its cats and no more rows`() = runTest {
        val view = view(RegionKey.NoLocation)

        assertEquals(listOf(nowhere), assertIs<RegionView.Cats>(view).encounters)
    }

    @Test
    fun `the top level has no node of its own`() = runTest {
        assertEquals(null, view(parent = null).self)
    }

    @Test
    fun `every level below the top carries its node exactly as the level above lists it`() = runTest {
        val spain = RegionKey.Country("ES")
        val barcelonaCity = RegionKey.City("ES", "Barcelona")
        val levels = listOf(
            null to spain,
            null to RegionKey.Unresolved,
            null to RegionKey.NoLocation,
            spain to barcelonaCity,
            spain to RegionKey.NoCity("ES"),
            barcelonaCity to areaOf(barcelona, barcelonaCity),
            RegionKey.Unresolved to areaOf(pending, RegionKey.Unresolved),
        )

        levels.forEach { (above, level) ->
            val listed = assertIs<RegionView.Places>(view(above)).children.single { it.key == level }
            assertEquals(listed, view(level).self, "$level")
        }
    }

    private fun RegionView.placeKeys() = assertIs<RegionView.Places>(this).children.map { it.key }

    private companion object {
        val BASE = Instant.parse("2026-09-22T08:00:00Z")
    }
}
