package dev.catsradar.domain.platform

import dev.catsradar.domain.about.BuildInfo

/** Reads the facts afresh on each call, so a changed locale or time zone shows up. */
fun interface BuildInfoReader {
    suspend fun read(): BuildInfo
}
