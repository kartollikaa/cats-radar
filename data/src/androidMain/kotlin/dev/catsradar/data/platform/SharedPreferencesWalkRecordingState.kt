package dev.catsradar.data.platform

import android.content.SharedPreferences
import dev.catsradar.domain.platform.WalkRecordingState

private const val KEY_RECORDING = "recording"

class SharedPreferencesWalkRecordingState(private val prefs: SharedPreferences) : WalkRecordingState {

    override val recording: Boolean
        get() = prefs.getBoolean(KEY_RECORDING, false)

    // commit(), not apply(): the mark must survive the process dying the moment after it is set.
    override fun markRecording() {
        prefs.edit().putBoolean(KEY_RECORDING, true).commit()
    }

    override fun markStopped() {
        prefs.edit().putBoolean(KEY_RECORDING, false).commit()
    }
}
