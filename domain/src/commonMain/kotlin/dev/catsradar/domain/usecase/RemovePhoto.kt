package dev.catsradar.domain.usecase

import dev.catsradar.domain.platform.PhotoStorage
import dev.catsradar.domain.repository.EncounterRepository
import kotlinx.coroutines.flow.first
import kotlin.time.Clock

class RemovePhoto(
    private val encounterRepository: EncounterRepository,
    private val photoStorage: PhotoStorage,
    private val clock: Clock,
) {
    suspend operator fun invoke(encounterId: String, photoId: String): Boolean {
        val photo = encounterRepository.observeById(encounterId).first()
            ?.photos
            ?.firstOrNull { it.id == photoId }
            ?: return false
        photoStorage.delete(photo.photoPath)
        photo.thumbPath?.let { photoStorage.delete(it) }
        return encounterRepository.removePhoto(encounterId, photoId, clock.now())
    }
}
