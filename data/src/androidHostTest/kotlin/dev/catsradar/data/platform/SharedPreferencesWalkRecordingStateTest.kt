package dev.catsradar.data.platform

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class SharedPreferencesWalkRecordingStateTest {

    private val prefs = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("walk_recording", Context.MODE_PRIVATE)

    @Test
    fun aFreshInstallIsNotRecording() {
        assertFalse(SharedPreferencesWalkRecordingState(prefs).recording)
    }

    @Test
    fun aRecordingMarkOutlivesTheInstanceThatSetIt() {
        SharedPreferencesWalkRecordingState(prefs).markRecording()

        assertTrue(SharedPreferencesWalkRecordingState(prefs).recording)
    }

    @Test
    fun stoppingClearsTheMarkForTheNextInstance() {
        SharedPreferencesWalkRecordingState(prefs).markRecording()
        SharedPreferencesWalkRecordingState(prefs).markStopped()

        assertFalse(SharedPreferencesWalkRecordingState(prefs).recording)
    }
}
