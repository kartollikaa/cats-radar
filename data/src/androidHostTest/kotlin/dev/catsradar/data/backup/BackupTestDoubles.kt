package dev.catsradar.data.backup

import dev.catsradar.domain.platform.DeviceIdProvider
import kotlin.time.Clock
import kotlin.time.Instant

internal class FixedClock(private val at: Instant) : Clock {
    override fun now(): Instant = at
}

internal class StubDeviceId(override val deviceId: String = "device-1") : DeviceIdProvider
