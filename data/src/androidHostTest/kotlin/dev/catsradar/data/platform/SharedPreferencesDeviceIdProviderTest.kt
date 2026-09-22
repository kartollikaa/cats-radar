package dev.catsradar.data.platform

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.domain.platform.IdGenerator
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

private class SequentialIdGenerator : IdGenerator {
    private var counter = 0
    override fun newId(): String = "generated-${++counter}"
}

@RunWith(AndroidJUnit4::class)
class SharedPreferencesDeviceIdProviderTest {
    @Test
    fun deviceIdIsGeneratedOnceAndPersistsAcrossInstances() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val idGenerator = SequentialIdGenerator()

        val first = SharedPreferencesDeviceIdProvider(context, idGenerator).deviceId()
        val second = SharedPreferencesDeviceIdProvider(context, idGenerator).deviceId()

        assertEquals("generated-1", first)
        assertEquals(first, second)
    }
}
