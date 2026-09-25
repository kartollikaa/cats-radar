package dev.catsradar.domain.usecase

import dev.catsradar.domain.Tuning
import dev.catsradar.domain.platform.PhotoStorage
import dev.catsradar.domain.repository.EncounterRepository
import kotlin.time.Clock
import kotlin.time.Duration

/**
 * Removes encounters that were soft-deleted long enough ago that nobody is coming back for them,
 * and the photo files with them.
 */
class PurgeDeleted(
    private val encounterRepository: EncounterRepository,
    private val photoStorage: PhotoStorage,
    private val clock: Clock,
    private val purgeAfter: Duration = Tuning.PURGE_AFTER,
) {
    /** Returns how many rows went. */
    suspend operator fun invoke(): Int {
        val cutoff = clock.now() - purgeAfter
        // Files first: a row deleted before its files would leave orphans nothing points at, and
        // nothing would ever look for them again.
        encounterRepository.loadDeletedBefore(cutoff)
            .flatMap { it.photos }
            .forEach { photo ->
                photoStorage.delete(photo.photoPath)
                photo.thumbPath?.let { photoStorage.delete(it) }
            }
        return encounterRepository.purgeDeletedBefore(cutoff)
    }
}
