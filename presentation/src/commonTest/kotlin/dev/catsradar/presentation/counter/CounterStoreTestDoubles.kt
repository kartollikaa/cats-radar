package dev.catsradar.presentation.counter

import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.EncounterKind
import dev.catsradar.domain.model.EncounterOrigin
import dev.catsradar.domain.model.LocationSource
import dev.catsradar.domain.platform.DeviceIdProvider
import dev.catsradar.domain.platform.IdGenerator
import dev.catsradar.domain.repository.EncounterRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlin.time.Clock
import kotlin.time.Instant

internal class FakeEncounterRepository : EncounterRepository {
    private val encounters = MutableStateFlow<List<Encounter>>(emptyList())
    val insertedIds = mutableListOf<String>()
    val softDeletedIds = mutableListOf<String>()
    var insertShouldThrow: Throwable? = null

    override fun observeAll(): Flow<List<Encounter>> = encounters
    override fun observeActiveCount(): Flow<Int> = encounters.map { list -> list.count { it.deletedAt == null } }
    override fun observeById(id: String): Flow<Encounter?> = encounters.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun insert(encounter: Encounter) {
        insertShouldThrow?.let { throw it }
        insertedIds += encounter.id
        encounters.update { it + encounter }
    }

    override suspend fun update(encounter: Encounter) {
        encounters.update { list -> list.map { if (it.id == encounter.id) encounter else it } }
    }

    override suspend fun softDelete(id: String, deletedAt: Instant) {
        softDeletedIds += id
        encounters.update { list -> list.map { if (it.id == id) it.copy(deletedAt = deletedAt) else it } }
    }

    override suspend fun undoDelete(id: String) {
        encounters.update { list -> list.map { if (it.id == id) it.copy(deletedAt = null) else it } }
    }

    override suspend fun findBySourceDigest(sourceDigest: String): Encounter? = null

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

internal class FakeDeviceIdProvider(private val id: String = "device-1") : DeviceIdProvider {
    override suspend fun deviceId(): String = id
}

internal class FakeClock(private val instant: Instant) : Clock {
    override fun now(): Instant = instant
}
