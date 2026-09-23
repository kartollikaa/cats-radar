package dev.catsradar.domain.testing

import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.LocationStamp
import dev.catsradar.domain.repository.EncounterRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlin.time.Instant

class FakeEncounterRepository : EncounterRepository {
    private val encounters = MutableStateFlow<List<Encounter>>(emptyList())
    val inserted = mutableListOf<Encounter>()
    val softDeleteCalls = mutableListOf<Pair<String, Instant>>()
    val softDeleteAllCalls = mutableListOf<Pair<List<String>, Instant>>()
    val undoDeleteAllCalls = mutableListOf<Pair<List<String>, Instant>>()
    val attachLocationCalls = mutableListOf<String>()
    val purgeCalls = mutableListOf<Instant>()

    // Mirrors the DAO's deletedAt IS NULL filter; a fake that returned deleted rows here would
    // hide every bug about what a read is allowed to see.
    override fun observeAll(): Flow<List<Encounter>> =
        encounters.map { list -> list.filter { it.deletedAt == null } }
    override fun observeById(id: String): Flow<Encounter?> = encounters.map { list -> list.firstOrNull { it.id == id } }

    // Mirrors the DAO's plain @Insert, which aborts on an id that is already there.
    override suspend fun insert(encounter: Encounter) {
        check(encounters.value.none { it.id == encounter.id }) { "UNIQUE constraint failed: ${encounter.id}" }
        inserted += encounter
        encounters.update { it + encounter }
    }

    override suspend fun update(encounter: Encounter) {
        encounters.update { list -> list.map { if (it.id == encounter.id) encounter else it } }
    }

    // Re-checks deletedAt against the state at write time, not a caller's earlier snapshot -
    // mirrors the real DAO's WHERE id = :id AND deletedAt IS NULL guard.
    override suspend fun attachLocation(id: String, stamp: LocationStamp) {
        attachLocationCalls += id
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
        softDeleteCalls += id to deletedAt
        encounters.update { list -> list.map { if (it.id == id) it.copy(deletedAt = deletedAt) else it } }
    }

    override suspend fun undoDelete(id: String) {
        encounters.update { list -> list.map { if (it.id == id) it.copy(deletedAt = null) else it } }
    }

    override suspend fun softDeleteAll(ids: List<String>, deletedAt: Instant) {
        softDeleteAllCalls += ids to deletedAt
        encounters.update { list ->
            list.map { if (it.id in ids && it.deletedAt == null) it.copy(deletedAt = deletedAt) else it }
        }
    }

    override suspend fun undoDeleteAll(ids: List<String>, deletedAt: Instant) {
        undoDeleteAllCalls += ids to deletedAt
        encounters.update { list ->
            list.map { if (it.id in ids && it.deletedAt == deletedAt) it.copy(deletedAt = null) else it }
        }
    }

    // Mirrors the DAO: a soft-deleted row does not block a re-import of the same bytes.
    override suspend fun findBySourceDigest(sourceDigest: String): Encounter? =
        encounters.value.firstOrNull { it.sourceDigest == sourceDigest && it.deletedAt == null }

    override suspend fun loadEvery(): List<Encounter> = encounters.value

    override suspend fun loadDeletedBefore(cutoff: Instant): List<Encounter> =
        encounters.value.filter { it.deletedAt != null && it.deletedAt!! < cutoff }

    override suspend fun purgeDeletedBefore(cutoff: Instant): Int {
        purgeCalls += cutoff
        val doomed = encounters.value.filter { it.deletedAt != null && it.deletedAt!! < cutoff }
        encounters.update { list -> list - doomed.toSet() }
        return doomed.size
    }
}
