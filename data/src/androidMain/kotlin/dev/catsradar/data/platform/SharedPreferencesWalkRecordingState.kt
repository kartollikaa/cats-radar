package dev.catsradar.data.platform

import android.content.Context
import dev.catsradar.domain.platform.WalkRecordingState

private const val PREFS_NAME = "walk_recording"
private const val KEY_RECORDING = "recording"

class SharedPreferencesWalkRecordingState(context: Context) : WalkRecordingState {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

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
