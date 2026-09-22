package dev.catsradar.domain.platform

/**
 * The installation's identifier: generated once and durable across app restarts.
 *
 * Non-suspending on purpose: an implementation must resolve the value at construction time, not
 * on first read, so nothing that reads it ever waits.
 */
interface DeviceIdProvider {
    val deviceId: String
}
