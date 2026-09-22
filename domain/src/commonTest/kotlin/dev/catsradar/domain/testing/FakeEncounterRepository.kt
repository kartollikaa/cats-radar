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
    val attachLocationCalls = mutableListOf<String>()

    override fun observeAll(): Flow<List<Encounter>> = encounters
    override fun observeActiveCount(): Flow<Int> = encounters.map { list -> list.count { it.deletedAt == null } }
    override fun observeById(id: String): Flow<Encounter?> = encounters.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun insert(encounter: Encounter) {
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

    override suspend fun findBySourceDigest(sourceDigest: String): Encounter? = null

    override suspend fun purgeDeletedBefore(cutoff: Instant): Int = 0
}
