package dev.catsradar.domain.repository

import dev.catsradar.domain.model.Encounter
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

interface EncounterRepository {
    fun observeAll(): Flow<List<Encounter>>

    fun observeActiveCount(): Flow<Int>

    fun observeById(id: String): Flow<Encounter?>

    suspend fun insert(encounter: Encounter)

    suspend fun update(encounter: Encounter)

    suspend fun softDelete(id: String, deletedAt: Instant)

    suspend fun undoDelete(id: String)

    suspend fun findBySourceDigest(sourceDigest: String): Encounter?

    suspend fun purgeDeletedBefore(cutoff: Instant): Int
}
