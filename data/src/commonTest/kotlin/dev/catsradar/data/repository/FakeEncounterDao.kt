package dev.catsradar.data.repository

import dev.catsradar.data.db.EncounterDao
import dev.catsradar.data.db.EncounterEntity
import dev.catsradar.domain.model.LocationSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.time.Instant

internal data class AttachLocationCall(
    val id: String,
    val lat: Double,
    val lon: Double,
    val accuracyMeters: Float?,
    val locationSource: LocationSource,
    val locationFixedAt: Instant,
    val geohash: String,
    val placeCellId: String,
    val updatedAt: Instant,
)

internal class FakeEncounterDao : EncounterDao {
    var observeAllResult: List<EncounterEntity> = emptyList()
    var observeByIdResult: EncounterEntity? = null
    var findBySourceDigestResult: EncounterEntity? = null
    var purgeDeletedBeforeResult: Int = 0
    var loadDeletedBeforeResult: List<EncounterEntity> = emptyList()
    var loadEveryResult: List<EncounterEntity> = emptyList()

    val inserted = mutableListOf<EncounterEntity>()
    val updated = mutableListOf<EncounterEntity>()
    var observeByIdCall: String? = null
    var softDeleteCall: Pair<String, Instant>? = null
    var clearDeletedAtCall: String? = null
    val clearDeletedAtIfDeletedAtCalls = mutableListOf<Pair<String, Instant>>()
    var attachLocationCall: AttachLocationCall? = null
    var findBySourceDigestCall: String? = null
    var purgeDeletedBeforeCall: Instant? = null
    var loadDeletedBeforeCall: Instant? = null

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

    override suspend fun clearDeletedAtIfDeletedAt(id: String, deletedAt: Instant) {
        clearDeletedAtIfDeletedAtCalls += id to deletedAt
    }

    @Suppress("LongParameterList") // mirrors EncounterDao.attachLocation's own Room binding constraint
    override suspend fun attachLocation(
        id: String,
        lat: Double,
        lon: Double,
        accuracyMeters: Float?,
        locationSource: LocationSource,
        locationFixedAt: Instant,
        geohash: String,
        placeCellId: String,
        updatedAt: Instant,
    ) {
        attachLocationCall = AttachLocationCall(
            id, lat, lon, accuracyMeters, locationSource, locationFixedAt, geohash, placeCellId, updatedAt,
        )
    }

    override suspend fun findBySourceDigest(sourceDigest: String): EncounterEntity? {
        findBySourceDigestCall = sourceDigest
        return findBySourceDigestResult
    }
    override suspend fun loadEvery(): List<EncounterEntity> = loadEveryResult

    override suspend fun loadDeletedBefore(cutoff: Instant): List<EncounterEntity> {
        loadDeletedBeforeCall = cutoff
        return loadDeletedBeforeResult
    }

    override suspend fun purgeDeletedBefore(cutoff: Instant): Int {
        purgeDeletedBeforeCall = cutoff
        return purgeDeletedBeforeResult
    }
}
