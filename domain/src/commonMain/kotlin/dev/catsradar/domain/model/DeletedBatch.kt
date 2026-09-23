package dev.catsradar.domain.model

import kotlin.time.Instant

/** Encounters soft-deleted together; [deletedAt] is what tells them apart from rows deleted any other time. */
data class DeletedBatch(val ids: List<String>, val deletedAt: Instant)
