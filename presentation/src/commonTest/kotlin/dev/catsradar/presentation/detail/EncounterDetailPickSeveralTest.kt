package dev.catsradar.presentation.detail

import androidx.lifecycle.ViewModelStore
import app.cash.turbine.test
import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.EncounterPhoto
import dev.catsradar.domain.platform.GalleryItemLocator
import dev.catsradar.domain.usecase.AttachPhoto
import dev.catsradar.domain.usecase.DeleteEncounter
import dev.catsradar.domain.usecase.ObserveEncounter
import dev.catsradar.domain.usecase.ObserveEncounterPlace
import dev.catsradar.domain.usecase.SetCoat
import dev.catsradar.domain.usecase.UndoDelete
import dev.catsradar.presentation.NoAnalytics
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
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
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class EncounterDetailPickSeveralTest {

    private val mainDispatcher = StandardTestDispatcher()
    private val repository = FakeEncounterRepository()
    private val clock = FakeClock(NOW)
    private val resizer = FakeImageResizer()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(mainDispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `every picked photo lands after the cat's own, in the order picked`() = runTest(mainDispatcher) {
        repository.insert(catWithPhotosOf("own"))
        val store = newStore()
        runCurrent()

        store.dispatch(EncounterDetailIntent.PhotosPicked(listOf(FIRST, SECOND, THIRD)))
        runCurrent()

        assertEquals(listOf("own", FIRST, SECOND, THIRD), photoSources())
        val state = assertIs<EncounterDetailState.Loaded>(store.state.value)
        assertEquals(4, state.photos.size)
        assertEquals(AddPhoto.READY, state.addPhoto)
        assertEquals(null, state.attachProgress)
    }

    @Test
    fun `a pick of several shows how many are through as it goes`() = runTest(mainDispatcher) {
        repository.insert(catWithPhotosOf())
        resizer.storeDelay = 1.seconds
        val store = newStore()
        runCurrent()

        store.dispatch(EncounterDetailIntent.PhotosPicked(listOf(FIRST, SECOND, THIRD)))
        runCurrent()
        assertEquals(AttachProgress(done = 0, total = 3), loaded(store).attachProgress)
        assertEquals(AddPhoto.ATTACHING, loaded(store).addPhoto)

        advanceTimeBy(1.seconds)
        runCurrent()
        assertEquals(AttachProgress(done = 1, total = 3), loaded(store).attachProgress)
        assertEquals(AddPhoto.ATTACHING, loaded(store).addPhoto)

        advanceTimeBy(1.seconds)
        runCurrent()
        assertEquals(AttachProgress(done = 2, total = 3), loaded(store).attachProgress)

        advanceTimeBy(1.seconds)
        runCurrent()
        assertEquals(AddPhoto.READY, loaded(store).addPhoto)
        assertEquals(null, loaded(store).attachProgress)
    }

    @Test
    fun `a single picked photo or a camera photo shows the attempt without a count`() = runTest(mainDispatcher) {
        repository.insert(catWithPhotosOf())
        resizer.storeDelay = 1.seconds
        val store = newStore()
        runCurrent()

        store.dispatch(EncounterDetailIntent.PhotosPicked(listOf(FIRST)))
        runCurrent()
        assertEquals(AddPhoto.ATTACHING, loaded(store).addPhoto)
        assertEquals(null, loaded(store).attachProgress)
        advanceTimeBy(2.seconds)
        runCurrent()

        store.dispatch(EncounterDetailIntent.PhotoTaken(CAPTURE))
        runCurrent()
        assertEquals(AddPhoto.ATTACHING, loaded(store).addPhoto)
        assertEquals(null, loaded(store).attachProgress)
    }

    @Test
    fun `a tap on either button mid-pick opens nothing`() = runTest(mainDispatcher) {
        repository.insert(catWithPhotosOf())
        resizer.storeDelay = 1.seconds
        val store = newStore()
        runCurrent()
        store.dispatch(EncounterDetailIntent.PhotosPicked(listOf(FIRST, SECOND, THIRD)))
        advanceTimeBy(1.seconds)
        runCurrent()

        store.effects.test {
            store.dispatch(EncounterDetailIntent.TakePhotoClicked)
            store.dispatch(EncounterDetailIntent.PickPhotoClicked)
            runCurrent()
            expectNoEvents()
        }
    }

    @Test
    fun `the progress stays until the cat carries every photo the pick attached`() = runTest(mainDispatcher) {
        repository.insert(catWithPhotosOf())
        resizer.storeDelay = 1.seconds
        val store = newStore()
        runCurrent()
        repository.observeDelay = 5.seconds

        store.dispatch(EncounterDetailIntent.PhotosPicked(listOf(FIRST, SECOND)))
        advanceTimeBy(3.seconds)
        runCurrent()
        assertEquals(0, loaded(store).photos.size)
        assertEquals(AttachProgress(done = 2, total = 2), loaded(store).attachProgress)

        advanceTimeBy(4.seconds)
        runCurrent()
        assertEquals(1, loaded(store).photos.size)
        assertEquals(AddPhoto.ATTACHING, loaded(store).addPhoto)
        assertEquals(AttachProgress(done = 2, total = 2), loaded(store).attachProgress)

        advanceTimeBy(5.seconds)
        runCurrent()
        assertEquals(2, loaded(store).photos.size)
        assertEquals(AddPhoto.READY, loaded(store).addPhoto)
    }

    @Test
    fun `one photo of a pick not attached says so once, and the others land`() = runTest(mainDispatcher) {
        repository.insert(catWithPhotosOf())
        resizer.unreadable = setOf(SECOND)
        val store = newStore()
        runCurrent()

        store.effects.test {
            store.dispatch(EncounterDetailIntent.PhotosPicked(listOf(FIRST, SECOND, THIRD)))
            runCurrent()
            assertEquals(EncounterDetailEffect.PhotoNotAttached, awaitItem())
            expectNoEvents()
        }
        assertEquals(listOf(FIRST, THIRD), photoSources())
        assertEquals(AddPhoto.READY, loaded(store).addPhoto)
    }

    @Test
    fun `several photos not attached say how many in one message`() = runTest(mainDispatcher) {
        repository.insert(catWithPhotosOf())
        resizer.unreadable = setOf(FIRST, THIRD)
        val store = newStore()
        runCurrent()

        store.effects.test {
            store.dispatch(EncounterDetailIntent.PhotosPicked(listOf(FIRST, SECOND, THIRD)))
            runCurrent()
            assertEquals(EncounterDetailEffect.PhotosNotAttached(count = 2), awaitItem())
            expectNoEvents()
        }
        assertEquals(listOf(SECOND), photoSources())
    }

    @Test
    fun `a pick of several the cat already has says so once and changes nothing`() = runTest(mainDispatcher) {
        val cat = catWithPhotosOf(FIRST, SECOND)
        repository.insert(cat)
        val store = newStore()
        runCurrent()

        store.effects.test {
            store.dispatch(EncounterDetailIntent.PhotosPicked(listOf(SECOND, FIRST)))
            runCurrent()
            assertEquals(EncounterDetailEffect.PhotosAlreadyThere, awaitItem())
            expectNoEvents()
        }
        assertEquals(listOf(cat), repository.encounters())
    }

    @Test
    fun `a duplicate among photos that were added is skipped without a word`() = runTest(mainDispatcher) {
        repository.insert(catWithPhotosOf(FIRST))
        val store = newStore()
        runCurrent()

        store.effects.test {
            store.dispatch(EncounterDetailIntent.PhotosPicked(listOf(FIRST, SECOND, SECOND)))
            runCurrent()
            expectNoEvents()
        }
        assertEquals(listOf(FIRST, SECOND), photoSources())
    }

    @Test
    fun `leaving mid-pick keeps the photos attached so far and attaches no more`() = runTest(mainDispatcher) {
        repository.insert(catWithPhotosOf())
        resizer.storeDelay = 1.seconds
        val store = newStore()
        runCurrent()
        store.dispatch(EncounterDetailIntent.PhotosPicked(listOf(FIRST, SECOND, THIRD)))
        runCurrent()
        advanceTimeBy(1.seconds)
        runCurrent()

        ViewModelStore().apply { put("detail", store) }.clear()
        advanceTimeBy(5.seconds)
        runCurrent()

        assertEquals(listOf(FIRST), photoSources())
    }

    @Test
    fun `the cat removed mid-pick is given no more photos and nothing is said`() = runTest(mainDispatcher) {
        repository.insert(catWithPhotosOf())
        resizer.storeDelay = 1.seconds
        val store = newStore()
        runCurrent()

        store.effects.test {
            store.dispatch(EncounterDetailIntent.PhotosPicked(listOf(FIRST, SECOND, THIRD)))
            runCurrent()
            repository.softDelete(ID, NOW)
            advanceTimeBy(5.seconds)
            runCurrent()
            expectNoEvents()
        }
        assertEquals(emptyList(), repository.encounters().single().photos)
        assertEquals(EncounterDetailState.Missing, store.state.value)
    }

    private fun loaded(store: EncounterDetailStore) = assertIs<EncounterDetailState.Loaded>(store.state.value)

    private fun photoSources(): List<String?> =
        repository.encounters().single { it.id == ID }.photos.map { it.sourceDigest }

    private fun catWithPhotosOf(vararg digests: String): Encounter = encounterFixture(ID, OCCURRED).copy(
        photos = digests.mapIndexed { index, digest ->
            EncounterPhoto(
                id = "own-$index",
                encounterId = ID,
                photoPath = "own-$index.jpg",
                thumbPath = null,
                galleryUri = null,
                sourceMediaUri = null,
                sourceDigest = digest,
                deviceId = "device-1",
                addedAt = OCCURRED,
                shotId = null,
            )
        },
    )

    private fun TestScope.newStore(): EncounterDetailStore = EncounterDetailStore(
        encounterId = ID,
        observeEncounter = ObserveEncounter(repository),
        observeEncounterPlace = ObserveEncounterPlace(FakePlaceCellRepository()),
        deleteEncounter = DeleteEncounter(repository, clock, analytics = NoAnalytics),
        undoDelete = UndoDelete(repository, analytics = NoAnalytics),
        setCoat = SetCoat(repository, clock, analytics = NoAnalytics),
        attachPhoto = AttachPhoto(
            encounterRepository = repository,
            settingsRepository = FakeSettingsRepository(),
            imageResizer = resizer,
            digest = FakeDigest { uri -> uri },
            gallerySaver = FakeGallerySaver(),
            galleryItemLocator = LocatesNothing,
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

    private companion object {
        const val ID = "cat-1"
        const val CAPTURE = "content://captures/1"
        const val FIRST = "content://picker/1"
        const val SECOND = "content://picker/2"
        const val THIRD = "content://picker/3"
        val NOW = Instant.parse("2026-09-22T12:00:00Z")
        val OCCURRED = Instant.parse("2026-09-22T10:00:00Z")
    }
}

private object LocatesNothing : GalleryItemLocator {
    override suspend fun locate(pickedUri: String): String? = null
}
