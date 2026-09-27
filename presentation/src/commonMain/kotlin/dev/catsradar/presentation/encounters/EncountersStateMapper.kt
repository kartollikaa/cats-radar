package dev.catsradar.presentation.encounters

import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.Walk
import dev.catsradar.domain.model.groupedByShot
import dev.catsradar.domain.platform.PhotoStorage
import dev.catsradar.domain.session.SessionSplitter
import dev.catsradar.presentation.DateTimeFormatter
import dev.catsradar.presentation.coat.toOption
import dev.catsradar.presentation.dayHeader
import dev.catsradar.presentation.map.isOnTheMap
import dev.catsradar.presentation.time
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.datetime.LocalDate
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class EncountersStateMapper(
    private val dateTimeFormatter: DateTimeFormatter,
    private val photoStorage: PhotoStorage,
) {

    /** The cats whose covers share a shot are one cell; [selectedIds] naming no cat on screen are dropped. */
    fun map(
        encounters: List<Encounter>,
        today: LocalDate,
        grid: Boolean,
        selectedIds: Set<String> = emptySet(),
        walks: List<Walk> = emptyList(),
    ): EncountersState {
        val outings = outingsNewestFirst(encounters)
        return EncountersState(
            rows = outings.rows(today, grid, byShot = true, walks),
            layout = if (grid) EncountersLayout.GRID else EncountersLayout.LIST,
            totals = outings.takeIf { it.isNotEmpty() }
                ?.let { EncountersTotals(cats = it.sumOf { outing -> outing.size }, outings = it.size) },
        ).withSelection(selectedIds)
    }

    /** Every cat on a list row of its own under its outing's header, a shot's cats each apart. */
    fun catRows(encounters: List<Encounter>, today: LocalDate): ImmutableList<EncountersRow> =
        outingsNewestFirst(encounters).rows(today, grid = false, byShot = false)

    /** The header label the list gives [outing]. */
    fun outingLabel(outing: List<Encounter>, today: LocalDate): String =
        outingsNewestFirst(outing).first().header(today).label

    private fun List<List<Encounter>>.rows(
        today: LocalDate,
        grid: Boolean,
        byShot: Boolean,
        walks: List<Walk> = emptyList(),
    ): ImmutableList<EncountersRow> = flatMap { outing ->
        val entries = if (byShot) outing.groupedByShot() else outing.map(::listOf)
        val cells = if (grid) {
            entries.gridRows()
        } else {
            entries.mapWithGroupPosition { entry, position -> EncountersRow.Single(entry.toCell(), position) }
        }
        val mapOutingId = outing.last().id.takeIf { outing.any { it.isOnTheMap() } }
        listOf(outing.header(today, mapOutingId, walks)) + cells
    }.toPersistentList()

    // Keyed to the outing's earliest encounter (its "start", per outings.md), so a midnight-
    // crossing outing keeps one header; the start time tells same-day outings apart.
    private fun List<Encounter>.header(
        today: LocalDate,
        mapOutingId: String? = null,
        walks: List<Walk> = emptyList(),
    ): OutingHeader {
        val earliest = last()
        val latest = first()
        val day = dateTimeFormatter.dayHeader(earliest, today)
        val start = dateTimeFormatter.time(earliest)
        val span = latest.occurredAt - earliest.occurredAt
        return OutingHeader(
            key = "header-${earliest.id}",
            label = "$day, $start",
            mapOutingId = mapOutingId,
            dayLabel = day,
            startLabel = start,
            count = size,
            spanLabel = dateTimeFormatter.duration(span).takeIf { span >= shortestSpanShown },
            onWalk = walks.any { it.meets(earliest.occurredAt, latest.occurredAt) },
        )
    }

    private fun List<List<Encounter>>.gridRows(): List<EncountersRow> {
        val packed = EncounterGridPacker.pack(this, hasPhoto = { it.first().thumbnail() != null })
        return packed.mapIndexed { index, row ->
            val closes = index == packed.lastIndex
            when (row) {
                is PackedRow.PhotoPair ->
                    EncountersRow.PhotoPair(row.first.toPhotoCell(), row.second.toPhotoCell(), closesOuting = closes)
                is PackedRow.Tiles ->
                    EncountersRow.Tiles(row.cats.map { it.toCell() }.toPersistentList(), closesOuting = closes)
                is PackedRow.Cards ->
                    EncountersRow.Cards(row.cats.map { it.toCell() }.toPersistentList(), closesOuting = closes)
            }
        }
    }

    // The first cat speaks for the entry: the others copied its time, place and photo when they joined it.
    private fun List<Encounter>.toCell(): EncounterCell = with(first()) {
        EncounterCell(
            id = id,
            timeLabel = dateTimeFormatter.time(this),
            location = locationSource.toLocationLabel(),
            lead = lead(showsCoat = this@toCell.size == 1),
            catIds = this@toCell.map { it.id }.toPersistentList(),
        )
    }

    private fun List<Encounter>.toPhotoCell(): PhotoCell = with(first()) {
        val thumbnail = checkNotNull(thumbnail()) { "a pair holds only cats with a photo" }
        PhotoCell(
            id = id,
            timeLabel = dateTimeFormatter.time(this),
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
            thumbnail != null -> CellLead.Photo(thumbnail, coatOption)
            coatOption != null -> CellLead.Coat(coatOption)
            else -> CellLead.Paw
        }
    }
}

// A span is told in whole minutes, so a shorter one would read "0 min".
private val shortestSpanShown = 1.minutes

private fun outingsNewestFirst(encounters: List<Encounter>): List<List<Encounter>> =
    SessionSplitter.groupByOuting(encounters).asReversed().map { it.asReversed() }

// A walk still on has no end yet, so it meets every outing from its start on.
private fun Walk.meets(start: Instant, end: Instant): Boolean =
    startedAt <= end && endedAt.let { it == null || it >= start }

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
