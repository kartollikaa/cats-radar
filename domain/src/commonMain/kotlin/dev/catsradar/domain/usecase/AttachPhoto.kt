package dev.catsradar.domain.usecase

import dev.catsradar.domain.analytics.Analytics
import dev.catsradar.domain.analytics.AnalyticsEvent
import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.EncounterPhoto
import dev.catsradar.domain.platform.DeviceIdProvider
import dev.catsradar.domain.platform.Digest
import dev.catsradar.domain.platform.GalleryItemLocator
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

    /** The cat is gone or already has a photo; it is unchanged and the attempt's own copies are removed. */
    data object NotAttachable : AttachResult
}

@Suppress("LongParameterList") // one parameter per collaborator; a holder would exist only to lower the count
class AttachPhoto(
    private val encounterRepository: EncounterRepository,
    private val settingsRepository: SettingsRepository,
    private val imageResizer: ImageResizer,
    private val digest: Digest,
    private val gallerySaver: GallerySaver,
    private val galleryItemLocator: GalleryItemLocator,
    private val photoStorage: PhotoStorage,
    private val idGenerator: IdGenerator,
    private val deviceIdProvider: DeviceIdProvider,
    private val clock: Clock,
    private val analytics: Analytics,
) {
    suspend operator fun invoke(encounterId: String, sourceUri: String, source: PhotoSource): AttachResult {
        val target = encounterRepository.observeById(encounterId).first()
        return if (target == null || target.photos.isNotEmpty()) {
            AttachResult.NotAttachable
        } else {
            storeAndAttach(target, sourceUri, source)
        }
    }

    private suspend fun storeAndAttach(target: Encounter, sourceUri: String, source: PhotoSource): AttachResult {
        // Gallery ids are per phone and this photo names its cat's install, so a link is kept only on this
        // install's cats: recorded on another's, it would open a different photo back there.
        val keepsLinks = target.deviceId == deviceIdProvider.deviceId
        // Not the cat's id: an attempt that loses the row to another must remove only its own files.
        val baseName = idGenerator.newId()
        val stored = imageResizer.store(sourceUri, baseName) ?: return AttachResult.Unreadable

        var attached = false
        try {
            val galleryUri = if (
                source == PhotoSource.CAMERA && settingsRepository.saveOriginalsToGallery().first()
            ) {
                gallerySaver.save(sourceUri, "$baseName.jpg")
            } else {
                null
            }
            val pickedItem = if (keepsLinks && source == PhotoSource.GALLERY) {
                galleryItemLocator.locate(sourceUri)
            } else {
                null
            }
            val photo = EncounterPhoto(
                id = target.id,
                encounterId = target.id,
                photoPath = stored.photoPath,
                thumbPath = stored.thumbPath,
                galleryUri = galleryUri?.takeIf { keepsLinks },
                sourceMediaUri = pickedItem,
                sourceDigest = digest.sha256(sourceUri),
                deviceId = target.deviceId,
                addedAt = clock.now(),
            )
            // Once the write lands, these files are the cat's, so the write must not be cancelled.
            withContext(NonCancellable) { attached = encounterRepository.addPhoto(photo) }
        } finally {
            if (!attached) withContext(NonCancellable) { discard(stored) }
        }
        if (attached) analytics.log(AnalyticsEvent.PhotoAttached(source))
        return if (attached) AttachResult.Attached else AttachResult.NotAttachable
    }

    private suspend fun discard(stored: StoredPhoto) {
        photoStorage.delete(stored.photoPath)
        stored.thumbPath?.let { photoStorage.delete(it) }
    }
}
