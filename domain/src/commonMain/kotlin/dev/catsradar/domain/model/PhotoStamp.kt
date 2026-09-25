package dev.catsradar.domain.model

import kotlin.time.Instant

/** The photo columns written together when a cat logged without a photo is given one. */
data class PhotoStamp(
    val photoPath: String,
    val thumbPath: String?,
    val galleryUri: String?,
    val sourceMediaUri: String?,
    val sourceDigest: String?,
    val updatedAt: Instant,
)
