package dev.catsradar.data.platform

import android.content.SharedPreferences
import dev.catsradar.domain.platform.LocationPermissionRequestState

private const val KEY_REQUESTED = "requested"

class SharedPreferencesLocationPermissionRequestState(
    private val prefs: SharedPreferences,
) : LocationPermissionRequestState {

    override val alreadyRequested: Boolean
        get() = prefs.getBoolean(KEY_REQUESTED, false)

    override fun markRequested() {
        prefs.edit().putBoolean(KEY_REQUESTED, true).apply()
    }
}
