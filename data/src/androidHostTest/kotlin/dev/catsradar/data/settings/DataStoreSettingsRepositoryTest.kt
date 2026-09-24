package dev.catsradar.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.domain.repository.ReportedJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class DataStoreSettingsRepositoryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun newStore(scope: TestScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope) {
            temporaryFolder.newFile("settings-${System.nanoTime()}.preferences_pb").also { it.delete() }
        }

    @Test
    fun galleryCopyingIsOnWhenNothingHasEverBeenStored() = runTest {
        val repository = DataStoreSettingsRepository(newStore(this))

        assertTrue(repository.saveOriginalsToGallery().first())
    }

    @Test
    fun turningItOffIsReadBack() = runTest {
        val repository = DataStoreSettingsRepository(newStore(this))

        repository.setSaveOriginalsToGallery(false)

        assertFalse(repository.saveOriginalsToGallery().first())
    }

    @Test
    fun turningItBackOnIsReadBack() = runTest {
        val repository = DataStoreSettingsRepository(newStore(this))

        repository.setSaveOriginalsToGallery(false)
        repository.setSaveOriginalsToGallery(true)

        assertTrue(repository.saveOriginalsToGallery().first())
    }

    @Test
    fun theEncountersGridIsOnWhenNothingHasEverBeenStored() = runTest {
        val repository = DataStoreSettingsRepository(newStore(this))

        assertTrue(repository.encountersGrid().first())
    }

    @Test
    fun turningTheEncountersGridOffAndOnAgainIsReadBack() = runTest {
        val repository = DataStoreSettingsRepository(newStore(this))

        repository.setEncountersGrid(false)
        val off = repository.encountersGrid().first()
        repository.setEncountersGrid(true)

        assertFalse(off)
        assertTrue(repository.encountersGrid().first())
    }

    @Test
    fun noRunIsAcknowledgedBeforeTheFirst() = runTest {
        val repository = DataStoreSettingsRepository(newStore(this))

        assertNull(repository.acknowledgedRun(ReportedJob.GALLERY_IMPORT).first())
        assertNull(repository.acknowledgedRun(ReportedJob.BACKUP).first())
    }

    @Test
    fun acknowledgingAnImportRunLeavesTheBackupRunAlone() = runTest {
        val repository = DataStoreSettingsRepository(newStore(this))

        repository.setAcknowledgedRun(ReportedJob.GALLERY_IMPORT, "import-run")

        assertEquals("import-run", repository.acknowledgedRun(ReportedJob.GALLERY_IMPORT).first())
        assertNull(repository.acknowledgedRun(ReportedJob.BACKUP).first())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun acknowledgedRunsAreReadBackFromTheFileByANewRepository() = runTest {
        val file = temporaryFolder.newFile("settings.preferences_pb").also { it.delete() }
        val writerScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
        val writer = DataStoreSettingsRepository(PreferenceDataStoreFactory.create(scope = writerScope) { file })
        writer.setAcknowledgedRun(ReportedJob.GALLERY_IMPORT, "import-run")
        writer.setAcknowledgedRun(ReportedJob.BACKUP, "backup-run")
        writerScope.cancel()

        val reader = DataStoreSettingsRepository(PreferenceDataStoreFactory.create(scope = backgroundScope) { file })

        assertEquals("import-run", reader.acknowledgedRun(ReportedJob.GALLERY_IMPORT).first())
        assertEquals("backup-run", reader.acknowledgedRun(ReportedJob.BACKUP).first())
    }

    @Test
    fun writingAnotherPreferenceDoesNotRepeatTheEncountersGridValue() = runTest {
        val repository = DataStoreSettingsRepository(newStore(this))
        val seen = mutableListOf<Boolean>()
        backgroundScope.launch { repository.encountersGrid().collect { seen += it } }
        advanceUntilIdle()

        repository.setWalkingMode(true)
        advanceUntilIdle()
        repository.setEncountersGrid(false)
        advanceUntilIdle()

        assertEquals(listOf(true, false), seen)
    }
}
