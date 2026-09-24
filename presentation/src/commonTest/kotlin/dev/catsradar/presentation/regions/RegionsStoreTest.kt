package dev.catsradar.presentation.regions

import app.cash.turbine.test
import dev.catsradar.domain.Tuning
import dev.catsradar.domain.geo.Geohash
import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.LocationSource
import dev.catsradar.domain.model.PlaceCell
import dev.catsradar.domain.model.PlaceStatus
import dev.catsradar.domain.region.RegionKey
import dev.catsradar.domain.usecase.ObserveRegion
import dev.catsradar.presentation.counter.FakeClock
import dev.catsradar.presentation.counter.FakeEncounterRepository
import dev.catsradar.presentation.counter.FakePlaceCellRepository
import dev.catsradar.presentation.encounters.EncounterListItem
import dev.catsradar.presentation.encounters.FakeDateTimeFormatter
import dev.catsradar.presentation.encounters.FakePhotoStorage
import dev.catsradar.presentation.encounters.encounterFixture
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class RegionsStoreTest {

    private val mainDispatcher = StandardTestDispatcher()
    private val encounters = FakeEncounterRepository()
    private val cells = FakePlaceCellRepository()
    private var next = 0

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newStore(parent: RegionKey?) = RegionsStore(
        parent = parent,
        observeRegion = ObserveRegion(encounters, cells),
        stateMapper = RegionsStateMapper(FakeDateTimeFormatter(), FakePhotoStorage()),
        clock = FakeClock(NOW),
        timeZone = TimeZone.UTC,
    )

    private suspend fun logCat(lat: Double, lon: Double, countryCode: String, city: String): Encounter {
        val geohash = Geohash.encode(lat, lon, Tuning.GEOHASH_PRECISION)
        val encounter = encounterFixture("e${next++}", NOW, locationSource = LocationSource.CURRENT_FIX).copy(
            lat = lat,
            lon = lon,
            geohash = geohash,
            placeCellId = Geohash.prefix(geohash, Tuning.PLACE_CELL_PRECISION),
        )
        cells.upsert(namedCell(encounter, countryCode, city))
        encounters.insert(encounter)
        return encounter
    }

    @Test
    fun `a country's store shows that country's cities`() = runTest(mainDispatcher) {
        logCat(41.390, 2.170, "ES", "Barcelona")
        logCat(41.440, 2.190, "ES", "Barcelona")
        logCat(41.980, 2.820, "ES", "Girona")
        logCat(48.850, 2.350, "FR", "Paris")
        val store = newStore(RegionKey.Country("ES"))

        runCurrent()

        assertEquals(
            RegionsState.Loaded(
                rows = persistentListOf(
                    RegionRowState(RegionRowKey.City("ES", "Barcelona"), RegionRowLabel.Named("Barcelona"), "2"),
                    RegionRowState(RegionRowKey.City("ES", "Girona"), RegionRowLabel.Named("Girona"), "1"),
                ),
            ),
            store.state.value,
        )
    }

    @Test
    fun `a level reads as loading, never as empty, until its places arrive`() = runTest(mainDispatcher) {
        logCat(41.390, 2.170, "ES", "Barcelona")
        val store = newStore(RegionKey.Country("ES"))

        store.state.test {
            assertEquals(RegionsState.Loading, awaitItem())
            assertEquals(
                RegionsState.Loaded(
                    rows = persistentListOf(
                        RegionRowState(RegionRowKey.City("ES", "Barcelona"), RegionRowLabel.Named("Barcelona"), "1"),
                    ),
                ),
                awaitItem(),
            )
        }
    }

    @Test
    fun `a cat logged while the store is open moves its city's count`() = runTest(mainDispatcher) {
        logCat(41.390, 2.170, "ES", "Barcelona")
        val store = newStore(RegionKey.Country("ES"))
        runCurrent()

        logCat(41.440, 2.190, "ES", "Barcelona")
        runCurrent()

        assertEquals(
            RegionsState.Loaded(
                rows = persistentListOf(
                    RegionRowState(RegionRowKey.City("ES", "Barcelona"), RegionRowLabel.Named("Barcelona"), "2"),
                ),
            ),
            store.state.value,
        )
    }

    @Test
    fun `the top level with no cat logged reads as having no places yet`() = runTest(mainDispatcher) {
        val store = newStore(parent = null)

        runCurrent()

        assertEquals(RegionsState.Empty(RegionsEmptyLabel.NO_PLACES_YET), store.state.value)
    }

    @Test
    fun `a country whose only cat is removed while it is open reads as having no places here`() =
        runTest(mainDispatcher) {
            val cat = logCat(41.390, 2.170, "ES", "Barcelona")
            val store = newStore(RegionKey.Country("ES"))
            runCurrent()
            assertEquals(listOf("1"), assertIs<RegionsState.Loaded>(store.state.value).rows.map { it.countLabel })

            encounters.softDelete(cat.id, NOW)
            runCurrent()

            assertEquals(RegionsState.Empty(RegionsEmptyLabel.NO_PLACES_HERE), store.state.value)
        }

    @Test
    fun `an area whose only cat is removed while it is open reads as having no cats`() = runTest(mainDispatcher) {
        val cat = logCat(41.390, 2.170, "ES", "Barcelona")
        val areaHash = Geohash.prefix(cat.geohash!!, Tuning.AREA_PRECISION)
        val store = newStore(RegionKey.Area(areaHash, RegionKey.City("ES", "Barcelona")))
        runCurrent()
        assertEquals(listOf(cat.id), assertIs<RegionsState.Loaded>(store.state.value).encounterIds())

        encounters.softDelete(cat.id, NOW)
        runCurrent()

        assertEquals(RegionsState.Empty(RegionsEmptyLabel.NO_CATS_HERE), store.state.value)
    }

    private fun namedCell(encounter: Encounter, countryCode: String, city: String) = PlaceCell(
        cellId = encounter.placeCellId!!,
        centerLat = encounter.lat!!,
        centerLon = encounter.lon!!,
        countryCode = countryCode,
        countryName = countryCode,
        adminArea = null,
        locality = city,
        subLocality = null,
        status = PlaceStatus.RESOLVED,
        attempts = 1,
        lastAttemptAt = NOW,
        resolvedAt = NOW,
    )

    private fun RegionsState.Loaded.encounterIds() =
        encounters.filterIsInstance<EncounterListItem.Row>().map { it.id }

    private companion object {
        val NOW = Instant.parse("2026-09-22T10:00:00Z")
    }
}
