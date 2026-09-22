package dev.catsradar.domain.platform

/**
 * Whether the location permission dialog has ever been requested. Persisted across process
 * death, so a killed-and-relaunched app does not re-open the system dialog on the next tally.
 */
interface LocationPermissionRequestState {
    val alreadyRequested: Boolean
    fun markRequested()
}
