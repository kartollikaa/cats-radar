package dev.catsradar.domain.usecase

import dev.catsradar.domain.analytics.Analytics
import dev.catsradar.domain.analytics.logged
import dev.catsradar.domain.model.CatCoat
import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.EncounterPhoto
import dev.catsradar.domain.model.LocationSource
import dev.catsradar.domain.platform.DeviceIdProvider
import dev.catsradar.domain.platform.IdGenerator
import dev.catsradar.domain.platform.PhotoStorage
import dev.catsradar.domain.platform.StoredPhoto
import dev.catsradar.domain.repository.EncounterRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.time.Clock

data class AddedCat(val id: String, val needsLocation: Boolean)

sealed interface AddCatsResult {
    data class Added(val cats: List<AddedCat>) : AddCatsResult
    data object NotAddable : AddCatsResult
}

@Suppress("LongParameterList") // one parameter per collaborator; a holder would exist only to lower the count
class AddCatsToPhoto(
    private val encounterRepository: EncounterRepository,
    private val photoStorage: PhotoStorage,
    private val idGenerator: IdGenerator,
    private val deviceIdProvider: DeviceIdProvider,
    private val clock: Clock,
    private val analytics: Analytics,
) {
    suspend operator fun invoke(
        sourceEncounterId: String,
        photoId: String,
        coats: List<CatCoat?>,
    ): AddCatsResult {
        if (coats.isEmpty()) return AddCatsResult.Added(emptyList())
        val source = encounterRepository.observeById(sourceEncounterId).first()
        val sourcePhoto = source?.photos?.firstOrNull { it.id == photoId }
        return if (source == null || sourcePhoto == null) {
            AddCatsResult.NotAddable
        } else {
            copyAndInsert(sourceEncounterId, source, sourcePhoto, coats)
        }
    }

    private suspend fun copyAndInsert(
        sourceEncounterId: String,
        source: Encounter,
        sourcePhoto: EncounterPhoto,
        coats: List<CatCoat?>,
    ): AddCatsResult {
        val copied = mutableListOf<StoredPhoto>()
        var committed = false
        try {
            val added = coats.map { coat ->
                val encounterId = idGenerator.newId()
                val newPhotoId = idGenerator.newId()
                val stored = photoStorage.copy(
                    StoredPhoto(sourcePhoto.photoPath, sourcePhoto.thumbPath),
                    newPhotoId,
                )
                copied += stored
                newEncounter(source, sourcePhoto, encounterId, newPhotoId, stored, coat)
            }
            val inserted = withContext(NonCancellable) {
                encounterRepository.insertAllIfSourceLive(sourceEncounterId, added)
            }
            if (!inserted) return AddCatsResult.NotAddable

            committed = true
            added.forEach { analytics.log(it.logged()) }
            return AddCatsResult.Added(
                added.map { AddedCat(it.id, needsLocation = it.locationSource == LocationSource.NONE) },
            )
        } finally {
            if (!committed) withContext(NonCancellable) { copied.forEach { discard(it) } }
        }
    }

    @Suppress("LongParameterList") // the source fields and fresh identities are already resolved by the caller
    private fun newEncounter(
        source: Encounter,
        sourcePhoto: EncounterPhoto,
        encounterId: String,
        photoId: String,
        stored: StoredPhoto,
        coat: CatCoat?,
    ): Encounter {
        val now = clock.now()
        return Encounter(
            id = encounterId,
            occurredAt = source.occurredAt,
            tzOffsetMinutes = source.tzOffsetMinutes,
            kind = source.kind,
            origin = source.origin,
            coat = coat,
            photos = listOf(
                EncounterPhoto(
                    id = photoId,
                    encounterId = encounterId,
                    photoPath = stored.photoPath,
                    thumbPath = stored.thumbPath,
                    galleryUri = sourcePhoto.galleryUri,
                    sourceMediaUri = sourcePhoto.sourceMediaUri,
                    sourceDigest = sourcePhoto.sourceDigest,
                    deviceId = sourcePhoto.deviceId,
                    addedAt = now,
                    shotId = sourcePhoto.shotId,
                ),
            ),
            lat = source.lat,
            lon = source.lon,
            accuracyMeters = source.accuracyMeters,
            locationSource = source.locationSource,
            locationFixedAt = source.locationFixedAt,
            geohash = source.geohash,
            placeCellId = source.placeCellId,
            deviceId = deviceIdProvider.deviceId,
            createdAt = now,
            updatedAt = now,
            deletedAt = null,
        )
    }

    private suspend fun discard(stored: StoredPhoto) {
        photoStorage.delete(stored.photoPath)
        stored.thumbPath?.let { photoStorage.delete(it) }
    }
}
