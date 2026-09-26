package dev.catsradar.data.repository

import dev.catsradar.data.db.EncounterDao
import dev.catsradar.data.db.EncounterEntity
import dev.catsradar.data.db.EncounterPhotoEntity
import dev.catsradar.data.db.EncounterWithPhotos
import dev.catsradar.domain.model.CatCoat
import dev.catsradar.domain.model.LocationSource
import dev.catsradar.domain.model.PlaceCellAssignment
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

internal data class AddPhotoCall(val photo: EncounterPhotoEntity, val updatedAt: Instant)

internal data class SetCoatCall(val id: String, val coat: CatCoat?, val updatedAt: Instant)

internal class FakeEncounterDao : EncounterDao {
    var observeAllResult: List<EncounterWithPhotos> = emptyList()
    var observeByIdResult: EncounterWithPhotos? = null
    var findBySourceDigestResult: EncounterWithPhotos? = null
    var purgeDeletedBeforeResult: Int = 0
    var loadDeletedBeforeResult: List<EncounterWithPhotos> = emptyList()
    var loadEveryResult: List<EncounterWithPhotos> = emptyList()
    val insertedPhotos = mutableListOf<EncounterPhotoEntity>()
    val addedPhotos = mutableListOf<EncounterPhotoEntity>()

    val inserted = mutableListOf<EncounterEntity>()
    val updated = mutableListOf<EncounterEntity>()
    var observeByIdCall: String? = null
    var softDeleteCall: Pair<String, Instant>? = null
    var clearDeletedAtCall: String? = null
    val clearDeletedAtIfDeletedAtCalls = mutableListOf<Pair<String, Instant>>()
    var attachLocationCall: AttachLocationCall? = null
    var attachLocationRows = 1
    var addPhotoResult: Boolean = true
    var addPhotoCall: AddPhotoCall? = null
    var setCoatCall: SetCoatCall? = null
    val setPlaceCellCalls = mutableListOf<PlaceCellAssignment>()
    var findBySourceDigestCall: String? = null
    var purgeDeletedBeforeCall: Instant? = null
    var loadDeletedBeforeCall: Instant? = null

    override fun observeAll(): Flow<List<EncounterWithPhotos>> = flowOf(observeAllResult)

    override fun observeById(id: String): Flow<EncounterWithPhotos?> {
        observeByIdCall = id
        return flowOf(observeByIdResult)
    }

    override suspend fun insert(encounter: EncounterEntity) {
        inserted += encounter
    }

    override suspend fun update(encounter: EncounterEntity) {
        updated += encounter
    }

    override suspend fun insertPhotos(photos: List<EncounterPhotoEntity>) {
        insertedPhotos += photos
    }

    override suspend fun countLive(id: String): Int = error("addPhoto is recorded whole")

    override suspend fun stampUpdatedAt(id: String, updatedAt: Instant) = error("addPhoto is recorded whole")

    override suspend fun addPhoto(photo: EncounterPhotoEntity, updatedAt: Instant): Boolean {
        addPhotoCall = AddPhotoCall(photo, updatedAt)
        return addPhotoResult
    }

    override suspend fun addPhotos(photos: List<EncounterPhotoEntity>) {
        addedPhotos += photos
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
    ): Int {
        attachLocationCall = AttachLocationCall(
            id, lat, lon, accuracyMeters, locationSource, locationFixedAt, geohash, placeCellId, updatedAt,
        )
        return attachLocationRows
    }

    override suspend fun setCoat(id: String, coat: CatCoat?, updatedAt: Instant): Int {
        setCoatCall = SetCoatCall(id, coat, updatedAt)
        return 1
    }

    override suspend fun setPlaceCell(id: String, lat: Double, lon: Double, geohash: String, placeCellId: String) {
        setPlaceCellCalls += PlaceCellAssignment(id, lat, lon, geohash, placeCellId)
    }

    override suspend fun findBySourceDigest(sourceDigest: String): EncounterWithPhotos? {
        findBySourceDigestCall = sourceDigest
        return findBySourceDigestResult
    }
    override suspend fun loadEvery(): List<EncounterWithPhotos> = loadEveryResult

    override suspend fun loadDeletedBefore(cutoff: Instant): List<EncounterWithPhotos> {
        loadDeletedBeforeCall = cutoff
        return loadDeletedBeforeResult
    }

    override suspend fun purgeDeletedBefore(cutoff: Instant): Int {
        purgeDeletedBeforeCall = cutoff
        return purgeDeletedBeforeResult
    }
}
