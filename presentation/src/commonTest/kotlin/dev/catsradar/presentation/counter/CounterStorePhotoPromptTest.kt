package dev.catsradar.presentation.counter

import app.cash.turbine.test
import dev.catsradar.domain.Tuning
import dev.catsradar.domain.model.CatCoat
import dev.catsradar.domain.platform.StoredPhoto
import dev.catsradar.presentation.coat.CoatOption
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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

        assertEquals(CoatPromptState(thumbPath = "/data/photos/cat_thumb.jpg"), store.state.value.coatPrompt)
    }

    @Test
    fun `picking a coat sets it on the photographed cat and closes the prompt`() = runTest(mainDispatcher) {
        val repository = FakeEncounterRepository()
        repository.insert(externalEncounter(id = "earlier-cat"))
        val store = newCounterStore(encounterRepository = repository)
        store.dispatch(CounterIntent.PhotoCaptured(CAPTURE))
        runCurrent()

        store.dispatch(CounterIntent.CoatPromptPicked(CoatOption.BLACK))
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

        store.dispatch(CounterIntent.CoatPromptDismissed)
        runCurrent()
        assertNull(store.state.value.coatPrompt)

        store.dispatch(CounterIntent.CoatPromptPicked(CoatOption.BLACK))
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

        store.dispatch(CounterIntent.CoatPromptPicked(CoatOption.BLACK))
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
        assertEquals(CoatPromptState(thumbPath = "/data/photos/second_thumb.jpg"), store.state.value.coatPrompt)

        store.dispatch(CounterIntent.CoatPromptPicked(CoatOption.GINGER))
        runCurrent()

        assertEquals(
            listOf("cat_thumb.jpg" to null, "second_thumb.jpg" to CatCoat.GINGER),
            repository.encounters().map { it.thumbPath to it.coat },
        )
    }

    @Test
    fun `the prompt stays open while the counter updates`() = runTest(mainDispatcher) {
        val repository = FakeEncounterRepository()
        val store = newCounterStore(encounterRepository = repository)
        store.dispatch(CounterIntent.PhotoCaptured(CAPTURE))
        runCurrent()
        val prompt = CoatPromptState(thumbPath = "/data/photos/cat_thumb.jpg")

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

        store.dispatch(CounterIntent.CoatPromptPicked(CoatOption.WHITE))
        runCurrent()
        assertEquals(CatCoat.WHITE, repository.encounters().single { it.thumbPath != null }.coat)
    }

    @Test
    fun `a failed coat write still closes the prompt`() = runTest(mainDispatcher) {
        val repository = FakeEncounterRepository()
        repository.setCoatShouldThrow = IllegalStateException("disk full")
        val store = newCounterStore(encounterRepository = repository)
        store.dispatch(CounterIntent.PhotoCaptured(CAPTURE))
        runCurrent()
        assertNotNull(store.state.value.coatPrompt)

        store.dispatch(CounterIntent.CoatPromptPicked(CoatOption.BLACK))
        runCurrent()

        assertNull(store.state.value.coatPrompt)
        assertNull(repository.encounters().single().coat)
        store.dispatch(CounterIntent.TallyClicked)
        runCurrent()
        assertEquals("2", store.state.value.totalLabel)
    }

    private companion object {
        const val CAPTURE = "file:///cache/capture.jpg"
    }
}
