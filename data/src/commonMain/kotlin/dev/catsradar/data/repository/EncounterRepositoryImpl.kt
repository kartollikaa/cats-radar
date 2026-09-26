package dev.catsradar.data.repository

import dev.catsradar.data.db.EncounterDao
import dev.catsradar.domain.model.CatCoat
import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.EncounterPhoto
import dev.catsradar.domain.model.LocationStamp
import dev.catsradar.domain.model.PlaceCellAssignment
import dev.catsradar.domain.repository.EncounterRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Instant

@Suppress("TooManyFunctions") // mirrors EncounterRepository one for one
class EncounterRepositoryImpl(private val dao: EncounterDao) : EncounterRepository {
    override fun observeAll(): Flow<List<Encounter>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override fun observeById(id: String): Flow<Encounter?> = dao.observeById(id).map { it?.toDomain() }

    override suspend fun insert(encounter: Encounter) =
        dao.insertWithPhotos(encounter.toEntity(), encounter.photos.map { it.toEntity() })

    override suspend fun update(encounter: Encounter) = dao.update(encounter.toEntity())

    override suspend fun attachLocation(id: String, stamp: LocationStamp): Boolean = dao.attachLocation(
        id = id,
        lat = stamp.lat,
        lon = stamp.lon,
        accuracyMeters = stamp.accuracyMeters,
        locationSource = stamp.locationSource,
        locationFixedAt = stamp.locationFixedAt,
        geohash = stamp.geohash,
        placeCellId = stamp.placeCellId,
        updatedAt = stamp.updatedAt,
    ) > 0

    override suspend fun addPhoto(photo: EncounterPhoto): Boolean = dao.addPhoto(photo.toEntity(), photo.addedAt)

    override suspend fun addPhotos(photos: List<EncounterPhoto>) = dao.addPhotos(photos.map { it.toEntity() })

    override suspend fun setCoat(id: String, coat: CatCoat?, updatedAt: Instant) {
        dao.setCoat(id, coat, updatedAt)
    }

    override suspend fun setPlaceCells(assignments: List<PlaceCellAssignment>) = dao.setPlaceCells(assignments)

    override suspend fun softDelete(id: String, deletedAt: Instant) = dao.softDelete(id, deletedAt)

    override suspend fun undoDelete(id: String) = dao.clearDeletedAt(id)

    override suspend fun softDeleteAll(ids: List<String>, deletedAt: Instant) = dao.softDeleteAll(ids, deletedAt)

    override suspend fun undoDeleteAll(ids: List<String>, deletedAt: Instant) = dao.undoDeleteAll(ids, deletedAt)

    override suspend fun findBySourceDigest(sourceDigest: String): Encounter? =
        dao.findBySourceDigest(sourceDigest)?.toDomain()

    override suspend fun loadEvery(): List<Encounter> = dao.loadEvery().map { it.toDomain() }

    override suspend fun loadDeletedBefore(cutoff: Instant): List<Encounter> =
        dao.loadDeletedBefore(cutoff).map { it.toDomain() }

    override suspend fun purgeDeletedBefore(cutoff: Instant): Int = dao.purgeDeletedBefore(cutoff)
}
