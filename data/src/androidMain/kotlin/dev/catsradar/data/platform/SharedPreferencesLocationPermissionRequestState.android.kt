package dev.catsradar.data.platform

import android.content.Context
import dev.catsradar.domain.platform.LocationPermissionRequestState

private const val PREFS_NAME = "location_permission"
private const val KEY_REQUESTED = "requested"

class SharedPreferencesLocationPermissionRequestState(context: Context) : LocationPermissionRequestState {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override val alreadyRequested: Boolean
        get() = prefs.getBoolean(KEY_REQUESTED, false)

    override fun markRequested() {
        prefs.edit().putBoolean(KEY_REQUESTED, true).apply()
    }
}
