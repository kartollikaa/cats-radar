package dev.catsradar.app.notification

import dev.catsradar.domain.location.LocationFix
import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.EncounterKind
import dev.catsradar.domain.model.EncounterOrigin
import dev.catsradar.domain.model.LocationSource
import dev.catsradar.domain.model.LocationStamp
import dev.catsradar.domain.model.PlaceCellAssignment
import dev.catsradar.domain.model.TrackPoint
import dev.catsradar.domain.model.Walk
import dev.catsradar.domain.platform.LocationProvider
import dev.catsradar.domain.platform.WalkRecordingState
import dev.catsradar.domain.repository.EncounterRepository
import dev.catsradar.domain.repository.ReportedJob
import dev.catsradar.domain.repository.SettingsRepository
import dev.catsradar.domain.repository.WalkRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlin.time.Duration
import kotlin.time.Instant

internal val WalkStart = Instant.parse("2026-09-23T09:00:00Z")

internal class TrackingOnlyLocationProvider(private val fixes: Flow<LocationFix>) : LocationProvider {
    override suspend fun getCurrentFix(timeout: Duration): LocationFix? = null
    override suspend fun lastKnown(): LocationFix? = null
    override fun trackFixes(): Flow<LocationFix> = fixes
}

internal class InMemoryRecordingState : WalkRecordingState {
    override var recording = false

    override fun markRecording() {
        recording = true
    }

    override fun markStopped() {
        recording = false
    }
}

internal class FakeWalkingSettings : SettingsRepository {
    val walking = MutableStateFlow(false)

    override fun walkingMode(): Flow<Boolean> = walking

    override suspend fun setWalkingMode(enabled: Boolean) {
        walking.value = enabled
    }

    override fun saveOriginalsToGallery(): Flow<Boolean> = MutableStateFlow(true)
    override suspend fun setSaveOriginalsToGallery(enabled: Boolean) = Unit
    override fun lastSeenMilestone(): Flow<Int> = MutableStateFlow(Int.MAX_VALUE)
    override suspend fun setLastSeenMilestone(value: Int) = Unit
    override fun encountersGrid(): Flow<Boolean> = MutableStateFlow(true)
    override suspend fun setEncountersGrid(enabled: Boolean) = Unit
    override fun acknowledgedRun(job: ReportedJob): Flow<String?> = MutableStateFlow(null)
    override suspend fun setAcknowledgedRun(job: ReportedJob, runId: String) = Unit
}

/** One walk, on from [WalkStart] until something ends it. */
internal class OneWalkRepository : WalkRepository {
    val points = MutableStateFlow(emptyList<TrackPoint>())
    val walk = MutableStateFlow(
        Walk(
            id = "walk-1",
            startedAt = WalkStart,
            endedAt = null,
            deviceId = "device-1",
            createdAt = WalkStart,
            updatedAt = WalkStart,
        ),
    )

    override fun observeAll(): Flow<List<Walk>> = flowOf(listOf(walk.value))
    override suspend fun openWalk(): Walk? = walk.value.takeIf { it.endedAt == null }
    override suspend fun startIfNoneOpen(walk: Walk): Walk = this.walk.value

    override suspend fun end(id: String, endedAt: Instant, updatedAt: Instant): Boolean {
        val open = openWalk() ?: return false
        walk.value = open.copy(endedAt = endedAt, updatedAt = updatedAt)
        return true
    }

    override suspend fun appendPoint(point: TrackPoint) = points.update { it + point }
    override suspend fun lastPoint(walkId: String): TrackPoint? = points.value.lastOrNull()
    override fun observeTrack(walkId: String): Flow<List<TrackPoint>> = points
    override suspend fun loadEveryPoint(): List<TrackPoint> = points.value

    override suspend fun upsert(walk: Walk): Unit = throw NotImplementedError("unused by these tests")

    override suspend fun appendPoints(points: List<TrackPoint>): Unit =
        throw NotImplementedError("unused by these tests")
}

internal fun tally(id: String, at: Instant): Encounter = Encounter(
    id = id,
    occurredAt = at,
    tzOffsetMinutes = 0,
    kind = EncounterKind.TALLY,
    origin = EncounterOrigin.APP,
    coat = null,
    photoPath = null,
    thumbPath = null,
    galleryUri = null,
    sourceDigest = null,
    lat = null,
    lon = null,
    accuracyMeters = null,
    locationSource = LocationSource.NONE,
    locationFixedAt = null,
    geohash = null,
    placeCellId = null,
    deviceId = "device-1",
    createdAt = at,
    updatedAt = at,
    deletedAt = null,
)

internal class InMemoryEncounters : EncounterRepository {
    private val rows = MutableStateFlow(emptyList<Encounter>())

    fun add(id: String, at: Instant) {
        rows.update { it + tally(id, at) }
    }

    override fun observeAll(): Flow<List<Encounter>> = rows

    override fun observeById(id: String): Flow<Encounter?> = throw NotImplementedError("unused by these tests")
    override suspend fun insert(encounter: Encounter) = rows.update { it + encounter }
    override suspend fun update(encounter: Encounter): Unit = throw NotImplementedError("unused by these tests")
    override suspend fun attachLocation(id: String, stamp: LocationStamp): Unit =
        throw NotImplementedError("unused by these tests")

    override suspend fun setPlaceCells(assignments: List<PlaceCellAssignment>): Unit =
        throw NotImplementedError("unused by these tests")

    override suspend fun softDelete(id: String, deletedAt: Instant): Unit =
        throw NotImplementedError("unused by these tests")

    override suspend fun undoDelete(id: String): Unit = throw NotImplementedError("unused by these tests")
    override suspend fun softDeleteAll(ids: List<String>, deletedAt: Instant): Unit =
        throw NotImplementedError("unused by these tests")

    override suspend fun undoDeleteAll(ids: List<String>, deletedAt: Instant): Unit =
        throw NotImplementedError("unused by these tests")

    override suspend fun findBySourceDigest(sourceDigest: String): Encounter? = null
    override suspend fun loadEvery(): List<Encounter> = rows.value
    override suspend fun loadDeletedBefore(cutoff: Instant): List<Encounter> = emptyList()
    override suspend fun purgeDeletedBefore(cutoff: Instant): Int = 0
}
