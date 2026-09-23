package dev.catsradar.presentation.encounters

internal sealed interface PackedRow<out T> {
    data class PhotoPair<T>(val first: T, val second: T) : PackedRow<T>

    data class Tiles<T>(val cats: List<T>) : PackedRow<T>

    data class Cards<T>(val cats: List<T>) : PackedRow<T>
}

/**
 * Keeps the cats' order. Two photos in a row share a pair; the run between pairs is one card row when
 * shorter than [MIN_TILES], else tile rows of [MIN_TILES] to [MAX_TILES], as even as they can be.
 */
internal object EncounterGridPacker {

    private const val MIN_TILES = 3
    private const val MAX_TILES = 5

    fun <T> pack(cats: List<T>, hasPhoto: (T) -> Boolean): List<PackedRow<T>> {
        val rows = mutableListOf<PackedRow<T>>()
        val run = mutableListOf<T>()
        var index = 0
        while (index < cats.size) {
            val next = cats.getOrNull(index + 1)
            if (next != null && hasPhoto(cats[index]) && hasPhoto(next)) {
                rows += splitRun(run.toList())
                run.clear()
                rows += PackedRow.PhotoPair(cats[index], next)
                index += 2
            } else {
                run += cats[index]
                index += 1
            }
        }
        rows += splitRun(run.toList())
        return rows
    }

    private fun <T> splitRun(run: List<T>): List<PackedRow<T>> = when {
        run.isEmpty() -> emptyList()
        run.size < MIN_TILES -> listOf(PackedRow.Cards(run))
        else -> balancedTileRows(run)
    }

    private fun <T> balancedTileRows(run: List<T>): List<PackedRow<T>> {
        val rowCount = (run.size + MAX_TILES - 1) / MAX_TILES
        val shortRowSize = run.size / rowCount
        val longRowCount = run.size % rowCount
        return List(rowCount) { row ->
            val start = row * shortRowSize + minOf(row, longRowCount)
            val size = if (row < longRowCount) shortRowSize + 1 else shortRowSize
            PackedRow.Tiles(run.subList(start, start + size).toList())
        }
    }
}
