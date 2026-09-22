package dev.catsradar.presentation.encounters

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

data class EncountersState(
    val rows: ImmutableList<EncounterListItem> = persistentListOf(),
    val isEmpty: Boolean = true,
)

sealed interface EncounterListItem {
    val key: String

    data class DayHeader(override val key: String, val dayLabel: String) : EncounterListItem

    data class Row(val id: String, val timeLabel: String, val locationLabel: String) : EncounterListItem {
        override val key: String get() = id
    }
}
