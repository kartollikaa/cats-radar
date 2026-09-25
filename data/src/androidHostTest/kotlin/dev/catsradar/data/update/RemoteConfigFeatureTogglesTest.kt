package dev.catsradar.data.update

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import dev.catsradar.domain.platform.Feature
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

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

    @After
    fun tearDown() = app.delete()

    @Test
    fun aSwitchNobodySetInTheConsoleIsOff() = runTest {
        val toggles = RemoteConfigFeatureToggles(FirebaseRemoteConfig.getInstance(app))

        assertEquals(false, toggles.isOn(Feature.IN_APP_UPDATES).first())
    }
}
