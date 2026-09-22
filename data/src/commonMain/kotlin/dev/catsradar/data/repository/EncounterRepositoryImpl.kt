package dev.catsradar.data.repository

import dev.catsradar.data.db.EncounterDao
import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.repository.EncounterRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Instant

class EncounterRepositoryImpl(private val dao: EncounterDao) : EncounterRepository {
    override fun observeAll(): Flow<List<Encounter>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override fun observeActiveCount(): Flow<Int> = dao.observeActiveCount()

    override fun observeById(id: String): Flow<Encounter?> = dao.observeById(id).map { it?.toDomain() }

    override suspend fun insert(encounter: Encounter) = dao.insert(encounter.toEntity())

    override suspend fun update(encounter: Encounter) = dao.update(encounter.toEntity())

    override suspend fun softDelete(id: String, deletedAt: Instant) = dao.softDelete(id, deletedAt)

    override suspend fun undoDelete(id: String) = dao.clearDeletedAt(id)

    override suspend fun findBySourceDigest(sourceDigest: String): Encounter? =
        dao.findBySourceDigest(sourceDigest)?.toDomain()

    override suspend fun purgeDeletedBefore(cutoff: Instant): Int = dao.purgeDeletedBefore(cutoff)
}
