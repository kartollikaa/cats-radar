package dev.catsradar.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class PhotoCopiesRegeneratedPreferenceTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun photoCopiesStartUnregeneratedAndOnceMarkedANewRepositoryReadsItBack() = runTest {
        val file = temporaryFolder.newFile("settings.preferences_pb").also { it.delete() }
        val writerScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
        val writer = DataStoreSettingsRepository(PreferenceDataStoreFactory.create(scope = writerScope) { file })
        assertFalse(writer.photoCopiesRegenerated().first())
        writer.setPhotoCopiesRegenerated(true)
        writerScope.cancel()

        val reader = DataStoreSettingsRepository(PreferenceDataStoreFactory.create(scope = backgroundScope) { file })

        assertTrue(reader.photoCopiesRegenerated().first())
    }
}
