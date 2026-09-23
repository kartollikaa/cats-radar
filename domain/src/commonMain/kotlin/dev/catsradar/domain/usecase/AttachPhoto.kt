package dev.catsradar.domain.usecase

import dev.catsradar.domain.model.PhotoStamp
import dev.catsradar.domain.platform.Digest
import dev.catsradar.domain.platform.GallerySaver
import dev.catsradar.domain.platform.IdGenerator
import dev.catsradar.domain.platform.ImageResizer
import dev.catsradar.domain.platform.PhotoStorage
import dev.catsradar.domain.platform.StoredPhoto
import dev.catsradar.domain.repository.EncounterRepository
import dev.catsradar.domain.repository.SettingsRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.time.Clock

/** Where a photo being attached came from: only a camera original is the gallery's to keep. */
enum class PhotoSource { CAMERA, GALLERY }

sealed interface AttachResult {
    data object Attached : AttachResult

    /** The image could not be decoded; the cat is unchanged. */
    data object Unreadable : AttachResult

    /** The cat is gone or already has a photo; it is unchanged and no file of the attempt is kept. */
    data object NotAttachable : AttachResult
}

@Suppress("LongParameterList") // one parameter per collaborator; a holder would exist only to lower the count
class AttachPhoto(
    private val encounterRepository: EncounterRepository,
    private val settingsRepository: SettingsRepository,
    private val imageResizer: ImageResizer,
    private val digest: Digest,
    private val gallerySaver: GallerySaver,
    private val photoStorage: PhotoStorage,
    private val idGenerator: IdGenerator,
    private val clock: Clock,
) {
    suspend operator fun invoke(encounterId: String, sourceUri: String, source: PhotoSource): AttachResult {
        val target = encounterRepository.observeById(encounterId).first()
        return if (target == null || target.photoPath != null) {
            AttachResult.NotAttachable
        } else {
            storeAndAttach(encounterId, sourceUri, source)
        }
    }

    private suspend fun storeAndAttach(encounterId: String, sourceUri: String, source: PhotoSource): AttachResult {
        // Not the cat's id: an attempt that loses the row to another must remove only its own files.
        val baseName = idGenerator.newId()
        val stored = imageResizer.store(sourceUri, baseName) ?: return AttachResult.Unreadable
        val galleryUri = if (source == PhotoSource.CAMERA && settingsRepository.saveOriginalsToGallery().first()) {
            gallerySaver.save(sourceUri, "$baseName.jpg")
        } else {
            null
        }
        val stamp = PhotoStamp(
            photoPath = stored.photoPath,
            thumbPath = stored.thumbPath,
            galleryUri = galleryUri,
            sourceDigest = digest.sha256(sourceUri),
            updatedAt = clock.now(),
        )

        var attached = false
        try {
            attached = encounterRepository.attachPhoto(encounterId, stamp)
        } finally {
            if (!attached) withContext(NonCancellable) { discard(stored) }
        }
        return if (attached) AttachResult.Attached else AttachResult.NotAttachable
    }

    private suspend fun discard(stored: StoredPhoto) {
        photoStorage.delete(stored.photoPath)
        stored.thumbPath?.let { photoStorage.delete(it) }
    }
}
