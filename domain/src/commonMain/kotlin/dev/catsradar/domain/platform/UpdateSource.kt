package dev.catsradar.domain.platform

import dev.catsradar.domain.update.ReleaseFeed

/** Where published releases of the app are listed; drafts are never among them. */
fun interface UpdateSource {
    suspend fun releases(): ReleaseFeed
}
