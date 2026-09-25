package dev.catsradar.domain.walk

import dev.catsradar.domain.model.Walk
import kotlin.time.Instant

/** Whether this walk and the span [from]..[to] share a moment, ends included; a walk still on has no end yet. */
fun Walk.overlaps(from: Instant, to: Instant): Boolean = startedAt <= to && endedAt.let { it == null || it >= from }
