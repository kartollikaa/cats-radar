package dev.catsradar.domain.model

import kotlin.time.Instant

/** One photo of a cat. [photoPath] and [thumbPath] are relative to the photo directory. */
data class EncounterPhoto(
    val id: String,
    val encounterId: String,
    val photoPath: String,
    /** Null when the thumbnail could not be written. */
    val thumbPath: String?,
    /** The camera original the app saved to the gallery. */
    val galleryUri: String?,
    /** The gallery item a picked photo came from. */
    val sourceMediaUri: String?,
    /** SHA-256 of the bytes the source handed over. */
    val sourceDigest: String?,
    /** The install that recorded [galleryUri] and [sourceMediaUri]. */
    val deviceId: String,
    val addedAt: Instant,
)
