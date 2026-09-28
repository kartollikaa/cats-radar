package dev.catsradar.presentation.detail

import dev.catsradar.domain.model.CatCoat
import dev.catsradar.domain.platform.GalleryItemLocator
import dev.catsradar.domain.usecase.AttachPhoto
import dev.catsradar.domain.usecase.DeleteEncounter
import dev.catsradar.domain.usecase.ObserveEncounterNumber
import dev.catsradar.domain.usecase.ObserveEncounterPlace
import dev.catsradar.domain.usecase.ObserveEncounters
import dev.catsradar.domain.usecase.SetCoat
import dev.catsradar.domain.usecase.UndoDelete
import dev.catsradar.presentation.NoAnalytics
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.presentation.counter.FakeClock
import dev.catsradar.presentation.counter.FakeDeviceIdProvider
import dev.catsradar.presentation.counter.FakeDigest
import dev.catsradar.presentation.counter.FakeEncounterRepository
import dev.catsradar.presentation.counter.FakeGallerySaver
import dev.catsradar.presentation.counter.FakeIdGenerator
import dev.catsradar.presentation.counter.FakeImageResizer
import dev.catsradar.presentation.counter.FakePlaceCellRepository
import dev.catsradar.presentation.counter.FakeSettingsRepository
import dev.catsradar.presentation.encounters.FakeDateTimeFormatter
import dev.catsradar.presentation.encounters.FakePhotoStorage
import dev.catsradar.presentation.encounters.encounterFixture
import dev.catsradar.presentation.encounters.shotFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class EncounterDetailShotTest {

    private val mainDispatcher = StandardTestDispatcher()
    private val repository = FakeEncounterRepository()
    private val clock = FakeClock(NOW)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(mainDispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `opening a shot shows one page for all its cats`() = runTest(mainDispatcher) {
        val store = storeOnShot()

        val state = assertIs<EncounterDetailState.Loaded>(store.state.value)
        assertEquals(listOf(LATER, FIRST), state.pages.map { it.id })
        assertEquals(listOf(FIRST, SECOND, THIRD), store.shownPage().onThisPhoto.map { it.id })
        assertEquals(2, state.currentNumber)
    }

    @Test
    fun `tapping a cat of the photo shows it on the same page`() = runTest(mainDispatcher) {
        val store = storeOnShot()

        store.dispatch(EncounterDetailIntent.PhotoCatClicked(THIRD))
        runCurrent()

        val state = assertIs<EncounterDetailState.Loaded>(store.state.value)
        assertEquals(THIRD to 2, state.currentId to state.currentNumber)
        assertEquals(listOf(LATER, THIRD), state.pages.map { it.id })
        assertEquals(listOf(false, false, true), store.shownPage().onThisPhoto.map { it.onScreen })
    }

    @Test
    fun `the coat and delete act on the cat of the photo on screen`() = runTest(mainDispatcher) {
        val store = storeOnShot()
        store.dispatch(EncounterDetailIntent.PhotoCatClicked(SECOND))
        runCurrent()

        store.dispatch(EncounterDetailIntent.CoatPicked(store.shownPage().id, CoatOption.GINGER))
        runCurrent()
        store.dispatch(EncounterDetailIntent.DeleteClicked)
        runCurrent()

        val cats = repository.encounters().associateBy { it.id }
        assertEquals(CatCoat.GINGER, cats.getValue(SECOND).coat)
        assertEquals(listOf(SECOND), cats.values.filter { it.deletedAt != null }.map { it.id })
    }

    @Test
    fun `reopening on a cat of the photo shows that cat on the shot's page`() = runTest(mainDispatcher) {
        val store = storeOnShot(restoredId = THIRD)

        val state = assertIs<EncounterDetailState.Loaded>(store.state.value)
        assertEquals(listOf(LATER, THIRD), state.pages.map { it.id })
        assertEquals(THIRD to 2, state.currentId to state.currentNumber)
    }

    private suspend fun TestScope.storeOnShot(restoredId: String? = null): EncounterDetailStore {
        repository.insert(encounterFixture(LATER, OCCURRED + 5.minutes))
        shotFixture(FIRST, SECOND, THIRD, occurredAt = OCCURRED).forEach { repository.insert(it) }
        val store = EncounterDetailStore(
            openedId = FIRST,
            restoredId = restoredId,
            observeEncounters = ObserveEncounters(repository),
            observeEncounterPlace = ObserveEncounterPlace(FakePlaceCellRepository()),
            observeEncounterNumber = ObserveEncounterNumber(repository),
            deleteEncounter = DeleteEncounter(repository, clock, analytics = NoAnalytics),
            undoDelete = UndoDelete(repository, analytics = NoAnalytics),
            setCoat = SetCoat(repository, clock, analytics = NoAnalytics),
            attachPhoto = AttachPhoto(
                encounterRepository = repository,
                settingsRepository = FakeSettingsRepository(),
                imageResizer = FakeImageResizer(),
                digest = FakeDigest(),
                gallerySaver = FakeGallerySaver(),
                galleryItemLocator = ShotLocatesNoGalleryItem,
                photoStorage = FakePhotoStorage(),
                idGenerator = FakeIdGenerator(),
                deviceIdProvider = FakeDeviceIdProvider(),
                clock = clock,
                analytics = NoAnalytics,
            ),
            stateMapper = EncounterDetailStateMapper(FakeDateTimeFormatter(), FakePhotoStorage()),
            clock = clock,
            timeZone = TimeZone.UTC,
        )
        runCurrent()
        return store
    }

    private companion object {
        const val FIRST = "shot-1"
        const val SECOND = "shot-2"
        const val THIRD = "shot-3"
        const val LATER = "later"
        val NOW = Instant.parse("2026-09-22T12:00:00Z")
        val OCCURRED = Instant.parse("2026-09-22T10:00:00Z")
    }
}

private object ShotLocatesNoGalleryItem : GalleryItemLocator {
    override suspend fun locate(pickedUri: String): String? = null
}
