package dev.catsradar.data.platform

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import dev.catsradar.domain.platform.Feature
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class RemoteConfigFeatureTogglesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val app = FirebaseApp.initializeApp(
        context,
        FirebaseOptions.Builder()
            .setApplicationId("1:000000000000:android:0000000000000000")
            .setApiKey("AIza-test-only")
            .setProjectId("cats-radar-test")
            .build(),
        "remote-config-test",
    )
    private val remoteConfig = FirebaseRemoteConfig.getInstance(app)

    @After
    fun tearDown() = app.delete()

    @Test
    fun aSwitchNobodySetInTheConsoleIsOff() = runTest {
        val toggles = RemoteConfigFeatureToggles(remoteConfig)

        assertEquals(false, toggles.isOn(Feature.IN_APP_UPDATES).first())
    }

    @Test
    fun inAppUpdatesReadsTheInAppUpdatesParameter() = runTest {
        remoteConfig.setDefaultsAsync(mapOf("in_app_updates" to true)).await()
        val toggles = RemoteConfigFeatureToggles(remoteConfig)

        assertEquals(true, toggles.isOn(Feature.IN_APP_UPDATES).first())
    }

    @Test
    fun theSwitchIsReadOnTheInjectedDispatcher() = runTest {
        val io = CountingDispatcher(StandardTestDispatcher(testScheduler))
        val toggles = RemoteConfigFeatureToggles(remoteConfig, ioDispatcher = io)

        toggles.isOn(Feature.IN_APP_UPDATES).first()

        assertTrue(io.dispatches.get() > 0)
    }

    private class CountingDispatcher(private val inner: CoroutineDispatcher) : CoroutineDispatcher() {
        val dispatches = AtomicInteger()

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            dispatches.incrementAndGet()
            inner.dispatch(context, block)
        }
    }
}
