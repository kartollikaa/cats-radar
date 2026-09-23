package dev.catsradar.presentation.encounters

import dev.catsradar.presentation.coat.CoatOption
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

data class EncountersState(
    val rows: ImmutableList<EncounterListItem> = persistentListOf(),
    val selectedIds: ImmutableSet<String> = persistentSetOf(),
    /** How many cats the last delete removed while its undo is still offered; null once it is not. */
    val removedCount: Int? = null,
) {
    val isEmpty: Boolean get() = rows.isEmpty()
    val isSelecting: Boolean get() = selectedIds.isNotEmpty()
    val selectedCount: Int get() = selectedIds.size
}

sealed interface EncounterListItem {
    val key: String

    data class OutingHeader(override val key: String, val label: String) : EncounterListItem

    data class Row(
        val id: String,
        val timeLabel: String,
        val location: LocationLabel,
        val lead: RowLead = RowLead.Paw,
        val position: GroupPosition = GroupPosition.ONLY,
        val selected: Boolean = false,
    ) : EncounterListItem {
        override val key: String get() = id
    }
}

/** What a row shows first: the most telling thing known about that cat. */
sealed interface RowLead {
    /** [thumbnailPath] is absolute. */
    data class Photo(val thumbnailPath: String) : RowLead

    data class Coat(val coat: CoatOption) : RowLead

    data object Paw : RowLead
}

/** Where a row sits among the rows of its outing. */
enum class GroupPosition { FIRST, MIDDLE, LAST, ONLY }
