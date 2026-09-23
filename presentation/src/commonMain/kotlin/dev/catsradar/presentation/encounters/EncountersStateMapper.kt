package dev.catsradar.presentation.encounters

import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.platform.PhotoStorage
import dev.catsradar.domain.session.SessionSplitter
import dev.catsradar.domain.time.localDate
import dev.catsradar.presentation.DateTimeFormatter
import dev.catsradar.presentation.coat.toOption
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.datetime.LocalDate
import kotlinx.datetime.UtcOffset

class EncountersStateMapper(
    private val dateTimeFormatter: DateTimeFormatter,
    private val photoStorage: PhotoStorage,
) {

    fun map(encounters: List<Encounter>, today: LocalDate): EncountersState = EncountersState(
        rows = outingsNewestFirst(encounters)
            .flatMap { outing -> listOf(outing.header(today)) + outing.gridRows() }
            .toPersistentList(),
    )

    fun mapList(encounters: List<Encounter>, today: LocalDate): ImmutableList<EncounterListItem> =
        outingsNewestFirst(encounters)
            .flatMap { outing -> listOf(outing.header(today)) + outing.map { it.toListRow() } }
            .toPersistentList()

    private fun outingsNewestFirst(encounters: List<Encounter>): List<List<Encounter>> =
        SessionSplitter.groupByOuting(encounters).asReversed().map { it.asReversed() }

    // Keyed to the outing's earliest encounter (its "start", per outings.md), so a midnight-
    // crossing outing keeps one header; the start time tells same-day outings apart.
    private fun List<Encounter>.header(today: LocalDate): OutingHeader {
        val earliest = last()
        return OutingHeader(
            key = "header-${earliest.id}",
            label = "${dateTimeFormatter.dayHeader(earliest.localDate(), today)}, ${earliest.timeLabel()}",
        )
    }

    private fun List<Encounter>.gridRows(): List<EncounterGridRow> =
        EncounterGridPacker.pack(this, hasPhoto = { it.thumbPath != null }).map { row ->
            when (row) {
                is PackedRow.PhotoPair -> EncounterGridRow.PhotoPair(row.first.toPhotoCell(), row.second.toPhotoCell())
                is PackedRow.Tiles -> EncounterGridRow.Tiles(row.cats.map { it.toCell() }.toPersistentList())
                is PackedRow.Cards -> EncounterGridRow.Cards(row.cats.map { it.toCell() }.toPersistentList())
            }
        }

    private fun Encounter.toCell(): EncounterCell = EncounterCell(
        id = id,
        timeLabel = timeLabel(),
        location = locationSource.toLocationLabel(),
        lead = lead(),
    )

    private fun Encounter.toPhotoCell(): PhotoCell {
        val photo = checkNotNull(photoPath ?: thumbPath) { "a pair holds only cats with a photo" }
        return PhotoCell(
            id = id,
            timeLabel = timeLabel(),
            location = locationSource.toLocationLabel(),
            photoPath = photoStorage.resolve(photo),
        )
    }

    private fun Encounter.toListRow(): EncounterListItem.Row = EncounterListItem.Row(
        id = id,
        timeLabel = timeLabel(),
        location = locationSource.toLocationLabel(),
    )

    private fun Encounter.lead(): CellLead {
        val thumbnail = thumbPath?.let(photoStorage::resolve)
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
