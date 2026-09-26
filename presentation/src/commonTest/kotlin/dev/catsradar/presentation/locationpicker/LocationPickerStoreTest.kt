package dev.catsradar.presentation.locationpicker

import app.cash.turbine.test
import dev.catsradar.domain.geo.GeoPoint
import dev.catsradar.domain.location.LocationFix
import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.LocationSource
import dev.catsradar.domain.model.LocationStamp
import dev.catsradar.domain.platform.LocationProvider
import dev.catsradar.domain.usecase.LocatePhone
import dev.catsradar.domain.usecase.ObserveEncounter
import dev.catsradar.domain.usecase.SetLocationByHand
import dev.catsradar.domain.usecase.WhereToLook
import dev.catsradar.presentation.NoAnalytics
import dev.catsradar.presentation.counter.FakeClock
import dev.catsradar.presentation.counter.FakeEncounterRepository
import dev.catsradar.presentation.counter.FakePlaceCellRepository
import dev.catsradar.presentation.encounters.encounterFixture
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class LocationPickerStoreTest {

    private val mainDispatcher = StandardTestDispatcher()
    private val repository = FakeEncounterRepository()
    private val phone = ControlledLocationProvider()
    private val mapper = LocationPickerStateMapper()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(mainDispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `the picker opens on a street around the located cat logged closest in time`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED))
        repository.insert(encounterFixture("near", OCCURRED + 5.minutes).located(NEAR))
        val store = newStore()
        runCurrent()

        assertEquals(mapper.picking(NEAR), store.state.value)
    }

    @Test
    fun `with nowhere to look the picker opens on the whole world`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED))
        val store = newStore()
        runCurrent()

        assertEquals(LocationPickerState.Picking(start = null), store.state.value)
    }

    @Test
    fun `opening the picker asks for no permission`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED))
        val store = newStore()

        store.effects.test {
            runCurrent()
            expectNoEvents()
        }
    }

    @Test
    fun `where am I asks for the permission and shows it is looking`() = runTest(mainDispatcher) {
        val store = picking()

        store.effects.test {
            store.dispatch(LocationPickerIntent.WhereAmIClicked)
            runCurrent()
            assertEquals(LocationPickerEffect.RequestLocationPermission, awaitItem())
        }
        assertEquals(LocationPickerState.Picking(start = null, locating = true), store.state.value)
    }

    @Test
    fun `granted, the map is asked once to move to a street around the phone`() = runTest(mainDispatcher) {
        phone.currentFix = fixAt(PHONE)
        val store = picking()
        store.dispatch(LocationPickerIntent.WhereAmIClicked)
        runCurrent()

        store.dispatch(LocationPickerIntent.LocationPermissionResult(granted = true))
        runCurrent()

        assertEquals(LocationPickerState.Picking(start = null, moveTo = mapper.areaAround(PHONE)), store.state.value)

        store.dispatch(LocationPickerIntent.MoveReached)
        runCurrent()

        assertEquals(LocationPickerState.Picking(start = null), store.state.value)
    }

    @Test
    fun `refused, the position is unknown and the map moves nowhere`() = runTest(mainDispatcher) {
        phone.currentFix = fixAt(PHONE)
        val store = picking()

        store.effects.test {
            store.dispatch(LocationPickerIntent.WhereAmIClicked)
            runCurrent()
            assertEquals(LocationPickerEffect.RequestLocationPermission, awaitItem())
            store.dispatch(LocationPickerIntent.LocationPermissionResult(granted = false))
            runCurrent()
            assertEquals(LocationPickerEffect.PositionUnknown, awaitItem())
        }
        assertEquals(LocationPickerState.Picking(start = null), store.state.value)
        assertEquals(0, phone.currentFixCalls)
    }

    @Test
    fun `granted with no position found, the position is unknown and the map moves nowhere`() =
        runTest(mainDispatcher) {
            val store = picking()

            store.effects.test {
                store.dispatch(LocationPickerIntent.WhereAmIClicked)
                runCurrent()
                assertEquals(LocationPickerEffect.RequestLocationPermission, awaitItem())
                store.dispatch(LocationPickerIntent.LocationPermissionResult(granted = true))
                runCurrent()
                assertEquals(LocationPickerEffect.PositionUnknown, awaitItem())
            }
            assertEquals(LocationPickerState.Picking(start = null), store.state.value)
        }

    @Test
    fun `a second where am I while the phone is being looked for starts nothing`() = runTest(mainDispatcher) {
        phone.answer = CompletableDeferred()
        val store = picking()

        store.effects.test {
            store.dispatch(LocationPickerIntent.WhereAmIClicked)
            runCurrent()
            assertEquals(LocationPickerEffect.RequestLocationPermission, awaitItem())
            store.dispatch(LocationPickerIntent.LocationPermissionResult(granted = true))
            runCurrent()
            store.dispatch(LocationPickerIntent.WhereAmIClicked)
            runCurrent()
            expectNoEvents()
        }
        assertEquals(1, phone.currentFixCalls)
    }

    @Test
    fun `save gives the cat the point under the pin, set by hand, and closes`() = runTest(mainDispatcher) {
        val store = picking()

        store.effects.test {
            store.dispatch(LocationPickerIntent.SaveClicked(PICKED.lat, PICKED.lon))
            runCurrent()
            assertEquals(LocationPickerEffect.Close, awaitItem())
            expectNoEvents()
        }
        val cat = repository.encounters().single { it.id == ID }
        assertEquals(LocationSource.MANUAL, cat.locationSource)
        assertEquals(PICKED, GeoPoint(cat.lat!!, cat.lon!!))
    }

    @Test
    fun `a second save while the first is being written writes nothing more`() = runTest(mainDispatcher) {
        repository.attachLocationGate = CompletableDeferred()
        val store = picking()
        store.dispatch(LocationPickerIntent.SaveClicked(PICKED.lat, PICKED.lon))
        runCurrent()
        assertEquals(LocationPickerState.Picking(start = null, saving = true), store.state.value)

        store.dispatch(LocationPickerIntent.SaveClicked(PICKED.lat, PICKED.lon))
        runCurrent()
        repository.attachLocationGate?.complete(Unit)
        runCurrent()

        assertEquals(1, repository.attachLocationCalls.size)
    }

    @Test
    fun `a cat located some other way closes the picker once`() = runTest(mainDispatcher) {
        val store = picking()

        store.effects.test {
            repository.attachLocation(ID, stampAt(NEAR, LocationSource.CURRENT_FIX))
            runCurrent()
            assertEquals(LocationPickerEffect.Close, awaitItem())
            store.dispatch(LocationPickerIntent.BackClicked)
            runCurrent()
            expectNoEvents()
        }
    }

    @Test
    fun `a cat deleted elsewhere closes the picker once`() = runTest(mainDispatcher) {
        val store = picking()

        store.effects.test {
            repository.softDelete(ID, OCCURRED + 1.minutes)
            runCurrent()
            assertEquals(LocationPickerEffect.Close, awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun `a save that fails says so and keeps the picker open with save available again`() = runTest(mainDispatcher) {
        repository.attachLocationShouldThrow = IllegalStateException("disk full")
        val store = picking()

        store.effects.test {
            store.dispatch(LocationPickerIntent.SaveClicked(PICKED.lat, PICKED.lon))
            runCurrent()
            assertEquals(LocationPickerEffect.NotSaved, awaitItem())
            expectNoEvents()
        }
        assertEquals(LocationPickerState.Picking(start = null), store.state.value)
    }

    @Test
    fun `back leaves once and writes nothing`() = runTest(mainDispatcher) {
        val store = picking()

        store.effects.test {
            store.dispatch(LocationPickerIntent.BackClicked)
            store.dispatch(LocationPickerIntent.BackClicked)
            runCurrent()
            assertEquals(LocationPickerEffect.Close, awaitItem())
            expectNoEvents()
        }
        assertEquals(emptyList(), repository.attachLocationCalls)
    }

    private suspend fun TestScope.picking(): LocationPickerStore {
        repository.insert(encounterFixture(ID, OCCURRED))
        val store = newStore()
        runCurrent()
        assertIs<LocationPickerState.Picking>(store.state.value)
        return store
    }

    private fun newStore(): LocationPickerStore {
        val clock = FakeClock(NOW)
        return LocationPickerStore(
            encounterId = ID,
            observeEncounter = ObserveEncounter(repository),
            whereToLook = WhereToLook(repository, phone),
            locatePhone = LocatePhone(phone, clock),
            setLocationByHand = SetLocationByHand(repository, FakePlaceCellRepository(), clock, NoAnalytics),
            stateMapper = mapper,
        )
    }

    private fun Encounter.located(point: GeoPoint) =
        copy(lat = point.lat, lon = point.lon, locationSource = LocationSource.CURRENT_FIX)

    private fun fixAt(point: GeoPoint) = LocationFix(point.lat, point.lon, accuracyMeters = 5f, fixedAt = NOW)

    private fun stampAt(point: GeoPoint, source: LocationSource) = LocationStamp(
        lat = point.lat,
        lon = point.lon,
        accuracyMeters = 5f,
        locationSource = source,
        locationFixedAt = NOW,
        geohash = "sp3e3qe7",
        placeCellId = "sp3e3q",
        updatedAt = NOW,
    )

    private companion object {
        const val ID = "cat-1"
        val OCCURRED: Instant = Instant.parse("2026-09-20T08:30:00Z")
        val NOW: Instant = Instant.parse("2026-09-26T10:00:00Z")
        val NEAR = GeoPoint(lat = 41.39864, lon = 2.17842)
        val PHONE = GeoPoint(lat = 55.7558, lon = 37.6173)
        val PICKED = GeoPoint(lat = 41.40338, lon = 2.17403)
    }
}

internal class ControlledLocationProvider : LocationProvider {
    var currentFix: LocationFix? = null
    var lastKnownFix: LocationFix? = null

    /** When set, a fresh fix is held back until it completes. */
    var answer: CompletableDeferred<Unit>? = null
    var currentFixCalls = 0
        private set

    override suspend fun getCurrentFix(timeout: Duration): LocationFix? {
        currentFixCalls++
        answer?.await()
        return currentFix
    }

    override suspend fun lastKnown(): LocationFix? = lastKnownFix
    override fun trackFixes(): Flow<LocationFix> = emptyFlow()
}
