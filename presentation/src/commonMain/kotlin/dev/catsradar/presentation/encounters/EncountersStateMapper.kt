package dev.catsradar.presentation.encounters

import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.groupedByShot
import dev.catsradar.domain.platform.PhotoStorage
import dev.catsradar.domain.session.SessionSplitter
import dev.catsradar.presentation.DateTimeFormatter
import dev.catsradar.presentation.coat.toOption
import dev.catsradar.presentation.dayHeader
import dev.catsradar.presentation.map.isOnTheMap
import dev.catsradar.presentation.time
import kotlinx.collections.immutable.toPersistentList
import kotlinx.datetime.LocalDate

class EncountersStateMapper(
    private val dateTimeFormatter: DateTimeFormatter,
    private val photoStorage: PhotoStorage,
) {

    /**
     * [selectedIds] naming no cat on screen are dropped from the result's selection. With [byShot] the cats whose
     * covers share a shot are one cell; without it every cat is a cell of its own.
     */
    fun map(
        encounters: List<Encounter>,
        today: LocalDate,
        grid: Boolean,
        selectedIds: Set<String> = emptySet(),
        byShot: Boolean = true,
    ): EncountersState = EncountersState(
        rows = outingsNewestFirst(encounters)
            .flatMap { outing ->
                val entries = if (byShot) outing.groupedByShot() else outing.map(::listOf)
                val cells = if (grid) {
                    entries.gridRows()
                } else {
                    entries.mapWithGroupPosition { entry, position -> EncountersRow.Single(entry.toCell(), position) }
                }
                val mapOutingId = outing.last().id.takeIf { outing.any { it.isOnTheMap() } }
                listOf(outing.header(today, mapOutingId)) + cells
            }
            .toPersistentList(),
        layout = if (grid) EncountersLayout.GRID else EncountersLayout.LIST,
    ).withSelection(selectedIds)

    /** The header label the list gives [outing]. */
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
            label = "${dateTimeFormatter.dayHeader(earliest, today)}, ${earliest.timeLabel()}",
            mapOutingId = mapOutingId,
        )
    }

    private fun List<List<Encounter>>.gridRows(): List<EncountersRow> =
        EncounterGridPacker.pack(this, hasPhoto = { it.first().thumbnail() != null }).map { row ->
            when (row) {
                is PackedRow.PhotoPair -> EncountersRow.PhotoPair(row.first.toPhotoCell(), row.second.toPhotoCell())
                is PackedRow.Tiles -> EncountersRow.Tiles(row.cats.map { it.toCell() }.toPersistentList())
                is PackedRow.Cards -> EncountersRow.Cards(row.cats.map { it.toCell() }.toPersistentList())
            }
        }

    // The first cat speaks for the entry: the others copied its time, place and photo when they joined it.
    private fun List<Encounter>.toCell(): EncounterCell = with(first()) {
        EncounterCell(
            id = id,
            timeLabel = timeLabel(),
            location = locationSource.toLocationLabel(),
            lead = lead(showsCoat = this@toCell.size == 1),
            catIds = this@toCell.map { it.id }.toPersistentList(),
        )
    }

    private fun List<Encounter>.toPhotoCell(): PhotoCell = with(first()) {
        val thumbnail = checkNotNull(thumbnail()) { "a pair holds only cats with a photo" }
        PhotoCell(
            id = id,
            timeLabel = timeLabel(),
            location = locationSource.toLocationLabel(),
            photoPath = photoStorage.resolve(photos.first().photoPath),
            thumbnailPath = thumbnail,
            catIds = this@toPhotoCell.map { it.id }.toPersistentList(),
        )
    }

    private fun Encounter.thumbnail(): String? = cover?.thumbPath?.let(photoStorage::resolve)

    /** Without [showsCoat] a cell with no picture shows a paw: one coat would misname the cats of a shot. */
    private fun Encounter.lead(showsCoat: Boolean): CellLead {
        val thumbnail = thumbnail()
        val coatOption = coat?.toOption()?.takeIf { showsCoat }
        return when {
            thumbnail != null -> CellLead.Photo(thumbnail)
            coatOption != null -> CellLead.Coat(coatOption)
            else -> CellLead.Paw
        }
    }

    private fun Encounter.timeLabel(): String = dateTimeFormatter.time(this)
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
