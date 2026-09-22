package dev.catsradar.presentation.encounters

import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.LocationSource
import dev.catsradar.domain.session.SessionSplitter
import dev.catsradar.domain.time.localDate
import dev.catsradar.presentation.DateTimeFormatter
import kotlinx.collections.immutable.toPersistentList
import kotlinx.datetime.LocalDate
import kotlinx.datetime.UtcOffset

class EncountersStateMapper(private val dateTimeFormatter: DateTimeFormatter) {

    fun map(encounters: List<Encounter>, today: LocalDate): EncountersState {
        val rows = SessionSplitter.groupByOuting(encounters)
            .asReversed()
            .flatMap { outing -> outingToItems(outing, today) }
            .toPersistentList()
        return EncountersState(rows = rows, isEmpty = rows.isEmpty())
    }

    // The header carries the outing's start date (its earliest encounter, per outings.md), even
    // when the outing runs past midnight - it never gains a second header partway through.
    private fun outingToItems(outing: List<Encounter>, today: LocalDate): List<EncounterListItem> {
        val earliest = outing.first()
        val header = EncounterListItem.DayHeader(
            key = "header-${earliest.id}",
            dayLabel = dateTimeFormatter.dayHeader(earliest.localDate(), today),
        )
        return listOf(header) + outing.asReversed().map(::toRowItem)
    }

    private fun toRowItem(encounter: Encounter): EncounterListItem.Row = EncounterListItem.Row(
        id = encounter.id,
        timeLabel = dateTimeFormatter.time(encounter.occurredAt, UtcOffset(minutes = encounter.tzOffsetMinutes)),
        locationLabel = encounter.locationSource.toLocationLabel(),
    )

    private fun LocationSource.toLocationLabel(): String = when (this) {
        LocationSource.EXIF -> "From photo"
        LocationSource.CURRENT_FIX -> "Current location"
        LocationSource.LAST_KNOWN -> "Last known location"
        LocationSource.BACKFILLED -> "From this outing"
        LocationSource.NONE -> "No location yet"
    }
}
