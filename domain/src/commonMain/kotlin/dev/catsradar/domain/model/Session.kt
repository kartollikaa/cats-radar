package dev.catsradar.domain.model

import kotlin.time.Duration
import kotlin.time.Instant

data class Session(
    val count: Int,
    val start: Instant,
    val end: Instant,
    val duration: Duration,
)
