package dev.catsradar.domain.testing

import dev.catsradar.domain.platform.DeviceIdProvider
import dev.catsradar.domain.platform.IdGenerator

class FakeIdGenerator : IdGenerator {
    private var counter = 0
    override fun newId(): String = "id-${++counter}"
}

class FakeDeviceIdProvider(private val id: String = "device-1") : DeviceIdProvider {
    override suspend fun deviceId(): String = id
}
