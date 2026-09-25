package dev.catsradar.domain.walk

import dev.catsradar.domain.model.Walk
import kotlin.time.Instant

/** Whether [at] falls within this walk, both ends included; a walk still on has no end yet. */
fun Walk.covers(at: Instant): Boolean = at >= startedAt && endedAt.let { it == null || at <= it }

/** Whether this walk and the span [from]..[to] share a moment, ends included; a walk still on has no end yet. */
fun Walk.overlaps(from: Instant, to: Instant): Boolean = startedAt <= to && endedAt.let { it == null || it >= from }
