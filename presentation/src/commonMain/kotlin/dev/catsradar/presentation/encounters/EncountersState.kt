package dev.catsradar.presentation.encounters

import dev.catsradar.presentation.coat.CoatOption
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

data class EncountersState(val rows: ImmutableList<EncounterListItem> = persistentListOf()) {
    val isEmpty: Boolean get() = rows.isEmpty()
}

sealed interface EncounterListItem {
    val key: String

    data class OutingHeader(override val key: String, val label: String) : EncounterListItem

    data class Row(
        val id: String,
        val timeLabel: String,
        val location: LocationLabel,
        /** Absolute path of the thumbnail, or null for a tally and for a photo whose thumbnail failed. */
        val thumbnailPath: String? = null,
        /** Null when nobody noted the coat. */
        val coat: CoatOption? = null,
        val position: GroupPosition = GroupPosition.ONLY,
    ) : EncounterListItem {
        override val key: String get() = id
    }
}

/** Where a row sits among its outing's rows, which are drawn as one group. */
enum class GroupPosition { FIRST, MIDDLE, LAST, ONLY }
