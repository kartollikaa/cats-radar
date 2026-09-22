package dev.catsradar.domain.testing

import dev.catsradar.domain.location.LocationFix
import dev.catsradar.domain.platform.DeviceIdProvider
import dev.catsradar.domain.platform.IdGenerator
import dev.catsradar.domain.platform.LocationProvider
import kotlin.time.Duration

class FakeIdGenerator : IdGenerator {
    private var counter = 0
    override fun newId(): String = "id-${++counter}"
}

class FakeDeviceIdProvider(override val deviceId: String = "device-1") : DeviceIdProvider

class FakeLocationProvider(
    private val currentFix: LocationFix? = null,
    private val lastKnownFix: LocationFix? = null,
) : LocationProvider {
    override suspend fun getCurrentFix(timeout: Duration): LocationFix? = currentFix
    override suspend fun lastKnown(): LocationFix? = lastKnownFix
}
