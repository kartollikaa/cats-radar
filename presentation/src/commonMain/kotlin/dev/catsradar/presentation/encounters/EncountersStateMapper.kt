package dev.catsradar.presentation.encounters

import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.platform.PhotoStorage
import dev.catsradar.domain.session.SessionSplitter
import dev.catsradar.domain.time.localDate
import dev.catsradar.presentation.DateTimeFormatter
import kotlinx.collections.immutable.toPersistentList
import kotlinx.datetime.LocalDate
import kotlinx.datetime.UtcOffset

class EncountersStateMapper(
    private val dateTimeFormatter: DateTimeFormatter,
    private val photoStorage: PhotoStorage,
) {

    fun map(encounters: List<Encounter>, today: LocalDate): EncountersState {
        val rows = SessionSplitter.groupByOuting(encounters)
            .asReversed()
            .flatMap { outing -> outingToItems(outing, today) }
            .toPersistentList()
        return EncountersState(rows = rows)
    }

    // Keyed to the outing's earliest encounter (its "start", per outings.md), so a midnight-
    // crossing outing keeps one header; the start time tells same-day outings apart.
    private fun outingToItems(outing: List<Encounter>, today: LocalDate): List<EncounterListItem> {
        val earliest = outing.first()
        val header = EncounterListItem.OutingHeader(
            key = "header-${earliest.id}",
            label = "${dateTimeFormatter.dayHeader(earliest.localDate(), today)}, ${earliest.timeLabel()}",
        )
        return listOf(header) + outing.asReversed().map(::toRowItem)
    }

    private fun toRowItem(encounter: Encounter): EncounterListItem.Row = EncounterListItem.Row(
        id = encounter.id,
        timeLabel = encounter.timeLabel(),
        location = encounter.locationSource.toLocationLabel(),
        thumbnailPath = encounter.thumbPath?.let(photoStorage::resolve),
    )

    private fun Encounter.timeLabel(): String =
        dateTimeFormatter.time(occurredAt, UtcOffset(minutes = tzOffsetMinutes))
}
