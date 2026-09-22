package dev.catsradar.presentation.settings

import app.cash.turbine.test
import dev.catsradar.presentation.counter.FakeSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsStoreTest {

    private val mainDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(mainDispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun newStore() = SettingsStore(FakeSettingsRepository())

    @Test
    fun `asking to export opens the file picker and starts nothing yet`() = runTest(mainDispatcher) {
        val store = newStore()
        store.effects.test {
            store.dispatch(SettingsIntent.Backup.ExportRequested)
            runCurrent()

            assertEquals(SettingsEffect.PickExportTarget, awaitItem())
            assertEquals(false, store.state.value.backupRunning)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `choosing a target starts the export`() = runTest(mainDispatcher) {
        val store = newStore()
        store.effects.test {
            store.dispatch(SettingsIntent.Backup.ExportTargetChosen("content://out.zip"))
            runCurrent()

            assertEquals(SettingsEffect.StartExport("content://out.zip"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `dismissing the file picker starts nothing`() = runTest(mainDispatcher) {
        val store = newStore()
        store.effects.test {
            store.dispatch(SettingsIntent.Backup.ExportTargetChosen(null))
            store.dispatch(SettingsIntent.Backup.ImportSourceChosen(null))
            runCurrent()

            // Asserted on the effects, not only the state: a run started with an empty uri would
            // leave the state alone and still hand a worker a file it cannot open.
            expectNoEvents()
            assertEquals(false, store.state.value.backupRunning)
            assertNull(store.state.value.backupOutcome)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a run in progress takes both buttons away and clears the last outcome`() = runTest(mainDispatcher) {
        val store = newStore()
        store.dispatch(SettingsIntent.Backup.Finished(BackupOutcome.EXPORTED))
        runCurrent()

        store.dispatch(SettingsIntent.Backup.Started)
        runCurrent()

        assertEquals(true, store.state.value.backupRunning)
        assertNull(store.state.value.backupOutcome)
    }

    @Test
    fun `a finished run gives the buttons back and says what happened`() = runTest(mainDispatcher) {
        val store = newStore()
        store.dispatch(SettingsIntent.Backup.Started)
        runCurrent()

        store.dispatch(SettingsIntent.Backup.Finished(BackupOutcome.IMPORT_REFUSED_TOO_NEW))
        runCurrent()

        assertEquals(false, store.state.value.backupRunning)
        assertEquals(BackupOutcome.IMPORT_REFUSED_TOO_NEW, store.state.value.backupOutcome)
    }

    @Test
    fun `dismissing the outcome leaves the buttons available`() = runTest(mainDispatcher) {
        val store = newStore()
        store.dispatch(SettingsIntent.Backup.Finished(BackupOutcome.EXPORTED))
        runCurrent()

        store.dispatch(SettingsIntent.Backup.OutcomeDismissed)
        runCurrent()

        assertNull(store.state.value.backupOutcome)
        assertEquals(false, store.state.value.backupRunning)
    }

    @Test
    fun `the gallery switch still follows the stored value, not the tap`() = runTest(mainDispatcher) {
        val repository = FakeSettingsRepository(saveOriginals = true)
        val store = SettingsStore(repository)
        runCurrent()

        store.dispatch(SettingsIntent.SaveOriginalsToggled(false))
        runCurrent()

        assertEquals(false, store.state.value.saveOriginalsToGallery)
    }
}
