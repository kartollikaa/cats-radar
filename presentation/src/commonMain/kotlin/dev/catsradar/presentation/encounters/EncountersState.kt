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
    /** Null with no cats. */
    val totals: EncountersTotals? = null,
) {
    val isEmpty: Boolean get() = rows.isEmpty()
    val isSelecting: Boolean get() = selectedIds.isNotEmpty()
    val selectedCount: Int get() = selectedIds.size
}

enum class EncountersLayout { GRID, LIST }

/** Counts, not labels: only the platform knows their plural forms. [cats] counts a shot's cats each. */
data class EncountersTotals(val cats: Int, val outings: Int)

/** A row of the list. A pair, tile or card row whose `closesOuting` is set is the last row of its outing. */
sealed interface EncountersRow {
    val key: String

    data class PhotoPair(
        val first: PhotoCell,
        val second: PhotoCell,
        val closesOuting: Boolean = false,
    ) : EncountersRow {
        override val key: String get() = "pair-${first.id}"
    }

    data class Tiles(val cells: ImmutableList<EncounterCell>, val closesOuting: Boolean = false) : EncountersRow {
        init {
            require(cells.isNotEmpty()) { "a tile row holds at least one cat" }
        }

        override val key: String get() = "tiles-${cells.first().id}"
    }

    data class Cards(val cells: ImmutableList<EncounterCell>, val closesOuting: Boolean = false) : EncountersRow {
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

/**
 * [count] is every cat of the outing, a shot's cats each; [spanLabel] is null when its cats are under a minute apart;
 * [onWalk] says a stored walk overlapped it.
 */
data class OutingHeader(
    override val key: String,
    val label: String,
    /** The id the map focuses this outing by, when one of its cats has a location. */
    val mapOutingId: String? = null,
    val dayLabel: String = "",
    val startLabel: String = "",
    val count: Int = 0,
    val spanLabel: String? = null,
    val onWalk: Boolean = false,
) : EncountersRow

/** One cell of the list: [id] is the cat a tap opens; [catIds] are every cat the cell stands for, [id] first. */
sealed interface EntryCell {
    val id: String
    val catIds: ImmutableList<String>
    val selected: Boolean

    /** Null for a cell of one cat. */
    val badgeCount: Int? get() = catIds.size.takeIf { it > 1 }
}

data class EncounterCell(
    override val id: String,
    val timeLabel: String,
    val location: LocationLabel,
    val lead: CellLead = CellLead.Paw,
    override val selected: Boolean = false,
    override val catIds: ImmutableList<String> = persistentListOf(id),
) : EntryCell {
    init {
        requireOpensFirstCat()
    }
}

/** Both paths are absolute; [thumbnailPath] stands in for [photoPath] when that one cannot be read. */
data class PhotoCell(
    override val id: String,
    val timeLabel: String,
    val location: LocationLabel,
    val photoPath: String,
    val thumbnailPath: String,
    override val selected: Boolean = false,
    override val catIds: ImmutableList<String> = persistentListOf(id),
) : EntryCell {
    init {
        requireOpensFirstCat()
    }
}

private fun EntryCell.requireOpensFirstCat() =
    require(catIds.firstOrNull() == id) { "a cell opens the first of its cats" }

/** What a cell shows first: the most telling thing known about that cat. */
sealed interface CellLead {
    /** [thumbnailPath] is absolute; [coat] is the one cat's coat, null for a shot of several or with none noted. */
    data class Photo(val thumbnailPath: String, val coat: CoatOption? = null) : CellLead

    data class Coat(val coat: CoatOption) : CellLead

    data object Paw : CellLead
}
