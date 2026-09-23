package dev.catsradar.presentation.encounters

import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.platform.PhotoStorage
import dev.catsradar.domain.session.SessionSplitter
import dev.catsradar.domain.time.localDate
import dev.catsradar.presentation.DateTimeFormatter
import dev.catsradar.presentation.coat.toOption
import dev.catsradar.presentation.map.isOnTheMap
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
            label = outingLabel(outing, today),
            mapOutingId = earliest.id.takeIf { outing.any { it.isOnTheMap() } },
        )
        val rows = outing.asReversed()
        return listOf(header) + rows.mapIndexed { index, encounter ->
            toRowItem(encounter, positionOf(index, rows.lastIndex))
        }
    }

    /** The label an outing's header carries: the day and time its first cat was seen. */
    fun outingLabel(outing: List<Encounter>, today: LocalDate): String {
        val earliest = outing.minBy { it.occurredAt }
        return "${dateTimeFormatter.dayHeader(earliest.localDate(), today)}, ${earliest.timeLabel()}"
    }

    private fun positionOf(index: Int, lastIndex: Int): GroupPosition = when {
        lastIndex == 0 -> GroupPosition.ONLY
        index == 0 -> GroupPosition.FIRST
        index == lastIndex -> GroupPosition.LAST
        else -> GroupPosition.MIDDLE
    }

    private fun toRowItem(encounter: Encounter, position: GroupPosition): EncounterListItem.Row =
        EncounterListItem.Row(
            id = encounter.id,
            timeLabel = encounter.timeLabel(),
            location = encounter.locationSource.toLocationLabel(),
            lead = encounter.lead(),
            position = position,
        )

    private fun Encounter.lead(): RowLead {
        val thumbnail = thumbPath?.let(photoStorage::resolve)
        val coatOption = coat?.toOption()
        return when {
            thumbnail != null -> RowLead.Photo(thumbnail)
            coatOption != null -> RowLead.Coat(coatOption)
            else -> RowLead.Paw
        }
    }

    private fun Encounter.timeLabel(): String =
        dateTimeFormatter.time(occurredAt, UtcOffset(minutes = tzOffsetMinutes))
}
