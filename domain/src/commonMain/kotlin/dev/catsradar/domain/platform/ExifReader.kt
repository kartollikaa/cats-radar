package dev.catsradar.domain.platform

import kotlin.time.Instant

/** What a photo's own metadata says about where and when it was taken. Any field may be absent. */
data class ExifData(
    val lat: Double? = null,
    val lon: Double? = null,
    val takenAt: Instant? = null,
    val tzOffsetMinutes: Int? = null,
)

interface ExifReader {
    /** Empty [ExifData] when [uri] has no metadata, cannot be opened, or is not a readable image. */
    suspend fun read(uri: String): ExifData
}
