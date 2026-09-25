package dev.catsradar.data.platform

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class SharedPreferencesLocationPermissionRequestStateTest {
    private val prefs = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("location_permission", Context.MODE_PRIVATE)

    @Test
    fun startsUnrequestedForAFreshInstall() {
        assertFalse(SharedPreferencesLocationPermissionRequestState(prefs).alreadyRequested)
    }

    @Test
    fun markingRequestedPersistsAcrossANewInstance() {
        SharedPreferencesLocationPermissionRequestState(prefs).markRequested()

        // A fresh instance, as process death would produce, must still see the persisted flag.
        assertTrue(SharedPreferencesLocationPermissionRequestState(prefs).alreadyRequested)
    }
}
