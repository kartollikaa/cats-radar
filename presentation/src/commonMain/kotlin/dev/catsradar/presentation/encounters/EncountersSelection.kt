package dev.catsradar.presentation.encounters

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentSet

/** Re-marks the cats already on screen for [ids], dropping ids that name no cat shown. */
internal fun EncountersState.withSelection(ids: Set<String>): EncountersState {
    val marked = rows.map { row -> row.marked(ids) }
    return copy(
        rows = marked.toPersistentList(),
        selectedIds = marked.flatMap { it.selectedCatIds() }.toPersistentSet(),
    )
}

private fun EncountersRow.marked(ids: Set<String>): EncountersRow = when (this) {
    is OutingHeader -> this
    is EncountersRow.PhotoPair -> copy(
        first = first.copy(selected = first.id in ids),
        second = second.copy(selected = second.id in ids),
    )
    is EncountersRow.Tiles -> copy(cells = cells.marked(ids))
    is EncountersRow.Cards -> copy(cells = cells.marked(ids))
    is EncountersRow.Single -> copy(cell = cell.copy(selected = cell.id in ids))
}

private fun List<EncounterCell>.marked(ids: Set<String>): ImmutableList<EncounterCell> =
    map { it.copy(selected = it.id in ids) }.toPersistentList()

private fun EncountersRow.selectedCatIds(): List<String> = when (this) {
    is OutingHeader -> emptyList()
    is EncountersRow.PhotoPair -> listOf(first, second).filter { it.selected }.map { it.id }
    is EncountersRow.Tiles -> cells.filter { it.selected }.map { it.id }
    is EncountersRow.Cards -> cells.filter { it.selected }.map { it.id }
    is EncountersRow.Single -> listOf(cell).filter { it.selected }.map { it.id }
}
