package dev.catsradar.presentation.encounters

import dev.catsradar.presentation.coat.CoatOption
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

data class EncountersState(
    val rows: ImmutableList<EncountersRow> = persistentListOf(),
    val layout: EncountersLayout = EncountersLayout.GRID,
    val selectedIds: ImmutableSet<String> = persistentSetOf(),
    /** How many cats the last delete removed while its undo is still offered; null once it is not. */
    val removedCount: Int? = null,
) {
    val isEmpty: Boolean get() = rows.isEmpty()
    val isSelecting: Boolean get() = selectedIds.isNotEmpty()
    val selectedCount: Int get() = selectedIds.size
}

enum class EncountersLayout { GRID, LIST }

sealed interface EncountersRow {
    val key: String

    data class PhotoPair(val first: PhotoCell, val second: PhotoCell) : EncountersRow {
        override val key: String get() = "pair-${first.id}"
    }

    data class Tiles(val cells: ImmutableList<EncounterCell>) : EncountersRow {
        init {
            require(cells.isNotEmpty()) { "a tile row holds at least one cat" }
        }

        override val key: String get() = "tiles-${cells.first().id}"
    }

    data class Cards(val cells: ImmutableList<EncounterCell>) : EncountersRow {
        init {
            require(cells.isNotEmpty()) { "a card row holds at least one cat" }
        }

        override val key: String get() = "cards-${cells.first().id}"
    }

    /** One cat on a full row, joined to the rows of the same outing above and below it. */
    data class Single(val cell: EncounterCell, val position: GroupPosition) : EncountersRow {
        override val key: String get() = "single-${cell.id}"
    }
}

/** Where a row sits among the rows of its outing. */
enum class GroupPosition { FIRST, MIDDLE, LAST, ONLY }

data class OutingHeader(
    override val key: String,
    val label: String,
    /** The id the map focuses this outing by, when one of its cats has a location. */
    val mapOutingId: String? = null,
) : EncountersRow

/** [id] is the cat a tap opens; [catIds] are every cat the cell stands for, [id] first. */
data class EncounterCell(
    val id: String,
    val timeLabel: String,
    val location: LocationLabel,
    val lead: CellLead = CellLead.Paw,
    val selected: Boolean = false,
    val catIds: ImmutableList<String> = persistentListOf(id),
) {
    init {
        require(catIds.firstOrNull() == id) { "a cell opens the first of its cats" }
    }

    val badgeCount: Int? get() = catIds.badgeCount()
}

/**
 * Both paths are absolute; [thumbnailPath] stands in for [photoPath] when that one cannot be read.
 * [id] is the cat a tap opens; [catIds] are every cat the cell stands for, [id] first.
 */
data class PhotoCell(
    val id: String,
    val timeLabel: String,
    val location: LocationLabel,
    val photoPath: String,
    val thumbnailPath: String,
    val selected: Boolean = false,
    val catIds: ImmutableList<String> = persistentListOf(id),
) {
    init {
        require(catIds.firstOrNull() == id) { "a cell opens the first of its cats" }
    }

    val badgeCount: Int? get() = catIds.badgeCount()
}

private fun List<String>.badgeCount(): Int? = size.takeIf { it > 1 }

/** What a cell shows first: the most telling thing known about that cat. */
sealed interface CellLead {
    /** [thumbnailPath] is absolute. */
    data class Photo(val thumbnailPath: String) : CellLead

    data class Coat(val coat: CoatOption) : CellLead

    data object Paw : CellLead
}
