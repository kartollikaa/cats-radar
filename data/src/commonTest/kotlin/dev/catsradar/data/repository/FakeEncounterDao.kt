package dev.catsradar.data.repository

import dev.catsradar.data.db.EncounterDao
import dev.catsradar.data.db.EncounterEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.time.Instant

internal class FakeEncounterDao : EncounterDao {
    var observeAllResult: List<EncounterEntity> = emptyList()
    var observeByIdResult: EncounterEntity? = null
    var findBySourceDigestResult: EncounterEntity? = null
    var purgeDeletedBeforeResult: Int = 0

    val inserted = mutableListOf<EncounterEntity>()
    val updated = mutableListOf<EncounterEntity>()
    var observeByIdCall: String? = null
    var softDeleteCall: Pair<String, Instant>? = null
    var clearDeletedAtCall: String? = null
    var findBySourceDigestCall: String? = null
    var purgeDeletedBeforeCall: Instant? = null

    override fun observeAll(): Flow<List<EncounterEntity>> = flowOf(observeAllResult)

    override fun observeById(id: String): Flow<EncounterEntity?> {
        observeByIdCall = id
        return flowOf(observeByIdResult)
    }

    override suspend fun insert(encounter: EncounterEntity) {
        inserted += encounter
    }

    override suspend fun update(encounter: EncounterEntity) {
        updated += encounter
    }

    override suspend fun softDelete(id: String, deletedAt: Instant) {
        softDeleteCall = id to deletedAt
    }

    override suspend fun clearDeletedAt(id: String) {
        clearDeletedAtCall = id
    }

    override suspend fun findBySourceDigest(sourceDigest: String): EncounterEntity? {
        findBySourceDigestCall = sourceDigest
        return findBySourceDigestResult
    }

    override suspend fun purgeDeletedBefore(cutoff: Instant): Int {
        purgeDeletedBeforeCall = cutoff
        return purgeDeletedBeforeResult
    }
}
