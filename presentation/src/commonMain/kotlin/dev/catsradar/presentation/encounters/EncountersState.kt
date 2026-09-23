package dev.catsradar.presentation.encounters

import dev.catsradar.presentation.coat.CoatOption
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

data class EncountersState(
    val rows: ImmutableList<EncountersRow> = persistentListOf(),
    val layout: EncountersLayout = EncountersLayout.GRID,
) {
    val isEmpty: Boolean get() = rows.isEmpty()
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

/** One row per cat, for lists that show every cat the same way. */
sealed interface EncounterListItem {
    val key: String

    data class Row(val id: String, val timeLabel: String, val location: LocationLabel) : EncounterListItem {
        override val key: String get() = id
    }
}

data class OutingHeader(override val key: String, val label: String) : EncountersRow, EncounterListItem

data class EncounterCell(
    val id: String,
    val timeLabel: String,
    val location: LocationLabel,
    val lead: CellLead = CellLead.Paw,
)

/** Both paths are absolute; [thumbnailPath] stands in for [photoPath] when that one cannot be read. */
data class PhotoCell(
    val id: String,
    val timeLabel: String,
    val location: LocationLabel,
    val photoPath: String,
    val thumbnailPath: String,
)

/** What a cell shows first: the most telling thing known about that cat. */
sealed interface CellLead {
    /** [thumbnailPath] is absolute. */
    data class Photo(val thumbnailPath: String) : CellLead

    data class Coat(val coat: CoatOption) : CellLead

    data object Paw : CellLead
}
