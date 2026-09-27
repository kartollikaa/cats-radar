package dev.catsradar.presentation.encounters

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentSet

/**
 * Re-marks the cells already on screen for [ids]: a cell naming any of them is selected with every cat it stands
 * for, and ids that name no cat shown are dropped.
 */
internal fun EncountersState.withSelection(ids: Set<String>): EncountersState {
    val marked = rows.map { row -> row.marked(ids) }
    return copy(
        rows = marked.toPersistentList(),
        selectedIds = marked.flatMap { it.cells() }.filter { it.selected }.flatMap { it.catIds }.toPersistentSet(),
    )
}

/** Every cat of the cell that stands for [id]; just [id] when no cell on screen does. */
internal fun EncountersState.catsOfCell(id: String): Set<String> =
    rows.asSequence().flatMap { it.cells() }.firstOrNull { id in it.catIds }?.catIds?.toSet() ?: setOf(id)

private fun EncountersRow.marked(ids: Set<String>): EncountersRow = when (this) {
    is OutingHeader -> this
    is EncountersRow.PhotoPair -> copy(
        first = first.copy(selected = first.namesAnyOf(ids)),
        second = second.copy(selected = second.namesAnyOf(ids)),
    )
    is EncountersRow.Tiles -> copy(cells = cells.marked(ids))
    is EncountersRow.Cards -> copy(cells = cells.marked(ids))
    is EncountersRow.Single -> copy(cell = cell.copy(selected = cell.namesAnyOf(ids)))
}

private fun List<EncounterCell>.marked(ids: Set<String>): ImmutableList<EncounterCell> =
    map { it.copy(selected = it.namesAnyOf(ids)) }.toPersistentList()

private fun EntryCell.namesAnyOf(ids: Set<String>): Boolean = catIds.any { it in ids }

private fun EncountersRow.cells(): List<EntryCell> = when (this) {
    is OutingHeader -> emptyList()
    is EncountersRow.PhotoPair -> listOf(first, second)
    is EncountersRow.Tiles -> cells
    is EncountersRow.Cards -> cells
    is EncountersRow.Single -> listOf(cell)
}
