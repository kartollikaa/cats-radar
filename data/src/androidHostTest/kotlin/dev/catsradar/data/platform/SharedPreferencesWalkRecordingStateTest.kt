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

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun aFreshInstallIsNotRecording() {
        assertFalse(SharedPreferencesWalkRecordingState(context).recording)
    }

    @Test
    fun aRecordingMarkOutlivesTheInstanceThatSetIt() {
        SharedPreferencesWalkRecordingState(context).markRecording()

        assertTrue(SharedPreferencesWalkRecordingState(context).recording)
    }

    @Test
    fun stoppingClearsTheMarkForTheNextInstance() {
        SharedPreferencesWalkRecordingState(context).markRecording()
        SharedPreferencesWalkRecordingState(context).markStopped()

        assertFalse(SharedPreferencesWalkRecordingState(context).recording)
    }
}
