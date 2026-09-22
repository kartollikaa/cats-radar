package dev.catsradar.domain.platform

import kotlin.time.Instant

/** The date a picked file carries outside its own image metadata. */
interface SourceFileTime {
    /** Null when [uri] has no date, or the provider behind it will not say. */
    suspend fun createdAt(uri: String): Instant?
}
