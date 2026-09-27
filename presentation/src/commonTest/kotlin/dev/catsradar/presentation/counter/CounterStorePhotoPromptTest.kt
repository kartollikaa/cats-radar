package dev.catsradar.presentation.counter

import app.cash.turbine.Event
import app.cash.turbine.test
import dev.catsradar.domain.Tuning
import dev.catsradar.domain.model.CatCoat
import dev.catsradar.domain.platform.StoredPhoto
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.presentation.encounters.FakePhotoStorage
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class CounterStorePhotoPromptTest {

    private val mainDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a logged camera photo asks for its coat`() = runTest(mainDispatcher) {
        val store = newCounterStore()

        store.dispatch(CounterIntent.PhotoCaptured(CAPTURE))
        runCurrent()

        assertEquals(
            CoatPromptState("id-1", "id-1", thumbPath = "/data/photos/cat_thumb.jpg"),
            store.state.value.coatPrompt,
        )
    }

    @Test
    fun `picking a coat sets it on the photographed cat and closes the prompt`() = runTest(mainDispatcher) {
        val repository = FakeEncounterRepository()
        repository.insert(externalEncounter(id = "earlier-cat"))
        val store = newCounterStore(encounterRepository = repository)
        store.dispatch(CounterIntent.PhotoCaptured(CAPTURE))
        runCurrent()

        store.dispatch(CounterIntent.CoatPrompt.Picked(CoatOption.BLACK))
        runCurrent()

        assertNull(store.state.value.coatPrompt)
        assertEquals(
            listOf("earlier-cat" to null, "id-1" to CatCoat.BLACK),
            repository.encounters().map { it.id to it.coat },
        )
    }

    @Test
    fun `dismissing the prompt leaves the coat unset`() = runTest(mainDispatcher) {
        val repository = FakeEncounterRepository()
        val store = newCounterStore(encounterRepository = repository)
        store.dispatch(CounterIntent.PhotoCaptured(CAPTURE))
        runCurrent()
        assertNotNull(store.state.value.coatPrompt)

        store.dispatch(CounterIntent.CoatPrompt.Dismissed)
        runCurrent()
        assertNull(store.state.value.coatPrompt)

        store.dispatch(CounterIntent.CoatPrompt.Picked(CoatOption.BLACK))
        runCurrent()
        assertNull(repository.encounters().single().coat)
    }

    @Test
    fun `the prompt closes before the coat is written`() = runTest(mainDispatcher) {
        val repository = FakeEncounterRepository()
        val store = newCounterStore(encounterRepository = repository)
        store.dispatch(CounterIntent.PhotoCaptured(CAPTURE))
        runCurrent()
        assertNotNull(store.state.value.coatPrompt)
        val write = CompletableDeferred<Unit>()
        repository.setCoatGate = write

        store.dispatch(CounterIntent.CoatPrompt.Picked(CoatOption.BLACK))
        runCurrent()
        assertNull(store.state.value.coatPrompt)
        assertNull(repository.encounters().single().coat)

        write.complete(Unit)
        runCurrent()
        assertEquals(CatCoat.BLACK, repository.encounters().single().coat)
    }

    @Test
    fun `no prompt without a logged camera photo`() = runTest(mainDispatcher) {
        val unreadable = FakeImageResizer(result = null)
        val unreadableStore = newCounterStore(imageResizer = unreadable)
        unreadableStore.effects.test {
            unreadableStore.dispatch(CounterIntent.PhotoCaptured(CAPTURE))
            runCurrent()

            assertEquals(CounterEffect.PhotoNotSaved, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertNull(unreadableStore.state.value.coatPrompt)

        val cancelledRepository = FakeEncounterRepository()
        val cancelledStore = newCounterStore(encounterRepository = cancelledRepository)
        cancelledStore.dispatch(CounterIntent.PhotoCaptured(uri = null))
        runCurrent()
        assertEquals(emptyList(), cancelledRepository.insertedIds)
        assertNull(cancelledStore.state.value.coatPrompt)

        val importStore = newCounterStore()
        importStore.dispatch(CounterIntent.Import.PhotosPicked(persistentListOf("content://a")))
        runCurrent()
        importStore.dispatch(
            CounterIntent.Import.Finished("run-1", persistentListOf("imported-cat"), skipped = 0, failed = 0),
        )
        runCurrent()
        assertEquals(1, importStore.state.value.importSummary?.added)
        assertNull(importStore.state.value.coatPrompt)
    }

    @Test
    fun `a newer photo takes over the prompt`() = runTest(mainDispatcher) {
        val repository = FakeEncounterRepository()
        val imageResizer = FakeImageResizer()
        val store = newCounterStore(encounterRepository = repository, imageResizer = imageResizer)
        store.dispatch(CounterIntent.PhotoCaptured(CAPTURE))
        runCurrent()

        imageResizer.result = StoredPhoto(photoPath = "second.jpg", thumbPath = "second_thumb.jpg")
        store.dispatch(CounterIntent.PhotoCaptured(CAPTURE))
        runCurrent()
        assertEquals(
            CoatPromptState("id-2", "id-2", thumbPath = "/data/photos/second_thumb.jpg"),
            store.state.value.coatPrompt,
        )

        store.dispatch(CounterIntent.CoatPrompt.Picked(CoatOption.GINGER))
        runCurrent()

        assertEquals(
            listOf("cat_thumb.jpg" to null, "second_thumb.jpg" to CatCoat.GINGER),
            repository.encounters().map { it.cover?.thumbPath to it.coat },
        )
    }

    @Test
    fun `the prompt stays open while the counter updates`() = runTest(mainDispatcher) {
        val repository = FakeEncounterRepository()
        val store = newCounterStore(encounterRepository = repository)
        store.dispatch(CounterIntent.PhotoCaptured(CAPTURE))
        runCurrent()
        val prompt = CoatPromptState("id-1", "id-1", thumbPath = "/data/photos/cat_thumb.jpg")

        store.dispatch(CounterIntent.TallyClicked)
        runCurrent()
        assertEquals("2", store.state.value.totalLabel)
        assertEquals(prompt, store.state.value.coatPrompt)

        store.dispatch(CounterIntent.UndoClicked)
        runCurrent()
        assertEquals("1", store.state.value.totalLabel)
        assertEquals(prompt, store.state.value.coatPrompt)

        repository.insert(externalEncounter(id = "widget-cat"))
        runCurrent()
        advanceTimeBy(Tuning.UNDO_VISIBLE + 1.milliseconds)
        runCurrent()
        assertEquals("2", store.state.value.totalLabel)
        assertEquals(prompt, store.state.value.coatPrompt)

        store.dispatch(CounterIntent.CoatPrompt.Picked(CoatOption.WHITE))
        runCurrent()
        assertEquals(CatCoat.WHITE, repository.encounters().single { it.photos.isNotEmpty() }.coat)
    }

    @Test
    fun `a failed coat write still closes the prompt`() = runTest(mainDispatcher) {
        val repository = FakeEncounterRepository()
        repository.setCoatShouldThrow = IllegalStateException("disk full")
        val store = newCounterStore(encounterRepository = repository)
        store.dispatch(CounterIntent.PhotoCaptured(CAPTURE))
        runCurrent()
        assertNotNull(store.state.value.coatPrompt)

        store.dispatch(CounterIntent.CoatPrompt.Picked(CoatOption.BLACK))
        runCurrent()

        assertNull(store.state.value.coatPrompt)
        assertNull(repository.encounters().single().coat)
        store.dispatch(CounterIntent.TallyClicked)
        runCurrent()
        assertEquals("2", store.state.value.totalLabel)
    }

    @Test
    fun `several turns the prompt into counting with an empty tray`() = runTest(mainDispatcher) {
        val repository = FakeEncounterRepository()
        val store = countingStore(repository)

        assertEquals(
            CoatPromptState("id-1", "id-1", thumbPath = "/data/photos/cat_thumb.jpg", counting = CoatCountState()),
            store.state.value.coatPrompt,
        )
        assertEquals(listOf(null), repository.encounters().map { it.coat })
    }

    @Test
    fun `faces tapped while counting fill the tray in order and write nothing`() = runTest(mainDispatcher) {
        val repository = FakeEncounterRepository()
        val store = countingStore(repository)

        count(store, CoatOption.GINGER, CoatOption.BLACK, CoatOption.GINGER)

        assertEquals(
            tray(CoatOption.GINGER, CoatOption.BLACK, CoatOption.GINGER),
            store.state.value.coatPrompt?.counting,
        )
        assertEquals(listOf(null), repository.encounters().map { it.coat })
    }

    @Test
    fun `the paw adds a cat whose coat nobody saw`() = runTest(mainDispatcher) {
        val store = countingStore()

        store.dispatch(CounterIntent.CoatPrompt.UnseenPicked)
        runCurrent()

        assertEquals(tray(null), store.state.value.coatPrompt?.counting)
    }

    @Test
    fun `tapping a cat in the tray takes out that one`() = runTest(mainDispatcher) {
        val store = countingStore()
        count(store, CoatOption.GINGER, CoatOption.BLACK, CoatOption.WHITE)

        store.dispatch(CounterIntent.CoatPrompt.TrayCatClicked(index = 1, coat = CoatOption.BLACK))
        runCurrent()

        assertEquals(tray(CoatOption.GINGER, CoatOption.WHITE), store.state.value.coatPrompt?.counting)
    }

    @Test
    fun `a tap on a tray cat that has already moved takes out no other cat`() = runTest(mainDispatcher) {
        val store = countingStore()
        count(store, CoatOption.GINGER, CoatOption.BLACK, CoatOption.WHITE)

        store.dispatch(CounterIntent.CoatPrompt.TrayCatClicked(index = 1, coat = CoatOption.BLACK))
        store.dispatch(CounterIntent.CoatPrompt.TrayCatClicked(index = 1, coat = CoatOption.BLACK))
        runCurrent()

        assertEquals(tray(CoatOption.GINGER, CoatOption.WHITE), store.state.value.coatPrompt?.counting)
    }

    @Test
    fun `the count's own taps do nothing while the sheet asks for one coat`() = runTest(mainDispatcher) {
        val repository = FakeEncounterRepository()
        val store = newCounterStore(encounterRepository = repository)
        store.dispatch(CounterIntent.PhotoCaptured(CAPTURE))
        runCurrent()
        val asked = store.state.value.coatPrompt

        store.dispatch(CounterIntent.CoatPrompt.UnseenPicked)
        store.dispatch(CounterIntent.CoatPrompt.TrayCatClicked(index = 0, coat = null))
        store.dispatch(CounterIntent.CoatPrompt.SaveClicked)
        runCurrent()

        assertEquals(asked, store.state.value.coatPrompt)
        assertEquals(listOf(null), repository.encounters().map { it.coat })
    }

    @Test
    fun `saving closes the prompt before anything is written`() = runTest(mainDispatcher) {
        val repository = FakeEncounterRepository()
        val store = countingStore(repository)
        count(store, CoatOption.GINGER, CoatOption.BLACK)
        val write = CompletableDeferred<Unit>()
        repository.setCoatGate = write

        store.dispatch(CounterIntent.CoatPrompt.SaveClicked)
        runCurrent()
        assertNull(store.state.value.coatPrompt)
        assertEquals(listOf(null), repository.encounters().map { it.coat })

        write.complete(Unit)
        runCurrent()
        assertEquals(listOf(CatCoat.GINGER, CatCoat.BLACK), repository.encounters().map { it.coat })
    }

    @Test
    fun `saving sets the first coat on the photographed cat and adds the rest to its shot`() =
        runTest(mainDispatcher) {
            val repository = FakeEncounterRepository()
            val store = countingStore(repository)
            count(store, CoatOption.GINGER)
            store.dispatch(CounterIntent.CoatPrompt.UnseenPicked)
            count(store, CoatOption.BLACK)

            store.dispatch(CounterIntent.CoatPrompt.SaveClicked)
            runCurrent()

            val cats = repository.encounters()
            val shot = cats.first().cover?.shotId
            assertEquals(
                listOf(CatCoat.GINGER to shot, null to shot, CatCoat.BLACK to shot),
                cats.map { it.coat to it.cover?.shotId },
            )
            assertEquals(3, cats.map { it.id }.toSet().size)
        }

    @Test
    fun `saving one counted cat only sets its coat`() = runTest(mainDispatcher) {
        val repository = FakeEncounterRepository()
        val store = countingStore(repository)
        count(store, CoatOption.BLACK)

        store.dispatch(CounterIntent.CoatPrompt.SaveClicked)
        runCurrent()

        assertEquals(listOf(CatCoat.BLACK), repository.encounters().map { it.coat })
    }

    @Test
    fun `every added cat still without a location is sent to the location attach`() = runTest(mainDispatcher) {
        val repository = FakeEncounterRepository()
        val store = newCounterStore(encounterRepository = repository)

        store.effects.test {
            store.dispatch(CounterIntent.PhotoCaptured(CAPTURE))
            store.dispatch(CounterIntent.CoatPrompt.SeveralClicked)
            runCurrent()
            count(store, CoatOption.GINGER, CoatOption.BLACK, CoatOption.WHITE)
            store.dispatch(CounterIntent.CoatPrompt.SaveClicked)
            runCurrent()

            val added = repository.encounters().drop(1).map { it.id }
            assertEquals(
                effectsOfTheShutter + added.map { CounterEffect.AttachLocation(it) },
                cancelAndConsumeRemainingEvents().items(),
            )
            assertEquals(2, added.size)
        }
    }

    @Test
    fun `past the most cats a photo can hold, faces stop adding`() = runTest(mainDispatcher) {
        val store = countingStore()
        count(store, *Array(Tuning.SHOT_MAX_CATS) { CoatOption.GREY })

        count(store, CoatOption.BLACK)
        store.dispatch(CounterIntent.CoatPrompt.UnseenPicked)
        runCurrent()

        assertEquals(tray(*Array(Tuning.SHOT_MAX_CATS) { CoatOption.GREY }), store.state.value.coatPrompt?.counting)
    }

    @Test
    fun `leaving the count without saving keeps the photographed cat alone and uncoated`() = runTest(mainDispatcher) {
        val repository = FakeEncounterRepository()
        val store = countingStore(repository)
        count(store, CoatOption.GINGER, CoatOption.BLACK)

        store.dispatch(CounterIntent.CoatPrompt.Dismissed)
        runCurrent()
        store.dispatch(CounterIntent.CoatPrompt.SaveClicked)
        runCurrent()

        assertNull(store.state.value.coatPrompt)
        assertEquals(listOf(null), repository.encounters().map { it.coat })
    }

    @Test
    fun `a newer photo takes the sheet over and drops the tray`() = runTest(mainDispatcher) {
        val repository = FakeEncounterRepository()
        val imageResizer = FakeImageResizer()
        val store = newCounterStore(encounterRepository = repository, imageResizer = imageResizer)
        store.dispatch(CounterIntent.PhotoCaptured(CAPTURE))
        store.dispatch(CounterIntent.CoatPrompt.SeveralClicked)
        runCurrent()
        count(store, CoatOption.GINGER, CoatOption.BLACK)

        imageResizer.result = StoredPhoto(photoPath = "second.jpg", thumbPath = "second_thumb.jpg")
        store.dispatch(CounterIntent.PhotoCaptured(CAPTURE))
        runCurrent()

        assertEquals(
            CoatPromptState("id-2", "id-2", thumbPath = "/data/photos/second_thumb.jpg"),
            store.state.value.coatPrompt,
        )
        assertEquals(listOf(null, null), repository.encounters().map { it.coat })
    }

    @Test
    fun `the tray stays while the counter updates`() = runTest(mainDispatcher) {
        val store = countingStore()
        count(store, CoatOption.GINGER)

        store.dispatch(CounterIntent.TallyClicked)
        runCurrent()

        assertEquals("2", store.state.value.totalLabel)
        assertEquals(tray(CoatOption.GINGER), store.state.value.coatPrompt?.counting)
    }

    @Test
    fun `a failed coat write after saving shows one message and adds no cat`() = runTest(mainDispatcher) {
        val repository = FakeEncounterRepository()
        repository.setCoatShouldThrow = IllegalStateException("disk full")
        val store = countingStore(repository)
        count(store, CoatOption.GINGER, CoatOption.BLACK)

        store.effects.test {
            store.dispatch(CounterIntent.CoatPrompt.SaveClicked)
            runCurrent()

            assertEquals(effectsOfTheShutter + CounterEffect.CatsNotSaved, cancelAndConsumeRemainingEvents().items())
        }
        assertNull(store.state.value.coatPrompt)
        assertEquals(1, repository.encounters().size)
    }

    @Test
    fun `a failed add after saving shows one message and keeps the first coat`() = runTest(mainDispatcher) {
        val repository = FakeEncounterRepository()
        val photoStorage = FakePhotoStorage().apply { copyShouldThrow = IllegalStateException("disk full") }
        val store = countingStore(repository, photoStorage)
        count(store, CoatOption.GINGER, CoatOption.BLACK)

        store.effects.test {
            store.dispatch(CounterIntent.CoatPrompt.SaveClicked)
            runCurrent()

            assertEquals(effectsOfTheShutter + CounterEffect.CatsNotSaved, cancelAndConsumeRemainingEvents().items())
        }
        assertEquals(listOf(CatCoat.GINGER), repository.encounters().map { it.coat })
    }

    private fun TestScope.countingStore(
        repository: FakeEncounterRepository = FakeEncounterRepository(),
        photoStorage: FakePhotoStorage = FakePhotoStorage(),
    ): CounterStore {
        val store = newCounterStore(encounterRepository = repository, photoStorage = photoStorage)
        store.dispatch(CounterIntent.PhotoCaptured(CAPTURE))
        store.dispatch(CounterIntent.CoatPrompt.SeveralClicked)
        runCurrent()
        return store
    }

    private fun TestScope.count(store: CounterStore, vararg coats: CoatOption) {
        coats.forEach { store.dispatch(CounterIntent.CoatPrompt.Picked(it)) }
        runCurrent()
    }

    private fun tray(vararg coats: CoatOption?) = CoatCountState(tray = persistentListOf(*coats))

    private fun List<Event<CounterEffect>>.items() = filterIsInstance<Event.Item<CounterEffect>>().map { it.value }

    private val effectsOfTheShutter =
        listOf(CounterEffect.AttachLocation("id-1"), CounterEffect.DiscardCapture(CAPTURE))

    private companion object {
        const val CAPTURE = "file:///cache/capture.jpg"
    }
}
