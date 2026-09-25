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

    /** [selectedIds] naming no cat on screen are dropped from the result's selection. */
    fun map(
        encounters: List<Encounter>,
        today: LocalDate,
        grid: Boolean,
        selectedIds: Set<String> = emptySet(),
    ): EncountersState = EncountersState(
        rows = outingsNewestFirst(encounters)
            .flatMap { outing ->
                val cats = if (grid) {
                    outing.gridRows()
                } else {
                    outing.mapWithGroupPosition { encounter, position ->
                        EncountersRow.Single(encounter.toCell(), position)
                    }
                }
                val mapOutingId = outing.last().id.takeIf { outing.any { it.isOnTheMap() } }
                listOf(outing.header(today, mapOutingId)) + cats
            }
            .toPersistentList(),
        layout = if (grid) EncountersLayout.GRID else EncountersLayout.LIST,
    ).withSelection(selectedIds)

    /** The label the list heads [outing]'s run of cards with. */
    fun outingLabel(outing: List<Encounter>, today: LocalDate): String =
        outingsNewestFirst(outing).first().header(today).label

    private fun outingsNewestFirst(encounters: List<Encounter>): List<List<Encounter>> =
        SessionSplitter.groupByOuting(encounters).asReversed().map { it.asReversed() }

    // Keyed to the outing's earliest encounter (its "start", per outings.md), so a midnight-
    // crossing outing keeps one header; the start time tells same-day outings apart.
    private fun List<Encounter>.header(today: LocalDate, mapOutingId: String? = null): OutingHeader {
        val earliest = last()
        return OutingHeader(
            key = "header-${earliest.id}",
            label = "${dateTimeFormatter.dayHeader(earliest.localDate(), today)}, ${earliest.timeLabel()}",
            mapOutingId = mapOutingId,
        )
    }

    private fun List<Encounter>.gridRows(): List<EncountersRow> =
        EncounterGridPacker.pack(this, hasPhoto = { it.thumbnail() != null }).map { row ->
            when (row) {
                is PackedRow.PhotoPair -> EncountersRow.PhotoPair(row.first.toPhotoCell(), row.second.toPhotoCell())
                is PackedRow.Tiles -> EncountersRow.Tiles(row.cats.map { it.toCell() }.toPersistentList())
                is PackedRow.Cards -> EncountersRow.Cards(row.cats.map { it.toCell() }.toPersistentList())
            }
        }

    private fun Encounter.toCell(): EncounterCell = EncounterCell(
        id = id,
        timeLabel = timeLabel(),
        location = locationSource.toLocationLabel(),
        lead = lead(),
    )

    private fun Encounter.toPhotoCell(): PhotoCell {
        val thumbnail = checkNotNull(thumbnail()) { "a pair holds only cats with a photo" }
        return PhotoCell(
            id = id,
            timeLabel = timeLabel(),
            location = locationSource.toLocationLabel(),
            photoPath = photoPath?.let(photoStorage::resolve) ?: thumbnail,
            thumbnailPath = thumbnail,
        )
    }

    private fun Encounter.thumbnail(): String? = thumbPath?.let(photoStorage::resolve)

    private fun Encounter.lead(): CellLead {
        val thumbnail = thumbnail()
        val coatOption = coat?.toOption()
        return when {
            thumbnail != null -> CellLead.Photo(thumbnail)
            coatOption != null -> CellLead.Coat(coatOption)
            else -> CellLead.Paw
        }
    }

    private fun Encounter.timeLabel(): String =
        dateTimeFormatter.time(occurredAt, UtcOffset(minutes = tzOffsetMinutes))
}

private inline fun <T, R> List<T>.mapWithGroupPosition(transform: (T, GroupPosition) -> R): List<R> =
    mapIndexed { index, item ->
        val position = when {
            lastIndex == 0 -> GroupPosition.ONLY
            index == 0 -> GroupPosition.FIRST
            index == lastIndex -> GroupPosition.LAST
            else -> GroupPosition.MIDDLE
        }
        transform(item, position)
    }
