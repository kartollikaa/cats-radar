package dev.catsradar.data.platform

import android.content.Context
import dev.catsradar.domain.platform.DeviceIdProvider
import dev.catsradar.domain.platform.IdGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PREFS_NAME = "device"
private const val KEY_DEVICE_ID = "device_id"

class SharedPreferencesDeviceIdProvider(
    context: Context,
    private val idGenerator: IdGenerator,
) : DeviceIdProvider {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override suspend fun deviceId(): String = withContext(Dispatchers.IO) {
        prefs.getString(KEY_DEVICE_ID, null) ?: idGenerator.newId().also { id ->
            prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        }
    }
}
