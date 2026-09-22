package dev.catsradar.data.platform

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.domain.platform.IdGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

private class SequentialIdGenerator : IdGenerator {
    private var counter = 0
    override fun newId(): String = "generated-${++counter}"
}

// Widens the check-then-act window a real device's first-run race can hit: several taps calling
// deviceId() before any of them has persisted one.
private class SlowSequentialIdGenerator : IdGenerator {
    private var counter = 0
    override fun newId(): String {
        Thread.sleep(SLOW_GENERATE_MILLIS)
        return "generated-${++counter}"
    }
}

private const val SLOW_GENERATE_MILLIS = 50L
private const val CONCURRENT_CALLS = 8

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

    @Test
    fun concurrentFirstCallsAllSeeTheSameGeneratedId() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val provider = SharedPreferencesDeviceIdProvider(context, SlowSequentialIdGenerator())

        val results = List(CONCURRENT_CALLS) { async(Dispatchers.IO) { provider.deviceId() } }.awaitAll()

        assertEquals(1, results.toSet().size)
    }
}
