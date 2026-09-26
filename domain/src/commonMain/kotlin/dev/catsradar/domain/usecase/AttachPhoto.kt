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
    data class Attached(val photoId: String) : AttachResult

    /** The image could not be decoded; the cat is unchanged. */
    data object Unreadable : AttachResult

    /** One of this cat's photos is already this one; nothing was copied and the cat is unchanged. */
    data object AlreadyThere : AttachResult

    /** The cat is gone; the attempt's own copies are removed. */
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
        val target = encounterRepository.observeById(encounterId).first() ?: return AttachResult.NotAttachable
        // Ahead of the copy: a photo this cat already has should cost no disk at all.
        val sourceDigest = digest.sha256(sourceUri)
        return if (sourceDigest != null && target.photos.any { it.sourceDigest == sourceDigest }) {
            AttachResult.AlreadyThere
        } else {
            storeAndAttach(target, sourceUri, source, sourceDigest)
        }
    }

    private suspend fun storeAndAttach(
        target: Encounter,
        sourceUri: String,
        source: PhotoSource,
        sourceDigest: String?,
    ): AttachResult {
        // Not the cat's id: the photo is one of several, and an attempt that fails removes only its own files.
        val photoId = idGenerator.newId()
        val stored = imageResizer.store(sourceUri, photoId) ?: return AttachResult.Unreadable

        var attached = false
        try {
            val galleryUri = if (
                source == PhotoSource.CAMERA && settingsRepository.saveOriginalsToGallery().first()
            ) {
                gallerySaver.save(sourceUri, "$photoId.jpg")
            } else {
                null
            }
            val photo = EncounterPhoto(
                id = photoId,
                encounterId = target.id,
                photoPath = stored.photoPath,
                thumbPath = stored.thumbPath,
                galleryUri = galleryUri,
                sourceMediaUri = if (source == PhotoSource.GALLERY) galleryItemLocator.locate(sourceUri) else null,
                sourceDigest = sourceDigest,
                // Gallery ids mean something only here, so the photo names this install whoever logged its cat.
                deviceId = deviceIdProvider.deviceId,
                addedAt = clock.now(),
                shotId = photoId,
            )
            // Once the write lands, these files are the cat's, so the write must not be cancelled.
            withContext(NonCancellable) { attached = encounterRepository.addPhoto(photo) }
        } finally {
            if (!attached) withContext(NonCancellable) { discard(stored) }
        }
        if (attached) analytics.log(AnalyticsEvent.PhotoAttached(source))
        return if (attached) AttachResult.Attached(photoId) else AttachResult.NotAttachable
    }

    private suspend fun discard(stored: StoredPhoto) {
        photoStorage.delete(stored.photoPath)
        stored.thumbPath?.let { photoStorage.delete(it) }
    }
}
