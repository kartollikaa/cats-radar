package dev.catsradar.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import kotlin.test.assertFalse
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
}
