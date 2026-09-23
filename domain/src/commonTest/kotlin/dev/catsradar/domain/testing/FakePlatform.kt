package dev.catsradar.domain.testing

import dev.catsradar.domain.location.LocationFix
import dev.catsradar.domain.platform.DeviceIdProvider
import dev.catsradar.domain.platform.IdGenerator
import dev.catsradar.domain.platform.LocationProvider
import dev.catsradar.domain.platform.WalkRecordingState
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration

class FakeIdGenerator : IdGenerator {
    private var counter = 0
    override fun newId(): String = "id-${++counter}"
}

class FakeDeviceIdProvider(override val deviceId: String = "device-1") : DeviceIdProvider

class FakeLocationProvider(
    private val currentFix: LocationFix? = null,
    private val lastKnownFix: LocationFix? = null,
    private val trackedFixes: Flow<LocationFix> = emptyFlow(),
) : LocationProvider {
    override suspend fun getCurrentFix(timeout: Duration): LocationFix? = currentFix
    override suspend fun lastKnown(): LocationFix? = lastKnownFix
    override fun trackFixes(): Flow<LocationFix> = trackedFixes
}

class FakeWalkRecordingState(override var recording: Boolean = false) : WalkRecordingState {
    override fun markRecording() {
        recording = true
    }

    override fun markStopped() {
        recording = false
    }
}

// A current fix that never arrives on its own: getCurrentFix only ever returns via the [timeout]
// it is given, bounded internally so a caller that forgets to apply its own timeout hangs forever
// instead of silently passing - the hang never escapes this function.
class HangingLocationProvider(private val lastKnownFix: LocationFix? = null) : LocationProvider {
    var recordedTimeout: Duration? = null
        private set

    override suspend fun getCurrentFix(timeout: Duration): LocationFix? {
        recordedTimeout = timeout
        return withTimeoutOrNull(timeout) { awaitCancellation() }
    }

    override suspend fun lastKnown(): LocationFix? = lastKnownFix
    override fun trackFixes(): Flow<LocationFix> = emptyFlow()
}

// Simulates a platform implementation that fails to honour its own [timeout]: unlike
// HangingLocationProvider, this one never bounds its own wait, so only a caller's own outer
// timeout can unblock it. The hang still never escapes past that outer bound.
class MisbehavingLocationProvider : LocationProvider {
    override suspend fun getCurrentFix(timeout: Duration): LocationFix? = awaitCancellation()
    override suspend fun lastKnown(): LocationFix? = null
    override fun trackFixes(): Flow<LocationFix> = emptyFlow()
}
