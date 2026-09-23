package dev.catsradar.presentation.encounters

import dev.catsradar.presentation.coat.CoatOption
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

data class EncountersState(val rows: ImmutableList<EncounterListItem> = persistentListOf()) {
    val isEmpty: Boolean get() = rows.isEmpty()
}

sealed interface EncounterListItem {
    val key: String

    data class OutingHeader(
        override val key: String,
        val label: String,
        /** The id the map focuses this outing by, when one of its cats has a location. */
        val mapOutingId: String? = null,
    ) : EncounterListItem

    data class Row(
        val id: String,
        val timeLabel: String,
        val location: LocationLabel,
        val lead: RowLead = RowLead.Paw,
        val position: GroupPosition = GroupPosition.ONLY,
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
