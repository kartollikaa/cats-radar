package dev.catsradar.domain.platform

/** The installation's identifier: generated once and durable across app restarts. */
interface DeviceIdProvider {
    suspend fun deviceId(): String
}
