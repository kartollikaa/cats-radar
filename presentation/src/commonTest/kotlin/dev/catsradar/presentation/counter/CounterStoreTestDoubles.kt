package dev.catsradar.presentation.counter

import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.EncounterKind
import dev.catsradar.domain.model.EncounterOrigin
import dev.catsradar.domain.model.LocationSource
import dev.catsradar.domain.model.LocationStamp
import dev.catsradar.domain.model.PlaceCell
import dev.catsradar.domain.model.PlaceStatus
import dev.catsradar.domain.platform.DeviceIdProvider
import dev.catsradar.domain.platform.Digest
import dev.catsradar.domain.platform.ExifData
import dev.catsradar.domain.platform.ExifReader
import dev.catsradar.domain.platform.GallerySaver
import dev.catsradar.domain.platform.IdGenerator
import dev.catsradar.domain.platform.ImageResizer
import dev.catsradar.domain.platform.LocationPermissionRequestState
import dev.catsradar.domain.platform.StoredPhoto
import dev.catsradar.domain.repository.EncounterRepository
import dev.catsradar.domain.repository.PlaceCellRepository
import dev.catsradar.domain.repository.SettingsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

internal class FakeEncounterRepository : EncounterRepository {
    private val encounters = MutableStateFlow<List<Encounter>>(emptyList())
    val insertedIds = mutableListOf<String>()
    val softDeletedIds = mutableListOf<String>()
    var insertShouldThrow: Throwable? = null
    var softDeleteShouldThrow: Throwable? = null
    val softDeleteAllCalls = mutableListOf<List<String>>()
    var softDeleteAllShouldThrow: Throwable? = null
    var softDeleteAllGate: CompletableDeferred<Unit>? = null
    var undoDeleteAllShouldThrow: Throwable? = null

    /** Consumed one per insert, in call order: a write held back lands after the ones behind it. */
    val insertDelays = ArrayDeque<Duration>()
    var softDeleteDelay: Duration = Duration.ZERO

    fun encounters(): List<Encounter> = encounters.value

    override fun observeAll(): Flow<List<Encounter>> = encounters
    override fun observeById(id: String): Flow<Encounter?> =
        encounters.map { list -> list.firstOrNull { it.id == id && it.deletedAt == null } }

    override suspend fun insert(encounter: Encounter) {
        insertDelays.removeFirstOrNull()?.let { delay(it) }
        insertShouldThrow?.let { throw it }
        insertedIds += encounter.id
        encounters.update { it + encounter }
    }

    override suspend fun update(encounter: Encounter) {
        encounters.update { list -> list.map { if (it.id == encounter.id) encounter else it } }
    }

    override suspend fun attachLocation(id: String, stamp: LocationStamp) {
        encounters.update { list ->
            list.map { encounter ->
                if (encounter.id == id && encounter.deletedAt == null) {
                    encounter.copy(
                        lat = stamp.lat,
                        lon = stamp.lon,
                        accuracyMeters = stamp.accuracyMeters,
                        locationSource = stamp.locationSource,
                        locationFixedAt = stamp.locationFixedAt,
                        geohash = stamp.geohash,
                        placeCellId = stamp.placeCellId,
                        updatedAt = stamp.updatedAt,
                    )
                } else {
                    encounter
                }
            }
        }
    }

    override suspend fun softDelete(id: String, deletedAt: Instant) {
        delay(softDeleteDelay)
        softDeleteShouldThrow?.let { throw it }
        softDeletedIds += id
        encounters.update { list -> list.map { if (it.id == id) it.copy(deletedAt = deletedAt) else it } }
    }

    override suspend fun undoDelete(id: String) {
        encounters.update { list -> list.map { if (it.id == id) it.copy(deletedAt = null) else it } }
    }

    override suspend fun softDeleteAll(ids: List<String>, deletedAt: Instant) {
        softDeleteAllCalls += ids
        softDeleteAllGate?.await()
        softDeleteAllShouldThrow?.let { throw it }
        encounters.update { list ->
            list.map { if (it.id in ids && it.deletedAt == null) it.copy(deletedAt = deletedAt) else it }
        }
    }

    override suspend fun undoDeleteAll(ids: List<String>, deletedAt: Instant) {
        undoDeleteAllShouldThrow?.let { throw it }
        encounters.update { list ->
            list.map { if (it.id in ids && it.deletedAt == deletedAt) it.copy(deletedAt = null) else it }
        }
    }

    override suspend fun loadEvery(): List<Encounter> = encounters.value

    override suspend fun findBySourceDigest(sourceDigest: String): Encounter? = null

    override suspend fun loadDeletedBefore(cutoff: Instant): List<Encounter> =
        encounters.value.filter { it.deletedAt != null && it.deletedAt!! < cutoff }

    override suspend fun purgeDeletedBefore(cutoff: Instant): Int = 0
}

// A tally logged by something other than this Store — e.g. the widget (F4) writing to the same
// repository — to prove the total is read back from the repository, not kept locally.
internal fun externalEncounter(id: String, occurredAt: Instant = Instant.parse("2026-01-01T00:00:00Z")): Encounter =
    Encounter(
        id = id,
        occurredAt = occurredAt,
        tzOffsetMinutes = 0,
        kind = EncounterKind.TALLY,
        origin = EncounterOrigin.WIDGET,
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
        deviceId = "external-device",
        createdAt = occurredAt,
        updatedAt = occurredAt,
        deletedAt = null,
    )

internal class FakeIdGenerator : IdGenerator {
    private var counter = 0
    override fun newId(): String = "id-${++counter}"
}

internal class FakeDeviceIdProvider(override val deviceId: String = "device-1") : DeviceIdProvider

internal class FakeClock(private val instant: Instant) : Clock {
    override fun now(): Instant = instant
}

internal class FakeLocationPermissionRequestState(
    initiallyRequested: Boolean = false,
) : LocationPermissionRequestState {
    override var alreadyRequested: Boolean = initiallyRequested
        private set

    override fun markRequested() {
        alreadyRequested = true
    }
}

// :domain's own fakes live in its commonTest, which a sibling module cannot see; these are the
// smallest stand-ins the Counter's photo path needs.
internal class FakeExifReader(var data: ExifData = ExifData()) : ExifReader {
    override suspend fun read(uri: String): ExifData = data
}

internal class FakeImageResizer(
    var result: StoredPhoto? = StoredPhoto(photoPath = "cat.jpg", thumbPath = "cat_thumb.jpg"),
) : ImageResizer {
    override suspend fun store(sourceUri: String, encounterId: String): StoredPhoto? = result
}

internal class FakeDigest : Digest {
    override suspend fun sha256(uri: String): String? = "digest"
}

internal class FakePlaceCellRepository : PlaceCellRepository {
    private val cells = MutableStateFlow<List<PlaceCell>>(emptyList())

    override fun observeAll(): Flow<List<PlaceCell>> = cells

    override suspend fun upsert(cell: PlaceCell) {
        cells.update { list -> list.filterNot { it.cellId == cell.cellId } + cell }
    }

    override suspend fun loadById(cellId: String): PlaceCell? = cells.value.firstOrNull { it.cellId == cellId }

    override suspend fun loadPendingPage(limit: Int, offset: Int): List<PlaceCell> =
        cells.value.filter { it.status == PlaceStatus.PENDING }.drop(offset).take(limit)
}

internal class FakeGallerySaver : GallerySaver {
    var calls = 0
        private set

    override suspend fun save(sourceUri: String, displayName: String): String? {
        calls++
        return "content://gallery/1"
    }
}

internal class FakeSettingsRepository(
    saveOriginals: Boolean = true,
    lastMilestone: Int = 0,
    private val writesFail: Boolean = false,
) : SettingsRepository {
    private val state = MutableStateFlow(saveOriginals)
    private val milestone = MutableStateFlow(lastMilestone)

    override fun saveOriginalsToGallery(): Flow<Boolean> = state

    override suspend fun setSaveOriginalsToGallery(enabled: Boolean) {
        state.value = enabled
    }

    private val walking = MutableStateFlow(false)

    override fun walkingMode(): Flow<Boolean> = walking

    override suspend fun setWalkingMode(enabled: Boolean) {
        check(!writesFail) { "preferences unwritable" }
        walking.value = enabled
    }

    override fun lastSeenMilestone(): Flow<Int> = milestone

    override suspend fun setLastSeenMilestone(value: Int) {
        milestone.value = value
    }
}
