package dev.catsradar.data.repository

import dev.catsradar.domain.model.EncounterPhoto
import kotlin.time.Instant

/**
 * The photo a cat's own record carries in its photo fields, or null when it has no copy. Those fields name
 * no photo of their own, so it takes the cat's id, install and creation time.
 */
@Suppress("LongParameterList") // one parameter per photo field the record carries
internal fun carriedPhoto(
    encounterId: String,
    deviceId: String,
    createdAt: Instant,
    photoPath: String?,
    thumbPath: String?,
    galleryUri: String?,
    sourceMediaUri: String?,
    sourceDigest: String?,
): EncounterPhoto? = photoPath?.let {
    EncounterPhoto(
        id = encounterId,
        encounterId = encounterId,
        photoPath = it,
        thumbPath = thumbPath,
        galleryUri = galleryUri,
        sourceMediaUri = sourceMediaUri,
        sourceDigest = sourceDigest,
        deviceId = deviceId,
        addedAt = createdAt,
    )
}
