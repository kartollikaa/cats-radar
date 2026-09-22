package dev.catsradar.domain.repository

import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.LocationStamp
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

@Suppress("TooManyFunctions") // one function per operation on one aggregate
interface EncounterRepository {
    fun observeAll(): Flow<List<Encounter>>

    fun observeActiveCount(): Flow<Int>

    fun observeById(id: String): Flow<Encounter?>

    suspend fun insert(encounter: Encounter)

    suspend fun update(encounter: Encounter)

    /** No-ops if the row was soft-deleted in the meantime; never resurrects it. */
    suspend fun attachLocation(id: String, stamp: LocationStamp)

    suspend fun softDelete(id: String, deletedAt: Instant)

    suspend fun undoDelete(id: String)

    suspend fun findBySourceDigest(sourceDigest: String): Encounter?

    /** Soft-deleted rows older than [cutoff]; the only read that can see them. */
    suspend fun loadDeletedBefore(cutoff: Instant): List<Encounter>

    suspend fun purgeDeletedBefore(cutoff: Instant): Int
}
