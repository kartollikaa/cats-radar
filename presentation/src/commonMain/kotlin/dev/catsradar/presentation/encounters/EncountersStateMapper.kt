package dev.catsradar.presentation.encounters

import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.platform.PhotoStorage
import dev.catsradar.domain.session.SessionSplitter
import dev.catsradar.domain.time.localDate
import dev.catsradar.presentation.DateTimeFormatter
import dev.catsradar.presentation.coat.toOption
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.datetime.LocalDate
import kotlinx.datetime.UtcOffset

class EncountersStateMapper(
    private val dateTimeFormatter: DateTimeFormatter,
    private val photoStorage: PhotoStorage,
) {

    /** [selectedIds] naming no row on the list are dropped from the result's selection. */
    fun map(encounters: List<Encounter>, today: LocalDate, selectedIds: Set<String> = emptySet()): EncountersState {
        val rows = SessionSplitter.groupByOuting(encounters)
            .asReversed()
            .flatMap { outing -> outingToItems(outing, today) }
        return select(rows, selectedIds)
    }

    /** Re-marks already mapped [rows] for [selectedIds], dropping ids that name no row. */
    fun select(rows: List<EncounterListItem>, selectedIds: Set<String>): EncountersState {
        val marked = rows.map { item ->
            if (item is EncounterListItem.Row) item.copy(selected = item.id in selectedIds) else item
        }
        val selectedOnList = marked.filterIsInstance<EncounterListItem.Row>().filter { it.selected }.map { it.id }
        return EncountersState(rows = marked.toPersistentList(), selectedIds = selectedOnList.toPersistentSet())
    }

    // Keyed to the outing's earliest encounter (its "start", per outings.md), so a midnight-
    // crossing outing keeps one header; the start time tells same-day outings apart.
    private fun outingToItems(outing: List<Encounter>, today: LocalDate): List<EncounterListItem> {
        val earliest = outing.first()
        val header = EncounterListItem.OutingHeader(
            key = "header-${earliest.id}",
            label = "${dateTimeFormatter.dayHeader(earliest.localDate(), today)}, ${earliest.timeLabel()}",
        )
        val rows = outing.asReversed()
        return listOf(header) + rows.mapIndexed { index, encounter ->
            toRowItem(encounter, positionOf(index, rows.lastIndex))
        }
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
