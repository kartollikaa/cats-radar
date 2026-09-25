package dev.catsradar.data.platform

import android.content.SharedPreferences
import dev.catsradar.domain.platform.DeviceIdProvider
import dev.catsradar.domain.platform.IdGenerator

private const val KEY_DEVICE_ID = "device_id"
private val lock = Any()

// Resolved synchronously in the constructor so nothing awaits it; :app's Koin module constructs
// this single eagerly at start (createdAtStart), landing the one-time cost before any tap.
class SharedPreferencesDeviceIdProvider(
    prefs: SharedPreferences,
    idGenerator: IdGenerator,
) : DeviceIdProvider {
    override val deviceId: String = synchronized(lock) {
        prefs.getString(KEY_DEVICE_ID, null) ?: idGenerator.newId().also { id ->
            prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        }
    }
}
