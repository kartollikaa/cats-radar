package dev.catsradar.domain.time

import dev.catsradar.domain.model.Encounter
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.asTimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/** The calendar day the encounter happened on, in the offset it was captured at. */
fun Encounter.localDate(): LocalDate =
    occurredAt.toLocalDateTime(UtcOffset(minutes = tzOffsetMinutes).asTimeZone()).date

fun Clock.today(zone: TimeZone = TimeZone.currentSystemDefault()): LocalDate =
    now().toLocalDateTime(zone).date
