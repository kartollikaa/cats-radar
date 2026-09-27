package dev.catsradar.presentation.encounters

internal sealed interface PackedRow<out T> {
    data class PhotoPair<T>(val first: T, val second: T) : PackedRow<T>

    data class Tiles<T>(val cats: List<T>) : PackedRow<T>

    data class Cards<T>(val cats: List<T>) : PackedRow<T>
}

/**
 * Keeps the cats' order. Two photos in a row share a pair; the run between pairs is one card row when
 * shorter than [MIN_TILES], else tile rows of [MIN_TILES] to [MAX_TILES], as even as they can be. A lone photo's
 * tile row holds [MIN_TILES] cats where that sends no other photo to a card row.
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
                rows += splitRun(run.toList(), hasPhoto)
                run.clear()
                rows += PackedRow.PhotoPair(cats[index], next)
                index += 2
            } else {
                run += cats[index]
                index += 1
            }
        }
        rows += splitRun(run.toList(), hasPhoto)
        return rows
    }

    /**
     * Keeps [run]'s photos out of card rows, then out of tile rows wider than [MIN_TILES], then uses the fewest rows
     * and the fewest card rows. A photo no three-cat row can take without pushing another into a card stays wide.
     */
    private fun <T> splitRun(run: List<T>, hasPhoto: (T) -> Boolean): List<PackedRow<T>> {
        fun List<PackedRow<T>>.photosIn(where: (PackedRow<T>) -> Boolean): Int =
            filter(where).sumOf { row -> row.runCats().count(hasPhoto) }
        val byPhotoSize = compareBy<List<PackedRow<T>>>(
            { rows -> rows.photosIn { it is PackedRow.Cards } },
            { rows -> rows.photosIn { it is PackedRow.Tiles && it.cats.size > MIN_TILES } },
            { it.size },
            { rows -> rows.count { it is PackedRow.Cards } },
        )
        val best = HashMap<Int, List<PackedRow<T>>>()
        fun from(start: Int): List<PackedRow<T>> = best.getOrPut(start) {
            val rest = run.subList(start, run.size)
            val photo = rest.indexOfFirst(hasPhoto)
            if (photo == -1 || rest.size < MIN_TILES) return@getOrPut splitPlainRun(rest)
            val photoRows = (maxOf(0, photo - MIN_TILES + 1)..minOf(photo, rest.size - MIN_TILES)).map { offset ->
                splitPlainRun(rest.subList(0, offset)) +
                    PackedRow.Tiles(rest.subList(offset, offset + MIN_TILES).toList()) +
                    from(start + offset + MIN_TILES)
            }
            (photoRows + listOf(splitPlainRun(rest))).minWith(byPhotoSize)
        }
        return from(0)
    }

    private fun <T> PackedRow<T>.runCats(): List<T> = when (this) {
        is PackedRow.PhotoPair -> listOf(first, second)
        is PackedRow.Tiles -> cats
        is PackedRow.Cards -> cats
    }

    private fun <T> splitPlainRun(run: List<T>): List<PackedRow<T>> = when {
        run.isEmpty() -> emptyList()
        run.size < MIN_TILES -> listOf(PackedRow.Cards(run.toList()))
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
