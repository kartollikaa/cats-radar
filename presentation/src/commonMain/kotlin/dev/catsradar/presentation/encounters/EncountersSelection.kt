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
        selectedIds = marked.flatMap { it.selectedCatIds() }.toPersistentSet(),
    )
}

/** Every cat of the cell that stands for [id]; just [id] when no cell on screen does. */
internal fun EncountersState.catsOfCell(id: String): Set<String> =
    rows.asSequence().flatMap { it.cellCats() }.firstOrNull { id in it }?.toSet() ?: setOf(id)

private fun EncountersRow.marked(ids: Set<String>): EncountersRow = when (this) {
    is OutingHeader -> this
    is EncountersRow.PhotoPair -> copy(
        first = first.copy(selected = first.catIds.anyIn(ids)),
        second = second.copy(selected = second.catIds.anyIn(ids)),
    )
    is EncountersRow.Tiles -> copy(cells = cells.marked(ids))
    is EncountersRow.Cards -> copy(cells = cells.marked(ids))
    is EncountersRow.Single -> copy(cell = cell.copy(selected = cell.catIds.anyIn(ids)))
}

private fun List<EncounterCell>.marked(ids: Set<String>): ImmutableList<EncounterCell> =
    map { it.copy(selected = it.catIds.anyIn(ids)) }.toPersistentList()

private fun List<String>.anyIn(ids: Set<String>): Boolean = any { it in ids }

private fun EncountersRow.selectedCatIds(): List<String> = when (this) {
    is OutingHeader -> emptyList()
    is EncountersRow.PhotoPair -> listOf(first, second).filter { it.selected }.flatMap { it.catIds }
    is EncountersRow.Tiles -> cells.filter { it.selected }.flatMap { it.catIds }
    is EncountersRow.Cards -> cells.filter { it.selected }.flatMap { it.catIds }
    is EncountersRow.Single -> listOf(cell).filter { it.selected }.flatMap { it.catIds }
}

private fun EncountersRow.cellCats(): List<List<String>> = when (this) {
    is OutingHeader -> emptyList()
    is EncountersRow.PhotoPair -> listOf(first.catIds, second.catIds)
    is EncountersRow.Tiles -> cells.map { it.catIds }
    is EncountersRow.Cards -> cells.map { it.catIds }
    is EncountersRow.Single -> listOf(cell.catIds)
}
